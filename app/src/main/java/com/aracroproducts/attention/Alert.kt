package com.aracroproducts.attention

import androidx.appcompat.app.AppCompatActivity
import android.media.Ringtone
import android.widget.TextView
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.os.*
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import java.util.HashSet

class Alert : AppCompatActivity() {
    private val sTAG = javaClass.name
    private var from: String? = null
    private var message: String? = null
    private var id = 0
    private var timer: CountDownTimer? = null
    private var r: Ringtone? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert)
        setFinishOnTouchOutside(false)
        Log.d(sTAG, "Dialog opened")
        val messageView = findViewById<TextView>(R.id.alert_message_view)
        val intent = intent
        from = intent.getStringExtra(AlertHandler.REMOTE_FROM)
        message = intent.getStringExtra(AlertHandler.REMOTE_MESSAGE)
        if (intent.hasExtra(AlertHandler.ASSOCIATED_NOTIFICATION)) id = intent.getIntExtra(AlertHandler.ASSOCIATED_NOTIFICATION, 0)
        messageView.text = message
        if (intent.getBooleanExtra(AlertHandler.SHOULD_VIBRATE, true)) {
            val settings = PreferenceManager.getDefaultSharedPreferences(this)
            val vibrate = settings.getStringSet(getString(R.string.vibrate_preference_key), HashSet())
            val ring = settings.getStringSet(getString(R.string.ring_preference_key), HashSet())
            ring(ring)
            vibrate(vibrate)
        }
    }

    private fun shouldRing(ringAllowed: Set<String>?): Boolean {
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (manager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> if (ringAllowed!!.contains("silent")) return true
            AudioManager.RINGER_MODE_VIBRATE -> if (ringAllowed!!.contains("vibrate")) return true
            AudioManager.RINGER_MODE_NORMAL -> if (ringAllowed!!.contains("ring")) return true
        }
        return false
    }

    private fun shouldVibrate(vibrateAllowed: Set<String>?): Boolean {
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (manager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> if (vibrateAllowed!!.contains("silent")) return true
            AudioManager.RINGER_MODE_VIBRATE -> if (vibrateAllowed!!.contains("vibrate")) return true
            AudioManager.RINGER_MODE_NORMAL -> if (vibrateAllowed!!.contains("ring")) return true
        }
        return false
    }

    private fun ring(ringAllowed: Set<String>?) {
        if (shouldRing(ringAllowed)) {
            val notification = RingtoneManager.getActualDefaultRingtoneUri(this@Alert, RingtoneManager.TYPE_RINGTONE)
            r = RingtoneManager.getRingtone(this@Alert, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r!!.volume = 1.0f
            r!!.play()
        }
    }

    private fun vibrate(vibrateAllowed: Set<String>?) {
        if (!shouldRing(vibrateAllowed)) return
        timer = object : CountDownTimer(5000, 500) {
            override fun onTick(l: Long) {
                if (shouldVibrate(vibrateAllowed)) {
                    Log.d(sTAG, "Vibrating device")
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager =  getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        getSystemService(VIBRATOR_SERVICE) as Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vibrator.vibrate(400)
                    }
                }
            }

            override fun onFinish() {}
        }
        timer!!.start()
    }

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
        createNotificationChannel()
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

    fun onOK(view: View) {
        if (timer != null) {
            timer!!.cancel()
        }
        if (r != null && r!!.isPlaying) {
            r!!.stop()
        }
        finish()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.channel_name)
            val description = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(AlertHandler.CHANNEL_ID, name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}