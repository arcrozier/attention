package com.aracroproducts.attentionv2

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import kotlinx.coroutines.flow.Flow
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

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    fun insert(vararg friend: Friend) = database.getFriendDAO().insert(*friend)


    fun delete(vararg friend: Friend) = database.getFriendDAO().delete(*friend)

    fun edit(friend: Friend) = database.getFriendDAO().updateFriend(friend)

    fun isFromFriend(message: Message): Boolean {
        assert(message.direction == DIRECTION.Incoming)
        return database.getFriendDAO().isFriend(message.otherId)
    }

    fun getFriend(id: String): Friend = database.getFriendDAO().getFriend(id)

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

    fun retrieveAndSignChallenge(singleton: NetworkSingleton,
                                 responseListener: Response.Listener<JSONObject>, errorListener:
                                 Response.ErrorListener? = null) {
        val keyStore = KeyStore.getInstance(KEY_STORE).apply {
            load(null)
        }
        val uid = Base64.encode(keyStore.getCertificate(ALIAS).publicKey.encoded, Base64.URL_SAFE)
        val request = JsonObjectRequest(Request.Method.GET, "$BASE_URL/get_challenge/$uid/",
                null, { response ->
            responseListener.onResponse(JSONObject(mapOf(
                    "signature" to signChallenge(response["data"] as String),
                    "challenge" to response["data"]
            )))
        }, errorListener).apply {
            setShouldCache(false)
        }
        singleton.addToRequestQueue(request)
    }

    fun signChallenge(challenge: String): String {
        val keyStore = KeyStore.getInstance(KEY_STORE).apply {
            load(null)
        }
        val entry: KeyStore.Entry = keyStore.getEntry(ALIAS, null)
        if (entry !is KeyStore.PrivateKeyEntry) {
            throw IllegalStateException("A private key was not found to sign the challenge")
        }
        return String(Signature.getInstance(SIGNING_ALGORITHM).run {
            initSign(entry.privateKey)
            update(challenge.encodeToByteArray())
            sign()
        })
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

    private fun sendToken(params: JSONObject, singleton: NetworkSingleton,
                          responseListener: Response.Listener<JSONObject>?, errorListener:
                          Response.ErrorListener?) {
        val request = JsonObjectRequest(Request.Method.POST, "$BASE_URL/post_id/", params,
                responseListener, errorListener)
        singleton.addToRequestQueue(request)
    }

    fun sendToken(token: String, singleton: NetworkSingleton,
                  responseListener: Response.Listener<JSONObject>? = null,
                  errorListener: Response.ErrorListener? = null, authRequired: Boolean =
                          true) {
        if (authRequired) {
            retrieveAndSignChallenge(singleton, { response ->
                val params = JSONObject(mapOf(
                        "token" to token,
                        "id" to getPublicKey(),
                        "signature" to response["signature"],
                        "challenge" to response["challenge"]
                ))
                sendToken(params, singleton, responseListener, errorListener)
            }) { error ->
                errorListener?.onErrorResponse(VolleyError("Failed to get challenge while " +
                        "sending token: ${error.message}"))
            }
        } else {
            sendToken(JSONObject(mapOf("token" to token, "id" to getPublicKey())), singleton,
                    responseListener, errorListener)
        }
    }

    // TODO getUserInfo function

    // TODO register device function

    fun editUser(token: String, singleton: NetworkSingleton, firstName: String? = null, lastName:
    String? = null,
                 password:
    String? = null,
                 email: String? = null, responseListener: Response.Listener<JSONObject>? = null,
                 errorListener: Response.ErrorListener? = null) {
        val params = JSONObject(mapOf(
                "first_name" to firstName,
                "last_name" to lastName,
                "password" to password,
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

    fun updateUserInfo(friends: List<JSONObject>) {
        for (jsonFriend in friends) {
            val friend = Friend(jsonFriend.getString("friend"), jsonFriend.getString("name"),
                    jsonFriend.getInt("sent"),
                    jsonFriend.getInt("received"), jsonFriend.getString("last_message_id_sent"),
                    jsonFriend.getBoolean("last_message_read"))
            database.getFriendDAO().insert(friend)
        }
    }

    fun alertRead(username: String, alertId: String) {
        database.getFriendDAO().setMessageRead(true, alert_id = alertId, id = username)
    }

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
        private const val SIGNING_ALGORITHM = "SHA256withECDSA"
        private const val KEYGEN_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC
        private const val KEY_STORE = "AndroidKeyStore"
        private const val ALIAS = "USERID"
        private const val KEY_SIZE = 256

        fun keyToString(key: Key): String {
            return Base64.encodeToString(key.encoded, Base64.DEFAULT)
        }

        fun stringToPrivateKey(privateKey: String): PrivateKey {
            val keyFactory = KeyFactory.getInstance(SIGNING_ALGORITHM)
            return keyFactory.generatePrivate(stringToKeySpec(privateKey))
        }

        fun stringToPublicKey(publicKey: String): PublicKey {
            val keyFactory = KeyFactory.getInstance(SIGNING_ALGORITHM)
            return keyFactory.generatePublic(stringToKeySpec(publicKey))
        }

        private fun stringToKeySpec(key: String): KeySpec {
            return X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT))
        }
    }
}