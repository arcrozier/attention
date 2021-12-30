package com.aracroproducts.attention

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.lang.IllegalStateException
import java.security.KeyPair
import javax.inject.Inject

class MainViewModel @Inject constructor(
        private val attentionRepository: AttentionRepository,
        application: Application
) : AndroidViewModel(application) {
    // private val _friends: MutableLiveData<MutableMap<String, Friend>> = MutableLiveData
    // (HashMap())
    // val friends: LiveData<MutableMap<String, Friend>> = _friends

    enum class DialogStatus {
        NONE, USERNAME, FRIEND_NAME, CONFIRM_DELETE
    }
    val friends = attentionRepository.getFriends().asLiveData()

    val user: MutableLiveData<User> = MutableLiveData(User())

    /**
     * Used to determine which dialog to display (or none). If the dialog requires additional data,
     * like a user ID, this can be placed in the second part of the pair
     */
    val dialogState = mutableStateOf(Pair<DialogStatus, Friend?>(DialogStatus.NONE, null))

    val promptOverlay = mutableStateOf(false)

    fun onAddFriend(friend: Friend) {
        attentionRepository.insert(friend)
    }

    fun onDeleteFriend(friend: Friend) {
        attentionRepository.delete(friend)
        dialogState.value = Pair(DialogStatus.NONE, null)
    }

    fun onEditName(id: String, name: String) {
        attentionRepository.edit(Friend(id = id, name = name))
        dialogState.value = Pair(DialogStatus.NONE, null)
    }

    fun isFromFriend(message: Message): Boolean {
        return attentionRepository.isFromFriend(message = message)
    }

    fun getFriend(id: String): Friend {
        return attentionRepository.getFriend(id)
    }

    /**
     * Vibrates the phone to signal to the user that they have long-pressed
     */
    fun onLongPress() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                    getApplication<Application>().getSystemService(AppCompatActivity
                            .VIBRATOR_MANAGER_SERVICE) as
                            VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getApplication<Application>().getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as
                    Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect: VibrationEffect =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(
                            VibrationEffect.EFFECT_HEAVY_CLICK) else VibrationEffect.createOneShot(
                            100, COMPAT_HEAVY_CLICK)
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(100)
        }
    }

    // Prompts the user to confirm deleting the friend
    fun onDeletePrompt(friend: Friend) {
        dialogState.value = Pair(DialogStatus.CONFIRM_DELETE, friend)
        /*
        val alertDialog = android.app.AlertDialog.Builder(this).create()
        alertDialog.setTitle(context.getString(R.string.confirm_delete_title))
        alertDialog.setMessage(context.getString(R.string.confirm_delete_message, friend.name))
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                getString(R.string.yes)) { dialogInterface: DialogInterface, _: Int ->
            friendModel.onDeleteFriend(friend)
            dialogInterface.cancel()
        }
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(
                android.R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        alertDialog.show()
         */
    }

    /**
     * Notifies the user that an alert was not successfully sent
     *
     * @param code  - The error type; used to display an appropriate message
     * @param id    - The ID of the user that the alert was supposed to be sent to
     * @requires    - Code is one of ErrorType.SERVER_ERROR or ErrorType.BAD_REQUEST
     */
    private fun notifyUser(code: AppWorker.ErrorType, id: String) {
        val context = getApplication<Application>()
        val name = getFriend(id)
        val text = if (code == AppWorker.ErrorType.SERVER_ERROR)
            context.getString(R.string.alert_failed_server_error, name)
        else context.getString(R.string.alert_failed_bad_request, name)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        createFailedAlertNotificationChannel(context)
        val builder: NotificationCompat.Builder =
                NotificationCompat.Builder(context, AppWorker.FAILED_ALERT_CHANNEL_ID)
        builder
                .setSmallIcon(R.mipmap.add_foreground)
                .setContentTitle(context.getString(R.string.alert_failed))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent).setAutoCancel(true)

        val notificationID = (System.currentTimeMillis() % 1000000000L).toInt() + 1
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        notificationManagerCompat.notify(notificationID, builder.build())
    }

    init {
        val userInfo = application.getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE)
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(application)
        // Verify Firebase token and continue configuring settings
        val token = userInfo.getString(MainActivity.MY_TOKEN, null)

        // Check if there is already a key pair
        if (!attentionRepository.keyExists()) {
            // No key pair - generate a new one
            attentionRepository.genKeyPair()

            // Update state
            user.value = User(attentionRepository.getPublicKey(), token)

            // Signal that the new key needs to be uploaded
            val defaultEditor = defaultPrefs.edit()
            defaultEditor.putBoolean(MainActivity.UPLOADED, false)
            defaultEditor.apply()
        } else {
            // Keys already created - load them
            user.value = User(attentionRepository.getPublicKey())
        }
        val publicKeyString = AttentionRepository.keyToString(
                attentionRepository.getPublicKey() ?: throw IllegalStateException("Public key " +
                        "should not be null"))

        // Do we need to prompt user for a name?
        if (!defaultPrefs.contains(application.getString(R.string.name_key))) {
            dialogState.value = Pair(DialogStatus.USERNAME, null)
        }
        // Do we need to upload a token (note we don't want to upload if we don't have a token yet)
        if (token != null && !userInfo.getBoolean(MainActivity.UPLOADED, false)) {
            attentionRepository.sendToken<JSONObject>(token, publicKeyString, NetworkSingleton
                    .getInstance(application).requestQueue)
        } else if (token == null) { // We don't have a token, so let's get one
            getToken(application, publicKeyString)
        }

        if (!Settings.canDrawOverlays(application) && !userInfo.getBoolean(OVERLAY_NO_PROMPT,
                        false)) {
            promptOverlay.value = true
        }
    }

    // Shows the edit name dialog
    fun onEditName(friend: Friend) {
        dialogState.value = Pair(DialogStatus.FRIEND_NAME, friend)
        /*
        val intent = Intent(getApplication(), DialogActivity::class.java)
        intent.putExtra(DialogActivity.EXTRA_EDIT_NAME, true)
        intent.putExtra(DialogActivity.EXTRA_USER_ID, id)
        startEditNameDialogForResult.launch(intent)

         */
    }

    /**
    * Helper method that gets the Firebase token
     *
     * Automatically uploads the token and updates the "uploaded" sharedPreference
    */
    private fun getToken(context: Context, publicKey: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String?> ->
            if (!task.isSuccessful) {
                Log.w(sTAG, "getInstanceId failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new Instance ID token
            val token = task.result
            Log.d(sTAG, "Got token! $token")

            user.value?.token = token
            val preferences = context.getSharedPreferences(MainActivity.USER_INFO,
                    AppCompatActivity.MODE_PRIVATE)
            if (token != null && token != preferences.getString(MainActivity.MY_TOKEN, "")) {
                attentionRepository.sendToken<JSONObject>(token, publicKey, NetworkSingleton
                        .getInstance(context).requestQueue,
                        {
                            // TODO make sure error checking happens in repository
                            val editor = preferences.edit()
                            editor.putBoolean(MainActivity.UPLOADED, true)
                            editor.apply()
                            Toast.makeText(context, context.getString(R.string.user_registered),
                                    Toast.LENGTH_SHORT).show()
                        })
            }

            // Log and toast
            val msg = context.getString(R.string.msg_token_fmt, token)
            Log.d(sTAG, msg)
        }
    }

    companion object {
        private val sTAG: String = MainViewModel::class.java.name

        private const val PREF_PRIVATE_KEY = "private_key"
        private const val PREF_PUBLIC_KEY = "public_key"

        private const val COMPAT_HEAVY_CLICK = 5

        const val OVERLAY_NO_PROMPT = "OverlayDoNotAsk"

        /**
         * Helper function to create the notification channel for the failed alert
         *
         * @param context   A context for getting strings
         */
        fun createFailedAlertNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name: CharSequence = context.getString(R.string.alert_failed_channel_name)
                val description = context.getString(R.string.alert_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel =
                        NotificationChannel(AppWorker.FAILED_ALERT_CHANNEL_ID, name, importance)
                channel.description = description
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }


}