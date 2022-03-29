package com.aracroproducts.attentionv2

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel

class LoginViewModel(application: Application) : AndroidViewModel(application) {

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
# TODO UI enabled = false
# TODO use volley to call obtain token
# on ok -> save username in default prefs and token in USER_INFO, call onLoggedIn
# on error -> display message in password caption, set error on username and password, set UI enabled = true
    }

    fun createUser(onLoggedIn: () -> Unit) {
# TODO UI enabled = false
# TODO check username is not blank - display message in caption (and is error = true)
# TODO check password is not blank (and check that in the API too) - display in password caption (and is error = true
# TODO check passwords are the same - display in confirm password caption
# TODO if failed, UI enabled = true
# TODO use volley to call register user 
# on ok -> call login, passing onLoggedIn
# on error -> read message and display invalid username in username caption (check invalid usernames fail in API tests), invalid email in email caption, UI enabled = true
    }
}
