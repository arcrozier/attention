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

class LoginViewModel(
    private val attentionRepository: AttentionRepository,
    private val preferencesRepository: PreferencesRepository,
    application: Application
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
        snackbarHostState: SnackbarHostState?,
        coroutineScope: CoroutineScope?,
        onLoggedIn: (token: String) -> Unit
    ) {
        val localIdToken = idToken ?: throw IllegalStateException("idToken was null")
        uiEnabled = false

        if (username.isNotBlank() && !agreedToToS) {
            checkboxError = true
            uiEnabled = true
            return
        }

        val context = getApplication<Application>()
        attentionRepository.signInWithGoogle(userIdToken = localIdToken,
                                             username = username.ifBlank { null },
                                             agree = if (agreedToToS) "yes" else null,
                                             responseListener = { _, response, _ ->
                                                 if (response.code() != 200 && response.body() != null) uiEnabled =
                                                     true
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
                                                         viewModelScope.launch {
                                                             loginFinished(body.token)
                                                             onLoggedIn(body.token)
                                                             uiEnabled = true
                                                         }
                                                     }
                                                     400 -> {
                                                         Log.e(
                                                             sTAG, response.errorBody().toString()
                                                         )
                                                         if (response.errorBody().toString()
                                                                 .contains("terms of service")
                                                         ) {
                                                             checkboxError = true
                                                         } else usernameCaption =
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
        onLoggedIn: () -> Unit
    ) {
        val localIdToken = idToken ?: throw IllegalStateException("idToken was null")
        uiEnabled = false
        val context = getApplication<Application>()

        if (password.isBlank()) {
            passwordCaption = context.getString(R.string.wrong_password)
            return
        }

        viewModelScope.launch {

            // token is auth token
            val token = preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))
            if (token == null) {
                login = State.LOGIN
                usernameCaption = context.getString(R.string.password_verification_failed)
                uiEnabled = true
                return@launch
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
                                                              viewModelScope.launch {
                                                                  preferencesRepository.setValue(
                                                                      booleanPreferencesKey(
                                                                          context.getString(R.string.password_key)
                                                                      ), false
                                                                  )
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

    }

    fun login(
        snackbarHostState: SnackbarHostState?,
        scope: CoroutineScope?,
        onLoggedIn: (username: String, password: String, token: String) -> Unit
    ) {
        uiEnabled = false
        val context = getApplication<Application>()
        attentionRepository.getAuthToken(username = username,
                                         password = password,
                                         responseListener = { _, response, _ ->
                                             if (response.code() != 200 && response.body() != null) uiEnabled =
                                                 true
                                             when (response.code()) {
                                                 200 -> {
                                                     val body = response.body()
                                                     if (body == null) {
                                                         Log.e(
                                                             sTAG, "Got response but body was null!"
                                                         )
                                                         return@getAuthToken
                                                     }
                                                     viewModelScope.launch {
                                                         val tempUsername = username
                                                         val tempPassword = password
                                                         loginFinished(body.token)
                                                         onLoggedIn(
                                                             tempUsername,
                                                             tempPassword,
                                                             body.token
                                                         )
                                                         uiEnabled = true
                                                     }
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
                                                 0, snackbarHostState, scope, context, t
                                             )
                                             uiEnabled = true
                                         })
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

        attentionRepository.registerUser(username = username,
                                         password = password,
                                         firstName = firstName,
                                         lastName = lastName,
                                         email = email,
                                         responseListener = { _, response, errorBody ->
                                             uiEnabled = true
                                             when (response.code()) {
                                                 200 -> {
                                                     viewModelScope.launch(context = Dispatchers.IO) {
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
                                                     }
                                                     login(snackbarHostState, scope, onLoggedIn)
                                                 }
                                                 400 -> {
                                                     if (errorBody == null) {
                                                         Log.e(
                                                             sTAG, "Got response but body was null"
                                                         )
                                                         return@registerUser
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
                                                     }
                                                 }
                                             }
                                         },
                                         errorListener = { _, t ->
                                             genericErrorHandling(
                                                 0, snackbarHostState, scope, context, t
                                             )
                                             uiEnabled = true
                                         })
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
            attentionRepository.editUser(token,
                                         password = password,
                                         oldPassword = oldPassword,
                                         responseListener = { _, response, _ ->
                                             when (response.code()) {
                                                 200 -> {
                                                     attentionRepository.getAuthToken(savedUsername,
                                                                                      password,
                                                                                      responseListener = { _, innerResponse, _ ->
                                                                                          when (innerResponse.code()) {
                                                                                              200 -> {
                                                                                                  viewModelScope.launch(
                                                                                                      context = Dispatchers.IO
                                                                                                  ) {
                                                                                                      preferencesRepository.setValue(
                                                                                                          stringPreferencesKey(
                                                                                                              MY_TOKEN
                                                                                                          ),
                                                                                                          innerResponse.body()?.token
                                                                                                          ?: ""
                                                                                                      )
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
                                                 401 -> {
                                                     oldPasswordCaption =
                                                         context.getString(R.string.wrong_password)
                                                 }
                                                 403 -> {
                                                     login = State.LOGIN
                                                 }
                                                 else -> {
                                                     genericErrorHandling(
                                                         response.code(),
                                                         snackbarHostState,
                                                         scope,
                                                         context
                                                     )
                                                 }
                                             }
                                         },
                                         errorListener = { _, t ->
                                             genericErrorHandling(
                                                 0, snackbarHostState, scope, context, t
                                             )
                                             uiEnabled = true

                                         })
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

    fun setTokenUploaded(uploaded: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setValue(
                booleanPreferencesKey(
                    MainViewModel.TOKEN_UPLOADED
                ), uploaded
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
