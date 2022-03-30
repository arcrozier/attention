package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject

class LoginViewModel(
        private val attentionRepository: AttentionRepository,
        application: Application) : AndroidViewModel(application) {

    var login by mutableStateOf(true)

    var uiEnabled by mutableStateOf(true)
    var username by mutableStateOf("")
    var usernameCaption by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordCaption by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var confirmPasswordCaption by mutableStateOf("")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var email by mutableStateOf("")
    var emailCaption by mutableStateOf("")

    fun login(onLoggedIn: () -> Unit) {
// TODO UI enabled = false
// TODO use volley to call obtain token
// on ok -> save username in default prefs and token in USER_INFO, call onLoggedIn
// on error -> display message in password caption, set error on username and password, set UI enabled = true
        uiEnabled = false
        val context = getApplication<Application>()
        attentionRepository.getAuthToken(username = username, password = password,
                NetworkSingleton.getInstance(context), responseListener = {
                    val userInfoEditor = context.getSharedPreferences(MainViewModel.USER_INFO,
                            Context.MODE_PRIVATE).edit()
            userInfoEditor.putString(MainViewModel.MY_TOKEN, it.getString("token"))
            userInfoEditor.apply()
            val defaultPrefsEditor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            defaultPrefsEditor.putString(MainViewModel.MY_ID, username)
            defaultPrefsEditor.apply()
            onLoggedIn()
        }, errorListener = {
            passwordCaption = context.getString(R.string.wrong_password)
            uiEnabled = true
        })
    }

    fun createUser(onLoggedIn: () -> Unit) {
// TODO UI enabled = false
// TODO check username is not blank - display message in caption (and is error = true)
// TODO check password is not blank (and check that in the API too) - display in password caption (and is error = true
// TODO check passwords are the same - display in confirm password caption
// TODO if failed, UI enabled = true
// TODO use volley to call register user
// on ok -> call login, passing onLoggedIn
// on error -> read message and display invalid username in username caption (check invalid usernames fail in API tests), invalid email in email caption, UI enabled = true
        uiEnabled = false
        val context = getApplication<Application>()
        var passed = true
        if (username.isBlank()) {
            usernameCaption = context.getString(R.string.username_in_use)
            passed = false
        }
        if (password.length < 8) {
            passwordCaption = context.getString(R.string.password_validation_failed)
            passed = false
        }
        if (password != confirmPassword) {
            confirmPasswordCaption = context.getString(R.string.passwords_different)
            passed = false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailCaption = context.getString(R.string.invalid_email)
            passed = false
        }
        if (!passed) {
            uiEnabled = true
            return
        }

        attentionRepository.registerUser(username = username, password = password, firstName =
        firstName, lastName = lastName, email = email, NetworkSingleton.getInstance(context),
                responseListener = {
                    login(onLoggedIn)
                }, errorListener = {
                    if (JSONObject(it.networkResponse.data.toString())
        })
    }
}
