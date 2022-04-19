package com.aracroproducts.attentionv2

import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

// Declares the DAO as a private property in the constructor. Pass in the DAO
// instead of the whole database, because you only need access to the DAO
class AttentionRepository(private val database: AttentionDB) {

    // Room executes all queries on a separate thread.
    // Observed Flow will notify the observer when the data has changed.
    fun getFriends() = database.getFriendDAO().getFriends()

    suspend fun getFriendsSnapshot() = database.getFriendDAO().getFriendsSnapshot()

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    fun insert(vararg friend: Friend) {
        MainScope().launch {
            database.getFriendDAO().insert(*friend)
        }
    }

    fun clearTables() {
        database.clearAllTables()
    }


    fun delete(
        friend: Friend, token: String, singleton: NetworkSingleton,
        responseListener: Response.Listener<JSONObject>? = null, errorListener: Response
        .ErrorListener? = null
    ) {
        val url = "$BASE_URL/delete_friend/"
        val params = JSONObject(
            mapOf(
                "friend" to friend.id
            )
        )
        val request = AuthorizedJsonObjectRequest(Request.Method.POST, url,
            params,
            {
                MainScope().launch {
                    database.getFriendDAO().delete(friend)
                }
                responseListener?.onResponse(it)
            }, { error ->
                printNetworkError(error, url)
                errorListener?.onErrorResponse(error)
            }, token
        )
        singleton.addToRequestQueue(request)
    }

    fun edit(
        friend: Friend, token: String, singleton: NetworkSingleton, responseListener:
        Response.Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null
    ) {
        val url = "$BASE_URL/edit_friend_name/"
        val params = JSONObject(
            mapOf(
                "username" to friend.id,
                "new_name" to friend.name
            )
        )

        val request = AuthorizedJsonObjectRequest(Request.Method.PUT,
            url, params, {
                MainScope().launch {
                    database.getFriendDAO().updateFriend(friend)
                }
                responseListener?.onResponse(it)
            }, errorListener = {
                printNetworkError(it, url)
                errorListener?.onErrorResponse(it)
            }, token
        )
        singleton.addToRequestQueue(request)
    }

    fun getFriend(id: String): Friend = database.getFriendDAO().getFriend(id)

    fun cacheFriend(username: String) = database.getCachedFriendDAO().insert(CachedFriend(username))

    fun getCachedFriends(): Flow<List<CachedFriend>> = database.getCachedFriendDAO()
        .getCachedFriends()

    suspend fun getCachedFriendsSnapshot(): List<CachedFriend> =
        database.getCachedFriendDAO().getCachedFriendsSnapshot()

    fun deleteCachedFriend(username: String) = database.getCachedFriendDAO().delete(
        CachedFriend
            (username)
    )

    fun getMessages(friend: Friend): Flow<List<Message>> = database.getMessageDAO()
        .getMessagesFromUser(friend.id)

    fun appendMessage(message: Message, save: Boolean = false) {
        if (save) {
            val mMessage = Message(
                timestamp = Calendar.getInstance().timeInMillis, direction =
                message.direction, otherId = message.otherId, message = message.message
            )
            database.getMessageDAO().insertMessage(mMessage)
        }
        MainScope().launch {
            when (message.direction) {
                DIRECTION.Incoming -> database.getFriendDAO().incrementReceived(message.otherId)
                DIRECTION.Outgoing -> database.getFriendDAO().incrementSent(message.otherId)
            }
        }
    }

    fun getName(
        token: String, username: String, singleton: NetworkSingleton, responseListener:
        Response.Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null
    ):
            Request<JSONObject> {
        val url = "$BASE_URL/get_name/?username=$username"

        val request = AuthorizedJsonObjectRequest(
            Request.Method.GET, url,
            null, responseListener, errorListener = {
                printNetworkError(it, url)
                errorListener?.onErrorResponse(it)
            }, token
        )

        singleton.addToRequestQueue(request)

        return request
    }

    fun sendMessage(
        message: Message, token: String, singleton: NetworkSingleton,
        responseListener: Response.Listener<JSONObject>? = null, errorListener: Response
        .ErrorListener? = null
    ) {
        assert(message.direction == DIRECTION.Outgoing)
        appendMessage(message)
        val url = "$BASE_URL/send_alert/"
        val params = JSONObject(
            mapOf(
                "to" to message.otherId,
                "message" to message.message
            )
        )
        val request = AuthorizedJsonObjectRequest(Request.Method.POST, url,
            params,
            {
                val alertId = it.getString("id")
                MainScope().launch {
                    database.getFriendDAO().setMessageAlert(alertId, message.otherId)
                    database.getFriendDAO().setMessageRead(
                        false, alert_id = alertId, id =
                        message.otherId
                    )
                }
                responseListener?.onResponse(it)
            }, { error ->
                printNetworkError(error, url)
                errorListener?.onErrorResponse(
                    VolleyError("Couldn't send alert: ${error.message}")
                )
            }, token
        )
        singleton.addToRequestQueue(request)

    }

