package com.aracroproducts.attentionv2

import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*
import kotlin.concurrent.thread

// Declares the DAO as a private property in the constructor. Pass in the DAO
// instead of the whole database, because you only need access to the DAO
class AttentionRepository(private val database: AttentionDB) {

    private val apiInterface = APIClient.getClient().create(APIV2::class.java)

    // Room executes all queries on a separate thread.
    // Observed Flow will notify the observer when the data has changed.
    fun getFriends() = database.getFriendDAO().getFriends()

    suspend fun getFriendsSnapshot() = database.getFriendDAO().getFriendsSnapshot()

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    suspend fun insert(vararg friend: Friend) {
        database.getFriendDAO().insert(*friend)
    }

    fun clearTables() {
        thread {
            database.clearAllTables()
        }
    }


    fun delete(
            friend: Friend,
            token: String,
            responseListener: ((Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.deleteFriend(friend.id, authHeader(token))
        call.enqueue(object : Callback<GenericResult<Void>> {
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                else {
                    MainScope().launch {
                        database.getFriendDAO().delete(friend)
                    }
                }
                responseListener?.invoke(call, response, responseErrorBody)
            }
        })
    }

    fun edit(
            friend: Friend,
            token: String,
            responseListener: ((Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.editFriendName(friend.id, friend.name, authHeader(token))
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                else {
                    MainScope().launch {
                        database.getFriendDAO().updateFriend(friend)
                    }
                }
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }
        })
    }

    suspend fun getFriend(id: String): Friend = database.getFriendDAO().getFriend(id)

    suspend fun cacheFriend(username: String) {

            database.getCachedFriendDAO().insert(
                    CachedFriend(username)
            )
    }

    fun getCachedFriends() = database.getCachedFriendDAO().getCachedFriends()

    suspend fun getCachedFriendsSnapshot(): List<CachedFriend> =
            database.getCachedFriendDAO().getCachedFriendsSnapshot()

    suspend fun deleteCachedFriend(username: String) {

            database.getCachedFriendDAO().delete(CachedFriend(username))

    }

    fun getMessages(friend: Friend) = database.getMessageDAO().getMessagesFromUser(friend.id)

    suspend fun appendMessage(message: Message, save: Boolean = false) {
        if (save) {
            val mMessage = Message(
                    timestamp = Calendar.getInstance().timeInMillis,
                    direction = message.direction,
                    otherId = message.otherId,
                    message = message.message
            )

                database.getMessageDAO().insertMessage(mMessage)
        }

            when (message.direction) {
                DIRECTION.Incoming -> database.getFriendDAO().incrementReceived(message.otherId)
                DIRECTION.Outgoing -> database.getFriendDAO().incrementSent(message.otherId)
            }

    }

