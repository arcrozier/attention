package com.aracroproducts.attentionv2

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.MainViewModel.Companion.EXTRA_BODY
import com.aracroproducts.attentionv2.MainViewModel.Companion.EXTRA_RECIPIENT
import com.aracroproducts.attentionv2.MainViewModel.Companion.FAILED_ALERT_CHANNEL_ID
import com.aracroproducts.attentionv2.MainViewModel.Companion.createFailedAlertNotificationChannel
import kotlinx.coroutines.launch
import kotlin.math.min


fun filterUsername(username: String): String {
    return username.substring(0, min(username.length, 150)).filter {
        it.isLetterOrDigit() or (it == '@') or (it == '_') or (it == '-') or (it == '+') or (it == '.')
    }
}

fun filterSpecialChars(string: String): String {
    return string.filter { letter ->
        letter != '\n' && letter != '\t' && letter != '\r'
    }
}

const val DISABLED_ALPHA = 0.38f
const val HIGH_ALPHA = 1f

/**
 * Notifies the user that an alert was not successfully sent
 *
 * @param text  - The message to display in the body of the notification
 * @requires    - Code is one of ErrorType.SERVER_ERROR or ErrorType.BAD_REQUEST
 */
fun notifyUser(context: Context, text: String, message: Message? = null) {
        val intent = getSendIntent(context, message)

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
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManagerCompat.notify(notificationID, builder.build())
}

fun getSendIntent(context: Context, message: Message?): Intent {
    val intent = Intent(context, MainActivity::class.java)
    if (message != null) {
        intent.action = context.getString(R.string.reopen_failed_alert_action)
        intent.putExtra(EXTRA_RECIPIENT, message.otherId)
        intent.putExtra(EXTRA_BODY, message.message)
    }
    return intent
}

const val EXTRA_ALERT_ID = "com.aracroproducts.attention.extra.ALERT_ID"

