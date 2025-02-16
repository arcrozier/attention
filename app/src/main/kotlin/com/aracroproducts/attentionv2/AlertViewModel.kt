package com.aracroproducts.attentionv2

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.AlertSendService.Companion.ACTION_MARK_AS_READ
import com.aracroproducts.attentionv2.AlertSendService.Companion.ACTION_REPLY
import com.aracroproducts.attentionv2.AlertSendService.Companion.EXTRA_NOTIFICATION_ID
import com.aracroproducts.attentionv2.AlertSendService.Companion.EXTRA_SENDER
import com.aracroproducts.attentionv2.AlertSendService.Companion.KEY_TEXT_REPLY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertViewModel(
    intent: Intent,
    private val attentionRepository: AttentionRepository,
    private val preferencesRepository: PreferencesRepository,
    application: Application
) : AndroidViewModel(application) {

    var silenced: Boolean by mutableStateOf(
        !intent.getBooleanExtra(AlertHandler.SHOULD_VIBRATE, true)
    )
    private var showAlertOnClose = true
    val from = intent.getStringExtra(AlertHandler.REMOTE_FROM) ?: ""
    val messageText = intent.getStringExtra(AlertHandler.REMOTE_MESSAGE)
    var message by mutableStateOf(
        AnnotatedString(
            messageText ?: ""
        )
    )
    val timestamp = intent.getLongExtra(AlertHandler.EXTRA_TIMESTAMP, System.currentTimeMillis())
    var showDNDButton by mutableStateOf(false)

    /**
     * ID for the associated notification
     */
    val id = intent.getIntExtra(AlertHandler.ASSOCIATED_NOTIFICATION, NO_ID)

    /**
     * ID for the alert (from the backend)
     */
    val alertId = intent.getStringExtra(AlertHandler.ALERT_ID) ?: ""
    private val fromUsername = intent.getStringExtra(AlertHandler.REMOTE_FROM_USERNAME) ?: ""
    private var ringerMode: Int? = null

    var sender: Friend by mutableStateOf(Friend(fromUsername, ""))
    var showReply: Boolean by mutableStateOf(false)
    var replyMessage: String by mutableStateOf("")

    init {
        viewModelScope.launch {
            sender = attentionRepository.getFriend(fromUsername)
        }
    }


    private val ringtone = RingtoneManager.getRingtone(
        getApplication(), RingtoneManager.getActualDefaultRingtoneUri(
            getApplication(), RingtoneManager.TYPE_RINGTONE
        )
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) volume = 1.0f
    }

    private val timer = object : CountDownTimer(5000, 500) {
        override fun onTick(l: Long) {
            if (silenced) {
                cancel()
                return
            }
            val context = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(
                    AppCompatActivity.VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        400, MAX_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(400)
            }
        }

        override fun onFinish() {
            start()
        }
    }

    fun startPrompting() {
        if (!silenced) {
            viewModelScope.launch(context = Dispatchers.IO) {
                val context = getApplication<Application>()
                val vibrate = preferencesRepository.getValue(
                    stringSetPreferencesKey(
                        context.getString(
                            R.string.vibrate_preference_key
                        )
                    ), HashSet()
                )
                val ring = preferencesRepository.getValue(
                    stringSetPreferencesKey(
                        context.getString(
                            R.string.ring_preference_key
                        )
                    ), HashSet()
                )

                ring(ring)
                vibrate(vibrate)
            }
        }
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
                } catch (_: SecurityException) {
                    showDNDButton = true

                    message = buildAnnotatedString {
                        append(messageText)
                        append("\n\n")
                        val start = this.length - 1
                        append(context.getString(R.string.could_not_ring))
                        val end = this.length - 1
                        addStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light
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

    /**
     * Silences the alert by calling [silence], updates the associated notification if defined
     *
     * @param cancel If true, clears the associated notification, otherwise removes the silence button
     */
    fun silenceAndUpdateNotification(cancel: Boolean) {
        silence()

        viewModelScope.launch {
            if (cancel) {
                clearNotification()
            } else {
                AlertHandler.showNotification(
                    getApplication(),
                    message.text,
                    sender,
                    alertId,
                    0,
                    timestamp,
                    id,
                )
            }
        }
    }

    /**
     * Silences the alert
     *
     * Does not send a read receipt, does not finish the activity
     */
    fun silence() {
        silenced = true
        showAlertOnClose = false
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
        clearNotification()
        if (!showAlertOnClose) return  // prevent this notification from being shown when the user clicks "ok"


        viewModelScope.launch {
            AlertHandler.showNotification(
                getApplication(),
                messageText ?: "",
                sender,
                alertId,
                AlertHandler.Companion.NotificationFlags.MISSED,
                timestamp
            )
        }

    }

    fun clearNotification() {
        if (id != NO_ID) {
            val notificationManager = getApplication<Application>().getSystemService(
                AppCompatActivity.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.cancel(id)
        }
    }

    fun markAsRead() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, AlertSendService::class.java).apply {
            action = ACTION_MARK_AS_READ
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_NOTIFICATION_ID, id)
            putExtra(EXTRA_SENDER, sender.username)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

    }

    fun sendAlert() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, AlertSendService::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_SENDER, sender.username)
            putExtra(KEY_TEXT_REPLY, replyMessage)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val NO_ID = -1
        const val MAX_AMPLITUDE = 255
    }
}