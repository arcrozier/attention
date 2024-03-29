package com.aracroproducts.attentionv2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.SettingsActivity.Companion.DEFAULT_DELAY
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Response
import java.io.File
import java.io.IOException

class MainViewModel(
    private val attentionRepository: AttentionRepository,
    private val preferencesRepository: PreferencesRepository,
    applicationScope: CoroutineScope,
    private val application: AttentionApplication
) : ViewModel() {

    init {
        viewModelScope.launch {
            preferencesRepository.subscribe {
                it[floatPreferencesKey(application.getString(R.string.delay_key))]
            }.collect {
                if (it != null) delay = it
            }
        }

    }

    private val sharedViewModel = SharedViewModel(
        attentionRepository, preferencesRepository, applicationScope, application
    )

    sealed class DialogStatus(val priority: Int) {
        sealed class FriendStatus(val friend: Friend, priority: Int) : DialogStatus(priority)
        object AddFriend : DialogStatus(0)
        object OverlayPermission : DialogStatus(1)
        class AddMessageText(friend: Friend, val onSend: (String) -> Unit) : FriendStatus(friend, 2)
        class FriendName(friend: Friend) : FriendStatus(friend, 3)
        class ConfirmDelete(friend: Friend) : FriendStatus(friend, 4)
        class ConfirmDeleteCached(friend: Friend) : FriendStatus(friend, 5)
        data class PermissionRationale(val permission: String) : DialogStatus(6)
        object None : DialogStatus(7)
    }

    val friends = attentionRepository.getFriends()
    val cardStatus = mutableStateMapOf<String, MainActivity.State>()

    val cachedFriends = attentionRepository.getCachedFriends()

    var isRefreshing by mutableStateOf(false)

    var isSnackBarShowing by mutableStateOf("")
        private set

    var connectionState by mutableStateOf("")
    var connected by mutableStateOf(true)

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

    var waitForLoginResult = false

    var usernameCaption by mutableStateOf("")

    var message by mutableStateOf("")

    private var lastNameRequest: Call<GenericResult<NameResult>>? = null

    var delay = DEFAULT_DELAY

    /**
     * Used to determine which dialog to display (or none). If the dialog requires additional data,
     * like a user ID, this can be placed in the second part of the pair
     */
    val dialogState = mutableStateOf<DialogStatus>(DialogStatus.None)

    private val dialogQueue = PriorityQueueSet<DialogStatus> { t, t2 ->
        val typeCompare = t.priority.compareTo(t2.priority)
        if (typeCompare != 0) {
            typeCompare
        } else if (t is DialogStatus.FriendStatus) {
            t.friend.name.compareTo(t.friend.name)
        } else {
            0
        }
    }

    fun logout(context: Context) = sharedViewModel.logout(context)

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
                dialogState.value = DialogStatus.None
            } else {
                dialogState.value = dialogQueue.remove()
            }
        }
    }

    /**
     * Adds a new dialog state to the queue. If the queue is empty, updates dialogState immediately
     */
    fun appendDialogState(state: DialogStatus) {
        if (state is DialogStatus.None) return
        synchronized(this) {
            if (dialogQueue.isEmpty() && dialogState.value is DialogStatus.None) dialogState.value =
                state
            else dialogQueue.add(state)
        }
    }

    fun swapDialogState(state: DialogStatus) {
        synchronized(this) {
            if (dialogState.value is DialogStatus.None) {
                dialogState.value = state
            } else {
                dialogQueue.add(dialogState.value)
                dialogState.value = state
            }
        }
    }

    private fun uploadCachedFriends() {
        backgroundScope.launch {
            val token = getToken() ?: return@launch
            val friends: List<CachedFriend> = attentionRepository.getCachedFriendsSnapshot()
            for (friend in friends) {
                attentionRepository.getName(token,
                                            friend.username,
                                            responseListener = { _, response, _ ->
                                                connected = true
                                                if (response.isSuccessful) {
                                                    response.body()?.data?.name?.let {
                                                        attentionRepository.addFriend(friend.username,
                                                                                      it,
                                                                                      token,
                                                                                      responseListener = { _, response, _ ->
                                                                                          if (response.isSuccessful || response.code() == 400) backgroundScope.launch {
                                                                                              attentionRepository.deleteCachedFriend(
                                                                                                  friend.username
                                                                                              )
                                                                                          }
                                                                                      })
                                                    }
                                                } else if (response.code() == 400) {
                                                    backgroundScope.launch {
                                                        attentionRepository.deleteCachedFriend(
                                                            friend.username
                                                        )
                                                    }
                                                }
                                            })
            }
        }
    }

    fun onAddFriend(
        friend: Friend, responseListener: ((
            Call<GenericResult<Void>>, Response<GenericResult<Void>>
        ) -> Unit)? = null, launchLogin: () -> Unit
    ) {
        viewModelScope.launch {
            val token = getToken()
            if (token == null) {
                backgroundScope.launch { attentionRepository.cacheFriend(friend.id) }
                addFriendException = false
                popDialogState()
                launchLogin()
                return@launch
            }
            addFriendException = false
            attentionRepository.addFriend(
                friend.id,
                friend.name,
                token,
                responseListener = { call, response, _ ->
                    setConnectStatus(response.code())
                    when (response.code()) {
                        200 -> {
                            responseListener?.invoke(call, response)
                        }
                        400 -> {
                            usernameCaption = application.getString(
                                R.string.add_friend_failed
                            )
                        }
                        403 -> {
                            backgroundScope.launch {
                                attentionRepository.cacheFriend(friend.id)
                            }
                            launchLogin()
                        }
                    }
                }) { _, _ ->
                backgroundScope.launch {
                    attentionRepository.cacheFriend(friend.id)
                }
                setConnectStatus(null)
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        backgroundScope.cancel()
    }

    fun getFriendName(
        username: String,
        responseListener: ((name: String) -> Unit)? = null,
        launchLogin: () -> Unit
    ) {
        viewModelScope.launch {
            val token = getToken()
            if (token == null) {
                if (!addFriendException) launchLogin()
                else {
                    responseListener?.invoke(username)
                    newFriendName = username
                }
                return@launch
            }

            lastNameRequest?.cancel()
            friendNameLoading = true
            lastNameRequest =
                attentionRepository.getName(token, username, responseListener = { _, response, _ ->
                    setConnectStatus(response.code())
                    lastNameRequest = null
                    newFriendName = response.body()?.data?.name ?: ""
                    friendNameLoading = false
                    when (response.code()) {
                        200 -> {
                            usernameCaption = ""
                            responseListener?.invoke(newFriendName)
                        }
                        400 -> {
                            usernameCaption = application.getString(
                                R.string.nonexistent_username
                            )
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
                    setConnectStatus(null)
                })
        }

    }

    /**
     * Updates `DialogState` to display the dialog to confirm deletion of the specified friend
     */
    fun onDeleteFriend(friend: Friend) {
        appendDialogState(DialogStatus.ConfirmDelete(friend))
    }

    fun onDeleteCachedFriend(friend: Friend) {
        appendDialogState(DialogStatus.ConfirmDeleteCached(friend))
    }

    /**
     * Deletes the friend from the Room database (does NOT display any confirmation)
     */
    fun confirmDeleteFriend(friend: Friend, launchLogin: () -> Unit) {
        viewModelScope.launch {
            val token = getToken()
            if (token == null) {
                launchLogin()
                return@launch
            }
            attentionRepository.delete(friend, token)
        }

    }

    fun confirmDeleteCachedFriend(friend: Friend) {
        backgroundScope.launch {
            attentionRepository.deleteCachedFriend(friend.id)
        }
    }

    /**
     * Shows the edit name dialog (by updating `DialogState`)
     */
    fun onEditName(friend: Friend) {
        appendDialogState(DialogStatus.FriendName(friend))
    }

    /**
     * Modifies the friend's name associated with the ID
     *
     * @param id    The ID of the friend to edit
     * @param name  The new name of the friend
     */
    fun confirmEditName(id: String, name: String, launchLogin: () -> Unit) {
        viewModelScope.launch {
            val token = getToken()
            if (token == null) {
                launchLogin()
                return@launch
            }
            attentionRepository.edit(Friend(id = id, name = name),
                                     token,
                                     responseListener = { _, response, _ ->
                                         val context = application
                                         setConnectStatus(response.code())
                                         when (response.code()) {
                                             400 -> {
                                                 showSnackBar(context.getString(R.string.edit_friend_name_failed))
                                             }
                                             403 -> {
                                                 val loginIntent =
                                                     Intent(context, LoginActivity::class.java)
                                                 context.startActivity(loginIntent)
                                             }
                                         }

                                     },
                                     errorListener = { _, _ ->
                                         setConnectStatus(null)
                                     })
        }

    }

    suspend fun getFriend(id: String): Friend {
        return attentionRepository.getFriend(id)
    }

    private suspend fun getToken(): String? {
        return preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))
    }

    private suspend fun getFCMToken(): String? {
        return preferencesRepository.getValue(stringPreferencesKey(FCM_TOKEN))
    }

    /**
     * Vibrates the phone to signal to the user that they have long-pressed
     */
    fun onLongPress() {
        @Suppress("DEPRECATION") val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = application.getSystemService(
                    AppCompatActivity.VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                application.getSystemService(
                    AppCompatActivity.VIBRATOR_SERVICE
                ) as Vibrator
            }
        @Suppress("DEPRECATION") if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
     * @param text  - The message to display in the body of the notification
     * @requires    - Code is one of ErrorType.SERVER_ERROR or ErrorType.BAD_REQUEST
     */
    private fun notifyUser(text: String, message: Message? = null) {
        viewModelScope.launch {
            val context = application

            val intent = Intent(context, MainActivity::class.java)
            if (message != null) {
                intent.action = context.getString(R.string.reopen_failed_alert_action)
                intent.putExtra(EXTRA_RECIPIENT, message.otherId)
                intent.putExtra(EXTRA_BODY, message.message)
            }

            val pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            createFailedAlertNotificationChannel(context)
            val builder: NotificationCompat.Builder =
                NotificationCompat.Builder(context, FAILED_ALERT_CHANNEL_ID)
            builder.setSmallIcon(R.drawable.app_icon_foreground)
                .setContentTitle(context.getString(R.string.alert_failed)).setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX).setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationID = System.currentTimeMillis().toInt()
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.notify(notificationID, builder.build())
        }
    }

    fun checkOverlayPermission() {
        viewModelScope.launch {
            if (!Settings.canDrawOverlays(application) && preferencesRepository.getValue(
                    booleanPreferencesKey(OVERLAY_NO_PROMPT)
                ) != true
            ) {
                appendDialogState(DialogStatus.OverlayPermission)
            }
        }
    }

    fun <T> setPreference(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            preferencesRepository.setValue(key, value)
        }
    }

    fun getUserInfo(
        onAuthError: () -> Unit, onSuccess: (() -> Unit)? = null, token: String? = null
    ) {
        isRefreshing = true
        val context = application
        viewModelScope.launch {
            token?.let {
                preferencesRepository.setValue(stringPreferencesKey(MY_TOKEN), token)
            } // datastore guarantees read-after-write consistency
            val innerToken = token ?: getToken()

            // we want an exception to the login if they opened an add-friend link
            // if opened from a link, the action is ACTION_VIEW, so we delay logging in
            if (innerToken == null && !addFriendException) {
                Log.d(MainViewModel::class.java.name, "Token null; logging out")
                onAuthError()
            } else if (innerToken != null) {
                attentionRepository.downloadUserInfo(innerToken, { _, response, _ ->
                    setConnectStatus(response.code())
                    when (response.code()) {
                        200 -> {
                            Log.d(sTAG, response.body().toString())
                            val data = response.body()?.data
                            if (data == null) {
                                Log.e(sTAG, "Got user info but body was null!")
                                return@downloadUserInfo
                            }
                            onSuccess?.invoke()
                            viewModelScope.launch {
                                preferencesRepository.bulkEdit { settings ->
                                    settings[stringPreferencesKey(
                                        context.getString(
                                            R.string.username_key
                                        )
                                    )] = data.username
                                    settings[stringPreferencesKey(
                                        context.getString(
                                            R.string.first_name_key
                                        )
                                    )] = data.firstName
                                    settings[stringPreferencesKey(
                                        context.getString(
                                            R.string.last_name_key
                                        )
                                    )] = data.lastName
                                    settings[stringPreferencesKey(
                                        context.getString(
                                            R.string.email_key
                                        )
                                    )] = data.email
                                    settings[booleanPreferencesKey(
                                        context.getString(
                                            R.string.password_key
                                        )
                                    )] = data.password
                                }
                            }
                            viewModelScope.launch {
                                attentionRepository.updateUserInfo(
                                    data.friends
                                )
                            }
                            viewModelScope.launch {
                                @Suppress("BlockingMethodInNonBlockingContext") withContext(
                                    Dispatchers.IO
                                ) {
                                    val file = File(
                                        context.filesDir, PFP_FILENAME
                                    ).apply { createNewFile() }

                                    data.photo?.let {
                                        file.writeBytes(
                                            Base64.decode(
                                                data.photo, Base64.DEFAULT
                                            )
                                        )
                                    } ?: file.writeBytes(ByteArray(0))
                                }
                            }
                            uploadCachedFriends()
                        }
                        403 -> {
                            onAuthError()
                        }
                    }
                    isRefreshing = false
                }, { _, _ ->
                                                         isRefreshing = false
                                                         setConnectStatus(null)
                                                     })
            } else {
                isRefreshing = false
            }
        }

    }

    fun registerDevice() {
        viewModelScope.launch {
            val context = application

            // token is auth token
            val token = getToken()

            val fcmToken: String? = preferencesRepository.getValue(stringPreferencesKey(FCM_TOKEN))
            val fcmTokenUploaded: Boolean = preferencesRepository.getValue(
                booleanPreferencesKey(TOKEN_UPLOADED), false
            ) // Do we need to upload a token (note we don't want to upload if we don't have a token yet)

            if (fcmToken != null && !fcmTokenUploaded && token != null) {
                attentionRepository.registerDevice(token, fcmToken, { _, response, errorBody ->
                    setConnectStatus(response.code())
                    when (response.code()) {
                        200 -> {
                            Log.d(sTAG, "Successfully uploaded token")
                            viewModelScope.launch {
                                preferencesRepository.setValue(
                                    booleanPreferencesKey(TOKEN_UPLOADED), true
                                )
                            }
                        }
                        else -> {
                            Log.e(sTAG, "Error uploading token: $errorBody")
                        }
                    }
                }, { _, _ ->
                                                       setConnectStatus(null)
                                                   })
            } else if (fcmToken == null) { // We don't have a token, so let's get one
                getToken(context)
            }
        }

    }

    fun loadUserPrefs() {
        viewModelScope.launch {
            val context = application // Load user preferences and data

            val notificationValues = context.resources.getStringArray(
                R.array.notification_values
            ) // Set up default preferences
            if (!preferencesRepository.contains(
                    stringPreferencesKey(
                        context.getString(
                            R.string.ring_preference_key
                        )
                    )
                )
            ) {
                preferencesRepository.setValue(
                    stringSetPreferencesKey(
                        context.getString(
                            R.string.ring_preference_key
                        )
                    ), setOf(notificationValues[2])
                )
            }
            if (!preferencesRepository.contains(
                    stringPreferencesKey(
                        context.getString(
                            R.string.vibrate_preference_key
                        )
                    )
                )
            ) {
                preferencesRepository.setValue(
                    stringSetPreferencesKey(
                        context.getString(
                            R.string.vibrate_preference_key
                        )
                    ), setOf(notificationValues[1], notificationValues[2])
                )
            }
            if (!preferencesRepository.contains(booleanPreferencesKey(TOKEN_UPLOADED))) {
                preferencesRepository.setValue(booleanPreferencesKey(TOKEN_UPLOADED), false)
            }
        }

    }

    fun sendAlert(
        to: Friend,
        body: String?,
        launchLogin: () -> Unit,
        onError: (() -> Unit)? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val context = application
            val token = getToken()
            if (token == null) {
                launchLogin()
                return@launch
            }
            val message = Message(
                timestamp = System.currentTimeMillis(),
                otherId = to.id,
                message = body,
                direction = DIRECTION.Outgoing
            )
            backgroundScope.launch {
                pushFriendShortcut(context, to)
                attentionRepository.sendMessage(message, token = token, { _, response, errorBody ->
                    setConnectStatus(response.code())
                    when (response.code()) {
                        200 -> {
                            val responseBody = response.body()
                            if (responseBody == null) {
                                Log.e(
                                    sTAG, "Got response but body was null"
                                )
                                return@sendMessage
                            }
                            showSnackBar(
                                application.getString(
                                    R.string.alert_sent
                                )
                            )
                            onSuccess?.invoke()
                        }
                        400 -> {
                            if (errorBody == null) {
                                Log.e(
                                    sTAG, "Got response but body was null"
                                )
                                return@sendMessage
                            }
                            when {
                                errorBody.contains(
                                    "Could not find user", true
                                ) -> {
                                    notifyUser(
                                        context.getString(
                                            R.string.alert_failed_no_user, to.name
                                        )
                                    )
                                }
                                else -> {
                                    notifyUser(
                                        context.getString(
                                            R.string.alert_failed_bad_request, to.name
                                        )
                                    )
                                }
                            }
                            onError?.invoke()
                        }
                        403 -> {
                            if (errorBody == null) {
                                Log.e(
                                    sTAG, "Got response but body was null"
                                )
                                return@sendMessage
                            }
                            when {
                                errorBody.contains(
                                    "does not have you as a friend", true
                                ) -> {
                                    notifyUser(
                                        context.getString(
                                            R.string.alert_failed_not_friend, to.name
                                        )
                                    )
                                }
                                else -> launchLogin()
                            }
                            onError?.invoke()
                        }
                        429 -> {
                            notifyUser(
                                context.getString(
                                    R.string.alert_rate_limited
                                ), message
                            )
                            onError?.invoke()

                        }
                        else -> {
                            notifyUser(
                                context.getString(
                                    R.string.alert_failed_server_error, to.name
                                )
                            )
                            onError?.invoke()
                        }
                    }
                }, { _, _ ->
                                                    notifyUser(
                                                        context.getString(
                                                            R.string.alert_failed_no_connection,
                                                            to.name
                                                        ), message
                                                    )
                                                    onError?.invoke()
                                                    setConnectStatus(null)
                                                })
            }
        }

    }

    /**
     * Helper method that gets the Firebase token
     *
     * Automatically uploads the token and updates the "uploaded" sharedPreference
     */
    private fun getToken(context: Context) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String?> ->
            viewModelScope.launch {
                if (!task.isSuccessful) {
                    Log.w(sTAG, "getInstanceId failed", task.exception)
                    return@launch
                }

                // Get new Instance ID token
                val token = task.result
                Log.d(sTAG, "Got token! $token")

                val fcmToken = getFCMToken()
                val authToken = getToken()
                if (token != null && token != fcmToken) {
                    preferencesRepository.bulkEdit { settings ->
                        settings[stringPreferencesKey(FCM_TOKEN)] = token
                        settings[booleanPreferencesKey(TOKEN_UPLOADED)] = false
                    }
                    if (authToken != null) {
                        attentionRepository.registerDevice(authToken, token, { _, response, _ ->
                            setConnectStatus(response.code())
                            when (response.code()) {
                                200 -> {
                                    viewModelScope.launch {
                                        preferencesRepository.setValue(
                                            booleanPreferencesKey(TOKEN_UPLOADED), true
                                        )
                                    }

                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.user_registered),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        })
                    }
                }

                // Log and toast
                val msg = context.getString(R.string.msg_token_fmt, token)
                Log.d(sTAG, msg)
            }

        }
    }

    private fun setConnectStatus(responseCode: Int?) {
        val context = application
        when (responseCode) {
            200, 400, 403, 415 -> { // even though some of these are errors, they represent the
                // server working correctly and the calling code should handle these gracefully
                if (connectionState != context.getString(R.string.sharing)) {
                    connectionState = ""
                }
                connected = true
            }
            null -> { // no internet
                connected = false
                if (connectionState != context.getString(R.string.sharing)) {
                    connectionState = application.getString(R.string.disconnected)
                }
            }
            else -> { // 500 errors, other weird stuff
                if (connectionState != context.getString(R.string.sharing)) {
                    connectionState = context.getString(R.string.server_error)
                }
                connected = false
            }
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
        const val PFP_FILENAME = "profile.photo"

        const val EXTRA_RECIPIENT = "extra_recipient"
        const val EXTRA_BODY = "extra_body"

        private const val SHARE_CATEGORY =
            "com.aracroproducts.attentionv2.sharingshortcuts.category.TEXT_SHARE_TARGET"

        fun pushFriendShortcut(context: Context, friend: Friend) {
            val contactCategories = setOf(SHARE_CATEGORY)
            val icon = if (friend.photo != null) IconCompat.createWithBitmap(run {
                val imageDecoded = Base64.decode(friend.photo, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)

                // From diesel - https://stackoverflow.com/a/15537470/7484693
                // Licensed under CC BY-SA 3.0
                val output: Bitmap = if (bitmap.width > bitmap.height) {
                    Bitmap.createBitmap(bitmap.height, bitmap.height, Bitmap.Config.ARGB_8888)
                } else {
                    Bitmap.createBitmap(bitmap.width, bitmap.width, Bitmap.Config.ARGB_8888)
                }

                Log.d(MainViewModel::class.java.name, "${bitmap.width} x ${bitmap.height}")
                val canvas = Canvas(output)

                val color: UInt = 0xff424242u
                val paint = Paint()
                val rect = Rect(0, 0, bitmap.width, bitmap.height)

                val r = if (bitmap.width > bitmap.height) {
                    (bitmap.height / 2).toFloat()
                } else {
                    (bitmap.width / 2).toFloat()
                }

                paint.isAntiAlias = true
                canvas.drawARGB(0, 0, 0, 0)
                paint.color = color.toInt()
                canvas.drawCircle(r, r, r, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(bitmap, rect, rect, paint)
                output
            }) else null

            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                ShortcutInfoCompat.Builder(context, friend.id).setShortLabel(friend.name).setPerson(
                    Person.Builder().setName(friend.name).setKey(friend.id).setImportant(true)
                        .setIcon(icon).build()
                ).setIcon(icon).setIntent(Intent(
                    context, MainActivity::class.java
                ).apply {
                    action = Intent.ACTION_SENDTO
                    putExtra(EXTRA_RECIPIENT, friend.id)
                }).setLongLived(true).setCategories(contactCategories).setPerson(
                    Person.Builder().setName(friend.name).build()
                ).build()
            )
        }

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
                val channel = NotificationChannel(FAILED_ALERT_CHANNEL_ID, name, importance)
                channel.description =
                    description // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }


}