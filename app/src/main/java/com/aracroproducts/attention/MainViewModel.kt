package com.aracroproducts.attention

import android.app.Application
import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.android.volley.Response
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.security.KeyPair
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject

class MainViewModel @Inject constructor(
        private val attentionRepository: AttentionRepository,
        application: Application
) : AndroidViewModel(application) {
    // private val _friends: MutableLiveData<MutableMap<String, Friend>> = MutableLiveData
    // (HashMap())
    // val friends: LiveData<MutableMap<String, Friend>> = _friends

    enum class UserStatus {
        GOOD, NEEDS_UPLOAD, AWAITING_INPUT
    }
    val friends = attentionRepository.getFriends().asLiveData()

    val user: MutableLiveData<User> = MutableLiveData(User())

    val userStatus = mutableStateOf(UserStatus.GOOD)

    fun onAddFriend(friend: Friend) {
        attentionRepository.insert(friend)
    }

    fun onDeleteFriend(friend: Friend) {
        attentionRepository.delete(friend)
    }

    fun onEditName(id: String, name: String) {
        attentionRepository.edit(Friend(id = id, name = name))
    }

    fun isFromFriend(message: Message): Boolean {
        return attentionRepository.isFromFriend(message = message)
    }

    fun getFriend(id: String): Friend {
        return attentionRepository.getFriend(id)
    }

    init {
        val userInfo = application.getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE)
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(application)
        // Verify Firebase token and continue configuring settings
        val token = userInfo.getString(MainActivity.MY_TOKEN, null)
        var privateKeyString = userInfo.getString(PREF_PRIVATE_KEY, null)
        var publicKeyString = userInfo.getString(PREF_PUBLIC_KEY, null)

        if (privateKeyString == null || publicKeyString == null) {
            val keyPair = attentionRepository.getKeyPair()
            user.value = User(keyPair, token)
            val editor = userInfo.edit()
            privateKeyString = AttentionRepository.keyToString(keyPair.private)
            publicKeyString = AttentionRepository.keyToString(keyPair.public)
            editor.putString(PREF_PUBLIC_KEY, publicKeyString)
            editor.putString(PREF_PRIVATE_KEY, privateKeyString)
            editor.apply()

            val defaultEditor = defaultPrefs.edit()
            defaultEditor.putBoolean(MainActivity.UPLOADED, false)
            defaultEditor.apply()
        } else {
            user.value = User(KeyPair(AttentionRepository.stringToPublicKey(publicKeyString),
                    AttentionRepository.stringToPrivateKey(privateKeyString)))
        }


        if (!defaultPrefs.contains(application.getString(R.string.name_key)) || !userInfo.contains
                (MainActivity.MY_ID)) {
            userStatus.value = UserStatus.AWAITING_INPUT
        } else {
            user.value = User(attentionRepository.getKeyPair(), token)
        }
        if (token != null && !userInfo.getBoolean(MainActivity.UPLOADED, false)) {
            attentionRepository.sendToken<JSONObject>(token, publicKeyString)
        } else if (token == null) {
            getToken(application, publicKeyString)
        }
    }

    /**
    * Helper method that gets the Firebase token
    */
    private fun getToken(context: Context, publicKey: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String?> ->
            if (!task.isSuccessful) {
                Log.w(sTAG, "getInstanceId failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new Instance ID token
            val token = task.result
            Log.d(sTAG, "Got token! $token")

            user.value?.token = token
            val preferences = context.getSharedPreferences(MainActivity.USER_INFO,
                    AppCompatActivity.MODE_PRIVATE)
            if (token != null && token != preferences.getString(MainActivity.MY_TOKEN, "")) {
                attentionRepository.sendToken<JSONObject>(token, publicKey,
                        {
                            // TODO make sure error checking happens in repository
                            val editor = preferences.edit()
                            editor.putBoolean(MainActivity.UPLOADED, true)
                            editor.apply()
                            Toast.makeText(context, context.getString(R.string.user_registered),
                                    Toast.LENGTH_SHORT).show()
                        })
            }

            // Log and toast
            val msg = context.getString(R.string.msg_token_fmt, token)
            Log.d(sTAG, msg)
        }
    }

    companion object {
        private val sTAG: String = MainViewModel::class.java.name

        private const val PREF_PRIVATE_KEY = "private_key"
        private const val PREF_PUBLIC_KEY = "public_key"
    }


}