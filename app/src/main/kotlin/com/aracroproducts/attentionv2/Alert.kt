package com.aracroproducts.attentionv2

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import java.util.Calendar

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

    inner class AlertBroadCastReceiver : BroadcastReceiver() {
        private val sTAG = javaClass.name

        /**
         * This method is called when the BroadcastReceiver is receiving an Intent
         * broadcast.  During this time you can use the other methods on
         * BroadcastReceiver to view/modify the current result values.  This method
         * is always called within the main thread of its process, unless you
         * explicitly asked for it to be scheduled on a different thread using
         * [android.content.Context.registerReceiver]. When it runs on the main
         * thread you should
         * never perform long-running operations in it (there is a timeout of
         * 10 seconds that the system allows before considering the receiver to
         * be blocked and a candidate to be killed). You cannot launch a popup dialog
         * in your implementation of onReceive().
         *
         *
         * **If this BroadcastReceiver was launched through a &lt;receiver&gt; tag,
         * then the object is no longer alive after returning from this
         * function.** This means you should not perform any operations that
         * return a result to you asynchronously. If you need to perform any follow up
         * background work, schedule a [android.app.job.JobService] with
         * [android.app.job.JobScheduler].
         *
         * If you wish to interact with a service that is already running and previously
         * bound using [bindService()][android.content.Context.bindService],
         * you can use [.peekService].
         *
         *
         * The Intent filters used in [android.content.Context.registerReceiver]
         * and in application manifests are *not* guaranteed to be exclusive. They
         * are hints to the operating system about how to find suitable recipients. It is
         * possible for senders to force delivery to specific recipients, bypassing filter
         * resolution.  For this reason, [onReceive()][.onReceive]
         * implementations should respond only to known actions, ignoring any unexpected
         * Intents that they may receive.
         *
         * @param context The Context in which the receiver is running.
         * @param intent The Intent being received.
         */
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) {
                Log.e(sTAG, "null context for receiver")
                return
            }

            if (intent == null) {
                Log.w(sTAG, "Null intent")
                return
            }

            if (intent.getStringExtra(EXTRA_ALERT_ID) != alertModel.alertId) {
                return
            }

            when (intent.action) {
                context.getString(R.string.dismiss_action) -> {
                    alertModel.ok()
                    finish()
                }

                context.getString(R.string.silence_action) -> {
                    alertModel.ok()
                    AlertHandler.showNotification(
                        application,
                        alertModel.message.text,
                        alertModel.sender,
                        alertModel.alertId,
                        0,
                        alertModel.id
                    )
                }

                else -> {
                    Log.w(sTAG, "Unexpected action: ${intent.action}")
                    return
                }
            }
        }

    }

    private val receiver = AlertBroadCastReceiver()

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

        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(getString(R.string.silence_action))
            addAction(getString(R.string.dismiss_action))
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

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

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    fun Dialog(message: AnnotatedString) {

        var imageBitmap: Bitmap? by remember {
            mutableStateOf(null)
        }
        LaunchedEffect(key1 = alertModel.sender.photo) {
            val photo = alertModel.sender.photo
            if (photo != null) {
                launch(context = Dispatchers.Default) {
                    val imageDecoded = Base64.decode(photo, Base64.DEFAULT)
                    imageBitmap = BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)
                }
            }
        }
        BasicAlertDialog(onDismissRequest = { }) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
                    Text(
                        text = getString(R.string.alert_title),
                        style = MaterialTheme.typography.titleLarge
                    )
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
                            }), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Thin
                        )
                        Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
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
                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedVisibility(visible = alertModel.showReply, enter = expandVertically(), exit = shrinkVertically()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f, fill = true),
                                value = alertModel.replyMessage,
                                onValueChange = {
                                    alertModel.replyMessage = it
                                },
                                label = {
                                    Text(
                                        text = getString(
                                            R.string.message_label,
                                            alertModel.sender.name
                                        )
                                    )
                                })
                            IconButton(onClick = {
                                alertModel.sendAlert()
                                alertModel.clearNotification()
                                finish()
                            }, modifier = Modifier.fillMaxHeight()) {
                                Icon(Icons.AutoMirrored.Filled.Send, getString(R.string.send))
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.End) {
                        AnimatedVisibility(
                            visible = !alertModel.silenced, enter = fadeIn(), exit = fadeOut()
                        ) {
                            TextButton(onClick = {
                                alertModel.ok()
                                alertModel.clearNotification()
                            }) {
                                Text(text = getString(R.string.silence))
                            }
                        }

                        AnimatedVisibility(
                            visible = !alertModel.showReply,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Button(onClick = {
                                alertModel.ok()
                                alertModel.showReply = true
                            }) {
                                Row(Modifier.wrapContentSize()) {
                                    Icon(Icons.AutoMirrored.Filled.Reply, null)
                                    Text(text = getString(R.string.reply))
                                }
                            }
                        }

                        Button(onClick = {
                            alertModel.ok()
                            finish()
                        }) {
                            when (alertModel.showReply) {
                                false -> {
                                    Text(text = getString(android.R.string.ok))
                                }

                                true -> {
                                    Row(Modifier.wrapContentSize()) {
                                        Icon(Icons.Filled.Close, null)
                                        Text(text = getString(R.string.close))
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}