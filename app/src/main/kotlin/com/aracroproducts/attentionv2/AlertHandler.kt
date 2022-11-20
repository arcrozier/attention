package com.aracroproducts.attentionv2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
        MainScope().launch(context = Dispatchers.IO) {
            val preferences = applicationContext.dataStore.data.first()
            if (preferences[stringPreferencesKey(MainViewModel.FCM_TOKEN)] != token) {
                Log.d(TAG, "Token is new: updating shared preferences")
                applicationContext.dataStore.edit { settings ->
                    settings[booleanPreferencesKey(MainViewModel.TOKEN_UPLOADED)] = false
                    settings[stringPreferencesKey(MainViewModel.FCM_TOKEN)] = token
                }
                val authToken = preferences[stringPreferencesKey(MainViewModel.MY_TOKEN)] ?: return@launch
                val repository = AttentionRepository(AttentionDB.getDB(applicationContext))
                repository.registerDevice(authToken, token, { _, response, errorBody ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully uploaded token")
                        MainScope().launch(context = Dispatchers.IO) {
                            applicationContext.dataStore.edit { settings ->
                            settings[booleanPreferencesKey(MainViewModel.TOKEN_UPLOADED)] = true
                        } }

                    } else {
                        Log.e(TAG, "An error occurred when uploading token: $errorBody")
                    }
                }, { _, t ->
                                              Log.e(
                                                  TAG,
                                                  "An error occurred when uploading token: ${t.message}"
                                              )
                                          })
            }
        }
    }

    /**
     * Receives a message from Firebase (not called by local code)
     * @param remoteMessage - The message from Firebase
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        MainScope().launch {
            Log.d(TAG, "Message received! $remoteMessage")
            val messageData = remoteMessage.data
            when (messageData["action"]) {
                "alert" -> {
                    val message = Message(timestamp = messageData[ALERT_TIMESTAMP]?.toLong()?.let {
                        it * 1000
                    } ?: System.currentTimeMillis(),
                                          otherId = messageData[REMOTE_FROM] ?: return@launch,
                                          direction = DIRECTION.Incoming,
                                          message = messageData[REMOTE_MESSAGE])
                    val repository = AttentionRepository(AttentionDB.getDB(applicationContext))
                    val preferences = applicationContext.dataStore.data.first()
                    val username = preferences[stringPreferencesKey(getString(R.string.username_key))]
                    if (messageData[REMOTE_TO] != username || messageData[REMOTE_TO] == null) return@launch  //if message is not addressed to the user, ends

                    val alertId = messageData[ALERT_ID]
                    if (alertId == null) {
                        Log.w(TAG, "Received message without an id: $remoteMessage")
                        return@launch
                    }
                    val senderName = repository.getFriend(message.otherId).name

                    val display = if (message.message == "None") getString(
                        R.string.default_message, senderName
                    ) else getString(R.string.message_prefix, senderName, message.message)

                    repository.appendMessage(message = message)

                    // token is auth token
                    val token = preferences[stringPreferencesKey(MainViewModel.MY_TOKEN)]

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
                        showNotification(display, senderName, alertId, message.otherId, true)
                        return@launch
                    }
                    try {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && Settings.Global.getInt(
                                contentResolver,
                                "zen_mode"
                            ) > 1
                        ) { // a variant of do not disturb
                            Log.d(TAG, "Device's zen mode is enabled")
                            showNotification(display, senderName, alertId, message.otherId, true)
                            return@launch
                        }
                    } catch (e: SettingNotFoundException) {
                        e.printStackTrace()
                    }
                    val pm = getSystemService(POWER_SERVICE) as PowerManager

                    // Stores the id so the notification can be cancelled by the user
                    val id = showNotification(display, senderName, alertId, message.otherId, false)

                    AttentionDB.getDB(applicationContext).getFriendDAO()
                        .incrementReceived(message.otherId)

                    // Device should only show pop up if the device is off or if it has the ability to draw overlays (required to show pop up if screen is on)
                    if (!pm.isInteractive || Settings.canDrawOverlays(this@AlertHandler) || AttentionApplication.isActivityVisible()) {
                        val intent = Intent(this@AlertHandler, Alert::class.java).apply {
                            putExtra(REMOTE_FROM, senderName)
                            putExtra(REMOTE_MESSAGE, display)
                            putExtra(ASSOCIATED_NOTIFICATION, id)
                            putExtra(SHOULD_VIBRATE, true)
                            putExtra(ALERT_ID, alertId)
                            putExtra(ALERT_TIMESTAMP, message.timestamp)
                            putExtra(REMOTE_FROM_USERNAME, message.otherId)
                            if (AttentionApplication.isActivityVisible()) addFlags(
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
                    val attentionRepository =
                        AttentionRepository(AttentionDB.getDB(this@AlertHandler))
                    attentionRepository.alertDelivered(
                        username = messageData["username_to"], alertId = messageData["alert_id"]
                    )
                }
                "read" -> {
                    val attentionRepository =
                        AttentionRepository(AttentionDB.getDB(this@AlertHandler))
                    attentionRepository.alertRead(
                        username = messageData["username_to"], alertId = messageData["alert_id"]
                    )

                    if (AttentionApplication.shownAlertID == messageData["alert_id"]) {
                        (application as? AttentionApplication)?.activity?.finish() ?: Log.e(
                            TAG, "Couldn't finish application"
                        )
                    }
                }
                else -> {
                    Log.w(TAG, "Unrecognized action: $remoteMessage")
                    return@launch
                }
            }
        }
    }

    private fun areNotificationsAllowed(): Boolean {
        val overrideDND = runBlocking { applicationContext.dataStore.data.first()[booleanPreferencesKey(getString(R.string.override_dnd_key))] ?: false }
        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager // Check if SDK >= Android 7.0, uses the new notification manager, else uses the compat manager (SDK 19+)
        // Checks if the app should avoid notifying because it has notifications disabled or:
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
     * @param senderName    - The name of the sender
     * @param missed        - Whether the alert was missed
     * @return              - Returns the ID of the notification
     */
    private fun showNotification(
        message: String, senderName: String?, alertId: String, fromUser: String, missed: Boolean
    ): Int {
        val intent = Intent(this, Alert::class.java).apply {
            putExtra(REMOTE_MESSAGE, message)
            putExtra(REMOTE_FROM, senderName)
            putExtra(SHOULD_VIBRATE, false)
            putExtra(REMOTE_FROM_USERNAME, fromUser)
            putExtra(ALERT_ID, alertId)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder: NotificationCompat.Builder
        if (missed) {
            createMissedNotificationChannel(this)
            builder = NotificationCompat.Builder(this, CHANNEL_ID)
            builder.setSmallIcon(R.drawable.app_icon_foreground)
                .setContentTitle(getString(R.string.notification_title, senderName))
                .setContentText(message).setCategory(Notification.CATEGORY_MESSAGE)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX).setContentIntent(pendingIntent)
                .setAutoCancel(true)
        } else {
            createNotificationChannel()
            builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            builder.setSmallIcon(R.drawable.app_icon_foreground)
                .setContentTitle(getString(R.string.alert_notification_title, senderName))
                .setContentText(message).setCategory(Notification.CATEGORY_MESSAGE)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX).setContentIntent(pendingIntent)
                .setAutoCancel(true)
        }
        val notificationID = System.currentTimeMillis().toInt()
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.notify(notificationID, builder.build())
        return notificationID
    }

    /**
     * Creates the notification channel for notifications that are displayed alongside dialogs
     */
    private fun createNotificationChannel() { // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.alert_channel_name)
            val description = getString(R.string.alert_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALERT_CHANNEL_ID, name, importance)
            channel.description =
                description // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }


    companion object {
        private val TAG = AlertHandler::class.java.name
        const val CHANNEL_ID = "Missed Alert Channel"
        const val ALERT_CHANNEL_ID = "Alert Channel"
        const val REMOTE_FROM = "alert_from"
        const val REMOTE_FROM_USERNAME = "alert_from_username"
        const val REMOTE_TO = "alert_to"
        const val REMOTE_MESSAGE = "alert_message"
        const val ALERT_ID = "alert_id"
        const val ALERT_TIMESTAMP = "alert_timestamp"
        const val ASSOCIATED_NOTIFICATION = "notification_id"
        const val SHOULD_VIBRATE = "vibrate"

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
                val channel = NotificationChannel(CHANNEL_ID, name, importance)
                channel.description =
                    description // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}