package com.aracroproducts.attentionv2

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
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.preference.PreferenceManager
import com.android.volley.*
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.Integer.min
import javax.inject.Inject

class MainViewModel @Inject internal constructor(
        private val attentionRepository: AttentionRepository,
        application: Application
) : AndroidViewModel(application) {

    enum class DialogStatus {
        ADD_FRIEND, OVERLAY_PERMISSION, ADD_MESSAGE_TEXT, FRIEND_NAME, CONFIRM_DELETE,
        CONFIRM_DELETE_CACHED, NONE
    }

    /**
     * Used to store the different return types from the connect function
     */
    enum class ErrorType {
        BAD_REQUEST,  // Something was wrong with the request (don't retry)
        SERVER_ERROR,  // The server isn't working (don't retry)
        CONNECTION_FAILED  // There was an issue with the connection (should retry)
    }

    val friends = attentionRepository.getFriends().asLiveData()

    val cachedFriends = attentionRepository.getCachedFriends().asLiveData()

    var isSnackBarShowing by mutableStateOf("")
        private set

    var connectionState by mutableStateOf("")

    var addFriendException by mutableStateOf(false)

    var addFriendUsername by mutableStateOf("")

    private val backgroundScope = MainScope()

    fun showSnackBar(message: String) {
        isSnackBarShowing = message
    }

    fun dismissSnackBar() {
        isSnackBarShowing = ""
    }

    var newFriendName by mutableStateOf("")
    var friendNameLoading by mutableStateOf(false)

    var usernameCaption by mutableStateOf("")

    var message by mutableStateOf("")

    private var lastNameRequest: Request<JSONObject>? = null

    /**
     * Used to determine which dialog to display (or none). If the dialog requires additional data,
     * like a user ID, this can be placed in the second part of the pair
     */
    val dialogState = mutableStateOf(Triple<DialogStatus, Friend?, (String) -> Unit>(DialogStatus
            .NONE,
            null) {})

    private val dialogQueue = PriorityQueueSet<Triple<DialogStatus, Friend?, (String) -> Unit>> { t,
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
        newFriendName = ""
        usernameCaption = ""
        addFriendUsername = ""
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

    private fun uploadCachedFriends() {
        backgroundScope.launch {
            val token = getApplication<Application>().getSharedPreferences(USER_INFO, Context
                    .MODE_PRIVATE).getString(MY_TOKEN, null) ?: return@launch
            val friends: List<CachedFriend> = attentionRepository.getCachedFriendsSnapshot()
            for (friend in friends) {
                attentionRepository.getName(token, friend.username, NetworkSingleton.getInstance
                (getApplication()), responseListener = {
                    attentionRepository.addFriend(friend.username, it.getString("name"), token,
                            NetworkSingleton.getInstance(getApplication()), responseListener = {
                                attentionRepository.deleteCachedFriend(friend.username)
                    }, errorListener = { error ->
                        if (error is ClientError && error.networkResponse.statusCode == 400) {
                            attentionRepository.deleteCachedFriend(friend.username)
                        }
                    })
                }, errorListener = {
                    // Username was invalid - otherwise we leave it cached
                    if (it is ClientError && it.networkResponse.statusCode == 400) {
                        attentionRepository.deleteCachedFriend(friend.username)
                    }
                })
            }
        }
    }

    private fun populateShareTargets() {
        backgroundScope.launch {
            val context = getApplication<Application>()
            val shortcuts = ArrayList<ShortcutInfoCompat>()
            val contactCategories = setOf(SHARE_CATEGORY)

            val friends = attentionRepository.getFriendsSnapshot()
            val staticShortcutIntent = Intent(Intent.ACTION_DEFAULT)

            for (x in 0 until min(friends.size, MAX_SHORTCUTS)) {
                shortcuts.add(
                        ShortcutInfoCompat.Builder(context, friends[x].id)
                                .setShortLabel(friends[x].name)
                                .setIntent(staticShortcutIntent)
                                .setLongLived(true)
                                .setCategories(contactCategories)
                                .setPerson(
                                        Person.Builder()
                                                .setName(friends[x].name)
                                                .build()
                                )
                                .build()
                )
            }
            ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
        }
    }

    fun onAddFriend(friend: Friend, responseListener: Response.Listener<JSONObject>? = null,
                    launchLogin: () -> Unit) {
        val token = getApplication<Application>().getSharedPreferences(USER_INFO, Context
                .MODE_PRIVATE).getString(MY_TOKEN, null)
        if (token == null) {
            if (!addFriendException) launchLogin()
            return
        }
        addFriendException = false
        attentionRepository.addFriend(friend.id, friend.name, token, NetworkSingleton.getInstance
        (getApplication()), responseListener) {
            Log.e(sTAG, "An error occurred when adding friend: ${it.message} ${it.networkResponse}")
            when (it) {
                is ClientError -> {
                    when (it.networkResponse.statusCode) {
                        400 -> {
                            usernameCaption = getApplication<Application>().getString(
                                    R.string.add_friend_failed)
                        }
                        403 -> {
                            attentionRepository.cacheFriend(friend.id)
                            launchLogin()
                        }
                    }
                }
                is NoConnectionError -> {
                    attentionRepository.cacheFriend(friend.id)
                    connectionState = getApplication<Application>().getString(R.string.disconnected)
                }
                else -> {
                    attentionRepository.cacheFriend(friend.id)
                    connectionState = getApplication<Application>().getString(R.string
                            .connection_error)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        backgroundScope.cancel()
    }

    fun getLocalFriendName(username: String) = attentionRepository.getLocalFriendName(username)

    fun getFriendName(username: String, responseListener: Response.Listener<JSONObject>? = null,
                      launchLogin: () -> Unit) {
        val token = getApplication<Application>().getSharedPreferences(USER_INFO, Context
                .MODE_PRIVATE).getString(MY_TOKEN, null)
        if (token == null) {
            if (!addFriendException) launchLogin()
            return
        }

        lastNameRequest?.cancel()
        friendNameLoading = true
        lastNameRequest = attentionRepository.getName(token, username, NetworkSingleton
                .getInstance(getApplication()), responseListener = {
            lastNameRequest = null
            newFriendName = it.getJSONObject("data").getString("name")
            friendNameLoading = false
            responseListener?.onResponse(it)
        }, errorListener = {
            lastNameRequest = null
            friendNameLoading = false
            Log.e(sTAG, "An error occurred when getting friend name: ${it.message} ${it
                    .networkResponse}")
            when (it) {
                is ClientError -> {
                    when (it.networkResponse.statusCode) {
                        400 -> {
                            usernameCaption = getApplication<Application>().getString(R.string.nonexistent_username)
                        }
                        403 -> {
                            if (!addFriendException) launchLogin()
                        }
                    }
                }
                is NoConnectionError -> {
                    connectionState = getApplication<Application>().getString(R.string.disconnected)
                }
                else -> {
                    connectionState = getApplication<Application>().getString(R.string
                            .connection_error)
                }
            }
        })
    }

    /**
     * Updates `DialogState` to display the dialog to confirm deletion of the specified friend
     */
    fun onDeleteFriend(friend: Friend) {
        appendDialogState(Triple(DialogStatus.CONFIRM_DELETE, friend) {})
    }

    fun onDeleteCachedFriend(friend: Friend) {
        appendDialogState(Triple(DialogStatus.CONFIRM_DELETE_CACHED, friend) {})
    }

    /**
     * Deletes the friend from the Room database (does NOT display any confirmation)
     */
    fun confirmDeleteFriend(friend: Friend, launchLogin: () -> Unit) {
        val token = getApplication<Application>().getSharedPreferences(USER_INFO, Context
                .MODE_PRIVATE).getString(MY_TOKEN, null)
        if (token == null) {
            launchLogin()
            return
        }
        attentionRepository.delete(friend, token, NetworkSingleton.getInstance(getApplication()))
    }

    fun confirmDeleteCachedFriend(friend: Friend) {
        attentionRepository.deleteCachedFriend(friend.id)
    }

    /**
     * Shows the edit name dialog (by updating `DialogState`)
     */
    fun onEditName(friend: Friend) {
        appendDialogState(Triple(DialogStatus.FRIEND_NAME, friend) {})
    }

    /**
     * Modifies the friend's name associated with the ID
     *
     * @param id    The ID of the friend to edit
     * @param name  The new name of the friend
     */
    fun confirmEditName(id: String, name: String, launchLogin: () -> Unit) {
        val token = getApplication<Application>().getSharedPreferences(USER_INFO, Context
                .MODE_PRIVATE).getString(MY_TOKEN, null)
        if (token == null) {
            launchLogin()
            return
        }
        attentionRepository.edit(Friend(id = id, name = name), token, NetworkSingleton
                .getInstance(getApplication()), errorListener = {
            val context = getApplication<Application>()
            Log.e(sTAG, "An error occurred when editing name: ${it.message} ${it
                    .networkResponse}")
            when (it) {
                is ClientError -> {
                    when (it.networkResponse.statusCode) {
                        403 -> {
                            val loginIntent = Intent(context, LoginActivity::class.java)
                            context.startActivity(loginIntent)
                        }
                        400 -> {
                            showSnackBar(context.getString(R.string.edit_friend_name_failed))
                        }
                    }
                }
                is NoConnectionError -> {
                    connectionState = context.getString(R.string.disconnected)
                }
                else -> {
                    connectionState = context.getString(R.string.connection_error)
                }
            }
        })
    }

    private fun getFriend(id: String): Friend {
        return attentionRepository.getFriend(id)
    }

    /**
     * Vibrates the phone to signal to the user that they have long-pressed
     */
    fun onLongPress() {
        @Suppress("DEPRECATION")
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                    getApplication<Application>().getSystemService(AppCompatActivity
                            .VIBRATOR_MANAGER_SERVICE) as
                            VibratorManager
            vibratorManager.defaultVibrator
        }
        else {
            getApplication<Application>().getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as
                    Vibrator
        }
        @Suppress("DEPRECATION")
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
                .setSmallIcon(R.mipmap.app_icon)
                .setContentTitle(context.getString(R.string.alert_failed))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent).setAutoCancel(true)

        val notificationID = (System.currentTimeMillis() % 1000000000L).toInt() + 1
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        notificationManagerCompat.notify(notificationID, builder.build())
    }

    fun getUserInfo(token: String, onAuthError: () -> Unit) {
        val context = getApplication<Application>()
        val singleton = NetworkSingleton.getInstance(context)
        attentionRepository.getUserInfo(token, singleton, {
            Log.d(sTAG, it.toString())
            val defaultPrefsEditor = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .edit()
            val data = it.getJSONObject("data")
            defaultPrefsEditor.putString(context.getString(R.string.username_key), data.getString
                ("username"))
            defaultPrefsEditor.putString(
                    context.getString(R.string.first_name_key),
                    data.getString("first_name"))
            defaultPrefsEditor.putString(
                    context.getString(R.string.last_name_key),
                    data.getString("last_name"))
            defaultPrefsEditor.putString(
                    context.getString(R.string.email_key),
                    data.getString("email"))
            defaultPrefsEditor.apply()
            attentionRepository.updateUserInfo(it.getJSONObject("data").getJSONArray("friends"))
            uploadCachedFriends()
            populateShareTargets()
        }, {
            Log.e(sTAG, "An error occurred when initializing: ${it.message} ${it
                    .networkResponse}")
            when (it) {
                is ClientError -> {
                    onAuthError()
                }
                is NoConnectionError -> {
                    connectionState = context.getString(R.string.disconnected)
                }
                else -> {
                    connectionState = context.getString(R.string.connection_error)
                }
            }
        })
    }

    init {
        val context = getApplication<Application>()
        val userInfo = context.getSharedPreferences(USER_INFO, Context.MODE_PRIVATE)

        val fcmTokenPrefs = context.getSharedPreferences(FCM_TOKEN, Context.MODE_PRIVATE)

        // token is auth token
        val token = userInfo.getString(MY_TOKEN, null)

        val fcmToken = fcmTokenPrefs.getString(FCM_TOKEN, null)
        // Do we need to upload a token (note we don't want to upload if we don't have a token yet)
        if (fcmToken != null && !fcmTokenPrefs.getBoolean(TOKEN_UPLOADED, false) && token != null) {
            attentionRepository.registerDevice(
                    token, fcmToken,
                    NetworkSingleton
                            .getInstance(application),
                    { Log.d(sTAG, "Successfully uploaded token")
                    fcmTokenPrefs.edit().apply {
                        putBoolean(TOKEN_UPLOADED, true)
                        apply()
                    }
                    },
                    {
                        Log.e(sTAG, "Error uploading token: ${it.message}")
                    },
            )
        } else if (fcmToken == null) { // We don't have a token, so let's get one
            getToken(application)
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

    fun sendAlert(to: String, message: String?, launchLogin: () -> Unit) {
        val token = getApplication<Application>().getSharedPreferences(USER_INFO, Context
                .MODE_PRIVATE).getString(MY_TOKEN, null)
        if (token == null) {
            launchLogin()
            return
        }
        attentionRepository.sendMessage(Message(timestamp = System.currentTimeMillis(),
                otherId = to, message
        = message, direction = DIRECTION.Outgoing), token = token,
                NetworkSingleton.getInstance(getApplication<Application>()), {
            showSnackBar(getApplication<Application>().getString(R.string.alert_sent))
        }, {
            Log.e(sTAG, "Error sending alert: ${it.message} ${it.networkResponse}")
            when (it) {
                is ClientError -> {
                    notifyUser(ErrorType.BAD_REQUEST, to)
                }
                is NoConnectionError -> {
                    notifyUser(ErrorType.CONNECTION_FAILED, to)
                }
                else -> {
                    notifyUser(ErrorType.SERVER_ERROR, to)
                }
            }
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
                    Context.MODE_PRIVATE)
            val fcmTokenPrefs = context.getSharedPreferences(FCM_TOKEN, Context.MODE_PRIVATE)
            val authToken = preferences.getString(MY_TOKEN, null)
            if (token != null && token != fcmTokenPrefs.getString(FCM_TOKEN, null)) {
                val editor = fcmTokenPrefs.edit()
                editor.putString(FCM_TOKEN, token)
                editor.putBoolean(TOKEN_UPLOADED, false)
                editor.apply()
                if (authToken != null) {
                    attentionRepository.registerDevice(
                            authToken, token,
                            NetworkSingleton
                                    .getInstance(context),
                            {
                                editor.putBoolean(TOKEN_UPLOADED, true)
                                editor.apply()
                                Toast.makeText(context, context.getString(R.string.user_registered),
                                        Toast.LENGTH_SHORT).show()
                            },
                            { error ->
                                Log.e(sTAG,
                                        "An error occurred while uploading token: ${error
                                                .message} ${error.networkResponse}")
                            },
                    )
                }
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
        const val MY_ID = "id"
        const val MY_TOKEN = "token"
        const val FCM_TOKEN = "fcm_token"
        const val FAILED_ALERT_CHANNEL_ID = "Failed alert channel"

        private const val MAX_SHORTCUTS = 4
        private const val SHARE_CATEGORY = "com.aracroproducts.attentionv2.sharingshortcuts.category.TEXT_SHARE_TARGET"

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

        fun launchLogin(context: Context) {
            val loginIntent = Intent(context, LoginActivity::class.java)
            context.startActivity(loginIntent)
        }
    }


}