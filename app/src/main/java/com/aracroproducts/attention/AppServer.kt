package com.aracroproducts.attention

import android.content.Context
import androidx.core.app.JobIntentService
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

// TODO
open class AppServer : JobIntentService() {
    private val sTAG = javaClass.name
    override fun onHandleWork(intent: Intent) {
        Log.d(sTAG, "Handling work")
        if (intent.action != null) {
            Log.d(sTAG, intent.toString() + " Action: " + intent.action)
            val action = intent.action
            val manager = LocalBroadcastManager.getInstance(this)

            //PendingIntent reply = intent.getParcelableExtra(EXTRA_PENDING_RESULT);
            val result = Intent()
            when (action) {
                ACTION_POST_TOKEN -> {
                    val token = intent.getStringExtra(EXTRA_TOKEN)
                    val id = intent.getStringExtra(EXTRA_ID)
                    result.action = ACTION_POST_TOKEN
                    val success = connect(true, arrayOf(arrayOf("token", token), arrayOf("id", id)), PARAM_FUNCTION_ID)
                    if (success) {
                        Log.d(sTAG, "Message sent")
                        result.putExtra(EXTRA_DATA, "Sent id successfully")
                        result.putExtra(EXTRA_RESULT_CODE, CODE_SUCCESS)
                    } else {
                        Log.e(sTAG, "An error occurred")
                        result.putExtra(EXTRA_DATA, "Error sending ID")
                        result.putExtra(EXTRA_RESULT_CODE, CODE_ERROR)
                    }
                }
                ACTION_SEND_ALERT -> {
                    val to = intent.getStringExtra(EXTRA_TO)
                    val from = intent.getStringExtra(EXTRA_FROM)
                    val message = intent.getStringExtra(EXTRA_MESSAGE)
                    val success = connect(true, arrayOf(arrayOf("to", to), arrayOf("from", from), arrayOf("message", message)), PARAM_FUNCTION_ALERT)
                    result.action = ACTION_SEND_ALERT
                    if (success) {
                        Log.d(sTAG, "Alert sent")
                        result.putExtra(EXTRA_DATA, "Sent alert successfully")
                        result.putExtra(EXTRA_RESULT_CODE, CODE_SUCCESS)
                    } else {
                        Log.e(sTAG, "An error occurred")
                        result.putExtra(EXTRA_DATA, "Error sending alert")
                        result.putExtra(EXTRA_RESULT_CODE, CODE_ERROR)
                    }
                }
            }
            manager.sendBroadcast(result)
        }
    }

    /**
     * Sends data via http
     * @param post      - Whether to use POST or GET
     * @param params    - Additional parameters for the server
     * @param function  - Which function it is calling (one of 'post_id' or 'send_alert')
     * @return          - A boolean representing whether the request was successful
     */
    private fun connect(post: Boolean, params: Array<Array<String?>>, function: String): Boolean {
        val c: HttpURLConnection
        val getParams = parseGetParams(params, !post)
        try {
            val url: URL = if (post) {
                URL(BASE_URL + function)
            } else {
                URL(BASE_URL + function + getParams)
            }
            c = url.openConnection() as HttpURLConnection
            if (post) {
                c.requestMethod = "POST"
            } else {
                c.requestMethod = "GET"
            }
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("charset", "utf-8")
            c.setRequestProperty("User-Agent", "Attention! client app for Android")
            c.setRequestProperty("Cache-control", "no-cache") // tell server not to send cached response
            c.useCaches = false // do it twice just to be sure
            if (post) {
                c.doOutput = true
                val os = DataOutputStream(c.outputStream) // "out" is considered what the app is sending to the server
                os.write(getParams.toByteArray(StandardCharsets.UTF_8))
                os.flush()
                os.close()
            }
            val code = c.responseCode
            val br = BufferedReader(InputStreamReader(c.inputStream, StandardCharsets.UTF_8)) // likewise, "in" is what is coming from the server
            val sb = StringBuilder()
            var line: String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
            }
            br.close()
            Log.d(sTAG, "$url $code $sb")
            return code == HttpURLConnection.HTTP_CREATED || code == HttpURLConnection.HTTP_OK
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private fun parseGetParams(params: Array<Array<String?>>, leadingAmp: Boolean): String {
        val builder = StringBuilder()
        for (i in params.indices) {
            if (i != 0 || leadingAmp) {
                builder.append('&')
            }
            builder.append(params[i][0])
            builder.append('=')
            builder.append(params[i][1])
        }
        return builder.toString()
    }

    companion object {
        const val ACTION_POST_TOKEN = "com.aracroproducts.attention.action.token"
        const val ACTION_SEND_ALERT = "com.aracroproducts.attention.action.send"
        const val EXTRA_TOKEN = "com.aracroproducts.attention.extra.token"
        const val EXTRA_ID = "com.aracroproducts.attention.extra.id"
        const val EXTRA_TO = "com.aracroproducts.attention.extra.to"
        const val EXTRA_FROM = "com.aracroproducts.attention.extra.from"
        const val EXTRA_MESSAGE = "com.aracroproducts.attention.extra.message"
        const val EXTRA_DATA = "com.aracroproducts.attention.extra.data"
        const val EXTRA_RESULT_CODE = "com.aracroproducts.attention.extra.result_code"
        const val CODE_SUCCESS = 0
        const val CODE_ERROR = 1
        const val CODE_NA = -1
        private const val JOB_ID = 0
        private const val PARAM_FUNCTION_ID = "post_id"
        private const val PARAM_FUNCTION_ALERT = "send_alert"
        private const val BASE_URL = "https://aracroproducts.com/attention/api/api.php?function="
        fun enqueueWork(context: Context?, intent: Intent?) {
            enqueueWork(context!!, AppServer::class.java, JOB_ID, intent!!)
        }
    }
}