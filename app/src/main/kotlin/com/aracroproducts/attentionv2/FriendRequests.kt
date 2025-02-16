package com.aracroproducts.attentionv2

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aracroproducts.attentionv2.AlertSendService.Companion.EXTRA_NOTIFICATION_ID
import com.aracroproducts.attentionv2.AlertSendService.Companion.FRIEND_SERVICE_CHANNEL_ID
import com.aracroproducts.attentionv2.AlertViewModel.Companion.NO_ID
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

class FriendManagementService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val jobs = ConcurrentHashMap<Int, Job>()

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        jobs[startId] = scope.launch {
            try {
                execute(intent)
            } finally {
                jobs.remove(startId)
                if (jobs.isEmpty) {
                    stopSelfResult(startId)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private suspend fun execute(intent: Intent?) {
        if (intent == null) return

        val username = intent.getStringExtra(EXTRA_USERNAME) ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_ID)
        val action = intent.action

        val repository = AttentionRepository(AttentionDB.getDB(this))
        val preferencesRepository = PreferencesRepository(getDataStore(this.applicationContext))

        val token = preferencesRepository.getToken()

        if (token == null) {
            if (action == ACTION_ACCEPT) {
                repository.cacheFriend(username)
            }

            return
        }

        try {
            when (action) {
                ACTION_ACCEPT, ACTION_IGNORE, ACTION_BLOCK -> {
                    if (notificationId != NO_ID) {
                        (getSystemService(
                            NOTIFICATION_SERVICE
                        ) as NotificationManager).cancel(notificationId)
                    }
                }
            }

            when (action) {
                ACTION_ACCEPT -> {
                    repository.cacheFriend(username)
                    repository.addFriend(username, name, token)
                    repository.deleteCachedFriend(username)
                }

                ACTION_IGNORE -> {
                    repository.ignore(username, token)
                }

                ACTION_BLOCK -> {
                    repository.block(username, token)
                }
            }

        } catch (e: HttpException) {
            val response = e.response()
            val message = e.toMessage()
            if (response?.code()?.mod(100) != 2 && response?.code()?.mod(100) != 4) {
                Firebase.crashlytics.log(message)
            }
            Log.e(sTAG, message)
        } catch (e: Exception) {
            Log.e(
                sTAG,
                "An error occurred: ${e.stackTraceToString()}"
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(
                sTAG,
                "An error occurred: ${e.stackTraceToString()}"
            )
            throw e
        }
    }

    private fun foreground() {
        try {
            val notification = NotificationCompat.Builder(this, FRIEND_SERVICE_CHANNEL_ID)
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

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        jobs[startId]?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private val sTAG: String = FriendManagementService::class.java.simpleName
    }

}

const val ACTION_ACCEPT = "com.aracroproducts.attention.broadcast.ACCEPT"
const val ACTION_IGNORE = "com.aracroproducts.attention.broadcast.IGNORE"
const val ACTION_BLOCK = "com.aracroproducts.attention.broadcast.BLOCK"
const val EXTRA_USERNAME = "username"
const val EXTRA_NAME = "name"