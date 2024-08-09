package com.aracroproducts.attentionv2

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aracroproducts.attentionv2.LoginActivity.Companion.LIST_ELEMENT_PADDING
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * An Activity that displays the pop up dialog for an alert
 */
class Alert : AppCompatActivity() {
    private val sTAG = javaClass.name

    val alertModel: AlertViewModel by viewModels(factoryProducer = {
        AlertViewModelFactory(
            intent,
            (application as AttentionApplication).container.repository,
            (application as AttentionApplication).container.settingsRepository,
            application
        )
    })

    inner class AlertViewModelFactory(
        private val intent: Intent,
        private val attentionRepository: AttentionRepository,
        private val preferencesRepository: PreferencesRepository,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlertViewModel::class.java)) {
                return AlertViewModel(
                    intent, attentionRepository, preferencesRepository, application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    /**
     * Called when the activity is created
     *
     * @param savedInstanceState - Data saved from before a configuration changed. Not used
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // don't let users dismiss by tapping outside the dialog - prevent accidental dismissals
        setFinishOnTouchOutside(false)

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HarmonizedTheme {
                    Dialog(message = alertModel.message)
                }
            } else {
                AppTheme {
                    Dialog(message = alertModel.message)
                }
            }
        }

        Log.d(sTAG, "Dialog opened")
        alertModel.startPrompting()
        Log.d(sTAG, "${alertModel.timestamp} ${System.currentTimeMillis()}")
    }

    @Composable
    fun Dialog(message: AnnotatedString) {

        var imageBitmap: Bitmap? by remember {
            mutableStateOf(null)
        }
        LaunchedEffect(key1 = alertModel.sender?.photo) {
            val photo = alertModel.sender?.photo
            if (photo != null) {
                launch(context = Dispatchers.Default) {
                    val imageDecoded = Base64.decode(photo, Base64.DEFAULT)
                    imageBitmap = BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)
                }
            }
        }
        AlertDialog(onDismissRequest = { }, dismissButton = {
            // TODO inline reply
            Row {
                AnimatedVisibility(
                    visible = !alertModel.silenced, enter = fadeIn(), exit = fadeOut()
                ) {
                    TextButton(onClick = { alertModel.silence() }) {
                        Text(text = getString(R.string.silence))
                    }
                }

                AnimatedVisibility(
                    visible = alertModel.showDNDButton && (getSystemService(
                        NOTIFICATION_SERVICE
                    ) as NotificationManager).isNotificationPolicyAccessGranted,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(onClick = {
                        alertModel.silence()
                        val intent = Intent(
                            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                        )
                        startActivity(intent)
                    }) {
                        Text(text = getString(R.string.open_settings))
                    }
                }
            }
        }, confirmButton = {
            Button(onClick = {
                alertModel.ok()
                finish()
            }) {
                Text(text = getString(android.R.string.ok))
            }
        }, title = { Text(getString(R.string.alert_title)) }, text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.Top) {
                    imageBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = getString(
                                R.string.pfp_description,
                                alertModel.from
                            ),
                            modifier = Modifier
                                .size(MainActivity.ICON_SIZE)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(MainActivity.ICON_SPACING))
                    }
                    Text(message)
                }
                Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
                Text(
                    timeSince(since = Calendar.getInstance().apply {
                        timeInMillis = alertModel.timestamp
                    }), color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = ContentAlpha.medium
                    )
                )
            }
        })
    }

    @Composable
    fun timeSince(since: Calendar): String {
        var value by remember { mutableStateOf(durationToMinimalDisplay(since)) }
        Log.d(Alert::class.java.name, since.toString())
        LaunchedEffect(Unit) {
            while (true) { // we never need to recompose
                if (value.second == -1L) {
                    break
                }
                delay(value.second)
                value = durationToMinimalDisplay(since)
            }
        }

        return value.first
    }

    /**
     * Given a time instant, returns a minimal way of displaying this time (relative to current
     * time)
     *
     * If the time was less than a minute ago, will display as "x s ago". If less than an hour
     * ago, will display as "x m ago". If the time was the same calendar day, displays as a
     * localized hour:minute format, in local time. If the time was on a previous calendar day,
     * displays as a localized date-time format, in local time.
     *
     * The first element of the pair is the formatted time. The second element is how long until
     * this value will change and should be refreshed, in milliseconds
     */
    private fun durationToMinimalDisplay(since: Calendar): Pair<String, Long> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val now = Instant.now()
            val duration = Duration.between(since.toInstant(), now)
            when {
                duration.seconds < 60 -> {
                    return Pair(
                        getString(R.string.seconds_ago, duration.seconds),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) secondsToMillis(1) - duration.toMillisPart() else secondsToMillis(
                            1
                        ) - duration.nano / 1e6.toLong()
                    )
                }
                duration.toMinutes() < 60 -> {
                    return Pair(
                        getString(R.string.minutes_ago, duration.toMinutes()),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) minutesToMillis() - secondsToMillis(
                            duration.toSecondsPart().toLong()
                        ) else duration.seconds % 60
                    )
                }
                since.toInstant().truncatedTo(ChronoUnit.DAYS) == now.truncatedTo(
                    ChronoUnit.DAYS
                ) -> {
                    return Pair(
                        getString(
                            R.string.sent_at, DateFormat.getTimeInstance().format(
                                since.time
                            )
                        ), Duration.between(
                            since.toInstant(),
                            since.toInstant().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS)
                        ).toMillis()
                    ) // This returns the amount of time (in milliseconds) until tomorrow
                }
                else -> {
                    return Pair(
                        getString(
                            R.string.sent_on, DateFormat.getDateTimeInstance().format(since.time)
                        ), -1
                    ) // This value will never change (unless the user changes their timezone, which
                    // probably wouldn't happen without the app getting recomposed?)
                }
            }
        } else { // Look I can't be bothered to figure out how to durations without the Duration class
            // I'm sure there's a way but I'm not doing it sorry
            // Besides, Android O is now 5 years old - basically everyone is running it or newer
            return Pair(
                getString(
                    R.string.sent_on, DateFormat.getDateTimeInstance().format(since.time)
                ), -1
            )
        }
    }

    private fun secondsToMillis(seconds: Long): Long {
        return seconds * 1000
    }

    private fun minutesToMillis(minutes: Long = 1): Long {
        return minutes * secondsToMillis(60)
    }
}