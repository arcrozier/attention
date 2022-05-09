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
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
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

    val friends = attentionRepository.getFriends()

    val cachedFriends = attentionRepository.getCachedFriends()

    var isRefreshing by mutableStateOf(false)

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

    private var lastNameRequest: Call<GenericResult<NameResult>>? = null

    /**
     * Used to determine which dialog to display (or none). If the dialog requires additional data,
     * like a user ID, this can be placed in the second part of the pair
     */
    val dialogState = mutableStateOf(Triple<DialogStatus, Friend?, (String) -> Unit>(
            DialogStatus
                    .NONE,
            null
    ) {})

    private val dialogQueue = PriorityQueueSet<Triple<DialogStatus, Friend?, (String) -> Unit>> { t,
                                                                                                  t2 ->
        val typeCompare = t.first.compareTo(t2.first)
        if (typeCompare != 0) {
            typeCompare
        } else {
            t.second?.name?.compareTo(t.first.name) ?: 0
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
            if (dialogQueue.isEmpty() && dialogState.value.first == DialogStatus.NONE)
                dialogState.value = state
            else dialogQueue.add(state)
        }
    }

    fun swapDialogState(state: Triple<DialogStatus, Friend?, (String) -> Unit>) {
        synchronized(this) {
            if (dialogState.value.first == DialogStatus.NONE) {
                dialogState.value = state
            } else {
                dialogQueue.add(dialogState.value)
                dialogState.value = state
            }
        }
    }

    private fun uploadCachedFriends() {
        backgroundScope.launch {
            val token = getApplication<Application>().getSharedPreferences(
                    USER_INFO, Context
                    .MODE_PRIVATE
            ).getString(MY_TOKEN, null) ?: return@launch
            val friends: List<CachedFriend> = attentionRepository.getCachedFriendsSnapshot()
            for (friend in friends) {
                attentionRepository.getName(token, friend.username,
                        responseListener = { _, response, _ ->
                            if (response.isSuccessful) {
                                response.body()?.data?.name?.let {
                                    attentionRepository.addFriend(friend.username,
                                            it,
                                            token, responseListener = { _, response, _ ->
                                        if (response.isSuccessful || response.code() == 400)
                                            attentionRepository.deleteCachedFriend(friend.username)
                                    })
                                }
                            } else if (response.code() == 400) {
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

    fun onAddFriend(
            friend: Friend, responseListener: ((Call<GenericResult<Void>>,
                                                Response<GenericResult<Void>>) -> Unit)? = null,
            launchLogin: () -> Unit
    ) {
        val token = getApplication<Application>().getSharedPreferences(
                USER_INFO, Context
                .MODE_PRIVATE
        ).getString(MY_TOKEN, null)
        if (token == null) {
            attentionRepository.cacheFriend(friend.id)
            addFriendException = false
            popDialogState()
            launchLogin()
            return
        }
        addFriendException = false
        attentionRepository.addFriend(
                friend.id, friend.name, token, responseListener = { call, response, _ ->
            when (response.code()) {
                200 -> {
                    responseListener?.invoke(call, response)
                }
                400 -> {
                    usernameCaption = getApplication<Application>().getString(
                            R.string.add_friend_failed
                    )
                }
                403 -> {
                    attentionRepository.cacheFriend(friend.id)
                    launchLogin()
                }
            }
        }
        ) { _, _ ->
            attentionRepository.cacheFriend(friend.id)
            connectionState = getApplication<Application>().getString(R.string.disconnected)
        }
    }

    override fun onCleared() {
        super.onCleared()
        backgroundScope.cancel()
    }

    fun getFriendName(
            username: String, responseListener: ((name: String) -> Unit)? = null,
            launchLogin: () -> Unit
    ) {
        val token = getApplication<Application>().getSharedPreferences(
                USER_INFO, Context
                .MODE_PRIVATE
        ).getString(MY_TOKEN, null)
        if (token == null) {
            if (!addFriendException) launchLogin()
            else {
                responseListener?.invoke(username)
                newFriendName = username
            }
            return
        }

        lastNameRequest?.cancel()
        friendNameLoading = true
        lastNameRequest = attentionRepository.getName(token, username,
                responseListener = { _, response, _ ->
            if (connectionState != getApplication<Application>().getString(R.string.sharing))
                connectionState = ""
            lastNameRequest = null
            newFriendName = response.body()?.data?.name ?: ""
            friendNameLoading = false
            when (response.code()) {
                200 -> {
                    usernameCaption = ""
                    responseListener?.invoke(newFriendName)
                }
                400 -> {
                    usernameCaption =
                            getApplication<Application>().getString(R.string.nonexistent_username)
                }
                403 -> {
                    if (!addFriendException) launchLogin()
                    else responseListener?.invoke(
                            username
                    )
                }
            }
        }, errorListener = { _, t ->
            if (t is IOException) return@getName
            lastNameRequest = null
            friendNameLoading = false
            newFriendName = ""
            if (connectionState != getApplication<Application>().getString(R.string.sharing))
                connectionState = getApplication<Application>().getString(R.string.disconnected)
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
        val token = getApplication<Application>().getSharedPreferences(
                USER_INFO, Context
                .MODE_PRIVATE
        ).getString(MY_TOKEN, null)
        if (token == null) {
            launchLogin()
            return
        }
        attentionRepository.delete(friend, token)
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

    private fun errorListener() {
        val context = getApplication<Application>()
        if (connectionState != context.getString(R.string.sharing)) {
            connectionState = getApplication<Application>().getString(R.string.disconnected)
        }
    }

    /**
     * Modifies the friend's name associated with the ID
     *
     * @param id    The ID of the friend to edit
     * @param name  The new name of the friend
     */
    fun confirmEditName(id: String, name: String, launchLogin: () -> Unit) {
        val token = getApplication<Application>().getSharedPreferences(
                USER_INFO, Context
                .MODE_PRIVATE
        ).getString(MY_TOKEN, null)
        if (token == null) {
            launchLogin()
            return
        }
        attentionRepository.edit(Friend(id = id, name = name), token, responseListener = { _,
                                                                                           response, _ ->
            val context = getApplication<Application>()
            when (response.code()) {
                200 -> {
                    if (connectionState != context.getString(R.string.sharing)) {
                        connectionState = ""
                    }
                }
                400 -> {
                    showSnackBar(context.getString(R.string.edit_friend_name_failed))
                }
                403 -> {
                    val loginIntent = Intent(context, LoginActivity::class.java)
                    context.startActivity(loginIntent)
                }
                else -> {
                    if (connectionState != context.getString(R.string.sharing)) {
                        connectionState = context.getString(R.string.connection_error)
                    }
                }
            }

        },
                errorListener = { _, _ ->
                    errorListener()
                })
    }

    private suspend fun getFriend(id: String): Friend {
        return attentionRepository.getFriend(id)
    }

    /**
     * Vibrates the phone to signal to the user that they have long-pressed
     */
    fun onLongPress() {
        @Suppress("DEPRECATION")
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                    getApplication<Application>().getSystemService(
                            AppCompatActivity
                                    .VIBRATOR_MANAGER_SERVICE
                    ) as
                            VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getApplication<Application>().getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as
                    Vibrator
        }
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect: VibrationEffect =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(
                            VibrationEffect.EFFECT_HEAVY_CLICK
                    ) else VibrationEffect.createOneShot(
                            100, COMPAT_HEAVY_CLICK
                    )
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(100)
        }
    }

    /**
     * Notifies the user that an alert was not successfully sent
     *
     * @param message  - The message to display in the body of the notification
     * @requires    - Code is one of ErrorType.SERVER_ERROR or ErrorType.BAD_REQUEST
     */
    private fun notifyUser(message: String) {
        MainScope().launch {
            val context = getApplication<Application>()
            val text = message

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent =
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            createFailedAlertNotificationChannel(context)
            val builder: NotificationCompat.Builder =
                    NotificationCompat.Builder(context, FAILED_ALERT_CHANNEL_ID)
            builder
                    .setSmallIcon(R.drawable.app_icon_foreground)
                    .setContentTitle(context.getString(R.string.alert_failed))
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent).setAutoCancel(true)

            val notificationID = (System.currentTimeMillis() % 1000000000L).toInt() + 1
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.notify(notificationID, builder.build())
        }
    }

    fun getUserInfo(token: String, onAuthError: () -> Unit) {
        val context = getApplication<Application>()
        attentionRepository.downloadUserInfo(token, { _, response, _ ->
            when (response.code()) {
                200 -> {
                    if (connectionState != context.getString(R.string.sharing)) {
                        connectionState = ""
                    }
                    Log.d(sTAG, response.body().toString())
                    val defaultPrefsEditor = PreferenceManager
                            .getDefaultSharedPreferences(context)
                            .edit()
                    val data = response.body()?.data
                    if (data == null) {
                        Log.e(sTAG, "Got user info but body was null!")
                        return@downloadUserInfo
                    }
                    defaultPrefsEditor.putString(
                            context.getString(R.string.username_key), data.username
                    )
                    defaultPrefsEditor.putString(
                            context.getString(R.string.first_name_key),
                            data.firstName
                    )
                    defaultPrefsEditor.putString(
                            context.getString(R.string.last_name_key),
                            data.lastName
                    )
                    defaultPrefsEditor.putString(
                            context.getString(R.string.email_key),
                            data.email
                    )
                    defaultPrefsEditor.apply()
                    attentionRepository.updateUserInfo(
                            data.friends
                    )
                    uploadCachedFriends()
                    populateShareTargets()
                    isRefreshing = false
                }
                403 -> {
                    onAuthError()
                }
                else -> {
                    if (connectionState != context.getString(R.string.sharing)) {
                        connectionState = context.getString(R.string.server_error)
                    }
                }
            }
        }, { _, _ ->
            isRefreshing = false
            errorListener()
        })
    }

    fun registerDevice() {
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
                    { _, response, errorBody ->
                        when (response.code()) {
                            200 -> {
                                Log.d(sTAG, "Successfully uploaded token")
                                fcmTokenPrefs.edit().apply {
                                    putBoolean(TOKEN_UPLOADED, true)
                                    apply()
                                }
                            }
                            else -> {
                                Log.e(sTAG, "Error uploading token: ${errorBody}")
                            }
                        }
                    },
            )
        } else if (fcmToken == null) { // We don't have a token, so let's get one
            getToken(context)
        }
    }

    fun loadUserPrefs() {
        val context = getApplication<Application>()
        // Load user preferences and data
        val prefs = context.getSharedPreferences(FCM_TOKEN, AppCompatActivity.MODE_PRIVATE)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val notificationValues = context.resources.getStringArray(R.array.notification_values)
        // Set up default preferences
        if (!settings.contains(context.getString(R.string.ring_preference_key))) {
            val settingsEditor = settings.edit()
            val ringAllowed: MutableSet<String> = HashSet()
            ringAllowed.add(notificationValues[2])
            settingsEditor.putStringSet(
                    context.getString(R.string.ring_preference_key),
                    ringAllowed
            )
            settingsEditor.apply()
        }
        if (!settings.contains(context.getString(R.string.vibrate_preference_key))) {
            val settingsEditor = settings.edit()
            val vibrateAllowed: MutableSet<String> = HashSet()
            vibrateAllowed.add(notificationValues[1])
            vibrateAllowed.add(notificationValues[2])
            settingsEditor.putStringSet(
                    context.getString(R.string.vibrate_preference_key),
                    vibrateAllowed
            )
            settingsEditor.apply()
        }
        if (!prefs.contains(TOKEN_UPLOADED)) {
            val editor = prefs.edit()
            editor.putBoolean(TOKEN_UPLOADED, false)
            editor.apply()
        }
    }

    fun sendAlert(to: String, message: String?, launchLogin: () -> Unit) {
        val context = getApplication<Application>()
        val token = context.getSharedPreferences(
                USER_INFO, Context
                .MODE_PRIVATE
        ).getString(MY_TOKEN, null)
        if (token == null) {
            launchLogin()
            return
        }
        MainScope().launch {
            val name = getFriend(to).name
            attentionRepository.sendMessage(Message(
                    timestamp = System.currentTimeMillis(),
                    otherId = to, message
            = message, direction = DIRECTION.Outgoing
            ), token = token,
                    { _, response, errorBody ->
                        when (response.code()) {
                            200 -> {
                                val body = response.body()
                                if (body == null) {
                                    Log.e(sTAG, "Got response but body was null")
                                    return@sendMessage
                                }
                                showSnackBar(getApplication<Application>().getString(R.string
                                        .alert_sent))
                            }
                            400 -> {
                                if (errorBody == null) {
                                    Log.e(sTAG, "Got response but body was null")
                                    return@sendMessage
                                }
                                when {
                                    errorBody.contains("Could not find user", true) -> {
                                        notifyUser(context.getString(
                                                R.string.alert_failed_no_user, name))
                                    }
                                    else -> {
                                        notifyUser(
                                                context.getString(R.string.alert_failed_bad_request,
                                                        name)
                                        )
                                    }
                                }
                            }
                            403 -> {
                                if (errorBody == null) {
                                    Log.e(sTAG, "Got response but body was null")
                                    return@sendMessage
                                }
                                when {
                                    errorBody
                                            .contains("does not have you as a friend", true)
                                    -> {
                                        notifyUser(
                                                context.getString(
                                                        R.string
                                                                .alert_failed_not_friend, name
                                                )
                                        )
                                    }
                                    else ->
                                        launchLogin()
                                }
                            }
                            else -> {
                                notifyUser(
                                        context.getString(R.string.alert_failed_server_error, name))
                            }
                        }
                    }, { _, _ ->
                notifyUser(
                        context.getString(R.string.alert_failed_no_connection, name)
                )
            })
        }
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


            val preferences = context.getSharedPreferences(
                    USER_INFO,
                    Context.MODE_PRIVATE
            )
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
                            { _, response, _ ->
                                when (response.code()) {
                                    200 -> {
                                        editor.putBoolean(TOKEN_UPLOADED, true)
                                        editor.apply()
                                        Toast.makeText(
                                                context,
                                                context.getString(R.string.user_registered),
                                                Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
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
        const val TOKEN_UPLOADED = "token_needs_upload"
        const val USER_INFO = "user"
        const val MY_ID = "id"
        const val MY_TOKEN = "token"
        const val FCM_TOKEN = "fcm_token"
        const val FAILED_ALERT_CHANNEL_ID = "Failed alert channel"

        private const val MAX_SHORTCUTS = 4
        private const val SHARE_CATEGORY =
                "com.aracroproducts.attentionv2.sharingshortcuts.category.TEXT_SHARE_TARGET"

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