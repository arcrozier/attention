package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.android.volley.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class LoginViewModel @Inject constructor(
        private val attentionRepository: AttentionRepository,
        application: Application
) : AndroidViewModel(application) {

    enum class State {
        LOGIN, CREATE_USER, CHANGE_PASSWORD
    }

    var login by mutableStateOf(State.LOGIN)

    var uiEnabled by mutableStateOf(true)
    var username by mutableStateOf("")
    var usernameCaption by mutableStateOf("")
    var oldPassword by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordCaption by mutableStateOf("")
    var newPasswordCaption by mutableStateOf("")
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
            val userInfoEditor = context.getSharedPreferences(
                    MainViewModel.USER_INFO,
                    Context.MODE_PRIVATE
            ).edit()
            userInfoEditor.putString(MainViewModel.MY_TOKEN, it.getString("token"))
            userInfoEditor.apply()
            val defaultPrefsEditor =
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
            defaultPrefsEditor.putString(MainViewModel.MY_ID, username)
            defaultPrefsEditor.apply()
            onLoggedIn()
        }, errorListener = {
            when (it) {
                is ClientError -> {
                    Log.e(sTAG, String(it.networkResponse.data))
                    passwordCaption = context.getString(R.string.wrong_password)
                }
                else -> {
                    genericErrorHandling(it, scaffoldState, scope, context)
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
        if (!(email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())) {
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
                    PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                        putString(context.getString(R.string.username_key), username)
                        putString(context.getString(R.string.first_name_key), firstName)
                        putString(context.getString(R.string.last_name_key), lastName)
                        putString(context.getString(R.string.email_key), email)
                        apply()
                    }
                    login(scaffoldState, scope, onLoggedIn)
                }, errorListener = {
            Log.e(sTAG, it.networkResponse.toString())
            when (it) {
                is ClientError -> {
                    val strResponse = String(it.networkResponse.data)
                    Log.e(sTAG, strResponse)
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
                            passwordCaption =
                                    context.getString(R.string.password_validation_failed)
                        }
                    }
                }
                else -> {
                    genericErrorHandling(it, scaffoldState, scope, context)
                }
            }
            uiEnabled = true
        })
    }

    fun changePassword(
            scaffoldState: ScaffoldState, scope: CoroutineScope, onPasswordChanged: () ->
            Unit
    ) {
        uiEnabled = false
        val context = getApplication<Application>()
        var passed = true
        if (oldPassword.isBlank()) {
            passwordCaption = context.getString(R.string.wrong_password)
            passed = false
        }
        if (password.length < 8) {
            newPasswordCaption = context.getString(R.string.password_validation_failed)
            passed = false
        }
        if (password != confirmPassword) {
            confirmPasswordCaption = context.getString(R.string.passwords_different)
            passed = false
        }
        if (!passed) {
            uiEnabled = true
            return
        }

        val savedUsername = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(MainViewModel.MY_ID, null)
        val userInfo = context.getSharedPreferences(MainViewModel.USER_INFO, Context.MODE_PRIVATE)
        val token = userInfo.getString(MainViewModel.MY_TOKEN, null)
        if (savedUsername == null || token == null) {
            login = State.LOGIN
            usernameCaption = context.getString(R.string.password_verification_failed)
            uiEnabled = true
            return
        }
        attentionRepository.editUser(
                token, password = password,
                oldPassword = oldPassword,
                singleton = NetworkSingleton.getInstance(context),
                responseListener = {
                    attentionRepository.getAuthToken(savedUsername, password, NetworkSingleton
                            .getInstance(context), responseListener = {
                        userInfo.edit().apply {
                            putString(MainViewModel.MY_TOKEN, it.getString("token"))
                            apply()
                        }
                        onPasswordChanged()
                    },
                            errorListener = {
                                if (it is ClientError) {
                                    usernameCaption = context.getString(
                                            R.string.mysterious_password_change_login_issue
                                    )
                                    login = State.LOGIN
                                } else {
                                    usernameCaption = context.getString(R.string.password_updated)
                                    genericErrorHandling(it, scaffoldState, scope, context)
                                    login = State.LOGIN
                                }
                                uiEnabled = true
                            })
                }, errorListener = { error ->
            when (error) {
                is ClientError -> {
                    passwordCaption = context.getString(R.string.password_validation_failed)

                }
                is AuthFailureError -> {
                    val responseData = String(error.networkResponse.data)
                    if (responseData.contains("incorrect old password", true))
                        passwordCaption = context.getString(R.string.wrong_password)
                    else {
                        login = State.LOGIN
                    }
                }
                else -> {
                    genericErrorHandling(error, scaffoldState, scope, context)
                }
            }
            uiEnabled = true

        })

    }

    private fun displaySnackBar(
            scaffoldState: ScaffoldState,
            scope: CoroutineScope,
            message: String,
            actionText: String,
            length: SnackbarDuration
    ) {
        scope.launch {
            scaffoldState.snackbarHostState.showSnackbar(
                    message, actionLabel = actionText,
                    duration = length
            )
        }
    }

    private fun genericErrorHandling(
            error: VolleyError, scaffoldState: ScaffoldState, scope:
            CoroutineScope, context: Context
    ) {
        when (error) {
            is NoConnectionError -> {
                displaySnackBar(
                        scaffoldState, scope, context.getString(
                        R.string
                                .connection_error
                ), context.getString(android.R.string.ok),
                        SnackbarDuration.Indefinite
                )
            }
            is NetworkError -> {
                displaySnackBar(
                        scaffoldState, scope, context.getString(
                        R.string
                                .network_error
                ), context.getString(android.R.string.ok), SnackbarDuration
                        .Indefinite
                )
            }
            is ServerError -> {
                displaySnackBar(
                        scaffoldState, scope, context.getString(
                        R.string
                                .server_error
                ), context.getString(android.R.string.ok), SnackbarDuration
                        .Indefinite
                )
            }
            else -> {
                displaySnackBar(
                        scaffoldState, scope, context.getString(
                        R.string
                                .unknown_error
                ), context.getString(android.R.string.ok), SnackbarDuration
                        .Indefinite
                )
                Log.e(sTAG, "An unexpected error occurred: ${error.message}")
            }
        }
    }

    companion object {
        private val sTAG = LoginViewModel::class.java.name
    }
}
