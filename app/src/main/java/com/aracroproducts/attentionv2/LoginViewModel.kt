package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    fun login(scaffoldState: ScaffoldState, scope: CoroutineScope, onLoggedIn: () -> Unit) {
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
            when (it) {
                is ClientError -> {
                    passwordCaption = context.getString(R.string.wrong_password)
                }
                is NoConnectionError -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .connection_error), context.getString(android.R.string.ok), SnackbarDuration.Indefinite)
                }
                is NetworkError -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .network_error), context.getString(android.R.string.ok), SnackbarDuration
                            .Indefinite)
                }
                is ServerError -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .server_error), context.getString(android.R.string.ok), SnackbarDuration
                            .Indefinite)
                }
                else -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .unknown_error), context.getString(android.R.string.ok), SnackbarDuration
                            .Indefinite)
                    Log.e(sTAG, "An unexpected error occurred: ${it.message}")
                }
            }
            uiEnabled = true
        })
    }

    fun createUser(scaffoldState: ScaffoldState, scope: CoroutineScope, onLoggedIn: () -> Unit) {
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
                    login(scaffoldState, scope, onLoggedIn)
                }, errorListener = {
            when (it) {
                is ClientError -> {
                    val strResponse = String(it.networkResponse.data)
                    when {
                        strResponse.contains("username taken", true) -> {
                            usernameCaption = context.getString(R.string.username_in_use)
                        }
                        strResponse.contains("enter a valid username", true) -> {
                            usernameCaption = context.getString(R.string.invalid_username)
                        }
                        strResponse.contains("email address", true) -> {
                            emailCaption = context.getString(R.string.invalid_email)
                        }
                        strResponse.contains("password", true) -> {
                            passwordCaption = context.getString(R.string.password_validation_failed)
                        }
                    }
                }
                is NoConnectionError -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .connection_error), context.getString(android.R.string.ok), SnackbarDuration.Indefinite)
                }
                is NetworkError -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .network_error), context.getString(android.R.string.ok), SnackbarDuration
                            .Indefinite)
                }
                is ServerError -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .server_error), context.getString(android.R.string.ok), SnackbarDuration
                            .Indefinite)
                }
                else -> {
                    displaySnackBar(scaffoldState, scope, context.getString(R.string
                            .unknown_error), context.getString(android.R.string.ok), SnackbarDuration
                            .Indefinite)
                    Log.e(sTAG, "An unexpected error occurred: ${it.message}")
                }
            }
            uiEnabled = true
        })
    }

    private fun displaySnackBar(scaffoldState: ScaffoldState,
                                scope: CoroutineScope,
                                message: String,
                                actionText: String,
                                length: SnackbarDuration) {
        scope.launch {
            scaffoldState.snackbarHostState.showSnackbar(message, actionLabel = actionText,
                    duration = length)
        }
    }

    companion object {
        private val sTAG = LoginViewModel::class.java.name
    }
}
