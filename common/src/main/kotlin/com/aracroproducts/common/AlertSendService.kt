package com.aracroproducts.common

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aracroproducts.common.AlertHandler.Companion.EXTRA_TIMESTAMP
import com.aracroproducts.common.PreferencesRepository.Companion.MY_TOKEN
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
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

    private val mainActivity = (application as AttentionApplicationBase).mainActivity

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        jobs[startId] = scope.launch {
            try {
                if (intent == null) {
                    return@launch
                }
                val alertId = intent.getStringExtra(EXTRA_ALERT_ID)
                val senderUsername = intent.getStringExtra(EXTRA_SENDER)
                if (senderUsername == null) {
                    return@launch
                }

                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_ID)
                val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)

                when (intent.action) {
                    ACTION_REPLY -> {
                        val messageStr = if (fromNotification) {
                            val broadcastIntent = Intent(ACTION_MARK_AS_READ).apply {
                                putExtra(EXTRA_ALERT_ID, alertId)
                                setPackage(this@AlertSendService.packageName)
                            }
                            applicationContext.sendBroadcast(broadcastIntent)

                            RemoteInput.getResultsFromIntent(intent)
                                ?.getCharSequence(KEY_TEXT_REPLY)?.toString()
                        } else {
                            intent.getStringExtra(KEY_TEXT_REPLY)
                        }

                        val readJob =
                            launch { if (alertId != null) sendMarkAsRead(senderUsername, alertId) }
                        val sendJob =
                            launch { sendAlert(senderUsername, notificationId, messageStr) }

                        readJob.join()
                        sendJob.join()
                    }

                    ACTION_SILENCE -> {
                        if (fromNotification) {
                            val broadcastIntent = Intent(ACTION_SILENCE).apply {
                                putExtra(EXTRA_ALERT_ID, alertId)
                                setPackage(this@AlertSendService.packageName)
                            }
                            applicationContext.sendBroadcast(broadcastIntent)
                        }
                        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0)
                        val messageText = intent.getStringExtra(EXTRA_MESSAGE_TEXT)

                        val sender = AttentionDB.getDB(applicationContext).getFriendDAO()
                            .getFriend(senderUsername)

                        if (messageText == null || sender == null || notificationId == NO_ID || alertId == null) {
                            return@launch
                        }

                        // Updates existing notification to not show the silence button
                        AlertHandler.showNotification(
                            application as AttentionApplicationBase,
                            messageText,
                            sender,
                            alertId,
                            0,
                            timestamp,
                            notificationId,
                        )
                        sendMarkAsRead(senderUsername, alertId)
                    }

                    ACTION_MARK_AS_READ -> {
                        if (alertId == null) return@launch
                        if (fromNotification) {
                            val broadcastIntent = Intent(ACTION_MARK_AS_READ).apply {
                                putExtra(EXTRA_ALERT_ID, alertId)
                                setPackage(this@AlertSendService.packageName)
                            }
                            applicationContext.sendBroadcast(broadcastIntent)
                        }

                        sendMarkAsRead(senderUsername, alertId)
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(
                            notificationId
                        )
                    }
                }
                // On silence, do this

            } finally {
                jobs.remove(startId)
                if (jobs.isEmpty) {
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

    private suspend fun sendMarkAsRead(fromUsername: String, alertId: String) {
        val attentionRepository = (application as AttentionApplicationBase).container.repository
        val preferencesRepository =
            (application as AttentionApplicationBase).container.settingsRepository
        // token is auth token
        val token = preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))

        val fcmToken = preferencesRepository.getValue(
            stringPreferencesKey(
                FCM_TOKEN
            )
        )

        if (token == null || fcmToken == null) {
            Log.e(javaClass.name, "Token is null when sending read receipt!")
            return
        }

        attentionRepository.sendReadReceipt(
            from = fromUsername, alertId = alertId, fcmToken = fcmToken, authToken = token
        )
    }

    private suspend fun sendAlert(recipient: String, notificationId: Int, messageStr: String?) {

        val repository =
            AttentionRepository(AttentionDB.getDB(this), application as AttentionApplicationBase)
        val preferencesRepository = PreferencesRepository(getDataStore(this.applicationContext))

        val message = Message(
            otherId = recipient,
            timestamp = System.currentTimeMillis(),
            direction = DIRECTION.Outgoing,
            message = messageStr
        )
        val token = preferencesRepository.getToken()
        if (token == null) {
            notifyUser(
                this,
                this.getString(R.string.alert_failed_signed_out),
                mainActivity,
                message,
            )

            sendLoginBroadcast(message)

            return
        }
        val to = repository.getFriend(recipient)

        try {
            repository.alertSending(message.otherId)
            repository.sendMessage(
                message, token
            )
            if (notificationId != NO_ID) {
                (getSystemService(
                    NOTIFICATION_SERVICE
                ) as NotificationManager).cancel(notificationId)
            }
            sendBroadcast(ACTION_SUCCESS, message.otherId)
        } catch (e: HttpException) {
            val response = e.response()
            val errorBody = response?.errorBody()?.string()
            repository.alertError(message.otherId)
            if (response?.code() != 403) {
                sendBroadcast(ACTION_ERROR, message.otherId)
            }
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
                                ),
                                mainActivity
                            )
                        }

                        else -> {
                            notifyUser(
                                this,
                                getString(
                                    R.string.alert_failed_bad_request, to.name
                                ),
                                mainActivity
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
                                ),
                                mainActivity
                            )
                            repository.alertError(message.otherId)
                            sendBroadcast(ACTION_ERROR, message.otherId)
                        }

                        else -> {
                            notifyUser(
                                this,
                                getString(R.string.alert_failed_signed_out),
                                mainActivity,
                                message
                            )
                            sendLoginBroadcast(message)
                        }
                    }
                }

                429 -> {
                    notifyUser(
                        this,
                        getString(
                            R.string.alert_rate_limited
                        ), mainActivity, message
                    )

                }

                else -> {
                    notifyUser(
                        this,
                        getString(
                            R.string.alert_failed_server_error, to.name
                        ),
                        mainActivity
                    )
                    if (e.response()?.code()?.mod(100) != 2 && response?.code()?.mod(100) != 4) {
                        Firebase.crashlytics.log(e.toMessage())
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = "An error occurred: ${e.stackTraceToString()}"
            Log.e(
                sTAG,
                errorMessage
            )
            notifyUser(this, getString(R.string.alert_failed), mainActivity, message)
            repository.alertError(message.otherId)
            sendBroadcast(ACTION_ERROR, message.otherId)

        } catch (e: TimeoutCancellationException) {
            Log.e(
                sTAG,
                "An error occurred: ${e.stackTraceToString()}"
            )
            notifyUser(this, getString(R.string.alert_failed), mainActivity, message)
            repository.alertError(message.otherId)
            sendBroadcast(ACTION_ERROR, message.otherId)
            throw e
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

    private fun sendBroadcast(action: String, recipient: String) {
        sendBroadcast(Intent().apply {
            this.action = action
            putExtra(EXTRA_RECIPIENT, recipient)
            setPackage(this@AlertSendService.packageName)
        })
    }

    private fun sendLoginBroadcast(message: Message) {

        sendBroadcast(Intent().apply {
            action = ACTION_LOGIN
            putExtra(
                Intent.EXTRA_INTENT,
                getSendIntent(
                    this@AlertSendService,
                    message,
                    (application as AttentionApplicationBase).mainActivity
                )
            )
            setPackage(this@AlertSendService.packageName)
        })
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "service_alert"
        const val FRIEND_SERVICE_CHANNEL_ID = "friend_service_alert"
        const val ACTION_SUCCESS = "com.aracroproducts.attention.broadcast.SUCCESS"
        const val ACTION_LOGIN = "com.aracroproducts.attention.broadcast.LOGIN"
        const val ACTION_ERROR = "com.aracroproducts.attention.broadcast.ERROR"
        const val ACTION_MARK_AS_READ =
            "com.aracroproducts.attention.action.MARK_AS_READ"  // dismisses the dialog and marks as read
        const val ACTION_REPLY =
            "com.aracroproducts.attention.action.REPLY"  // dismisses the dialog and marks as read
        const val ACTION_SILENCE =
            "com.aracroproducts.attention.action.SILENCE"  // marks as read but does not dismiss the dialog
        const val EXTRA_MESSAGE_TEXT = "com.aracroproducts.attention.extra.MESSAGE_TEXT"
        const val EXTRA_RECIPIENT = "com.aracroproducts.attention.extra.RECIPIENT"
        const val EXTRA_SENDER = "com.aracroproducts.attention.extra.SENDER"
        const val EXTRA_NOTIFICATION_ID = "com.aracroproducts.attention.extra.NOTIFICATION_ID"
        const val EXTRA_FROM_NOTIFICATION = "com.aracroproducts.attention.extra.FROM_NOTIFICATION"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private val sTAG = AlertSendService::class.java.simpleName
    }
}