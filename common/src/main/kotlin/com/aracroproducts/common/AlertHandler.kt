package com.aracroproducts.common

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aracroproducts.common.AlertSendService.Companion.ACTION_MARK_AS_READ
import com.aracroproducts.common.AlertSendService.Companion.ACTION_REPLY
import com.aracroproducts.common.AlertSendService.Companion.ACTION_SILENCE
import com.aracroproducts.common.AlertSendService.Companion.EXTRA_FROM_NOTIFICATION
import com.aracroproducts.common.AlertSendService.Companion.EXTRA_MESSAGE_TEXT
import com.aracroproducts.common.AlertSendService.Companion.EXTRA_NOTIFICATION_ID
import com.aracroproducts.common.AlertSendService.Companion.EXTRA_SENDER
import com.aracroproducts.common.AlertSendService.Companion.KEY_TEXT_REPLY
import com.aracroproducts.common.FriendManagementService.Companion.ACTION_ACCEPT
import com.aracroproducts.common.FriendManagementService.Companion.ACTION_BLOCK
import com.aracroproducts.common.FriendManagementService.Companion.ACTION_IGNORE
import com.aracroproducts.common.FriendManagementService.Companion.EXTRA_NAME
import com.aracroproducts.common.FriendManagementService.Companion.EXTRA_USERNAME
import com.aracroproducts.common.PreferencesRepository.Companion.MY_TOKEN
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException


/**
 * Handles Firebase alerts
 */
open class AlertHandler : FirebaseMessagingService() {


