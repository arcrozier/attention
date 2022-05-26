package com.aracroproducts.attentionv2

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager

class AlertViewModel(
    intent: Intent, private val attentionRepository: AttentionRepository,
    application: Application
) :
    AndroidViewModel(application) {

    var silenced: Boolean by mutableStateOf(
        !intent.getBooleanExtra(AlertHandler.SHOULD_VIBRATE, true)
    )
    private var isFinishing: Boolean by mutableStateOf(false)
    val from = intent.getStringExtra(AlertHandler.REMOTE_FROM) ?: ""
    var message by mutableStateOf(
        AnnotatedString(
            intent.getStringExtra(
                AlertHandler
                    .REMOTE_MESSAGE
            ) ?: ""
        )
    )
    var showDNDButton by mutableStateOf(false)
    val id = intent.getIntExtra(AlertHandler.ASSOCIATED_NOTIFICATION, NO_ID)
    val alertId = intent.getStringExtra(AlertHandler.ALERT_ID) ?: ""
    private val fromUsername = intent.getStringExtra(AlertHandler.REMOTE_FROM_USERNAME) ?: ""
    private var ringerMode: Int? = null


    private val ringtone = RingtoneManager.getRingtone(
        getApplication(), RingtoneManager
            .getActualDefaultRingtoneUri(getApplication(), RingtoneManager.TYPE_RINGTONE)
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) volume = 1.0f
    }

    private val timer = object : CountDownTimer(5000, 500) {
        @Suppress("DEPRECATION")
        override fun onTick(l: Long) {
            if (silenced) {
                cancel()
                return
            }
            val context = getApplication<Application>()
            Log.d(sTAG, "Vibrating device")
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(AppCompatActivity.VIBRATOR_MANAGER_SERVICE)
                            as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        400,
                        MAX_AMPLITUDE
                    )
                )
            } else {
                vibrator.vibrate(400)
            }
        }

        override fun onFinish() {
            start()
        }
    }

    fun startPrompting() {
        if (!silenced) {
            val context = getApplication<Application>()
            val settings = PreferenceManager.getDefaultSharedPreferences(context)
            val vibrate =
                settings.getStringSet(context.getString(R.string.vibrate_preference_key), HashSet())
            val ring = settings.getStringSet(
                context.getString(R.string.ring_preference_key),
                HashSet()
            )

            if (ring != null) ring(ring)
            if (vibrate != null) vibrate(vibrate)
        }
    }

    fun ok() {
        silence()
        isFinishing = true

        val context = getApplication<Application>()
        val userInfo = context.getSharedPreferences(MainViewModel.USER_INFO, Context.MODE_PRIVATE)

        val fcmTokenPrefs =
            context.getSharedPreferences(MainViewModel.FCM_TOKEN, Context.MODE_PRIVATE)

        // token is auth token
        val token = userInfo.getString(MainViewModel.MY_TOKEN, null)

        val fcmToken = fcmTokenPrefs.getString(MainViewModel.FCM_TOKEN, null)

        if (token == null || fcmToken == null) {
            Log.e(javaClass.name, "Token is null when sending read receipt!")
            return
        }

        attentionRepository.sendReadReceipt(
            from = fromUsername,
            alertId = alertId,
            fcmToken = fcmToken,
            authToken = token
        )
    }

    /**
     * Rings if it's allowed - calls shouldRing()
     *
     * @param ringAllowed - The set of user settings for when the system notification settings can
     * be overridden
     */
    private fun ring(ringAllowed: Set<String>) {
        if (soundAllowed(ringAllowed)) {
            val context = getApplication<Application>()
            val manager = context.getSystemService(AppCompatActivity.AUDIO_SERVICE) as AudioManager
            if (manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                try {
                    ringerMode = manager.ringerMode
                    manager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                } catch (e: SecurityException) {
                    showDNDButton = true

                    message = buildAnnotatedString {
                        append(message)
                        append("\n\n")
                        val start = this.length - 1
                        append(context.getString(R.string.could_not_ring))
                        val end = this.length - 1
                        addStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic, fontWeight = FontWeight
                                    .Light
                            ), start, end
                        )
                    }
                }
            }
            ringtone.play()
        }
    }

    /**
     * Vibrates if it's allowed - calls soundAllowed() - does not vibrate if it is not allowed
     *
     * @param vibrateAllowed - User settings for when system ring settings can be overridden
     */
    private fun vibrate(vibrateAllowed: Set<String>) {
        if (!soundAllowed(vibrateAllowed)) return
        timer.start()
    }

    fun silence() {
        silenced = true
        val ringerModeToRestore = ringerMode
        if (ringerModeToRestore != null) {
            val context = getApplication<Application>()
            val manager = context.getSystemService(AppCompatActivity.AUDIO_SERVICE) as AudioManager
            manager.ringerMode = ringerModeToRestore
        }
        timer.cancel()
        if (ringtone.isPlaying) {
            ringtone.stop()
        }
    }

    /**
     * Whether the user preferences allow the requested action (ringtone or vibration)
     *
     * @param prefs - The set of user settings for when the system notification settings can
     * be overridden
     */
    private fun soundAllowed(prefs: Set<String>): Boolean {
        if (silenced) return false
        val context = getApplication<Application>()
        val manager = context.getSystemService(AppCompatActivity.AUDIO_SERVICE) as AudioManager
        return when (manager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> prefs.contains("silent")
            AudioManager.RINGER_MODE_VIBRATE -> prefs.contains("vibrate")
            AudioManager.RINGER_MODE_NORMAL -> prefs.contains("ring")
            else -> false
        }
    }

    /**
     * Handles the destruction of the dialog. If the user did not click "ok" displays the "missed
     * alert" notification
     */
    override fun onCleared() {
        super.onCleared()
        val context = getApplication<Application>()
        if (id != NO_ID) {
            val notificationManager = context.getSystemService(
                AppCompatActivity.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.cancel(id)
        }
        if (isFinishing) return  // prevent this notification from being shown when the user clicks "ok"
        val intent = Intent(context, Alert::class.java)
        intent.putExtra("alert_message", message)
        intent.putExtra("alert_from", from)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent
                .FLAG_IMMUTABLE
        )

        AlertHandler.createMissedNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, AlertHandler.CHANNEL_ID).apply {
            setSmallIcon(R.mipmap.app_icon)
            setContentTitle(context.getString(R.string.notification_title, from))
            setContentText(message)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
            priority = NotificationCompat.PRIORITY_MAX
            setContentIntent(pendingIntent).setAutoCancel(true)
        }

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        notificationManagerCompat.notify(System.currentTimeMillis().toInt(), builder.build())

    }

    companion object {
        val sTAG: String? = AlertViewModel::class.java.canonicalName
        const val NO_ID = -1
        const val MAX_AMPLITUDE = 255
    }
}