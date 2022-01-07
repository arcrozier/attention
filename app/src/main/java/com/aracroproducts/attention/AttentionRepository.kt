package com.aracroproducts.attention

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.key
import com.android.volley.RequestQueue
import com.android.volley.Response
import kotlinx.coroutines.flow.Flow
import java.lang.IllegalStateException
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

    fun genKeyPair() {
        val generator = KeyPairGenerator.getInstance(KEYGEN_ALGORITHM, KEY_STORE)
        val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
                ALIAS,  // The spec will automatically save newly generated keys in the key store
                // with this alias
                KeyProperties.PURPOSE_SIGN
        ).run {
            setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            setKeySize(KEY_SIZE)
            build()
        }

        generator.initialize(parameterSpec)

        var keyPair: KeyPair? = null
        while (keyPair == null) {
            keyPair = generator.genKeyPair()  // automatically added to key store
        }
    }

    fun getPublicKey(): PublicKey? {
        val keyStore: KeyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        val privateKeyEntry = keyStore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        return privateKeyEntry.certificate.publicKey
    }

    fun keyExists(): Boolean {
        val keyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        return keyStore.isKeyEntry(ALIAS)
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
        private const val SIGNING_ALGORITHM = "SHA256withECDSA"
        private const val KEYGEN_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC
        private const val KEY_STORE = "AndroidKeyStore"
        private const val ALIAS = "USERID"
        private const val KEY_SIZE = 256
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