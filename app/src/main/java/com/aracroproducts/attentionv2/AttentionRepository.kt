package com.aracroproducts.attentionv2

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.security.*
import java.security.spec.KeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

// Declares the DAO as a private property in the constructor. Pass in the DAO
// instead of the whole database, because you only need access to the DAO
class AttentionRepository(private val database: AttentionDB) {

    // Room executes all queries on a separate thread.
    // Observed Flow will notify the observer when the data has changed.
    fun getFriends() = database.getFriendDAO().getFriends()

    suspend fun getFriendsSnapshot() = database.getFriendDAO().getFriendsSnapshot()

    fun getLocalFriendName(username: String) = database.getFriendDAO().getFriendName(username)

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    fun insert(vararg friend: Friend) = database.getFriendDAO().insert(*friend)


    fun delete(friend: Friend, token: String, singleton: NetworkSingleton,
               responseListener: Response.Listener<JSONObject>? = null, errorListener: Response
            .ErrorListener? = null) {
        val params = JSONObject(mapOf(
                "friend" to friend.id
        ))
        val request = AuthorizedJsonObjectRequest(Request.Method.POST, "$BASE_URL/delete_friend/",
                params,
                {
                    database.getFriendDAO().delete(friend)
                    responseListener?.onResponse(it)
                }, { error ->
            errorListener?.onErrorResponse(
                    VolleyError("Couldn't send alert: ${error.message}"))
        }, token)
        singleton.addToRequestQueue(request)
    }

    fun edit(friend: Friend, token: String, singleton: NetworkSingleton, responseListener:
    Response.Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null) {
        val params = JSONObject(mapOf(
                "username" to friend.id,
                "new_name" to friend.name
        ))

        val request = AuthorizedJsonObjectRequest(Request.Method.PUT,
                "$BASE_URL/edit_friend_name", params, {
            database.getFriendDAO().updateFriend(friend)
            responseListener?.onResponse(it)
        }, errorListener, token)
        singleton.addToRequestQueue(request)
    }

    fun getFriend(id: String): Friend = database.getFriendDAO().getFriend(id)

    fun cacheFriend(username: String) = database.getCachedFriendDAO().insert(CachedFriend(username))

    fun getCachedFriends(): Flow<List<CachedFriend>> = database.getCachedFriendDAO()
            .getCachedFriends()

    suspend fun getCachedFriendsSnapshot(): List<CachedFriend> = database.getCachedFriendDAO().getCachedFriendsSnapshot()

    fun deleteCachedFriend(username: String) = database.getCachedFriendDAO().delete(CachedFriend
    (username))

    fun getMessages(friend: Friend): Flow<List<Message>> = database.getMessageDAO()
            .getMessagesFromUser(friend.id)

    fun appendMessage(message: Message, save: Boolean = false) {
        if (save) {
            val mMessage = Message(timestamp = Calendar.getInstance().timeInMillis, direction =
            message.direction, otherId = message.otherId, message = message.message)
            database.getMessageDAO().insertMessage(mMessage)
        }
        when (message.direction) {
            DIRECTION.Incoming -> database.getFriendDAO().incrementReceived(message.otherId)
            DIRECTION.Outgoing -> database.getFriendDAO().incrementSent(message.otherId)
        }
    }

    fun getPublicKey(): PublicKey? {
        val keyStore: KeyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        val privateKeyEntry =
                keyStore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        return privateKeyEntry.certificate.publicKey
    }

    fun getName(token: String, username: String, singleton: NetworkSingleton, responseListener:
    Response.Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null):
            Request<JSONObject> {
        val params = JSONObject(mapOf(
                "username" to username
        ))

        val request = AuthorizedJsonObjectRequest(Request.Method.GET, "$BASE_URL/get_name/",
                params, responseListener, errorListener, token)

        singleton.addToRequestQueue(request)

        return request
    }

    fun sendMessage(message: Message, token: String, singleton: NetworkSingleton,
                    responseListener: Response.Listener<JSONObject>? = null, errorListener: Response
            .ErrorListener? = null) {
        assert(message.direction == DIRECTION.Outgoing)
        appendMessage(message)
        val params = JSONObject(mapOf(
                "to" to message.otherId,
                "message" to message
        ))
        val request = AuthorizedJsonObjectRequest(Request.Method.POST, "$BASE_URL/send_alert/",
                params,
                {
                    val alertId = it.getString("id")
                    database.getFriendDAO().setMessageAlert(alertId, message.otherId)
                    database.getFriendDAO().setMessageRead(false, alert_id = alertId, id =
                    message.otherId)
                    responseListener?.onResponse(it)
                }, { error ->
            errorListener?.onErrorResponse(
                    VolleyError("Couldn't send alert: ${error.message}"))
        }, token)
        singleton.addToRequestQueue(request)

    }

