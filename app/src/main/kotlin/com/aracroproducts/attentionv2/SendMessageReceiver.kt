package com.aracroproducts.attentionv2

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.RemoteInput
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aracroproducts.attentionv2.AlertViewModel.Companion.NO_ID
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import kotlin.time.Duration.Companion.seconds

class SendMessageReceiver : BroadcastReceiver() {
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
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        if (intent.action != ACTION_REPLY) return

        val recipient = intent.getStringExtra(EXTRA_SENDER) ?: return
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_ID)
        val messageStr =
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT_REPLY) ?: return
        val repository = AttentionRepository(AttentionDB.getDB(context))
        val preferencesRepository = PreferencesRepository(getDataStore(context.applicationContext))

        val message = Message(
            otherId = recipient,
            timestamp = System.currentTimeMillis(),
            direction = DIRECTION.Outgoing,
            message = messageStr.toString()
        )
        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                withTimeout(9.seconds) {
                    val token = preferencesRepository.getToken()
                    val fcmToken = preferencesRepository.getValue(
                        stringPreferencesKey(
                            MainViewModel.FCM_TOKEN
                        )
                    )
                    if (token == null) {
                        notifyUser(
                            context,
                            context.getString(R.string.alert_failed_signed_out),
                            message
                        )
                        return@withTimeout
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
                        (context.getSystemService(
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
                                    return@withTimeout
                                }
                                when {
                                    errorBody.contains(
                                        "Could not find user", true
                                    ) -> {
                                        notifyUser(
                                            context,
                                            context.getString(
                                                R.string.alert_failed_no_user, to.name
                                            )
                                        )
                                    }

                                    else -> {
                                        notifyUser(
                                            context,
                                            context.getString(
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
                                    return@withTimeout
                                }
                                when {
                                    errorBody.contains(
                                        "does not have you as a friend", true
                                    ) -> {
                                        notifyUser(
                                            context,
                                            context.getString(
                                                R.string.alert_failed_not_friend, to.name
                                            )
                                        )
                                    }

                                    else -> notifyUser(
                                        context,
                                        context.getString(R.string.alert_failed_signed_out),
                                        message
                                    )
                                }
                            }

                            429 -> {
                                notifyUser(
                                    context,
                                    context.getString(
                                        R.string.alert_rate_limited
                                    ), message
                                )

                            }

                            else -> {
                                notifyUser(
                                    context,
                                    context.getString(
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
                        notifyUser(context, context.getString(R.string.alert_failed), message)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(
                    sTAG,
                    "An error occurred: ${e.message}\n${
                        e.stackTrace.joinToString(
                            separator = "\n"
                        )
                    }"
                )
                notifyUser(context, context.getString(R.string.alert_failed), message)
            } finally {
                pendingResult.finish()
            }

            // 1. get recipient
            // 2. get the message with getResultsFromIntent
            // 3. show cancel progress?
            // 4. call repository.sendMessage
            // 5. dismiss the notification
        }
    }

    companion object {
        const val ACTION_REPLY = "com.aracroproducts.attention.broadcast.REPLY"
        const val EXTRA_SENDER = "com.aracroproducts.attention.extra.RECIPIENT"
        const val EXTRA_NOTIFICATION_ID = "com.aracroproducts.attention.extra.NOTIFICATION_ID"
        const val EXTRA_ALERT_ID = "com.aracroproducts.attention.extra.ALERT_ID"
        const val KEY_TEXT_REPLY = "key_text_reply"
        val sTAG = SendMessageReceiver::class.qualifiedName ?: ""
    }
}