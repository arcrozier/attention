package com.aracroproducts.attentionv2

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.*

const val BASE_URL = "https://attention.aracroproducts.com/api/v2"


class NameResult(@SerializedName("name") val name: String)

class TokenResult(@SerializedName("token") val token: String)

class GenericResult<T>(@SerializedName("success") val success: Boolean,
                    @SerializedName("message") val message: String, @SerializedName("data") val
                       data: T)

class AlertResult(@SerializedName("id") val id: String)

class UserDataResult(@SerializedName("username") val username: String,
                     @SerializedName("first_name") val firstName: String,
                     @SerializedName("last_name") val lastName: String,
                     @SerializedName("email") val email: String,
                     @SerializedName("friends") val friends: List<Friend>)

class APIClient {

    companion object {
        private var retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        fun getClient(): Retrofit {
            return retrofit
        }
    }
}

interface APIV2 {
    @POST("api_token_auth/")
    fun getToken(@Field("username") username: String, @Field("password") password: String):
            Call<TokenResult>

    @POST("send_alert/")
    fun sendAlert(@Field("to") to: String, @Field("message") message: String?, @Header
    ("Authorization") token: String): Call<GenericResult<AlertResult>>

    @POST("register_device/")
    fun registerDevice(@Field("fcm_token") fcmToken: String, @Header("Authorization") token:
    String): Call<GenericResult<Void>>

    @POST("register_user/")
    fun registerUser(@Field("first_name") firstName: String, @Field("last_name") lastName:
    String, @Field("username") username: String, @Field("password") password: String, @Field
                     ("email") email: String?): Call<GenericResult<Void>>

    @POST("add_friend/")
    fun addFriend(@Field("username") username: String, @Header("Authorization") token: String):
            Call<GenericResult<Void>>

    @PUT("edit_friend_name/")
    fun editFriendName(@Field("username") username: String, @Field("new_name") newName: String,
                       @Header("Authorization") token: String): Call<GenericResult<Void>>

    @GET("get_name/")
    fun getName(@Query("username") username: String, @Header("Authorization") token: String):
            Call<GenericResult<NameResult>>

    @DELETE("delete_friend/")
    fun deleteFriend(@Field("friend") friend: String, @Header("Authorization") token: String):
            Call<GenericResult<Void>>

    @PUT("edit/")
    fun editUser(@Field("first_name") firstName: String?, @Field("last_name") lastName: String?,
                 @Field("email") email: String?, @Field("password") password: String?, @Field
                 ("old_password") oldPassword: String?, @Header("Authorization") token: String):
            Call<GenericResult<Void>>

    @GET("get_info/")
    fun getUserInfo(@Header("Authorization") token: String): Call<GenericResult<UserDataResult>>

    @POST("alert_read/")
    fun alertRead(@Field("alert_id") alertId: String, @Field("from") from: String, @Field
    ("fcm_token") fcmToken: String, @Header("Authorization") token: String):
            Call<GenericResult<Void>>


}