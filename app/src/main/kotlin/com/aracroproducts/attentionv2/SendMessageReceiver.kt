package com.aracroproducts.attentionv2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.RemoteInput
import com.aracroproducts.attentionv2.AlertViewModel.Companion.NO_ID

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
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        if (intent.action != ACTION_REPLY) return

        val recipient = intent.getStringExtra(EXTRA_SENDER) ?: return
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_ID)
        val messageStr =
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT_REPLY) ?: return
        val serviceIntent = Intent(context, AlertSendService::class.java).apply {
            putExtra(EXTRA_SENDER, recipient)
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(KEY_TEXT_REPLY, messageStr)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_REPLY = "com.aracroproducts.attention.broadcast.REPLY"
        const val EXTRA_SENDER = "com.aracroproducts.attention.extra.RECIPIENT"
        const val EXTRA_NOTIFICATION_ID = "com.aracroproducts.attention.extra.NOTIFICATION_ID"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }
}