    fun registerDevice(
        token: String, fcmToken: String, singleton: NetworkSingleton,
        responseListener: Response.Listener<JSONObject>? = null, errorListener:
        Response.ErrorListener? = null
    ) {
        val url = "$BASE_URL/register_device/"
        val params = JSONObject(
            mapOf(
                "fcm_token" to fcmToken
            )
        )
        val request = AuthorizedJsonObjectRequest(
            Request.Method.POST,
            url, params, responseListener, errorListener = {
                printNetworkError(it, url)
                errorListener?.onErrorResponse(it)
            }, token
        )
        singleton.addToRequestQueue(request)
    }

    fun editUser(
        token: String, singleton: NetworkSingleton, firstName: String? = null, lastName:
        String? = null, password: String? = null, oldPassword: String? = null,
        email: String? = null, responseListener: Response.Listener<JSONObject>? = null,
        errorListener: Response.ErrorListener? = null
    ) {
        val url = "$BASE_URL/edit/"
        val params = JSONObject(buildMap {
            if (firstName != null) put("first_name", firstName)
            if (lastName != null) put("last_name", lastName)
            if (password != null) put("password", password)
            if (oldPassword != null) put("old_password", oldPassword)
            if (email != null) put("email", email)
        })
        val request = AuthorizedJsonObjectRequest(
            Request.Method.PUT, url,
            params, responseListener, errorListener = {
                printNetworkError(it, url)
                errorListener?.onErrorResponse(it)
            }, token
        )
        singleton.addToRequestQueue(request)
    }

    fun getUserInfo(
        token: String, singleton: NetworkSingleton, responseListener: Response
        .Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null
    ) {
        val url = "$BASE_URL/get_info/"
        val request = AuthorizedJsonObjectRequest(
            Request.Method.GET, url,
            null, responseListener, errorListener = {
                printNetworkError(it, url)
                errorListener?.onErrorResponse(it)
            }, token
        )
        singleton.addToRequestQueue(request)
    }

    fun updateUserInfo(friends: JSONArray?) {
        if (friends == null) return
        val keepIDs: Array<String> = Array(friends.length()) { "" }
        for (i in 0 until friends.length()) {
            val jsonFriend = friends.getJSONObject(i)
            val id = jsonFriend.getString("friend")
            keepIDs[i] = id
            val friend = Friend(
                id, jsonFriend.getString("name"),
                jsonFriend.getInt("sent"),
                jsonFriend.getInt("received"), jsonFriend.getString("last_message_id_sent"),
                jsonFriend.getBoolean("last_message_read")
            )
            MainScope().launch {
                database.getFriendDAO().insert(friend)
            }
        }
        MainScope().launch {
            database.getFriendDAO().keepOnly(*keepIDs)
        }
    }

    fun addFriend(
        username: String, name: String, token: String, singleton: NetworkSingleton,
        responseListener:
        Response.Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null
    ) {
        val url = "$BASE_URL/add_friend/"
        val params = JSONObject(
            mapOf(
                "username" to username
            )
        )
        val request = AuthorizedJsonObjectRequest(Request.Method.POST, url,
            params, {
                insert(Friend(username, name))
                responseListener?.onResponse(it)
            }, errorListener = {
                printNetworkError(it, url)
                errorListener?.onErrorResponse(it)
            }, token
        )
        singleton.addToRequestQueue(request)
    }

    fun alertRead(username: String?, alertId: String?) {
        MainScope().launch {
            database.getFriendDAO()
                .setMessageRead(true, alert_id = alertId, id = username)
        }
    }

    fun registerUser(
        username: String, password: String, firstName: String, lastName: String,
        email: String, singleton: NetworkSingleton, responseListener: Response
        .Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null
    ) {
        val url = "$BASE_URL/register_user/"
        val params = JSONObject(buildMap {
            put("username", username)
            put("password", password)
            put("first_name", firstName)
            put("last_name", lastName)
            if (email.isNotBlank()) {
                put("email", email)
            }
        }
        )
        val request = JsonObjectRequest(
            Request.Method.POST, url, params,
            responseListener
        ) {
            printNetworkError(it, url)
            errorListener?.onErrorResponse(it)
        }
        singleton.addToRequestQueue(request)
    }

    fun getAuthToken(
        username: String, password: String, singleton: NetworkSingleton,
        responseListener: Response.Listener<JSONObject>? = null, errorListener:
        Response
        .ErrorListener? = null
    ) {
        val url = "$BASE_URL/api_token_auth/"
        val params = JSONObject(
            mapOf(
                "username" to username,
                "password" to password
            )
        )
        val request = JsonObjectRequest(
            Request.Method.POST, url, params,
            responseListener
        ) {
            printNetworkError(it, url)
            errorListener?.onErrorResponse(it)
        }
        singleton.addToRequestQueue(request)
    }

    private fun printNetworkError(error: VolleyError, url: String) {
        Log.e(javaClass.name, error.stackTraceToString())
        Log.e(javaClass.name, "Response from $url")
        Log.e(
            javaClass.name, "Status ${error.networkResponse.statusCode} Data: ${
                String(
                    error
                        .networkResponse.data
                )
            } in ${
                error
                    .networkTimeMs
            } ms"
        )
        Log.e(javaClass.name, "Headers: ${error.networkResponse.allHeaders}")
    }
}