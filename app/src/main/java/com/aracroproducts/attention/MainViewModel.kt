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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import java.util.*
import javax.inject.Inject

class MainViewModel @Inject constructor(
        private val attentionRepository: AttentionRepository,
        application: Application
) : AndroidViewModel(application) {
    // private val _friends: MutableLiveData<MutableMap<String, Friend>> = MutableLiveData
    // (HashMap())
    // val friends: LiveData<MutableMap<String, Friend>> = _friends

    enum class DialogStatus {
        USERNAME, OVERLAY_PERMISSION, ADD_MESSAGE_TEXT, FRIEND_NAME, CONFIRM_DELETE, NONE
    }

    /**
     * Used to store the different return types from the connect function
     */
    enum class ErrorType(val code: Int) {
        OK(CODE_SUCCESS),  // Request was successful
        BAD_REQUEST(CODE_BAD_REQUEST),  // Something was wrong with the request (don't retry)
        SERVER_ERROR(CODE_SERVER_ERROR),  // The server isn't working (don't retry)
        CONNECTION_FAILED(
                CODE_CONNECTION_FAILED)  // There was an issue with the connection (should retry)
    }

    val friends = attentionRepository.getFriends().asLiveData()

    var isSnackBarShowing: Boolean by mutableStateOf(false)
        private set

    private fun showSnackBar() {
        isSnackBarShowing = true
    }

    fun dismissSnackBar() {
        isSnackBarShowing = false
    }

    /**
     * Used to determine which dialog to display (or none). If the dialog requires additional data,
     * like a user ID, this can be placed in the second part of the pair
     */
    val dialogState = mutableStateOf(Triple<DialogStatus, Friend?, (String) -> Unit>(DialogStatus
            .NONE,
            null) {})

    private val dialogQueue = PriorityQueueSet<Triple<DialogStatus, Friend?, (String) -> Unit>> {
        t,
                                                                                             t2 ->
        val typeCompare = t.first.compareTo(t2.first)
        if (typeCompare != 0) {
            typeCompare
        } else {
            if (t.second != null) {
                t.second!!.name.compareTo(t.first.name)
            } else {
                1
            }
        }
    }

    /**
     * Called to get the next expected dialog state
     *
     * This will set the dialogState value and remove the current state from the queue
     *
     * As soon as the dialog state has been handled, call this again to update the UI. This
     * should be called only by whatever is responsible for dealing with that state
     */
    fun popDialogState() {
        synchronized(this) {
            if (dialogQueue.isEmpty()) {
                dialogState.value = Triple(DialogStatus.NONE, null) {}
            } else {
                dialogState.value = dialogQueue.remove()
            }
        }
    }

    /**
     * Adds a new dialog state to the queue. If the queue is empty, updates dialogState immediately
     */
    fun appendDialogState(state: Triple<DialogStatus, Friend?, (String) -> Unit>) {
        if (state.first == DialogStatus.NONE) return
        synchronized(this) {
            if (dialogQueue.isEmpty()) dialogState.value = state
            else dialogQueue.add(state)
        }
    }

    fun onAddFriend(friend: Friend) {
        attentionRepository.insert(friend)
    }

    fun onDeleteFriend(friend: Friend) {
        appendDialogState(Triple(DialogStatus.NONE, friend) {})
    }

    fun confirmDeleteFriend(friend: Friend) {
        attentionRepository.delete(friend)
    }

    // Shows the edit name dialog
    fun onEditName(friend: Friend) {
        appendDialogState(Triple(DialogStatus.FRIEND_NAME, friend) {})
    }

    fun confirmEditName(id: String, name: String) {
        attentionRepository.edit(Friend(id = id, name = name))
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
        appendDialogState(Triple(DialogStatus.CONFIRM_DELETE, friend) {})
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
    private fun notifyUser(code: ErrorType, id: String) {
        val context = getApplication<Application>()
        val name = getFriend(id)
        val text = if (code == ErrorType.SERVER_ERROR)
            context.getString(R.string.alert_failed_server_error, name)
        else context.getString(R.string.alert_failed_bad_request, name)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        createFailedAlertNotificationChannel(context)
        val builder: NotificationCompat.Builder =
                NotificationCompat.Builder(context, FAILED_ALERT_CHANNEL_ID)
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
        val userInfo = application.getSharedPreferences(USER_INFO, Context.MODE_PRIVATE)
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(application)
        // Verify Firebase token and continue configuring settings
        val token = userInfo.getString(MY_TOKEN, null)

        // Check if there is already a key pair
        if (!attentionRepository.keyExists()) {
            // No key pair - generate a new one
            attentionRepository.genKeyPair()

            // Signal that the new key needs to be uploaded
            val editor = userInfo.edit()
            editor.putBoolean(KEY_UPLOADED, false)
            editor.apply()
        }

        // Do we need to prompt user for a name?
        if (!defaultPrefs.contains(application.getString(R.string.name_key))) {
            appendDialogState(Triple(DialogStatus.USERNAME, null) {})
        }
        // Do we need to upload a token (note we don't want to upload if we don't have a token yet)
        if (token != null && !userInfo.getBoolean(TOKEN_UPLOADED, false)) {
            attentionRepository.sendToken(token, NetworkSingleton.getInstance(application), {
                Log.d(sTAG, "Successfully uploaded token")
                val editor = userInfo.edit()
                editor.putBoolean(KEY_UPLOADED, true)
                editor.putBoolean(TOKEN_UPLOADED, true)
                editor.apply()
            }, {
                Log.e(sTAG, "Error uploading token: ${it.message}")
            }, userInfo.getBoolean(KEY_UPLOADED, false))
        } else if (token == null) { // We don't have a token, so let's get one
            getToken(application)
        }

        if (!Settings.canDrawOverlays(application) && !userInfo.getBoolean(OVERLAY_NO_PROMPT,
                        false)) {
            appendDialogState(Triple(DialogStatus.OVERLAY_PERMISSION, null) {})
        }
    }

    fun loadUserPrefs() {
        val context = getApplication<Application>()
        // Load user preferences and data
        val prefs = context.getSharedPreferences(USER_INFO, AppCompatActivity.MODE_PRIVATE)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val notificationValues = context.resources.getStringArray(R.array.notification_values)
        // Set up default preferences
        if (!settings.contains(context.getString(R.string.ring_preference_key))) {
            val settingsEditor = settings.edit()
            val ringAllowed: MutableSet<String> = HashSet()
            ringAllowed.add(notificationValues[2])
            settingsEditor.putStringSet(context.getString(R.string.ring_preference_key),
                    ringAllowed)
            settingsEditor.apply()
        }
        if (!settings.contains(context.getString(R.string.vibrate_preference_key))) {
            val settingsEditor = settings.edit()
            val vibrateAllowed: MutableSet<String> = HashSet()
            vibrateAllowed.add(notificationValues[1])
            vibrateAllowed.add(notificationValues[2])
            settingsEditor.putStringSet(context.getString(R.string.vibrate_preference_key),
                    vibrateAllowed)
            settingsEditor.apply()
        }
        if (!prefs.contains(TOKEN_UPLOADED)) {
            val editor = prefs.edit()
            editor.putBoolean(TOKEN_UPLOADED, false)
            editor.apply()
        }
    }

    fun sendAlert(to: String, message: String?) {
        attentionRepository.sendMessage(Message(timestamp = System.currentTimeMillis(),
                otherId = to, message
        = message, direction = DIRECTION.Outgoing),
                NetworkSingleton.getInstance(getApplication<Application>()), {
            showSnackBar()
        }, {
            Log.e(sTAG, "Error sending alert: ${it.message}")
            notifyUser(ErrorType.BAD_REQUEST, to)
        })
    }

    /**
     * Helper method that gets the Firebase token
     *
     * Automatically uploads the token and updates the "uploaded" sharedPreference
     */
    private fun getToken(context: Context) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String?> ->
            if (!task.isSuccessful) {
                Log.w(sTAG, "getInstanceId failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new Instance ID token
            val token = task.result
            Log.d(sTAG, "Got token! $token")

            val preferences = context.getSharedPreferences(USER_INFO,
                    AppCompatActivity.MODE_PRIVATE)
            if (token != null && token != preferences.getString(MY_TOKEN, "")) {
                attentionRepository.sendToken(token, NetworkSingleton
                        .getInstance(context),
                        {
                            val editor = preferences.edit()
                            editor.putBoolean(TOKEN_UPLOADED, true)
                            editor.putBoolean(KEY_UPLOADED, true)
                            editor.apply()
                            Toast.makeText(context, context.getString(R.string.user_registered),
                                    Toast.LENGTH_SHORT).show()
                        }, { error ->
                    Log.e(sTAG, "An error occurred while uploading token: ${error.message}")
                }, preferences.getBoolean(KEY_UPLOADED, false))
            }

            // Log and toast
            val msg = context.getString(R.string.msg_token_fmt, token)
            Log.d(sTAG, msg)
        }
    }

    companion object {
        private val sTAG: String = MainViewModel::class.java.name

        private const val COMPAT_HEAVY_CLICK = 5

        const val OVERLAY_NO_PROMPT = "OverlayDoNotAsk"
        const val KEY_UPLOADED = "public_key_requires_auth"
        const val TOKEN_UPLOADED = "token_needs_upload"
        const val USER_INFO = "user"
        const val FRIENDS = "listen"
        const val MY_ID = "id"
        const val MY_NAME = "name"
        const val MY_TOKEN = "token"
        const val FAILED_ALERT_CHANNEL_ID = "Failed alert channel"


        const val CODE_SUCCESS = 0
        const val CODE_SERVER_ERROR = 1
        const val CODE_BAD_REQUEST = 2
        const val CODE_CONNECTION_FAILED = -1

        /**
         * Helper function to create the notification channel for the failed alert
         *
         * @param context   A context for getting strings
         */
        fun createFailedAlertNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name: CharSequence = context.getString(R.string.alert_failed_channel_name)
                val description = context.getString(R.string.alert_failed_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel =
                        NotificationChannel(FAILED_ALERT_CHANNEL_ID, name, importance)
                channel.description = description
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }


}