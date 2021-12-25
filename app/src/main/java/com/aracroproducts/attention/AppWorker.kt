package com.aracroproducts.attention

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Outer class that holds the different workers
 */
class AppWorker {

    /**
     * Used to store the different return types from the connect function
     */
    enum class ErrorType(val code: Int) {
        OK(CODE_SUCCESS),  // Request was successful
        BAD_REQUEST(CODE_BAD_REQUEST),  // Something was wrong with the request (don't retry)
        SERVER_ERROR(CODE_SERVER_ERROR),  // The server isn't working (don't retry)
        CONNECTION_FAILED(
                CODE_CONNECTION_FAILED)  // There was an issue with the connection (should retry)
    }

    private val sTAG = javaClass.name

    /**
     * A Worker that sends the user's token and id to the server
     *
     * @param appContext    - The application context of the worker
     * @param workerParameters  - Additional data for the worker; In inputData, there should be
     * TOKEN and ID
     */
    inner class TokenWorker(appContext: Context, private val workerParameters: WorkerParameters) :
            Worker(appContext, workerParameters) {
        override fun doWork(): Result {
            val input = workerParameters.inputData
            val result = Data.Builder()
            val token = input.getString(TOKEN)
            val id = input.getString(ID)
            val code = connect(mapOf(TOKEN to token, ID to id), PARAM_FUNCTION_ID)
            result.putInt(RESULT_CODE, code.code)
            val built = result.build()
            when (code) {
                ErrorType.OK -> {
                    Log.i(sTAG, "Token sent")
                    return Result.success(built)
                }
                ErrorType.CONNECTION_FAILED -> {
                    Log.w(sTAG, "Failed to upload id: connection failed; retrying")
                    return Result.retry()
                }
                ErrorType.BAD_REQUEST -> {
                    Log.e(sTAG, "Unable to send id: bad request; not retrying")
                    return Result.failure(built)
                }
                ErrorType.SERVER_ERROR -> {
                    Log.e(sTAG, "Unable to send id: server error; not retrying")
                    return Result.failure(built)
                }
            }
        }
    }

    /**
     * A Worker that sends sends alerts
     *
     * @param appContext    - The application context of the worker
     * @param workerParameters  - Additional data for the worker; In inputData, there should be TO,
     * FROM, and, optionally, MESSAGE. Other parameters are ignored
     */
    inner class MessageWorker(appContext: Context, private val workerParameters: WorkerParameters) :
            Worker(appContext, workerParameters) {

        val model: MainViewModel by viewModels()
        override fun doWork(): Result {
            val input = workerParameters.inputData
            val result = Data.Builder()

            val repository = AttentionRepository(AttentionDB.getDB(applicationContext))
            repository.sendMessage()
            val to = input.getString(TO)
            val from = input.getString(FROM)
            val message = input.getString(MESSAGE)
            val code =
                    connect(mapOf(TO to to, FROM to from, MESSAGE to message), PARAM_FUNCTION_ALERT)
            result.putInt(RESULT_CODE, code.code)
            val built = result.build()
            when (code) {
                ErrorType.OK -> {
                    Log.i(sTAG, "Message sent")
                    return Result.success(built)
                }
                ErrorType.CONNECTION_FAILED -> {
                    Log.w(sTAG, "Failed to send message: connection failed; retrying")
                    return Result.retry()
                }
                ErrorType.BAD_REQUEST -> {
                    Log.e(sTAG, "Unable to send message: bad request; not retrying")
                    return Result.failure(built)
                }
                ErrorType.SERVER_ERROR -> {
                    Log.e(sTAG, "Unable to send message: server error; not retrying")
                    return Result.failure(built)
                }
            }
        }
    }

    /**
     * Sends data via http
     * @param params    - Additional parameters for the server
     * @param function  - Which function it is calling (one of 'post_id' or 'send_alert')
     * @return          - A boolean representing whether the request was successful
     */
    private fun connect(params: Map<String, String?>, function: String): ErrorType {
        val c: HttpURLConnection
        val getParams = parseGetParams(params)
        try {
            val url = URL(BASE_URL + function + getParams)
            c = url.openConnection() as HttpURLConnection
            c.requestMethod = "GET"

            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("charset", "utf-8")
            c.setRequestProperty("User-Agent", "Attention! client app for Android")
            c.setRequestProperty("Cache-control",
                    "no-cache") // tell server not to send cached response
            c.useCaches = false // do it twice just to be sure
            c.doOutput = true
            val os = DataOutputStream(
                    c.outputStream) // "out" is considered what the app is sending to the server
            os.write(getParams.toByteArray(StandardCharsets.UTF_8))
            os.flush()
            os.close()
            val code = c.responseCode
            val br = BufferedReader(InputStreamReader(c.inputStream,
                    StandardCharsets.UTF_8)) // likewise, "in" is what is coming from the server
            val sb = StringBuilder()
            var line: String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
            }
            br.close()
            Log.d(sTAG, "$url $code $sb")
            return when (code) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> ErrorType.OK
                HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_BAD_METHOD, HttpURLConnection.HTTP_ENTITY_TOO_LARGE, HttpURLConnection.HTTP_REQ_TOO_LONG -> ErrorType.BAD_REQUEST
                HttpURLConnection.HTTP_INTERNAL_ERROR, HttpURLConnection.HTTP_BAD_GATEWAY, HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_UNAUTHORIZED -> ErrorType.SERVER_ERROR
                HttpURLConnection.HTTP_CLIENT_TIMEOUT, HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> ErrorType.CONNECTION_FAILED
                else -> ErrorType.BAD_REQUEST
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return ErrorType.CONNECTION_FAILED
        }
    }

    /**
     * Converts key-value pairs for HTTP parameters into www/x-url-encoded format (not for HTTP
     * headers)
     *
     * @param params    - HTTP parameters to turn into a string
     * @return  - A string, with each key value pair separated with an = (key=value) and joined by
     * & (key1=value1&key2=value2&...). Not prepended with a ?
     */
    private fun parseGetParams(params: Map<String, String?>): String {
        val builder = StringBuilder()
        for (entry in params.entries) {
            builder.append("${entry.key}=${entry.value}")
        }
        return builder.toString()
    }

    companion object {
        const val TOKEN = "token"
        const val ID = "id"
        const val TO = "to"
        const val FROM = "from"
        const val MESSAGE = "message"
        const val RESULT_CODE = "com.aracroproducts.attention.data.result_code"
        const val CODE_SUCCESS = 0
        const val CODE_SERVER_ERROR = 1
        const val CODE_BAD_REQUEST = 2
        const val CODE_CONNECTION_FAILED = -1
        const val BASE_URL = "https://aracroproducts.com/attention/api/api.php?function="
        const val PARAM_FUNCTION_ID = "post_id"
        const val PARAM_FUNCTION_ALERT = "send_alert"
        const val FAILED_ALERT_CHANNEL_ID = "Failed alert channel"
    }
}
