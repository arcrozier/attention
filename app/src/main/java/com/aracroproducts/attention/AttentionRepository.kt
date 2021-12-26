package com.aracroproducts.attention

import android.util.Base64
import androidx.compose.runtime.key
import com.android.volley.RequestQueue
import com.android.volley.Response
import kotlinx.coroutines.flow.Flow
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

    fun appendMessage(message: Message, save: Boolean) {
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

    fun getKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(SIGNING_ALGORITHM)
        var keyPair: KeyPair? = null
        while (keyPair == null) {
            keyPair = generator.genKeyPair()
        }
        return keyPair
    }

    fun <T> sendMessage(message: Message, from: String, requestQueue: RequestQueue,
                        responseListener: Response.Listener<T>? = null, errorListener: Response
            .ErrorListener? = null) {
        // TODO send here - may need application context to get the RequestQueue
        // Use Volley: https://developer.android.com/training/volley
        appendMessage(message, false)
    }

    fun <T> sendToken(token: String, publicKey: String, requestQueue: RequestQueue,
                      responseListener: Response
    .Listener<T>? =
            null, errorListener: Response.ErrorListener? = null) {
        // TODO send here - may need application context to get the RequestQueue
        // https://developer.android.com/training/volley
    }

    companion object {
        private const val SIGNING_ALGORITHM = "DSA"
        const val MAGIC_NUMBER = 0xDEADBEEF

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