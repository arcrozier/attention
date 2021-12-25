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

const val BASE_URL = "https://aracroproducts.com/attention/api/api.php"
const val PARAM_FUNCTION_ID = "post_id"
const val PARAM_FUNCTION_ALERT = "send_alert"

data class APIResponse (
        @SerializedName("success")
    val success: Boolean,
        @SerializedName("data")
    val data: String,
        @SerializedName("code")
    val code: Int
    )

val retrofit: Retrofit = run {
    val interceptor = HttpLoggingInterceptor()
    interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
}


interface APIInterface {
    @FormUrlEncoded
    @POST("?function=$PARAM_FUNCTION_ID")
    suspend fun sendId(@Field("token") token: String, @Field("id")
    id: String): APIResponse

    @FormUrlEncoded
    @POST("?function=$PARAM_FUNCTION_ALERT")
    suspend fun sendAlert(@Field("to") to: String, @Field("from") from: String, @Field("message")
    message: String?): APIResponse
}

object AttentionAPI {
    val retrofitService : APIInterface by lazy {
        retrofit.create(APIInterface::class.java)
    }
}

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