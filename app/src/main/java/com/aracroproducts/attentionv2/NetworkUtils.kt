package com.aracroproducts.attentionv2

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.HttpClientStack
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

const val BASE_URL = "https://attention.aracroproducts.com/api/v2"


class NetworkSingleton constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: NetworkSingleton? = null
        fun getInstance(context: Context) =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: NetworkSingleton(context).also {
                        INSTANCE = it
                    }
                }
    }

    private val requestQueue: RequestQueue by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }

    fun <T> addToRequestQueue(req: Request<T>) {
        requestQueue.add(req)
    }
}

class NameResult

class TokenResult

class RegisterDeviceResult

class RegisterUserResult

class AuthorizedJsonObjectRequest(method: Int,
                           URL: String,
                           params: JSONObject? = null,
                           responseListener: Response.Listener<JSONObject>? = null,
                           errorListener: Response.ErrorListener? = null,
                           private val token: String
) : JsonObjectRequest(method, URL, params, responseListener, errorListener) {

    override fun getHeaders(): MutableMap<String, String> {
        return mutableMapOf(
                "AUTHORIZATION" to "Token $token",
                "Content-Type" to "application/json; charset=UTF-8"
        )
    }
}