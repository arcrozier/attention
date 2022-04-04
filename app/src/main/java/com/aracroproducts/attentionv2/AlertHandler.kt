package com.aracroproducts.attentionv2

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
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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
        val preferences = getSharedPreferences(MainViewModel.USER_INFO, MODE_PRIVATE)
        if (preferences.getString(MainViewModel.FCM_TOKEN, "") != token) {
            val authToken = preferences.getString(MainViewModel.MY_TOKEN, null) ?: return
            Log.d(TAG, "Token is new: updating shared preferences")
            val editor = preferences.edit()
            editor.putBoolean(MainViewModel.TOKEN_UPLOADED, false)
            editor.putString(MainViewModel.FCM_TOKEN, token)
            editor.apply()
            val repository = AttentionRepository(AttentionDB.getDB(applicationContext))
            repository.registerDevice(authToken, token, NetworkSingleton.getInstance
            (applicationContext), {
                Log.d(TAG, "Successfully uploaded token")
                editor.putBoolean(MainViewModel.TOKEN_UPLOADED, true)
                editor.putBoolean(MainViewModel.KEY_UPLOADED, true)
                editor.apply()
            }, {
                Log.e(TAG, "An error occurred when uploading token: ${it.message}")
            })
        }
    }

    /**
     * Receives a message from Firebase (not called by local code)
     * @param remoteMessage - The message from Firebase
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // TODO parse out alert read and alert incoming messages
        // TODO store received alerts somewhere
        Log.d(TAG, "Message received! $remoteMessage")
        val messageData = remoteMessage.data
        when (messageData["action"]) {
            "alert" -> {
                val message = Message(timestamp = System.currentTimeMillis(), otherId = messageData[REMOTE_FROM] ?: return,
                        direction = DIRECTION.Incoming, message = messageData[REMOTE_MESSAGE])
                val repository = AttentionRepository(AttentionDB.getDB(applicationContext))
                val userInfo = getSharedPreferences(MainViewModel.USER_INFO, MODE_PRIVATE)
                if (messageData[REMOTE_TO] != userInfo.getString(MainViewModel.MY_ID,
                                "")) return  //if message is not addressed to the user, ends
                val senderName = repository.getFriend(message.otherId).name

                val display = if (message.message == "null") getString(R.string.default_message,
                        senderName) else getString(R.string.message_prefix, senderName, message.message)

                repository.appendMessage(message = message)
                val preferences = PreferenceManager.getDefaultSharedPreferences(this)
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                NotificationManagerCompat.from(this)
                // Check if SDK >= Android 7.0, uses the new notification manager, else uses the compat manager (SDK 19+)
                // Checks if the app should avoid notifying because it has notifications disabled or:
                if ((!manager.areNotificationsEnabled())
                        || (!preferences.getBoolean(getString(R.string.override_dnd_key),
                                false) // Checks whether it should not be overriding Do Not Disturb
                                && (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL // Do not disturb is on
                                && manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN))) { // Also do not disturb is on
                    Log.d(TAG,
                            "App is disabled from showing notifications or interruption filter is set to block notifications")
                    showNotification(display, senderName, true)
                    return
                }
                try {
                    if (Settings.Global.getInt(contentResolver,
                                    "zen_mode") > 1) { // a variant of do not disturb
                        Log.d(TAG, "Device's zen mode is enabled")
                        return
                    }
                } catch (e: SettingNotFoundException) {
                    e.printStackTrace()
                }
                val pm = getSystemService(POWER_SERVICE) as PowerManager

                // Stores the id so the notification can be cancelled by the user
                val id = showNotification(display, senderName, false)

                // Device should only show pop up if the device is off or if it has the ability to draw overlays (required to show pop up if screen is on)
                if (!pm.isInteractive || Settings.canDrawOverlays(this)) {
                    val intent = Intent(this, Alert::class.java).apply {
                        putExtra(REMOTE_FROM, senderName)
                        putExtra(REMOTE_MESSAGE, display)
                        putExtra(ASSOCIATED_NOTIFICATION, id)
                        putExtra(SHOULD_VIBRATE, true)
                        addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    Log.d(TAG, "Sender: $senderName, ${message.otherId} Message: ${message.message}")
                    startActivity(intent)
                }
            }
            "read" -> {
                val attentionRepository = AttentionRepository(AttentionDB.getDB(this))
                attentionRepository.alertRead(username = messageData["username_to"], alertId =
                messageData["alert_id"])
            }
            else -> {
                Log.w(TAG, "Unrecognized action: $remoteMessage")
                return
            }
        }
    }

    /**
     * Helper method to show the notification
     * @param message       - The message to show
     * @param senderName    - The name of the sender
     * @param missed        - Whether the alert was missed
     * @return              - Returns the ID of the notification
     */
    private fun showNotification(message: String, senderName: String?, missed: Boolean): Int {
        val intent = Intent(this, Alert::class.java)
        intent.putExtra(REMOTE_MESSAGE, message)
        intent.putExtra(REMOTE_FROM, senderName)
        intent.putExtra(SHOULD_VIBRATE, false)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder: NotificationCompat.Builder
        if (missed) {
            createMissedNotificationChannel(this)
            builder = NotificationCompat.Builder(this, CHANNEL_ID)
            builder
                    .setSmallIcon(R.mipmap.add_foreground)
                    .setContentTitle(getString(R.string.notification_title, senderName))
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent).setAutoCancel(true)
        } else {
            createNotificationChannel()
            builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            builder
                    .setSmallIcon(R.mipmap.add_foreground)
                    .setContentTitle(getString(R.string.alert_notification_title, senderName))
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent).setAutoCancel(true)
        }
        val notificationID = (System.currentTimeMillis() % 1000000000L).toInt() + 1
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.notify(notificationID, builder.build())
        return notificationID
    }

    /**
     * Creates the notification channel for notifications that are displayed alongside dialogs
     */
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.alert_channel_name)
            val description = getString(R.string.alert_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALERT_CHANNEL_ID, name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
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
        const val REMOTE_TO = "alert_to"
        const val REMOTE_MESSAGE = "alert_message"
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
                channel.description = description
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}