    /**
     * Executed when the device gets a new Firebase token
     * @param token - The new token to use
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "New token: $token")
        val preferencesRepository =
            (application as AttentionApplicationBase).container.settingsRepository
        val repository = (application as AttentionApplicationBase).container.repository
        MainScope().launch {
            if (preferencesRepository.getValue(stringPreferencesKey(FCM_TOKEN)) != token) {
                Log.d(TAG, "Token is new: updating shared preferences")
                preferencesRepository.bulkEdit { settings ->
                    settings[stringPreferencesKey(FCM_TOKEN)] = token
                }
                val authToken = preferencesRepository.getValue(
                    stringPreferencesKey(
                        MY_TOKEN
                    )
                ) ?: return@launch
                try {
                    repository.registerDevice(authToken, token)
                } catch (e: HttpException) {
                    Log.e(
                        TAG,
                        "An error occurred when uploading token: ${e.response()?.errorBody()}"
                    )
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "An error occurred when uploading token: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Receives a message from Firebase (not called by local code)
     * @param remoteMessage - The message from Firebase
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val preferencesRepository =
            (application as AttentionApplicationBase).container.settingsRepository
        val repository = (application as AttentionApplicationBase).container.repository
        MainScope().launch {
            Log.d(TAG, "Message received! $remoteMessage")
            val messageData = remoteMessage.data
            when (messageData["action"]) {
                "alert" -> {
                    val message = Message(timestamp = messageData[EXTRA_TIMESTAMP]?.toLong()?.let {
                        it * 1000
                    } ?: System.currentTimeMillis(),
                        otherId = messageData[REMOTE_FROM] ?: return@launch,
                        direction = DIRECTION.Incoming,
                        message = messageData[REMOTE_MESSAGE])
                    val username = preferencesRepository.getValue(
                        stringPreferencesKey(
                            getString(
                                R.string.username_key
                            )
                        )
                    )
                    if (messageData[REMOTE_TO] != username || messageData[REMOTE_TO] == null) let {
                        Firebase.crashlytics.log("Received message without correct recipient: $messageData (receiver username is $username)")
                        return@launch
                    }  //if message is not addressed to the user, ends

                    val alertId = messageData[ALERT_ID]
                    if (alertId == null) {
                        Log.w(TAG, "Received message without an id: $remoteMessage")
                        return@launch
                    }
                    val sender = repository.getFriend(message.otherId)
                    val senderName = repository.getFriend(message.otherId).name
                    val topKFriends = repository.getTopKFriends()
                    val important =
                        sender in topKFriends.toSet() || topKFriends.size < MAX_IMPORTANT_PEOPLE

                    val display = if (message.message == null) getString(
                        R.string.default_message, sender.name
                    ) else getString(R.string.message_prefix, sender.name, message.message)

                    repository.appendMessage(message = message)

                    // token is auth token
                    val token = preferencesRepository.getValue(
                        stringPreferencesKey(
                            MY_TOKEN
                        )
                    )

                    if (token != null) {
                        repository.sendDeliveredReceipt(
                            from = message.otherId, alertId = alertId, authToken = token
                        )
                    } else {
                        Log.e(javaClass.name, "Token is null when sending delivery receipt!")
                    }

                    if (!areNotificationsAllowed()) {
                        Log.d(
                            TAG,
                            "App is disabled from showing notifications or interruption filter is set to block notifications"
                        )
                        showNotification(
                            display,
                            sender,
                            alertId,
                            true /* missed */,
                            important,
                            message.timestamp
                        )
                        return@launch
                    }
                    try {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && Settings.Global.getInt(
                                contentResolver, "zen_mode"
                            ) > 1
                        ) { // a variant of do not disturb
                            Log.d(TAG, "Device's zen mode is enabled")
                            showNotification(
                                display,
                                sender,
                                alertId,
                                true /* missed */,
                                important,
                                message.timestamp
                            )
                            return@launch
                        }
                    } catch (e: SettingNotFoundException) {
                        e.printStackTrace()
                    }
                    val pm = getSystemService(POWER_SERVICE) as PowerManager

                    // Stores the id so the notification can be cancelled by the user
                    val id =
                        showNotification(
                            display,
                            sender,
                            alertId,
                            false /* missed */,
                            important,
                            message.timestamp
                        )

                    // Device should only show pop up if the device is off or if it has the ability to draw overlays (required to show pop up if screen is on)
                    if (!pm.isInteractive || Settings.canDrawOverlays(this@AlertHandler) || AttentionApplicationBase.isActivityVisible()) {
                        val intent = Intent(
                            this@AlertHandler,
                            (application as AttentionApplicationBase).alertActivity
                        ).apply {
                            putExtra(REMOTE_FROM, senderName)
                            putExtra(REMOTE_MESSAGE, display)
                            putExtra(ASSOCIATED_NOTIFICATION, id)
                            putExtra(SHOULD_VIBRATE, true)
                            putExtra(ALERT_ID, alertId)
                            putExtra(EXTRA_TIMESTAMP, message.timestamp)
                            putExtra(REMOTE_FROM_USERNAME, message.otherId)
                            if (AttentionApplicationBase.isActivityVisible()) addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                            else addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        }
                        Log.d(
                            TAG,
                            "Sender: $senderName, ${message.otherId} Message: ${message.message}"
                        )
                        startActivity(intent)
                    }
                }

                "delivered" -> {
                    repository.alertDelivered(
                        username = messageData[USERNAME_TO], alertId = messageData[ALERT_ID]
                    )
                }
