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
    var agreedToToS by mutableStateOf(false)

    fun login(scaffoldState: ScaffoldState, scope: CoroutineScope, onLoggedIn: () -> Unit) {
        uiEnabled = false
        val context = getApplication<Application>()
        attentionRepository.getAuthToken(username = username, password = password,
                responseListener = { _, response, _ ->
                    uiEnabled = true
                    when (response.code()) {
                        200 -> {
                            val body = response.body()
                            if (body == null) {
                                Log.e(sTAG, "Got response but body was null!")
                                return@getAuthToken
                            }
                            val userInfoEditor = context.getSharedPreferences(
                                    MainViewModel.USER_INFO,
                                    Context.MODE_PRIVATE
                            ).edit()
                            userInfoEditor.putString(MainViewModel.MY_TOKEN, body.token)
                            userInfoEditor.apply()
                            val defaultPrefsEditor =
                                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                            defaultPrefsEditor.putString(MainViewModel.MY_ID, username)
                            defaultPrefsEditor.apply()
                            password = ""
                            onLoggedIn()
                        }
                        400 -> {
                            Log.e(sTAG, response.errorBody().toString())
                            passwordCaption = context.getString(R.string.wrong_password)
                        }
                    }
                }, errorListener = { _, t ->
            genericErrorHandling(0, scaffoldState, scope, context, t)
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
        firstName, lastName = lastName, email = email,
                responseListener = { _, response, errorBody ->
                    uiEnabled = true
                    when (response.code()) {
                        200 -> {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                                putString(context.getString(R.string.username_key), username)
                                putString(context.getString(R.string.first_name_key), firstName)
                                putString(context.getString(R.string.last_name_key), lastName)
                                putString(context.getString(R.string.email_key), email)
                                apply()
                            }
                            login(scaffoldState, scope, onLoggedIn)
                        }
                        400 -> {
                            if (errorBody == null) {
                                Log.e(sTAG, "Got response but body was null")
                                return@registerUser
                            }
                            when {
                                errorBody.contains("username taken", true) -> {
                                    usernameCaption = context.getString(R.string.username_in_use)
                                }
                                errorBody.contains("enter a valid username", true) -> {
                                    usernameCaption = context.getString(R.string.invalid_username)
                                }
                                errorBody.contains("email address", true) -> {
                                    emailCaption = context.getString(R.string.invalid_email)
                                }
                                errorBody.contains("password", true) -> {
                                    passwordCaption =
                                            context.getString(R.string.password_validation_failed)
                                }
                            }
                        }
                    }
                }, errorListener = { _, t ->
            genericErrorHandling(0, scaffoldState, scope, context, t)
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
                responseListener = { _, response, errorBody ->
                    when (response.code()) {
                        200 -> {
                            attentionRepository.getAuthToken(savedUsername,
                                    password,
                                    responseListener = { _, innerResponse, _ ->
                                        when (innerResponse.code()) {
                                            200 -> {
                                                userInfo.edit().apply {
                                                    putString(
                                                            MainViewModel.MY_TOKEN,
                                                            innerResponse.body()?.token
                                                    )
                                                    apply()
                                                }
                                                password = ""
                                                oldPassword = ""
                                                onPasswordChanged()
                                            }
                                            403 -> {
                                                usernameCaption = context.getString(
                                                        R.string.mysterious_password_change_login_issue
                                                )
                                                login = State.LOGIN
                                            }
                                            else -> {
                                                genericErrorHandling(
                                                        innerResponse.code(),
                                                        scaffoldState,
                                                        scope,
                                                        context
                                                )
                                            }
                                        }
                                    },
                                    errorListener = { _, t ->
                                        usernameCaption =
                                                context.getString(R.string.password_updated)
                                        genericErrorHandling(0, scaffoldState, scope, context, t)
                                        login = State.LOGIN
                                        uiEnabled = true
                                    })
                        }
                        400 -> {
                            passwordCaption = context.getString(R.string.password_validation_failed)
                        }
                        403 -> {
                            if (errorBody?.contains("incorrect old password", true) == true)
                                passwordCaption = context.getString(R.string.wrong_password)
                            else {
                                login = State.LOGIN
                            }
                        }
                    }
                }, errorListener = { _, t ->
            genericErrorHandling(0, scaffoldState, scope, context, t)
            uiEnabled = true

        })

    }

    private fun displaySnackBar(
            scaffoldState: ScaffoldState,
            scope: CoroutineScope,
            message: String,
            actionText: String,
            length: SnackbarDuration = SnackbarDuration.Indefinite
    ) {
        scope.launch {
            scaffoldState.snackbarHostState.showSnackbar(
                    message, actionLabel = actionText,
                    duration = length
            )
        }
    }

    private fun genericErrorHandling(
            code: Int, scaffoldState: ScaffoldState, scope:
            CoroutineScope, context: Context, t: Throwable? = null
    ) {
        when (code) {
            500 -> {
                displaySnackBar(
                        scaffoldState, scope, context.getString(
                        R.string
                                .server_error
                ), context.getString(android.R.string.ok)
                )
            }
            else -> {
                displaySnackBar(
                        scaffoldState, scope, context.getString(
                        R.string
                                .connection_error
                ), context.getString(android.R.string.ok)
                )
                Log.e(sTAG, "An unexpected error occurred: ${t?.message}")
            }
        }
    }

    companion object {
        private val sTAG = LoginViewModel::class.java.name
    }
}
