package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Annotation
import android.text.SpannedString
import android.util.Log
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_TAB
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.getSpans
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aracroproducts.common.AttentionDB
import com.aracroproducts.common.AttentionRepository
import com.aracroproducts.common.DISABLED_ALPHA
import com.aracroproducts.common.PreferencesRepository
import com.aracroproducts.common.PreferencesRepository.Companion.MY_TOKEN
import com.aracroproducts.common.centerWithBottomElement
import com.aracroproducts.common.filterSpecialChars
import com.aracroproducts.common.filterUsername
import com.aracroproducts.common.theme.AppTheme
import com.aracroproducts.common.theme.HarmonizedTheme
import com.aracroproducts.common.toMessage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// TODO support passkeys using the CredentialManager API
class LoginActivity : AppCompatActivity() {

    private val loginViewModel: LoginViewModel by viewModels(factoryProducer = {
        LoginViewModelFactory(
            AttentionRepository(AttentionDB.getDB(this), application as AttentionApplication),
            (application as AttentionApplication).container.settingsRepository,
            application
        )
    })

    private val credentialManager = CredentialManager.create(this)

    private fun handleSignIn(result: GetCredentialResponse, linkExisting: Boolean = false) {
        when (val credential = result.credential) {
            is PasswordCredential -> {
                // got a saved username/password
                loginViewModel.username = credential.id
                loginViewModel.password = credential.password
                loginViewModel.login(null, null, ::signInWithPassword)
            }

            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // Use googleIdTokenCredential and extract the ID to validate and
                        // authenticate on your server.
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)

                        if (linkExisting) {
                            loginViewModel.linkAccount(
                                null,
                                null,
                                googleIdTokenCredential.idToken
                            ) {
                                finish()
                            }
                        } else {
                            loginViewModel.loginWithGoogle(
                                null,
                                null,
                                googleIdTokenCredential.idToken
                            ) { token ->
                                completeSignIn(token)
                                finish()
                            }
                        }
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Received an invalid google id token response", e)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    Log.e(TAG, "Unexpected type of credential")
                }
            }

            else -> {
                // Catch any unrecognized credential type here.
                Log.e(TAG, "Unexpected type of credential")
            }
        }
    }

    private val returnedIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent?.getParcelableExtra(Intent.EXTRA_INTENT)
    }

    class LoginViewModelFactory(
        private val attentionRepository: AttentionRepository,
        private val preferencesRepository: PreferencesRepository,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(attentionRepository, preferencesRepository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (loginViewModel.login) {
                    LoginViewModel.State.CHOOSE_USERNAME -> {
                        loginViewModel.login = LoginViewModel.State.LOGIN
                    }

                    LoginViewModel.State.CHANGE_PASSWORD, LoginViewModel.State.LINK_ACCOUNT -> {
                        finish()
                    }

                    else -> {
                        moveTaskToBack(true)
                    }
                }
            }

        })

        when (intent.action) {
            getString(R.string.change_password_action) -> {
                loginViewModel.login = LoginViewModel.State.CHANGE_PASSWORD
            }

            getString(R.string.link_account_action) -> {
                loginViewModel.login = LoginViewModel.State.LINK_ACCOUNT
            }

            else -> {
                val getPasswordOption = GetPasswordOption()
                val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(getString(R.string.client_id))
                    .setAutoSelectEnabled(true)
                    .build()
                val getCredRequest = GetCredentialRequest(
                    listOf(getPasswordOption, googleIdOption),
                    preferImmediatelyAvailableCredentials = true
                )

                MainScope().launch {
                    try {
                        val result = credentialManager.getCredential(
                            context = this@LoginActivity,
                            request = getCredRequest
                        )
                        handleSignIn(result)
                    } catch (_: GetCredentialException) {
                        // some sort of error with getting a credential (including no previously saved password) - ignore, since this is happening opportunistically
                    }
                }
            }
        }

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HarmonizedTheme {
                    Screen(model = loginViewModel)
                }
            } else {
                AppTheme {
                    Screen(model = loginViewModel)
                }
            }
        }
    }

    @Composable
    fun ChooseUsername(
        model: LoginViewModel,
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Text(
                text = getString(R.string.choose_username_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))
            UsernameField(value = model.username,
                onValueChanged = { onUsernameChanged(model, it) },
                newUsername = true,
                error = model.usernameCaption.isNotBlank(),
                caption = model.usernameCaption,
                enabled = model.uiEnabled,
                context = this@LoginActivity,
                imeAction = ImeAction.Done,
                onDone = {
                    model.loginWithGoogle(snackbarHostState, coroutineScope, null) {
                        finish()
                    }
                })
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            ToSCheckbox(model = model)
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Button(
                onClick = {
                    model.loginWithGoogle(snackbarHostState, coroutineScope, null) {
                        finish()
                    }
                }, enabled = model.uiEnabled, modifier = Modifier.requiredHeight(56.dp)
            ) {
                Box {
                    Text(
                        text = getString(R.string.choose_username),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (!model.uiEnabled) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    @Composable
    fun ChangePassword(
        model: LoginViewModel,
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        val passwordFocusRequester = FocusRequester()
        val confirmPasswordFocusRequester = FocusRequester()
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Text(
                text = getString(R.string.change_password_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))
            OldPasswordField(model = model, passwordFocusRequester = passwordFocusRequester)
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            PasswordField(
                model = model,
                done = {
                    model.login(
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope,
                        onLoggedIn = ::signInWithPassword
                    )
                },
                imeAction = ImeAction.Next,
                nextFocusRequester = confirmPasswordFocusRequester,
                currentFocusRequester = passwordFocusRequester
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            ConfirmPasswordField(
                model = model, confirmPasswordFocusRequester = confirmPasswordFocusRequester
            ) {
                model.changePassword(
                    snackbarHostState = snackbarHostState, scope = coroutineScope
                ) {
                    finish()
                }
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))

            Button(
                onClick = {
                    model.changePassword(
                        snackbarHostState = snackbarHostState, scope = coroutineScope
                    ) {
                        finish()
                    }
                }, enabled = model.uiEnabled, modifier = Modifier.requiredHeight(56.dp)
            ) {
                Box {
                    Text(
                        text = getString(R.string.change_password),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (!model.uiEnabled) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }


    @Composable
    fun Login(
        model: LoginViewModel,
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        Column(
            verticalArrangement = centerWithBottomElement,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Text(
                text = getString(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))
            UsernameField(
                value = model.username,
                onValueChanged = { onUsernameChanged(model, it) },
                newUsername = true,
                error = model.passwordCaption.isNotBlank(),
                caption = model.usernameCaption,
                enabled = model.uiEnabled,
                context = this@LoginActivity
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            PasswordField(model, ImeAction.Done, {
                model.login(
                    snackbarHostState = snackbarHostState,
                    scope = coroutineScope,
                    onLoggedIn = ::signInWithPassword
                )
            })
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Button(
                onClick = {
                    model.login(
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope,
                        onLoggedIn = ::signInWithPassword
                    )
                }, enabled = model.uiEnabled, modifier = Modifier.requiredHeight(56.dp)
            ) {
                Box(modifier = Modifier.fillMaxHeight()) {
                    Text(
                        text = getString(R.string.login),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (!model.uiEnabled) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(
                    alpha = DISABLED_ALPHA
                ), modifier = Modifier.fillMaxWidth(0.75f)
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))

            OutlinedButton(onClick = {
                signInWithGoogle(
                    snackbarHostState, coroutineScope
                )
            }, enabled = model.uiEnabled) {
                Image(
                    painter = painterResource(id = R.drawable.ic_btn_google),
                    contentDescription = getString(R.string.google_logo),
                    modifier = Modifier.height(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = getString(R.string.sign_in_w_google))
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextButton(onClick = {
                model.passwordHidden = true
                model.login = LoginViewModel.State.CREATE_USER
                model.passwordCaption = ""
                model.agreedToToS = false
            }) {
                Text(text = getString(R.string.create_user))
            }
        }
    }

    @Composable
    fun CreateUser(
        model: LoginViewModel,
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        val confirmPasswordFocusRequester = FocusRequester()
        val focusManager = LocalFocusManager.current
        Column(
            verticalArrangement = centerWithBottomElement,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Text(
                text = getString(R.string.create_user_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))
            UsernameField(
                value = model.username,
                onValueChanged = { onUsernameChanged(model, it) },
                newUsername = true,
                error = model.usernameCaption.isNotBlank(),
                caption = model.usernameCaption,
                enabled = model.uiEnabled,
                context = this@LoginActivity
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            FirstNameField(model = model)
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            LastNameField(model = model)
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            EmailField(value = model.email, setValue = {
                model.email = it
            }, caption = model.emailCaption, setCaption = {
                model.emailCaption = it
            }, context = this@LoginActivity, enabled = model.uiEnabled)

            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            PasswordField(
                model = model, done = {
                    model.login(
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope,
                        onLoggedIn = ::signInWithPassword
                    )
                }, imeAction = ImeAction.Next, nextFocusRequester = confirmPasswordFocusRequester
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            ConfirmPasswordField(
                model = model, confirmPasswordFocusRequester = confirmPasswordFocusRequester
            ) {
                if (model.agreedToToS) {
                    model.createUser(
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope,
                        onLoggedIn = ::signInWithPassword
                    )
                } else {
                    focusManager.clearFocus()
                }
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            ToSCheckbox(model = model)
            Button(
                onClick = {
                    model.createUser(
                        snackbarHostState = snackbarHostState,
                        scope = coroutineScope,
                        onLoggedIn = ::signInWithPassword
                    )
                }, enabled = model.uiEnabled, modifier = Modifier.requiredHeight(56.dp)
            ) {
                Box {
                    Text(
                        text = getString(R.string.create_user),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (!model.uiEnabled) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            TextButton(onClick = {
                model.login = LoginViewModel.State.LOGIN
                model.passwordCaption = ""
            }) {
                Text(text = getString(R.string.login))
            }
        }

    }

    @Composable
    fun LinkAccount(
        model: LoginViewModel,
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Text(
                text = getString(R.string.link_account_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = getString(R.string.link_account_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))
            PasswordField(
                model = model, done = {
                    signInWithGoogle(
                        snackbarHostState, coroutineScope, linkExisting = true
                    )
                }, imeAction = ImeAction.Done
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            OutlinedButton(onClick = {
                signInWithGoogle(
                    snackbarHostState, coroutineScope, linkExisting = true
                )
            }, enabled = model.uiEnabled) {
                Text(text = getString(R.string.sign_in_w_google))
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Screen(model: LoginViewModel) {
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                MaterialTheme.colorScheme.scrim.toArgb(),
                MaterialTheme.colorScheme.scrim.toArgb()
            )
        )

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            topBar = {
                if (model.login == LoginViewModel.State.CHANGE_PASSWORD || model.login == LoginViewModel.State.CHOOSE_USERNAME || model.login == LoginViewModel.State.LINK_ACCOUNT) {
                    TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ), title = {
                        Text(
                            getString(R.string.app_name)
                        )
                    }, navigationIcon = {
                        IconButton(onClick = {
                            onBackPressedDispatcher.onBackPressed()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, getString(
                                    R.string.back
                                ), tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }, modifier = Modifier.nestedScroll(
                        scrollBehavior.nestedScrollConnection
                    ), scrollBehavior = scrollBehavior
                    )
                } else {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        ), title = {
                            Text(
                                getString(R.string.app_name)
                            )
                        }, modifier = Modifier.nestedScroll(
                            scrollBehavior.nestedScrollConnection
                        ), scrollBehavior = scrollBehavior
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            AnimatedContent(
                targetState = model.login,
                transitionSpec = {
                    if (targetState == LoginViewModel.State.LOGIN) {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right
                        ) togetherWith slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right
                        )
                    } else {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left
                        ) togetherWith slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left
                        )
                    }
                },
                label = "animate swap between screens",
                modifier = Modifier
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
            ) { targetState ->
                when (targetState) {
                    LoginViewModel.State.LOGIN -> {
                        Login(
                            model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope
                        )
                    }

                    LoginViewModel.State.CREATE_USER -> {
                        CreateUser(
                            model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope
                        )
                    }

                    LoginViewModel.State.CHANGE_PASSWORD -> {
                        ChangePassword(
                            model = model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope
                        )
                    }

                    LoginViewModel.State.CHOOSE_USERNAME -> {
                        ChooseUsername(
                            model = model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope
                        )
                    }

                    LoginViewModel.State.LINK_ACCOUNT -> {
                        LinkAccount(
                            model = model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope
                        )
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun FirstNameField(model: LoginViewModel) {
        val focusManager = LocalFocusManager.current
        TextField(
            value = model.firstName,
            onValueChange = {
                model.firstName = filterSpecialChars(it)
            },
            modifier = Modifier
                .autofill(autofillTypes = listOf(AutofillType.PersonFirstName), onFill = {
                    model.firstName = it.filter { letter ->
                        letter != '\n'
                    }
                })
                .onKeyEvent {
                    if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KEYCODE_TAB) {
                        focusManager.moveFocus(focusDirection = FocusDirection.Next)
                        true
                    } else false
                },
            singleLine = true,
            label = { Text(text = getString(R.string.first_name)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = true,
                imeAction = ImeAction.Next
            ),
            enabled = model.uiEnabled
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun LastNameField(model: LoginViewModel) {
        val focusManager = LocalFocusManager.current
        TextField(
            value = model.lastName,
            onValueChange = {
                model.lastName = filterSpecialChars(it)
            },
            modifier = Modifier
                .autofill(autofillTypes = listOf(AutofillType.PersonLastName), onFill = {
                    model.lastName = it.filter { letter ->
                        letter != '\n'
                    }
                })
                .onKeyEvent {
                    if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KEYCODE_TAB) {
                        focusManager.moveFocus(focusDirection = FocusDirection.Next)
                        true
                    } else false
                },
            label = { Text(text = getString(R.string.last_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = true,
                imeAction = ImeAction.Next
            ),
            enabled = model.uiEnabled
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun OldPasswordField(model: LoginViewModel, passwordFocusRequester: FocusRequester) {
        val focusManager = LocalFocusManager.current
        TextField(
            value = model.oldPassword,
            onValueChange = {
                onOldPasswordChanged(model, it)
            },
            modifier = Modifier
                .autofill(autofillTypes = listOf(AutofillType.Password), onFill = {
                    onOldPasswordChanged(model, it)
                })
                .onKeyEvent {
                    if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KEYCODE_TAB) {
                        focusManager.moveFocus(focusDirection = FocusDirection.Next)
                        true
                    } else false
                },
            visualTransformation = if (model.passwordHidden) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                IconButton(onClick = { model.passwordHidden = !model.passwordHidden }) {
                    val visibilityIcon =
                        if (model.passwordHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (model.passwordHidden) getString(
                        R.string.show_password
                    ) else getString(
                        R.string.hide_password
                    )
                    Icon(imageVector = visibilityIcon, contentDescription = description)
                }
            },
            isError = model.passwordCaption.isNotBlank(),
            label = {
                Text(text = getString(R.string.password))
            },
            singleLine = true,
            supportingText = {
                if (model.passwordCaption.isNotBlank()) {
                    Text(
                        text = model.oldPasswordCaption, overflow = TextOverflow.Ellipsis
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = {
                passwordFocusRequester.requestFocus()
            }),
            enabled = model.uiEnabled
        )

    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun PasswordField(
        model: LoginViewModel,
        imeAction: ImeAction,
        done: (() -> Unit)? = null,
        nextFocusRequester: FocusRequester? = null,
        currentFocusRequester: FocusRequester? = null
    ) {

        TextField(
            value = model.password,
            onValueChange = {
                onPasswordChanged(model, it)
            },
            modifier = Modifier
                .focusRequester(currentFocusRequester ?: FocusRequester())
                .autofill(autofillTypes = listOf(AutofillType.NewPassword), onFill = {
                    onPasswordChanged(model, it)
                    onConfirmPasswordChanged(model, it)
                })
                .onKeyEvent {
                    if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KEYCODE_TAB) {
                        when (imeAction) {
                            ImeAction.Done -> {
                                done?.invoke()
                            }

                            ImeAction.Next -> {
                                nextFocusRequester?.requestFocus()
                            }
                        }
                        true
                    } else false
                },
            visualTransformation = if (model.passwordHidden) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                IconButton(onClick = { model.passwordHidden = !model.passwordHidden }) {
                    val visibilityIcon =
                        if (model.passwordHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (model.passwordHidden) getString(
                        R.string.show_password
                    ) else getString(
                        R.string.hide_password
                    )
                    Icon(imageVector = visibilityIcon, contentDescription = description)
                }
            },
            label = {
                Text(text = getString(R.string.password))
            },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = imeAction
            ),
            supportingText = {
                if (model.passwordCaption.isNotBlank()) {
                    Text(
                        text = model.passwordCaption, overflow = TextOverflow.Ellipsis
                    )
                }
            },
            keyboardActions = KeyboardActions(onDone = {
                if (imeAction == ImeAction.Done) {
                    done?.invoke()
                }
            }, onNext = {
                if (imeAction == ImeAction.Next) {
                    nextFocusRequester?.requestFocus()
                }
            }),
            enabled = model.uiEnabled,
            isError = model.passwordCaption.isNotBlank()
        )

    }

    @Composable
    fun ConfirmPasswordField(
        model: LoginViewModel,
        confirmPasswordFocusRequester: FocusRequester,
        onDone: () -> Unit = {}
    ) {
        TextField(
            value = model.confirmPassword,
            onValueChange = {
                onConfirmPasswordChanged(model, it)
            },
            visualTransformation = if (model.passwordHidden) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                if (model.confirmPassword.isNotEmpty()) {
                    lateinit var visibilityIcon: ImageVector
                    lateinit var description: String

                    if (model.confirmPassword == model.password) {
                        description = getString(R.string.passwords_match)
                        visibilityIcon = Icons.Filled.Check
                    } else {
                        description = getString(R.string.passwords_different)
                        visibilityIcon = Icons.Filled.Error
                    }

                    Icon(imageVector = visibilityIcon, contentDescription = description)
                }
            },
            label = {
                Text(text = getString(R.string.confirm_password))
            },
            modifier = Modifier
                .focusRequester(confirmPasswordFocusRequester)
                .onKeyEvent {
                    if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KEYCODE_TAB) {
                        onDone()
                        true
                    } else false
                },
            isError = model.confirmPasswordCaption.isNotBlank(),
            singleLine = true,
            supportingText = {

                if (model.confirmPasswordCaption.isNotBlank()) {
                    Text(
                        text = model.confirmPasswordCaption, overflow = TextOverflow.Ellipsis
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            enabled = model.uiEnabled
        )
    }

    @Composable
    fun ToSCheckbox(model: LoginViewModel) {
        Row {
            Checkbox(
                checked = model.agreedToToS, onCheckedChange = {
                    model.agreedToToS = !model.agreedToToS
                    model.checkboxError = false
                }, enabled = model.uiEnabled, colors = CheckboxDefaults.colors(
                    uncheckedColor = if (model.checkboxError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )

            val spannedString = SpannedString(getText(R.string.tos_agree))
            val resultBuilder = AnnotatedString.Builder()
            resultBuilder.append(spannedString.toString())
            spannedString.getSpans<Annotation>(0, spannedString.length).forEach { annotation ->
                val spanStart = spannedString.getSpanStart(annotation)
                val spanEnd = spannedString.getSpanEnd(annotation)
                resultBuilder.addLink(
                    url = LinkAnnotation.Url(
                        getString(R.string.tos_url), TextLinkStyles(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    ),
                    start = spanStart,
                    end = spanEnd
                )
            }

            val newText = resultBuilder.toAnnotatedString()
            Text(
                text = newText, style = TextStyle(
                    color = if (model.checkboxError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                ), modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }


    private fun signInWithPassword(username: String, password: String, token: String) {
        val createPasswordRequest =
            CreatePasswordRequest(id = username, password = password)

        // Create credential and handle result.
        MainScope().launch {
            try {
                credentialManager.createCredential(
                    // Use an activity based context to avoid undefined
                    // system UI launching behavior.
                    this@LoginActivity,
                    createPasswordRequest
                )
                finish()
            } catch (e: CreateCredentialException) {
                Log.e(TAG, "Unable to save password: $e")
                finish()
            }
        }
        completeSignIn(token)
    }

    private fun signInWithGoogle(
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope,
        linkExisting: Boolean = false
    ) {
        loginViewModel.uiEnabled = false
        val signInWithGoogleOption: GetSignInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(getString(R.string.client_id)).build()

        val getCredRequest = GetCredentialRequest(
            listOf(signInWithGoogleOption),
            preferImmediatelyAvailableCredentials = true
        )

        MainScope().launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = getCredRequest
                )
                handleSignIn(result, linkExisting)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Sign-in failed: $e")
                loginViewModel.uiEnabled = true
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = getString(
                            R.string.google_sign_in_failed
                        ), duration = SnackbarDuration.Short
                    )
                }
                Firebase.crashlytics.log(e.toMessage())
            }
        }
    }

    private fun completeSignIn(token: String) {
        val result = Intent()
        result.putExtra(MY_TOKEN, token)
        if (returnedIntent != null) {
            result.putExtra(Intent.EXTRA_INTENT, returnedIntent)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        val LIST_ELEMENT_PADDING = 10.dp
        val TAG: String = LoginActivity::class.java.simpleName

        @OptIn(ExperimentalComposeUiApi::class)
        fun Modifier.autofill(
            autofillTypes: List<AutofillType>,
            onFill: ((String) -> Unit),
        ) = composed {
            val autofill = LocalAutofill.current
            val autofillNode = AutofillNode(onFill = onFill, autofillTypes = autofillTypes)
            LocalAutofillTree.current += autofillNode

            onGloballyPositioned {
                autofillNode.boundingBox = it.boundsInWindow()
            }.onFocusChanged { focusState ->
                print(autofill)
                autofill?.run {
                    if (focusState.isFocused) {
                        requestAutofillForNode(autofillNode)
                    } else {
                        cancelAutofillForNode(autofillNode)
                    }
                }
            }
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable
        fun UsernameField(
            value: String,
            onValueChanged: (newValue: String) -> Unit,
            newUsername: Boolean,
            enabled: Boolean,
            error: Boolean,
            caption: String,
            context: Context,
            imeAction: ImeAction = ImeAction.Next,
            onDone: (() -> Unit)? = null,
            reserveCaptionSpace: Boolean = false
        ) {
            val focusManager = LocalFocusManager.current
            TextField(
                value = value,
                onValueChange = { onValueChanged(filterUsername(it)) },
                modifier = Modifier
                    .autofill(autofillTypes = if (newUsername) listOf(
                        AutofillType.NewUsername
                    ) else listOf(AutofillType.Username),
                        onFill = { onValueChanged(filterUsername(it)) })
                    .onKeyEvent {
                        if ((it.nativeKeyEvent.keyCode == KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KEYCODE_TAB) && imeAction == ImeAction.Next) {
                            focusManager.moveFocus(FocusDirection.Next)
                            true
                        } else if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER && imeAction == ImeAction.Done) {
                            onDone?.invoke()
                            true
                        } else false
                    },
                label = { Text(text = context.getString(R.string.username)) },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false, imeAction = imeAction
                ),
                supportingText = {

                    if (caption.isNotBlank() || reserveCaptionSpace) {
                        Text(
                            text = caption, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                enabled = enabled,
                isError = error,
            )
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable
        fun EmailField(
            value: String,
            setValue: (String) -> Unit,
            caption: String,
            setCaption: (String) -> Unit,
            enabled: Boolean,
            context: Context,
            imeAction: ImeAction = ImeAction.Next,
            onDone: (() -> Unit)? = null,
            reserveCaptionSpace: Boolean = false
        ) {
            val focusManager = LocalFocusManager.current
            TextField(
                value = value,
                onValueChange = {
                    setValue(it.filter { letter ->
                        letter != '\n'
                    })
                    setCaption("")
                },
                modifier = Modifier
                    .autofill(autofillTypes = listOf(AutofillType.EmailAddress), onFill = {
                        setValue(it.filter { letter ->
                            letter != '\n'
                        })
                        setCaption("")
                    })
                    .onKeyEvent {
                        if ((imeAction == ImeAction.Next && it.nativeKeyEvent.keyCode == KEYCODE_ENTER) || it.nativeKeyEvent.keyCode == KEYCODE_TAB) {
                            focusManager.moveFocus(focusDirection = FocusDirection.Next)
                            true
                        } else if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER) {
                            onDone?.invoke()
                            true
                        } else false
                    },
                isError = !(value.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(
                    value
                ).matches()) || caption.isNotBlank(),
                enabled = enabled,
                supportingText = {
                    if (caption.isNotBlank() || reserveCaptionSpace) {
                        Text(
                            text = caption, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                singleLine = true,
                label = { Text(text = context.getString(R.string.email)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    onDone?.invoke()
                })
            )

        }

        private fun onUsernameChanged(model: LoginViewModel, username: String) {
            model.username = username
            model.usernameCaption = ""
        }

        private fun onPasswordChanged(model: LoginViewModel, password: String) {
            model.password = password.filter { letter ->
                letter != '\n'
            }
            model.passwordCaption = ""
        }

        private fun onConfirmPasswordChanged(model: LoginViewModel, password: String) {
            model.confirmPassword = filterSpecialChars(password)
            model.confirmPasswordCaption = ""
        }

        private fun onOldPasswordChanged(model: LoginViewModel, password: String) {
            model.oldPassword = filterSpecialChars(password)
            model.oldPasswordCaption = ""
        }
    }
}
