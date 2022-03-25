package com.aracroproducts.attentionv2

import androidx.appcompat.app.AppCompatActivity
import android.media.Ringtone
import android.widget.TextView
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationManagerCompat
import android.os.*
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import java.util.HashSet

/**
 * An Activity that displays the pop up dialog for an alert
 */
class Alert : AppCompatActivity() {
    private val sTAG = javaClass.name
    private var from: String? = null
    private var message: String? = null
    private var id = 0
    private var timer: CountDownTimer? = null
    private var r: Ringtone? = null

    /**
     * Called when the activity is created
     *
     * @param savedInstanceState - Data saved from before a configuration changed. Not used
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert)

        // don't let users dismiss by tapping outside the dialog - prevent accidental dismissals
        setFinishOnTouchOutside(false)
        Log.d(sTAG, "Dialog opened")
        val messageView = findViewById<TextView>(R.id.alert_message_view)

        // the intent used to launch this activity - from superclass
        val intent = intent
        from = intent.getStringExtra(AlertHandler.REMOTE_FROM)
        message = intent.getStringExtra(AlertHandler.REMOTE_MESSAGE)

        // gets the id so that it can dismiss the notification when the dialog is closed
        if (intent.hasExtra(AlertHandler.ASSOCIATED_NOTIFICATION)) id =
                intent.getIntExtra(AlertHandler.ASSOCIATED_NOTIFICATION, 0)
        messageView.text = message
        if (intent.getBooleanExtra(AlertHandler.SHOULD_VIBRATE, true)) {
            val settings = PreferenceManager.getDefaultSharedPreferences(this)
            val vibrate =
                    settings.getStringSet(getString(R.string.vibrate_preference_key), HashSet())
            val ring = settings.getStringSet(getString(R.string.ring_preference_key), HashSet())
            ring(ring)
            vibrate(vibrate)
        }
    }

    /**
     * Whether the user preferences allow the requested action (ringtone or vibration)
     *
     * @param prefs - The set of user settings for when the system notification settings can
     * be overridden
     */
    private fun soundAllowed(prefs: Set<String>?): Boolean {
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (manager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> if (prefs!!.contains("silent")) return true
            AudioManager.RINGER_MODE_VIBRATE -> if (prefs!!.contains("vibrate")) return true
            AudioManager.RINGER_MODE_NORMAL -> if (prefs!!.contains("ring")) return true
        }
        return false
    }

    /**
     * Rings if it's allowed - calls shouldRing()
     *
     * @param ringAllowed - The set of user settings for when the system notification settings can
     * be overridden
     */
    private fun ring(ringAllowed: Set<String>?) {
        if (soundAllowed(ringAllowed)) {
            val notification = RingtoneManager.getActualDefaultRingtoneUri(this@Alert,
                    RingtoneManager.TYPE_RINGTONE)
            r = RingtoneManager.getRingtone(this@Alert, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r!!.volume = 1.0f
            r!!.play()
        }
    }

    /**
     * Vibrates if it's allowed - calls soundAllowed() - does not vibrate if it is not allowed
     *
     * @param vibrateAllowed - User settings for when system ring settings can be overridden
     */
    private fun vibrate(vibrateAllowed: Set<String>?) {
        if (!soundAllowed(vibrateAllowed)) return
        timer = object : CountDownTimer(5000, 500) {
            override fun onTick(l: Long) {
                if (soundAllowed(vibrateAllowed)) {
                    Log.d(sTAG, "Vibrating device")
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager =
                                getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        getSystemService(VIBRATOR_SERVICE) as Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(400,
                                VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vibrator.vibrate(400)
                    }
                }
            }

            override fun onFinish() {}
        }
        timer!!.start()
    }

    /**
     * Handles the destruction of the dialog. If the user did not click "ok" displays the "missed
     * alert" notification
     */
    public override fun onDestroy() {
        super.onDestroy()
        if (id != 0) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
        }
        if (isFinishing) return  // prevent this notification from being shown when the user clicks "ok"
        val intent = Intent(this, Alert::class.java)
        intent.putExtra("alert_message", message)
        intent.putExtra("alert_from", from)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        AlertHandler.createMissedNotificationChannel(this)
        val builder = NotificationCompat.Builder(this, AlertHandler.CHANNEL_ID)
        builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.notification_title, from))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent).setAutoCancel(true)
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    /**
     * When the user clicks the "ok" button
     */
    fun onOK(view: View) {
        if (timer != null) {
            timer!!.cancel()
        }
        if (r != null && r!!.isPlaying) {
            r!!.stop()
        }
        finish()
    }
}