package com.aracroproducts.attentionv2

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aracroproducts.attentionv2.AlertViewModel.Companion.NO_ID
import com.aracroproducts.attentionv2.SendMessageReceiver.Companion.EXTRA_NOTIFICATION_ID
import com.aracroproducts.attentionv2.SendMessageReceiver.Companion.EXTRA_SENDER
import com.aracroproducts.attentionv2.SendMessageReceiver.Companion.KEY_TEXT_REPLY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

class AlertSendService : Service() {

    // from https://stackoverflow.com/a/63407811/7484693
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val jobs = ConcurrentHashMap<Int, Job>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        jobs[startId] = scope.launch {
            try {
                sendAlert(intent)
            } finally {
                jobs.remove(startId)
                if (jobs.isEmpty()) {
                    stopSelfResult(startId)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        jobs[startId]?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun foreground() {
        try {
            val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                // Create the notification to display while the service is running
                .build()
            ServiceCompat.startForeground(
                /* service = */ this,
                /* id = */ 100, // Cannot be 0
                /* notification = */ notification,
                /* foregroundServiceType = */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                } else {
                    0
                },
            )
        } catch (_: Exception) {
            // we might not be able to put ourselves in the foreground - oh well
        }
    }

    private suspend fun sendAlert(intent: Intent?) {
        if (intent == null) return

        val recipient = intent.getStringExtra(EXTRA_SENDER) ?: return
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_ID)
        val messageStr = intent.getStringExtra(KEY_TEXT_REPLY)
        val repository = AttentionRepository(AttentionDB.getDB(this))
        val preferencesRepository = PreferencesRepository(getDataStore(this.applicationContext))

        val message = Message(
            otherId = recipient,
            timestamp = System.currentTimeMillis(),
            direction = DIRECTION.Outgoing,
            message = messageStr.toString()
        )
        val token = preferencesRepository.getToken()
        val fcmToken = preferencesRepository.getValue(
            stringPreferencesKey(
                MainViewModel.FCM_TOKEN
            )
        )
        if (token == null) {
            notifyUser(
                this,
                this.getString(R.string.alert_failed_signed_out),
                message
            )
            return
        }
        val to = repository.getFriend(recipient)
        if (alertId != null && fcmToken != null) {
            repository.sendReadReceipt(
                alertId, recipient,
                fcmToken, token
            )
        }
        try {
            repository.sendMessage(
                Message(
                    otherId = recipient,
                    timestamp = System.currentTimeMillis(),
                    message = message.toString(),
                    direction = DIRECTION.Outgoing
                ), token
            )
            (getSystemService(
                AppCompatActivity.NOTIFICATION_SERVICE
            ) as NotificationManager).cancel(notificationId)
        } catch (e: HttpException) {
            val response = e.response()
            val errorBody = response?.errorBody()?.string()
            when (response?.code()) {
                400 -> {
                    if (errorBody == null) {
                        Log.e(
                            sTAG, "Got response but body was null"
                        )
                        return
                    }
                    when {
                        errorBody.contains(
                            "Could not find user", true
                        ) -> {
                            notifyUser(
                                this,
                                getString(
                                    R.string.alert_failed_no_user, to.name
                                )
                            )
                        }

                        else -> {
                            notifyUser(
                                this,
                                getString(
                                    R.string.alert_failed_bad_request, to.name
                                )
                            )
                        }
                    }
                }

                403 -> {
                    if (errorBody == null) {
                        Log.e(
                            sTAG, "Got response but body was null"
                        )
                        return
                    }
                    when {
                        errorBody.contains(
                            "does not have you as a friend", true
                        ) -> {
                            notifyUser(
                                this,
                                getString(
                                    R.string.alert_failed_not_friend, to.name
                                )
                            )
                        }

                        else -> notifyUser(
                            this,
                            getString(R.string.alert_failed_signed_out),
                            message
                        )
                    }
                }

                429 -> {
                    notifyUser(
                        this,
                        getString(
                            R.string.alert_rate_limited
                        ), message
                    )

                }

                else -> {
                    notifyUser(
                        this,
                        getString(
                            R.string.alert_failed_server_error, to.name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                sTAG,
                "An error occurred: ${e.message}\n${e.stackTrace.joinToString(separator = "\n")}"
            )
            notifyUser(this, getString(R.string.alert_failed), message)
        } catch (e: TimeoutCancellationException) {
            Log.e(
                sTAG,
                "An error occurred: ${e.message}\n${
                    e.stackTrace.joinToString(
                        separator = "\n"
                    )
                }"
            )
            notifyUser(this, getString(R.string.alert_failed), message)
        }

        // 1. get recipient
        // 2. get the message with getResultsFromIntent
        // 3. show cancel progress?
        // 4. call repository.sendMessage
        // 5. dismiss the notification
    }

    /**
     * This service cannot be bound. Always returns null
     */
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "service_alert"
        private val sTAG = AlertSendService::class.java.name
    }
}