    fun getName(
            token: String,
            username: String,
            responseListener: ((
                    Call<GenericResult<NameResult>>, Response<GenericResult<NameResult>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<NameResult>>, Throwable) -> Unit)? = null
    ): Call<GenericResult<NameResult>> {
        val call = apiInterface.getName(username, authHeader(token))
        call.enqueue(object : Callback<GenericResult<NameResult>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<NameResult>>,
                    response: Response<GenericResult<NameResult>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<NameResult>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }
        })
        return call
    }

    suspend fun sendMessage(
            message: Message,
            token: String,
            responseListener: ((
                    Call<GenericResult<AlertResult>>, Response<GenericResult<AlertResult>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<AlertResult>>, Throwable) -> Unit)? = null
    ) {
        assert(message.direction == DIRECTION.Outgoing)
        appendMessage(message)
        val call = apiInterface.sendAlert(message.otherId, message.message, authHeader(token))
        call.enqueue(object : Callback<GenericResult<AlertResult>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<AlertResult>>,
                    response: Response<GenericResult<AlertResult>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                else {
                    val alertId = response.body()?.data?.id
                    MainScope().launch {
                        database.getFriendDAO().setMessageAlert(
                                alertId, message.otherId
                        )
                        alertId?.let {
                            database.getFriendDAO().setMessageStatus(
                                    MessageStatus.SENT, alert_id = alertId, id = message.otherId
                            )
                        }
                        database.getFriendDAO().incrementSent(message.otherId)
                    }
                }
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<AlertResult>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }
        })
    }

    fun registerDevice(
            token: String, fcmToken: String, responseListener: ((
                    Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.registerDevice(fcmToken, authHeader(token))
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    fun unregisterDevice(
            token: String, fcmToken: String, responseListener: ((
                    Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.unregisterDevice(fcmToken, authHeader(token))
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    fun editUser(
            token: String,
            username: String? = null,
            firstName: String? = null,
            lastName: String? = null,
            password: String? = null,
            oldPassword: String? = null,
            email: String? = null,
            responseListener: ((
                    Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.editUser(
                username = username, firstName = firstName, lastName = lastName, email = email,
                password = password, oldPassword = oldPassword, token = authHeader(token)
        )
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    fun downloadUserInfo(
            token: String,
            responseListener: ((
                    Call<GenericResult<UserDataResult>>, Response<GenericResult<UserDataResult>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<UserDataResult>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.getUserInfo(authHeader(token))
        call.enqueue(object : Callback<GenericResult<UserDataResult>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<UserDataResult>>,
                    response: Response<GenericResult<UserDataResult>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<UserDataResult>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }
        })
    }

    suspend fun updateUserInfo(friends: List<Friend>) {
            // TODO investigate whether these return the number of rows updated
            // if yes, return a boolean indicating that profile pictures should be downloaded?
            database.getFriendDAO().insert(*friends.toTypedArray())
            val keepIDs: Array<String> = Array(friends.size) { index ->
                friends[index].id
            }
            database.getFriendDAO().keepOnly(*keepIDs)

    }

    fun addFriend(
            username: String, name: String, token: String, responseListener: ((
                    Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.addFriend(username, authHeader(token))
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                else {
                    MainScope().launch {
                        insert(Friend(username, name))
                    }
                }
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }
        })
    }

    suspend fun alertDelivered(username: String?, alertId: String?) {

            database.getFriendDAO()
                    .setMessageStatus(MessageStatus.DELIVERED, alert_id = alertId, id = username)

    }

    suspend fun alertRead(username: String?, alertId: String?) {

            database.getFriendDAO()
                    .setMessageStatus(MessageStatus.READ, alert_id = alertId, id = username)

    }

    fun sendDeliveredReceipt(
            alertId: String, from: String, authToken: String, responseListener: ((
                    Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.alertDelivered(alertId, from, authHeader(authToken))
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    fun sendReadReceipt(
            alertId: String, from: String, fcmToken: String, authToken: String, responseListener: ((
                    Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.alertRead(alertId, from, fcmToken, authHeader(authToken))
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    fun registerUser(
            username: String,
            password: String,
            firstName: String,
            lastName: String,
            email: String,
            responseListener: ((
                    Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?
            ) -> Unit)? = null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.registerUser(firstName, lastName, username, password, email)
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(
                    call: Call<GenericResult<Void>>, response: Response<GenericResult<Void>>
            ) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    fun signInWithGoogle(
            userIdToken: String,
            username: String? = null,
            responseListener: ((Call<TokenResult>, Response<TokenResult>, String?) -> Unit)? = null,
            errorListener: ((Call<TokenResult>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.googleSignIn(userIdToken, username)
        call.enqueue(object : Callback<TokenResult> {
            override fun onResponse(call: Call<TokenResult>, response: Response<TokenResult>) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            override fun onFailure(call: Call<TokenResult>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }
        })
    }

    fun getAuthToken(
            username: String,
            password: String,
            responseListener: ((Call<TokenResult>, Response<TokenResult>, String?) -> Unit)? = null,
            errorListener: ((Call<TokenResult>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.getToken(username, password)
        call.enqueue(object : Callback<TokenResult> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(call: Call<TokenResult>, response: Response<TokenResult>) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected
             * exception occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<TokenResult>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    fun linkGoogleAccount(
            password: String,
            googleToken: String,
            token: String,
            responseListener: ((Call<GenericResult<Void>>, Response<GenericResult<Void>>, String?)
            ->
            Unit)? =
                    null,
            errorListener: ((Call<GenericResult<Void>>, Throwable) -> Unit)? = null
    ) {
        val call = apiInterface.linkAccount(password, googleToken, authHeader(token))
        call.enqueue(object : Callback<GenericResult<Void>> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(call: Call<GenericResult<Void>>,
                                    response: Response<GenericResult<Void>>) {
                val responseErrorBody = response.errorBody()?.string()
                if (!response.isSuccessful) printNetworkError(response, call, responseErrorBody)
                responseListener?.invoke(call, response, responseErrorBody)
            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected exception
             * occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<GenericResult<Void>>, t: Throwable) {
                Log.e(javaClass.name, t.stackTraceToString())
                errorListener?.invoke(call, t)
            }

        })
    }

    private fun printNetworkError(error: Response<*>, request: Call<*>, errorBody: String?) {
        Log.e(javaClass.name, "Response from ${request.request().url}")
        Log.e(
                javaClass.name, "Status: ${error.code()} - Data: ${
            errorBody ?: "null"
        }"
        )
        Log.e(javaClass.name, "Headers: ${error.headers()}")
    }

    private fun authHeader(token: String): String {
        return "Token $token"
    }
}