    fun registerDevice(token: String, fcmToken: String, singleton: NetworkSingleton,
                       responseListener: Response.Listener<JSONObject>? = null, errorListener:
                       Response.ErrorListener? = null) {
        val params = JSONObject(mapOf(
                "fcm_token" to fcmToken
        ))
        val request = AuthorizedJsonObjectRequest(Request.Method.POST,
                "$BASE_URL/register_device/", params, responseListener, errorListener, token)
        singleton.addToRequestQueue(request)
    }

    fun editUser(token: String, singleton: NetworkSingleton, firstName: String? = null, lastName:
    String? = null, password: String? = null, oldPassword: String? = null,
                 email: String? = null, responseListener: Response.Listener<JSONObject>? = null,
                 errorListener: Response.ErrorListener? = null) {
        val params = JSONObject(mapOf(
                "first_name" to firstName,
                "last_name" to lastName,
                "password" to password,
                "old_password" to oldPassword,
                "email" to email
        ))
        val request = AuthorizedJsonObjectRequest(Request.Method.PUT, "$BASE_URL/v2/edit/",
                params, responseListener, errorListener, token)
        singleton.addToRequestQueue(request)
    }

    fun getUserInfo(token: String, singleton: NetworkSingleton, responseListener: Response
    .Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null) {
        val request = AuthorizedJsonObjectRequest(Request.Method.GET, "$BASE_URL/v2/get_info/",
                null, responseListener, errorListener, token)
        singleton.addToRequestQueue(request)
    }

    fun updateUserInfo(friends: JSONArray?) {
        if (friends == null) return
        for (i in 0..friends.length()) {
            val jsonFriend = friends.getJSONObject(i)
            val friend = Friend(jsonFriend.getString("friend"), jsonFriend.getString("name"),
                    jsonFriend.getInt("sent"),
                    jsonFriend.getInt("received"), jsonFriend.getString("last_message_id_sent"),
                    jsonFriend.getBoolean("last_message_read"))
            database.getFriendDAO().insert(friend)
        }
    }

    fun addFriend(username: String, name: String, token: String, singleton: NetworkSingleton,
                  responseListener:
    Response.Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null) {
        val params = JSONObject(mapOf(
                "username" to username
        ))
        val request = AuthorizedJsonObjectRequest(Request.Method.POST, "$BASE_URL/add_friend/",
                params, {
                    insert(Friend(username, name))
            responseListener?.onResponse(it)
        }, errorListener, token)
        singleton.addToRequestQueue(request)
    }

    fun alertRead(username: String?, alertId: String?)  = database.getFriendDAO()
            .setMessageRead(true, alert_id = alertId, id = username)

    fun registerUser(username: String, password: String, firstName: String, lastName: String,
                     email: String, singleton: NetworkSingleton, responseListener: Response
            .Listener<JSONObject>? = null, errorListener: Response.ErrorListener? = null) {
        val params = JSONObject(buildMap {
            "username" to username
            "password" to password
            "firstName" to firstName
            "lastName" to lastName
            if (email.isNotBlank()) { put("email", email) }
        })
        val request = JsonObjectRequest(Request.Method.POST, "$BASE_URL/register_user/", params,
                responseListener, errorListener)
        singleton.addToRequestQueue(request)
    }

    fun getAuthToken(username: String, password: String, singleton: NetworkSingleton,
                     responseListener: Response.Listener<JSONObject>? = null, errorListener:
                     Response
                     .ErrorListener? = null) {
        val params = JSONObject(mapOf(
                "username" to username,
                "password" to password
        ))
        val request = JsonObjectRequest(Request.Method.POST, "$BASE_URL/api_token_auth/", params,
                responseListener, errorListener)
        singleton.addToRequestQueue(request)
    }

    companion object {
        private const val KEY_STORE = "AndroidKeyStore"
        private const val ALIAS = "USERID"

    }
}