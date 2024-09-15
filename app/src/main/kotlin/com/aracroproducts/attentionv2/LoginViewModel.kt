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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.MainViewModel.Companion.MY_ID
import com.aracroproducts.attentionv2.MainViewModel.Companion.MY_TOKEN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LoginViewModel(
    private val attentionRepository: AttentionRepository,
    private val preferencesRepository: PreferencesRepository,
    application: Application
) : AndroidViewModel(application) {

    enum class State {
        LOGIN, CREATE_USER, CHANGE_PASSWORD, CHOOSE_USERNAME, LINK_ACCOUNT
    }

    private var savedIdToken: String? = null
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
        snackbarHostState: SnackbarHostState?,
        coroutineScope: CoroutineScope?,
        idToken: String?,
        onLoggedIn: (token: String) -> Unit
    ) {
        val localIdToken =
            idToken ?: savedIdToken ?: throw IllegalStateException("idToken was null")
        savedIdToken = localIdToken
        val context = getApplication<Application>()
        if (login == State.CHOOSE_USERNAME && username.isBlank()) {
            usernameCaption = context.getString(R.string.empty_username)
            return
        }
        uiEnabled = false

        if (username.isNotBlank() && !agreedToToS) {
            checkboxError = true
            uiEnabled = true
            login = State.CHOOSE_USERNAME
            return
        }

        viewModelScope.launch {
            try {
                val result = attentionRepository.signInWithGoogle(
                    userIdToken = localIdToken,
                    username = username.ifBlank { null },
                    agree = if (agreedToToS) "yes" else null
                )
                viewModelScope.launch {
                    loginFinished(result.token)
                    onLoggedIn(result.token)
                    uiEnabled = true
                }
            } catch (e: HttpException) {
                uiEnabled = true
                val response = e.response()
                when (response?.code()) {
                    400 -> {
                        login = State.CHOOSE_USERNAME
                        Log.e(
                            sTAG, response.errorBody().toString()
                        )
                        if (response.errorBody()?.string()?.contains("terms of service") == true
                        ) {
                            checkboxError = true
                        } else usernameCaption =
                            context.getString(R.string.username_in_use)
                    }

                    401 -> {
                        login = State.CHOOSE_USERNAME
                        Log.d(sTAG, "Selecting username")
                    }

                    403 -> {
                        login = State.CHOOSE_USERNAME
                        Log.e(sTAG, "Bad Google token: $idToken")
                        usernameCaption = context.getString(
                            R.string.bad_google_token
                        )
                    }

                    else -> {
                        genericErrorHandling(
                            response?.code() ?: -1,
                            snackbarHostState,
                            coroutineScope,
                            context
                        )
                    }
                }
            } catch (e: Exception) {
                genericErrorHandling(
                    0,
                    snackbarHostState,
                    coroutineScope,
                    context,
                    e
                )
                uiEnabled = true
            }
        }
    }

    private suspend fun loginFinished(token: String) {
        passwordHidden = true
        preferencesRepository.bulkEdit { settings ->
            settings[stringPreferencesKey(MY_TOKEN)] = token
            settings[stringPreferencesKey(MY_ID)] = username
        }
        password = ""
        confirmPassword = ""
        username = ""
        firstName = ""
        lastName = ""
        email = ""
    }

    fun linkAccount(
        snackbarHostState: SnackbarHostState?,
        coroutineScope: CoroutineScope?,
        idToken: String,
        onLoggedIn: () -> Unit
    ) {
        val context = getApplication<Application>()

        if (password.isBlank()) {
            passwordCaption = context.getString(R.string.wrong_password)
            return
        }
        uiEnabled = false

        viewModelScope.launch {

            // token is auth token
            val token = preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))
            if (token == null) {
                login = State.LOGIN
                usernameCaption = context.getString(R.string.password_verification_failed)
                uiEnabled = true
                return@launch
            }
            try {
                attentionRepository.linkGoogleAccount(
                    googleToken = idToken,
                    password = password,
                    token = token
                )
                onLoggedIn()
                preferencesRepository.setValue(
                    booleanPreferencesKey(
                        context.getString(R.string.password_key)
                    ), false
                )
            } catch (e: HttpException) {
                val response = e.response()
                when (response?.code()) {
                    400 -> {
                        Log.e(
                            sTAG,
                            response.errorBody().toString()
                        )
                        passwordCaption = context.getString(
                            R.string.google_account_in_use
                        )
                    }

                    403 -> {
                        val errorBody =
                            response.errorBody()?.string()
                        when {
                            errorBody?.contains("password") == true -> {
                                passwordCaption =
                                    context.getString(R.string.wrong_password)
                            }

                            errorBody?.contains(
                                "google", true
                            ) == true -> {
                                passwordCaption =
                                    context.getString(R.string.google_sign_in_failed)
                            }

                            else -> {
                                login = State.LOGIN
                            }
                        }
                    }

                    else -> {
                        genericErrorHandling(
                            response?.code() ?: -1,
                            snackbarHostState,
                            coroutineScope,
                            context
                        )
                    }
                }
            } catch (e: Exception) {
                genericErrorHandling(
                    0,
                    snackbarHostState,
                    coroutineScope,
                    context,
                    e
                )
            } finally {
                uiEnabled = true
            }
        }

    }

    fun login(
        snackbarHostState: SnackbarHostState?,
        scope: CoroutineScope?,
        onLoggedIn: (username: String, password: String, token: String) -> Unit
    ) {
        uiEnabled = false
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val response = attentionRepository.getAuthToken(
                    username = username,
                    password = password
                )
                val tempUsername = username
                val tempPassword = password
                loginFinished(response.token)
                onLoggedIn(
                    tempUsername, tempPassword, response.token
                )
            } catch (e: HttpException) {
                val response = e.response()
                when (response?.code()) {
                    400 -> {
                        Log.e(sTAG, response.errorBody().toString())
                        passwordCaption =
                            context.getString(R.string.wrong_password)
                    }

                    else -> {
                        genericErrorHandling(
                            response?.code() ?: -1,
                            snackbarHostState,
                            scope,
                            context,
                            e
                        )
                    }
                }
            } catch (e: Exception) {
                genericErrorHandling(
                    0, snackbarHostState, scope, context, e
                )
            } finally {
                uiEnabled = true
            }
        }
    }

    fun createUser(
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope,
        onLoggedIn: (username: String, password: String, token: String) -> Unit
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

        viewModelScope.launch {
            try {
                attentionRepository.registerUser(
                    username = username,
                    password = password,
                    firstName = firstName,
                    lastName = lastName,
                    email = email
                )
                preferencesRepository.bulkEdit { settings ->
                    settings[stringPreferencesKey(
                        context.getString(
                            R.string.username_key
                        )
                    )] = username
                    settings[stringPreferencesKey(
                        context.getString(
                            R.string.first_name_key
                        )
                    )] = firstName
                    settings[stringPreferencesKey(
                        context.getString(
                            R.string.last_name_key
                        )
                    )] = lastName
                    settings[stringPreferencesKey(
                        context.getString(
                            R.string.email_key
                        )
                    )] = email
                }
                login(snackbarHostState, scope, onLoggedIn)
            } catch (e: HttpException) {
                val response = e.response()
                val errorBody = response?.errorBody()?.string()
                when (response?.code()) {
                    400 -> {
                        if (errorBody == null) {
                            Log.e(
                                sTAG, "Got response but body was null"
                            )
                            return@launch
                        }
                        when {
                            errorBody.contains(
                                "username taken", true
                            ) -> {
                                usernameCaption =
                                    context.getString(R.string.username_in_use)
                            }

                            errorBody.contains(
                                "enter a valid username", true
                            ) -> {
                                usernameCaption =
                                    context.getString(R.string.invalid_username)
                            }

                            errorBody.contains(
                                "email address", true
                            ) -> {
                                emailCaption =
                                    context.getString(R.string.invalid_email)
                            }

                            errorBody.contains("password", true) -> {
                                passwordCaption =
                                    context.getString(R.string.password_validation_failed)
                            }

                            errorBody.contains(
                                "email taken",
                                true
                            ) -> {
                                emailCaption =
                                    context.getString(R.string.email_in_use)
                            }
                        }
                    }

                    else -> {
                        genericErrorHandling(
                            response?.code() ?: -1,
                            snackbarHostState,
                            scope,
                            context
                        )
                    }
                }
            } catch (e: Exception) {
                genericErrorHandling(
                    0, snackbarHostState, scope, context, e
                )
            } finally {
                uiEnabled = true
            }
        }
    }

    fun changePassword(
        snackbarHostState: SnackbarHostState, scope: CoroutineScope, onPasswordChanged: () -> Unit
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

        viewModelScope.launch(context = Dispatchers.IO) {
            val savedUsername = preferencesRepository.getValue(stringPreferencesKey(MY_ID))
            val token = preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))
            if (savedUsername == null || token == null) {
                login = State.LOGIN
                usernameCaption = context.getString(R.string.password_verification_failed)
                uiEnabled = true
                return@launch
            }
            try {
                val response = attentionRepository.editUser(
                    token,
                    password = password,
                    oldPassword = oldPassword
                )
                preferencesRepository.setValue(
                    stringPreferencesKey(
                        MY_TOKEN
                    ),
                    response.data.token
                )
                password =
                    ""
                oldPassword =
                    ""
                passwordHidden =
                    true
                onPasswordChanged()
            } catch (e: HttpException) {
                when (e.response()?.code()) {

                    400 -> {
                        passwordCaption =
                            context.getString(R.string.password_validation_failed)
                    }

                    401 -> {
                        oldPasswordCaption =
                            context.getString(R.string.wrong_password)
                    }

                    403 -> {
                        login = State.LOGIN
                    }

                    else -> {
                        genericErrorHandling(
                            e.response()?.code() ?: -1,
                            snackbarHostState,
                            scope,
                            context
                        )
                    }
                }
            } catch (e: Exception) {
                genericErrorHandling(
                    0, snackbarHostState, scope, context, e
                )
            } finally {
                uiEnabled = true
            }
        }


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

    private fun genericErrorHandling(
        code: Int,
        snackbarHostState: SnackbarHostState?,
        scope: CoroutineScope?,
        context: Context,
        t: Throwable? = null
    ) {
        when (code) {
            429 -> {
                snackOrToast(
                    context.getString(R.string.rate_limited), snackbarHostState, scope, context
                )
            }

            500, 502, 503, 504 -> {
                snackOrToast(
                    context.getString(
                        R.string.server_error
                    ), snackbarHostState, scope, context
                )
            }

            else -> {
                snackOrToast(
                    context.getString(
                        R.string.connection_error
                    ), snackbarHostState, scope, context
                )
                Log.e(sTAG, "An unexpected error occurred: ${t?.message}")
            }
        }

    }

    private fun snackOrToast(
        message: String,
        snackbarHostState: SnackbarHostState?,
        coroutineScope: CoroutineScope?,
        context: Context
    ) {
        if (snackbarHostState != null && coroutineScope != null) {
            displaySnackBar(
                snackbarHostState, coroutineScope, message, context.getString(android.R.string.ok)
            )
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val sTAG = LoginViewModel::class.java.name


    }
}
