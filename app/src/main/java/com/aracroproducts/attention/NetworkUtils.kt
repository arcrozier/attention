package com.aracroproducts.attention

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.gson.annotations.SerializedName
import retrofit2.converter.gson.GsonConverterFactory

import retrofit2.Retrofit

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

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

    val requestQueue: RequestQueue by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }

    fun <T> addToRequestQueue(req: Request<T>) {
        requestQueue.add(req)
    }
}