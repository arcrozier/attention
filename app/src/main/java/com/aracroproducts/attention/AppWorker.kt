package com.aracroproducts.attention

import android.content.Context
import android.util.Log
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

class AppWorker(appContext: Context, private val workerParameters: WorkerParameters): Worker(appContext, workerParameters) {

    private enum class ErrorType(val code: Int) {
        OK(CODE_SUCCESS),  // Request was successful
        BAD_REQUEST(CODE_BAD_REQUEST),  // Something was wrong with the request (don't retry)
        SERVER_ERROR(CODE_SERVER_ERROR),  // The server isn't working (don't retry)
        CONNECTION_FAILED(CODE_CONNECTION_FAILED)  // There was an issue with the connection (should retry)
    }

    private val sTAG = javaClass.name

    override fun doWork(): Result {
        val input = workerParameters.inputData
        val result = Data.Builder()

        when(input.getString(FUNCTION_KEY)) {
            ACTION_POST_TOKEN -> {
                val token = input.getString(TOKEN)
                val id = input.getString(ID)
                val code = connect(arrayOf(arrayOf("token", token), arrayOf("id", id)), PARAM_FUNCTION_ID)
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
            ACTION_SEND_ALERT -> {
                val to = input.getString(TO)
                val from = input.getString(FROM)
                val message = input.getString(MESSAGE)
                val code = connect(arrayOf(arrayOf("to", to), arrayOf("from", from), arrayOf("message", message)), PARAM_FUNCTION_ALERT)
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
            else -> throw IllegalArgumentException("An invalid argument was passed to the worker: ${input.getString(FUNCTION_KEY)}")
        }
    }

    /**
     * Sends data via http
     * @param params    - Additional parameters for the server
     * @param function  - Which function it is calling (one of 'post_id' or 'send_alert')
     * @return          - A boolean representing whether the request was successful
     */
    private fun connect(params: Array<Array<String?>>, function: String): ErrorType {
        val c: HttpURLConnection
        val getParams = parseGetParams(params)
        try {
            val url = URL(BASE_URL + function + getParams)
            c = url.openConnection() as HttpURLConnection
            c.requestMethod = "GET"

            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("charset", "utf-8")
            c.setRequestProperty("User-Agent", "Attention! client app for Android")
            c.setRequestProperty("Cache-control", "no-cache") // tell server not to send cached response
            c.useCaches = false // do it twice just to be sure
            c.doOutput = true
            val os = DataOutputStream(c.outputStream) // "out" is considered what the app is sending to the server
            os.write(getParams.toByteArray(StandardCharsets.UTF_8))
            os.flush()
            os.close()
            val code = c.responseCode
            val br = BufferedReader(InputStreamReader(c.inputStream, StandardCharsets.UTF_8)) // likewise, "in" is what is coming from the server
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

    private fun parseGetParams(params: Array<Array<String?>>): String {
        val builder = StringBuilder()
        for (i in params.indices) {
            builder.append("${params[i][0]}=${params[i][1]}")
        }
        return builder.toString()
    }

    companion object {
        const val FUNCTION_KEY = "function"
        const val TOKEN = "com.aracroproducts.attention.extra.token"
        const val ID = "com.aracroproducts.attention.extra.id"
        const val TO = "com.aracroproducts.attention.extra.to"
        const val FROM = "com.aracroproducts.attention.extra.from"
        const val MESSAGE = "com.aracroproducts.attention.extra.message"
        const val ACTION_POST_TOKEN = "com.aracroproducts.attention.action.token"
        const val ACTION_SEND_ALERT = "com.aracroproducts.attention.action.send"
        const val RESULT_CODE = "com.aracroproducts.attention.data.result_code"
        const val CODE_SUCCESS = 0
        const val CODE_SERVER_ERROR = 1
        const val CODE_BAD_REQUEST = 2
        const val CODE_CONNECTION_FAILED = -1
        const val BASE_URL = "https://aracroproducts.com/attention/api/api.php?function="
        const val PARAM_FUNCTION_ID = "post_id"
        const val PARAM_FUNCTION_ALERT = "send_alert"
    }
}
