package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class LoginViewModel @Inject constructor(
        private val attentionRepository: AttentionRepository, application: Application
) : AndroidViewModel(application) {

    enum class State {
        LOGIN, CREATE_USER, CHANGE_PASSWORD, CHOOSE_USERNAME, LINK_ACCOUNT
    }

    var idToken: String? = null
    var showOneTapUI = true
    var login by mutableStateOf(State.LOGIN)

    var uiEnabled by mutableStateOf(true)
    var username by mutableStateOf("")
    var usernameCaption by mutableStateOf("")
    var oldPassword by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordHidden by mutableStateOf(true)
    var passwordCaption by mutableStateOf("")
    var oldPasswordCaption by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var confirmPasswordCaption by mutableStateOf("")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var email by mutableStateOf("")
    var emailCaption by mutableStateOf("")
    var agreedToToS by mutableStateOf(false)
    var checkboxError by mutableStateOf(false)

    fun loginWithGoogle(
            snackbarHostState: SnackbarHostState?, coroutineScope: CoroutineScope?,
            onLoggedIn: () -> Unit
    ) {
        val localIdToken = idToken ?: throw IllegalStateException("idToken was null")
        uiEnabled = false
        val context = getApplication<Application>()
        attentionRepository.signInWithGoogle(userIdToken = localIdToken,
                username = username,
                responseListener = { _, response, _ ->
                    uiEnabled = true
                    when (response.code()) {
                        200 -> {
                            val body = response.body()
                            if (body == null) {
                                Log.e(
                                        sTAG,
                                        "Got response but body was null!"
                                )
                                return@signInWithGoogle
                            }
                            loginFinished(body.token)
                            onLoggedIn()
                        }
                        400 -> {
                            Log.e(
                                    sTAG,
                                    response.errorBody().toString()
                            )
                            usernameCaption =
                                    context.getString(R.string.username_in_use)
                        }
                        401 -> { // need to provide a username
                            login = State.CHOOSE_USERNAME
                        }
                        else -> {
                            genericErrorHandling(
                                    response.code(),
                                    snackbarHostState,
                                    coroutineScope,
                                    context
                            )
                        }
                    }
                },
                errorListener = { _, t ->
                    genericErrorHandling(
                            0,
                            snackbarHostState,
                            coroutineScope,
                            context,
                            t
                    )
                    uiEnabled = true
                })
    }

    private fun loginFinished(token: String) {
        val context = getApplication<Application>()
        val userInfoEditor = context.getSharedPreferences(
                MainViewModel.USER_INFO, Context.MODE_PRIVATE
        ).edit()
        userInfoEditor.putString(MainViewModel.MY_TOKEN, token)
        userInfoEditor.apply()
        val defaultPrefsEditor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        defaultPrefsEditor.putString(MainViewModel.MY_ID, username)
        defaultPrefsEditor.apply()
        password = ""
        passwordHidden = true
    }

    fun linkAccount(
            snackbarHostState: SnackbarHostState?, coroutineScope: CoroutineScope?,
            onLoggedIn: () -> Unit
    ) {
        val localIdToken = idToken ?: throw IllegalStateException("idToken was null")
        uiEnabled = false
        val context = getApplication<Application>()

        if (password.isBlank()) {
            passwordCaption = context.getString(R.string.wrong_password)
            return
        }

        val userInfo = context.getSharedPreferences(MainViewModel.USER_INFO, Context.MODE_PRIVATE)

        // token is auth token
        val token = userInfo.getString(MainViewModel.MY_TOKEN, null)
        if (token == null) {
            login = State.LOGIN
            usernameCaption = context.getString(R.string.password_verification_failed)
            uiEnabled = true
            return
        }
        attentionRepository.linkGoogleAccount(googleToken = localIdToken,
                password = password,
                token = token,
                responseListener = { _, response, _ ->
                    uiEnabled = true
                    when (response.code()) {
                        200 -> {
                            val body = response.body()
                            if (body == null) {
                                Log.e(
                                        sTAG,
                                        "Got response but body was null!"
                                )
                                return@linkGoogleAccount
                            }
                            PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .apply {
                                        putBoolean(context.getString(R.string.password_key), false)
                                        apply()
                                    }
                            onLoggedIn()
                        }
                        400 -> {
                            Log.e(
                                    sTAG,
                                    response.errorBody().toString()
                            )
                        }
                        else -> {
                            genericErrorHandling(
                                    response.code(),
                                    snackbarHostState,
                                    coroutineScope,
                                    context
                            )
                        }
                    }
                },
                errorListener = { _, t ->
                    genericErrorHandling(
                            0,
                            snackbarHostState,
                            coroutineScope,
                            context,
                            t
                    )
                    uiEnabled = true
                })
    }

    fun login(
            snackbarHostState: SnackbarHostState?,
            scope: CoroutineScope?,
            onLoggedIn: (username: String, password: String) -> Unit
    ) {
        uiEnabled = false
        val context = getApplication<Application>()
        attentionRepository.getAuthToken(username = username,
                password = password,
                responseListener = { _, response, _ ->
                    uiEnabled = true
                    when (response.code()) {
                        200 -> {
                            val body = response.body()
                            if (body == null) {
                                Log.e(
                                        sTAG,
                                        "Got response but body was null!"
                                )
                                return@getAuthToken
                            }
                            onLoggedIn(username, password)
                            loginFinished(body.token)
                        }
                        400 -> {
                            Log.e(sTAG, response.errorBody().toString())
                            passwordCaption =
                                    context.getString(R.string.wrong_password)
                        }
                    }
                },
                errorListener = { _, t ->
                    genericErrorHandling(
                            0,
                            snackbarHostState,
                            scope,
                            context,
                            t
                    )
                    uiEnabled = true
                })
    }

    fun createUser(
            snackbarHostState: SnackbarHostState,
            scope: CoroutineScope,
            onLoggedIn: (username: String, password: String) -> Unit
    ) {
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
        if (!agreedToToS) {
            checkboxError = true
            passed = false
        }
        if (!passed) {
            uiEnabled = true
            return
        }

        attentionRepository.registerUser(username = username,
                password = password,
                firstName = firstName,
                lastName = lastName,
                email = email,
                responseListener = { _, response, errorBody ->
                    uiEnabled = true
                    when (response.code()) {
                        200 -> {
                            PreferenceManager.getDefaultSharedPreferences(
                                    context
                            ).edit().apply {
                                putString(
                                        context.getString(R.string.username_key),
                                        username
                                )
                                putString(
                                        context.getString(R.string.first_name_key),
                                        firstName
                                )
                                putString(
                                        context.getString(R.string.last_name_key),
                                        lastName
                                )
                                putString(
                                        context.getString(R.string.email_key),
                                        email
                                )
                                apply()
                            }
                            login(snackbarHostState, scope, onLoggedIn)
                        }
                        400 -> {
                            if (errorBody == null) {
                                Log.e(
                                        sTAG,
                                        "Got response but body was null"
                                )
                                return@registerUser
                            }
                            when {
                                errorBody.contains(
                                        "username taken",
                                        true
                                ) -> {
                                    usernameCaption =
                                            context.getString(R.string.username_in_use)
                                }
                                errorBody.contains(
                                        "enter a valid username",
                                        true
                                ) -> {
                                    usernameCaption =
                                            context.getString(R.string.invalid_username)
                                }
                                errorBody.contains(
                                        "email address",
                                        true
                                ) -> {
                                    emailCaption =
                                            context.getString(R.string.invalid_email)
                                }
                                errorBody.contains("password", true) -> {
                                    passwordCaption =
                                            context.getString(R.string.password_validation_failed)
                                }
                            }
                        }
                    }
                },
                errorListener = { _, t ->
                    genericErrorHandling(
                            0,
                            snackbarHostState,
                            scope,
                            context,
                            t
                    )
                    uiEnabled = true
                })
    }

    fun changePassword(
            snackbarHostState: SnackbarHostState, scope: CoroutineScope,
            onPasswordChanged: () -> Unit
    ) {
        uiEnabled = false
        val context = getApplication<Application>()
        var passed = true
        if (oldPassword.isBlank()) {
            oldPasswordCaption = context.getString(R.string.wrong_password)
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
        attentionRepository.editUser(token,
                password = password,
                oldPassword = oldPassword,
                responseListener = { _, response, errorBody ->
                    when (response.code()) {
                        200 -> {
                            attentionRepository.getAuthToken(savedUsername,
                                    password,
                                    responseListener = { _, innerResponse, _ ->
                                        when (innerResponse.code()) {
                                            200 -> {
                                                userInfo.edit()
                                                        .apply {
                                                            putString(
                                                                    MainViewModel.MY_TOKEN,
                                                                    innerResponse.body()?.token
                                                            )
                                                            apply()
                                                        }
                                                password =
                                                        ""
                                                oldPassword =
                                                        ""
                                                passwordHidden =
                                                        true
                                                onPasswordChanged()
                                            }
                                            403 -> {
                                                usernameCaption =
                                                        context.getString(
                                                                R.string.mysterious_password_change_login_issue
                                                        )
                                                login =
                                                        State.LOGIN
                                            }
                                            else -> {
                                                genericErrorHandling(
                                                        innerResponse.code(),
                                                        snackbarHostState,
                                                        scope,
                                                        context
                                                )
                                            }
                                        }
                                    },
                                    errorListener = { _, t ->
                                        usernameCaption =
                                                context.getString(
                                                        R.string.password_updated
                                                )
                                        genericErrorHandling(
                                                0,
                                                snackbarHostState,
                                                scope,
                                                context,
                                                t
                                        )
                                        login =
                                                State.LOGIN
                                        uiEnabled =
                                                true
                                    })
                        }
                        400 -> {
                            passwordCaption =
                                    context.getString(R.string.password_validation_failed)
                        }
                        403 -> {
                            if (errorBody?.contains(
                                            "incorrect old password",
                                            true
                                    ) == true
                            ) passwordCaption =
                                    context.getString(R.string.wrong_password)
                            else {
                                login = State.LOGIN
                            }
                        }
                    }
                },
                errorListener = { _, t ->
                    genericErrorHandling(0, snackbarHostState, scope, context, t)
                    uiEnabled = true

                })

    }

    private fun displaySnackBar(
            snackbarHostState: SnackbarHostState,
            scope: CoroutineScope,
            message: String,
            actionText: String,
            length: SnackbarDuration = SnackbarDuration.Indefinite
    ) {
        scope.launch {
            snackbarHostState.showSnackbar(
                    message, actionLabel = actionText, duration = length
            )
        }
    }

    // TODO handle 429 - rate limited
    private fun genericErrorHandling(
            code: Int,
            snackbarHostState: SnackbarHostState?,
            scope: CoroutineScope?,
            context: Context,
            t: Throwable? = null
    ) {
        when (code) {
            500 -> {
                if (snackbarHostState != null && scope != null) {
                    displaySnackBar(
                            snackbarHostState, scope, context.getString(
                            R.string.server_error
                    ), context.getString(android.R.string.ok)
                    )
                } else {
                    Toast.makeText(context, R.string.server_error, Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                if (snackbarHostState != null && scope != null) {
                    displaySnackBar(
                            snackbarHostState, scope, context.getString(
                            R.string.connection_error
                    ), context.getString(android.R.string.ok)
                    )
                } else {
                    Toast.makeText(context, R.string.connection_error, Toast.LENGTH_LONG).show()
                }
                Log.e(sTAG, "An unexpected error occurred: ${t?.message}")
            }
        }
    }

    companion object {
        private val sTAG = LoginViewModel::class.java.name
    }
}