//1739695599.6292462
                "read" -> {
                    repository.alertRead(
                        username = messageData[USERNAME_TO], alertId = messageData[ALERT_ID]
                    )

                    sendBroadcast(Intent().apply {
                        action = ACTION_MARK_AS_READ
                        putExtra(EXTRA_ALERT_ID, messageData[ALERT_ID])
                        setPackage(this@AlertHandler.packageName)
                    })
                }

                "friended" -> {
                    val username = messageData[FCM_FRIEND_REQUEST_USERNAME] ?: run {
                        Log.w(TAG, "Received friend request with no username")
                        return@launch
                    }
                    val name = messageData[FCM_FRIEND_REQUEST_NAME] ?: run {
                        Log.w(TAG, "Received friend request from $username but no name")
                        return@launch
                    }
                    val photo = messageData[FCM_FRIEND_REQUEST_PHOTO]
                    val pendingFriend =
                        PendingFriend(username, name, if (photo.isNullOrBlank()) null else photo)
                    showFriendRequestNotification(
                        applicationContext,
                        pendingFriend,
                        (application as AttentionApplicationBase).mainActivity
                    )
                    repository.insert(pendingFriend)
                }
                "accepted" -> {
                    val username = messageData[FCM_FRIEND_REQUEST_USERNAME] ?: run {
                        Log.w(TAG, "Accepted friend request with no username")
                        return@launch
                    }
                    if (repository.getFriend(username, null) != null) {
                        Log.w(TAG, "Already friends with $username")
                        return@launch
                    }
                    val name = messageData[FCM_FRIEND_REQUEST_NAME] ?: run {
                        Log.w(TAG, "$username accepted friend request, but didn't receive a name")
                        return@launch
                    }
                    val photo = messageData[FCM_FRIEND_REQUEST_PHOTO]
                    repository.insert(Friend(username, name, photo = photo))
                    val intent = Intent(
                        applicationContext,
                        (application as AttentionApplicationBase).mainActivity
                    )
                    val pendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        REQUEST_MAIN_ACTIVITY,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    val builder: NotificationCompat.Builder =
                        NotificationCompat.Builder(
                            applicationContext,
                            FRIEND_REQUEST_CHANNEL_ID
                        )
                    val icon = if (!photo.isNullOrBlank()) {
                        val imageDecoded = Base64.decode(photo, Base64.DEFAULT)
                        val imageBitmap =
                            BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)
                        IconCompat.createWithBitmap(imageBitmap)
                    } else {
                        IconCompat.createWithResource(
                            applicationContext,
                            R.drawable.baseline_person_24
                        )
                    }
                    builder.setSmallIcon(icon)
                        .setContentTitle(
                            applicationContext.getString(
                                R.string.friend_request_accepted_notification_title,
                                name, username
                            )
                        )

                    builder
                        .setCategory(Notification.CATEGORY_SOCIAL)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                    val notificationManagerCompat =
                        NotificationManagerCompat.from(applicationContext)
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@launch
                    }
                    notificationManagerCompat.notify(
                        System.currentTimeMillis().toInt(),
                        builder.build()
                    )
                }
                else -> {
                    Log.w(TAG, "Unrecognized action: $remoteMessage")
                    return@launch
                }
            }
        }
    }

    private fun areNotificationsAllowed(): Boolean {
        val preferencesRepository =
            (application as AttentionApplicationBase).container.settingsRepository
        val overrideDND = runBlocking {
            preferencesRepository.getValue(
                booleanPreferencesKey(getString(R.string.override_dnd_key)), false
            )
        }
        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager // Check if SDK >= Android 7.0, uses the new notification manager, else uses the compat manager (SDK 19+) // Checks if the app should avoid notifying because it has notifications disabled or:
        val channel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.getNotificationChannel(
                ALERT_CHANNEL_ID
            ) else null
        return (manager.areNotificationsEnabled() && // all app notifications disabled
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && channel?.importance != NotificationManager.IMPORTANCE_NONE)) && // specifically this channel is disabled
                (overrideDND || // Checks whether it should not be overriding Do Not Disturb
                        ((manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL || // Do not disturb is on
                                manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || (manager.consolidatedNotificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES != 0 || manager.consolidatedNotificationPolicy.priorityMessageSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY))))

    }

    /**
     * Helper method to show the notification
     * @param message       - The message to show
     * @param sender        - The sender of the message
     * @param missed        - Whether the alert was missed
     * @param alertId       - The alert associated with the notification
     * @param important     - Whether the sender is important to the user or not
     * @return              - Returns the ID of the notification
     */
    private fun showNotification(
        message: String,
        sender: Friend,
        alertId: String,
        missed: Boolean,
        important: Boolean,
        timestamp: Long
    ): Int {
        val notificationID = System.currentTimeMillis().toInt()
        (application as AttentionApplicationBase).container.applicationScope.launch(
            context = Dispatchers.Default
        ) {
            val flags =
                NotificationFlags.ACTION_SILENCE or (if (missed) NotificationFlags.MISSED else 0) or (if (important) NotificationFlags.IMPORTANT else 0)
            showNotification(
                application as AttentionApplicationBase,
                message,
                sender,
                alertId,
                flags,
                timestamp,
                notificationID
            )
        }
        return notificationID
    }


    companion object {
        private val TAG = AlertHandler::class.java.simpleName
        const val CHANNEL_ID = "Missed Alert Channel"
        const val ALERT_CHANNEL_ID = "Alert Channel"
        const val REMOTE_FROM = "alert_from"
        const val REMOTE_FROM_USERNAME = "alert_from_username"
        const val REMOTE_TO = "alert_to"
        const val REMOTE_MESSAGE = "alert_message"
        const val ALERT_ID = "alert_id"
        const val USERNAME_TO = "username_to"
        const val EXTRA_TIMESTAMP = "alert_timestamp"
        const val ASSOCIATED_NOTIFICATION = "notification_id"
        const val SHOULD_VIBRATE = "vibrate"
        const val FCM_FRIEND_REQUEST_USERNAME = "friend"
        const val FCM_FRIEND_REQUEST_NAME = "name"
        const val FCM_FRIEND_REQUEST_PHOTO = "photo"

        class NotificationFlags {

            companion object {
                /**
                 * Set this flag to display a "silence" option in the notification. Has no effect if {@link MISSED} is also set
                 */
                const val ACTION_SILENCE = 0b1

                /**
                 * Set this flag to display a "missed alert from"-type message. Will not show "silence" or "ok" actions
                 */
                const val MISSED = 0b1 shl 3

                /**
                 * Whether the message is from an important sender
                 */
                const val IMPORTANT = 0b1 shl 4
            }
        }

        /**
         * Creates the missed alerts notification channel on Android O and later
         *
         * @param context - The app context to use for getting strings and the NotificationManager
         */
        fun createMissedNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name: CharSequence = context.getString(R.string.channel_name)
                val description = context.getString(R.string.channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH

                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val channel = NotificationChannel(CHANNEL_ID, name, importance)
                channel.description = description
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }

        /**
         * Helper method to show the notification
         * @param message       - The message to show
         * @param sender        - The sender of the message
         * @param alertId       - The alert associated with the notification
         * @param flags         - Flags for the notifications {@link AlertHandler#NotificationFlags}
         * @return              - Returns the ID of the notification
         */
        suspend fun showNotification(
            application: AttentionApplicationBase,
            message: String,
            sender: Friend,
            alertId: String,
            flags: Int = NotificationFlags.ACTION_SILENCE,
            timestamp: Long,
            notificationId: Int? = null,
        ): Int {
            val notificationID = notificationId ?: System.currentTimeMillis().toInt()
            val important = flagSet(flags, NotificationFlags.IMPORTANT)
            val missed = flagSet(flags, NotificationFlags.MISSED)
            val silenceAction = flagSet(flags, NotificationFlags.ACTION_SILENCE)
            val intent = Intent(application.applicationContext, application.alertActivity).apply {
                putExtra(REMOTE_MESSAGE, message)
                putExtra(REMOTE_FROM, sender.name)
                putExtra(SHOULD_VIBRATE, false)
                putExtra(REMOTE_FROM_USERNAME, sender.username)
                putExtra(ALERT_ID, alertId)
                putExtra(EXTRA_TIMESTAMP, timestamp)
            }

            val database = AttentionDB.getDB(application.applicationContext)

            // for more on the conversation api, see
            // https://developer.android.com/develop/ui/views/notifications/conversations#api-notifications
            pushFriendShortcut(application.applicationContext, sender, application.mainActivity)

            val icon = if (sender.photo != null) {
                val imageDecoded = Base64.decode(sender.photo, Base64.DEFAULT)
                val imageBitmap = BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)
                IconCompat.createWithBitmap(imageBitmap)
            } else {
                null
            }

            val person =
                Person.Builder().setName(sender.name).setKey(sender.username)
                    .setImportant(important)
                    .setIcon(icon).build()

            val messagingStyle = NotificationCompat.MessagingStyle(person).addMessage(
                NotificationCompat.MessagingStyle.Message(
                    message, System.currentTimeMillis(), person
                )
            )

            val pendingIntent = PendingIntent.getActivity(
                application.applicationContext,
                database.getConversationIdDAO().getOrInsert(
                    sender.username,
                    Purpose.DEFAULT
                ).conversationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder: NotificationCompat.Builder
            if (missed) {
                createMissedNotificationChannel(application.applicationContext)
                builder = NotificationCompat.Builder(application.applicationContext, CHANNEL_ID)
                builder.setContentTitle(
                    application.applicationContext.getString(
                            R.string.notification_title,
                            sender.name
                        )
                    )
            } else {
                builder =
                    NotificationCompat.Builder(application.applicationContext, ALERT_CHANNEL_ID)
                    .setContentTitle(
                        application.applicationContext.getString(
                            R.string.alert_notification_title,
                            sender.name
                        )
                    )

                if (silenceAction) {
                    val silenceIntent =
                        Intent(application.applicationContext, AlertSendService::class.java).apply {
                            action = ACTION_SILENCE
                            putExtra(EXTRA_NOTIFICATION_ID, notificationID)
                            putExtra(EXTRA_ALERT_ID, alertId)
                            putExtra(EXTRA_MESSAGE_TEXT, message)
                            putExtra(EXTRA_FROM_NOTIFICATION, true)
                            putExtra(EXTRA_SENDER, sender.username)
                            putExtra(EXTRA_TIMESTAMP, timestamp)
                        }
                    val silencePendingIntent = PendingIntent.getService(
                        application.applicationContext,
                        database.getConversationIdDAO().getOrInsert(
                            sender.username,
                            Purpose.SILENCE
                        ).conversationId,
                        silenceIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    builder.addAction(
                        R.drawable.baseline_notifications_off_24,
                        application.applicationContext.getString(R.string.silence),
                        silencePendingIntent
                    )
                }
            }

            // Mark as read button
            val dismissIntent =
                Intent(application.applicationContext, AlertSendService::class.java).apply {
                    action = ACTION_MARK_AS_READ
                    putExtra(EXTRA_NOTIFICATION_ID, notificationID)
                    putExtra(EXTRA_ALERT_ID, alertId)
                    putExtra(EXTRA_SENDER, sender.username)
                    putExtra(EXTRA_FROM_NOTIFICATION, true)
                }
            val dismissPendingIntent: PendingIntent =
                PendingIntent.getService(
                    application.applicationContext, database.getConversationIdDAO().getOrInsert(
                        sender.username,
                        Purpose.DISMISS
                    ).conversationId, dismissIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            createNotificationChannel(application.applicationContext)
            builder.setSmallIcon(R.drawable.icon)
                .addAction(
                    R.drawable.baseline_mark_chat_read_24,
                    application.applicationContext.getString(R.string.mark_as_read),
                    dismissPendingIntent
                )

            // Reply button
            val remoteInput: RemoteInput =
                RemoteInput.Builder(KEY_TEXT_REPLY).run {
                    setLabel(application.applicationContext.getString(R.string.reply))
                    build()
                }

            val replyPendingIntent: PendingIntent =
                PendingIntent.getService(
                    application.applicationContext,
                    database.getConversationIdDAO().getOrInsert(
                        sender.username,
                        Purpose.DISMISS
                    ).conversationId,
                    Intent(application.applicationContext, AlertSendService::class.java).apply {
                        action = ACTION_REPLY
                        putExtra(EXTRA_NOTIFICATION_ID, notificationID)
                        putExtra(EXTRA_ALERT_ID, alertId)
                        putExtra(EXTRA_FROM_NOTIFICATION, true)
                        putExtra(EXTRA_SENDER, sender.username)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            val action = NotificationCompat.Action.Builder(
                R.drawable.baseline_reply_24,
                application.applicationContext.getString(R.string.reply),
                replyPendingIntent
            ).addRemoteInput(remoteInput)
                .extend(  // something something helps with wear OS? todo might break things though
                    NotificationCompat.Action.WearableExtender().setHintDisplayActionInline(true)
                        .setHintLaunchesActivity(false)
                ).build()

            builder.setShortcutId(sender.username).setContentText(message)
                .setCategory(Notification.CATEGORY_MESSAGE).setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_MAX).setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(action)
            val notificationManagerCompat =
                NotificationManagerCompat.from(application.applicationContext)
            if (ActivityCompat.checkSelfPermission(
                    application.applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return NO_ID
            }
            notificationManagerCompat.notify(notificationID, builder.build())
            return notificationID
        }

        /**
         * Returns whether the given flag is set in flags
         *
         * @param flags the set flags. This may have 0 or more bits set
         * @param flag the flag to test for in flags. This must have exactly one bit set
         *
         * @return true iff the set bit in flag is also set in flags, false otherwise
         */
        private fun flagSet(flags: Int, flag: Int): Boolean {
            assert(flag.countOneBits() == 1)
            return flags and flag != 0
        }

        fun showFriendRequestNotification(
            applicationContext: Context,
            pendingFriend: PendingFriend,
            mainActivity: Class<*>,
            notificationId: Int? = null
        ): Int {
            val notificationID = notificationId ?: System.currentTimeMillis().toInt()
            val intent = Intent(applicationContext, mainActivity)

            // for more on the conversation api, see
            // https://developer.android.com/develop/ui/views/notifications/conversations#api-notifications

            val icon = if (!pendingFriend.photo.isNullOrBlank()) {
                val imageDecoded = Base64.decode(pendingFriend.photo, Base64.DEFAULT)
                val imageBitmap = BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)
                IconCompat.createWithBitmap(imageBitmap)
            } else {
                IconCompat.createWithResource(applicationContext, R.drawable.baseline_person_24)
            }


            val pendingIntent = PendingIntent.getActivity(
                applicationContext, REQUEST_MAIN_ACTIVITY, intent, PendingIntent.FLAG_IMMUTABLE
            )
            val baseIntent = Intent(applicationContext, FriendManagementService::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationID)
                putExtra(EXTRA_USERNAME, pendingFriend.username)
                putExtra(EXTRA_NAME, pendingFriend.name)
            }
            val acceptIntent = Intent(baseIntent).apply {
                action = ACTION_ACCEPT
            }

            val acceptPendingIntent: PendingIntent =
                PendingIntent.getService(
                    applicationContext, System.currentTimeMillis().toInt(), acceptIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

            val ignoreIntent = Intent(baseIntent).apply {
                action = ACTION_IGNORE
            }

            val ignorePendingIntent = PendingIntent.getService(
                applicationContext,
                System.currentTimeMillis().toInt(),
                ignoreIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val blockIntent = Intent(baseIntent).apply {
                action = ACTION_BLOCK
            }

            val blockPendingIntent = PendingIntent.getService(
                applicationContext,
                System.currentTimeMillis().toInt(),
                blockIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val builder: NotificationCompat.Builder =
                NotificationCompat.Builder(
                    applicationContext,
                    FRIEND_REQUEST_CHANNEL_ID
                )
            builder.setSmallIcon(icon)
                .setContentTitle(
                    applicationContext.getString(
                        R.string.friend_request_notification_title,
                        pendingFriend.username
                    )
                )
                .setContentText(
                    applicationContext.getString(
                        R.string.friend_request_notification_body,
                        pendingFriend.name,
                        pendingFriend.username
                    )
                )
                .addAction(
                    R.drawable.baseline_check_24,
                    applicationContext.getString(R.string.accept),
                    acceptPendingIntent
                )
                .addAction(
                    R.drawable.baseline_person_off_24,
                    applicationContext.getString(R.string.ignore),
                    ignorePendingIntent
                )
                .addAction(
                    R.drawable.baseline_block_24,
                    applicationContext.getString(R.string.block),
                    blockPendingIntent
                )

            builder
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pendingIntent)
                .setAutoCancel(true)
            val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return NO_ID
            }
            notificationManagerCompat.notify(notificationID, builder.build())
            return notificationID
        }
    }
}