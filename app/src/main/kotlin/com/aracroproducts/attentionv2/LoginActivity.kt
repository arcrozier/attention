package com.aracroproducts.attentionv2

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Annotation
import android.text.SpannedString
import android.util.Log
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_TAB
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.getSpans
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// TODO support passkeys using the CredentialManager API
class LoginActivity : AppCompatActivity() {

    private val loginViewModel: LoginViewModel by viewModels(factoryProducer = {
        LoginViewModelFactory(
            AttentionRepository(AttentionDB.getDB(this)),
            (application as AttentionApplication).container.settingsRepository,
            application
        )
    })

    private lateinit var oneTapClient: SignInClient

    private val passwordSaveResultHandler = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        if (!(result.resultCode == RESULT_OK && result.resultCode == RESULT_CANCELED)) {
            Log.e(
                TAG,
                "Unexpected result code from password saving: Got ${result.resultCode}, " + "expected $RESULT_OK or $RESULT_CANCELED"
            )
        }
        finish()
    }

    private val loginResultHandler = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        loginViewModel.uiEnabled = true // handle intent result here
        val credential: SignInCredential?
        try {
            credential = oneTapClient.getSignInCredentialFromIntent(result.data)
            val idToken = credential.googleIdToken
            val username = credential.id
            val password = credential.password
            if (idToken != null) { // Got an ID token from Google. Use it to authenticate
                // with your backend.
                loginViewModel.loginWithGoogle(null, null, idToken) { token ->
                    completeSignIn(token)
                    finish()
                }
                Log.d(TAG, "Got ID token.")
            } else if (password != null) { // Got a saved username and password. Use them to authenticate
                // with your backend.
                loginViewModel.username = username
                loginViewModel.password = password
                loginViewModel.login(null, null, ::signInWithPassword)
                Log.d(TAG, "Got password.")
            }
        } catch (e: ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> {
                    Log.d(TAG, "One-tap dialog was closed.") // Don't re-prompt the user.
                    loginViewModel.showOneTapUI = false
                }
                CommonStatusCodes.NETWORK_ERROR -> {
                    Log.d(TAG, "One-tap encountered a network error.") // Try again or just ignore.
                }
                else -> {
                    Log.d(
                        TAG, "Couldn't get credential from result." + " (${e.localizedMessage})"
                    )
                    e.printStackTrace()
                }
            }
        }
    }

    private val linkResultHandler = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        loginViewModel.uiEnabled = true // handle intent result here
        val credential: SignInCredential?
        try {
            credential = Identity.getSignInClient(this).getSignInCredentialFromIntent(result.data)
            val idToken = credential.googleIdToken
            if (idToken != null) { // Got an ID token from Google. Use it to authenticate
                // with your backend.
                Log.d(TAG, "Got ID token.")
                loginViewModel.linkAccount(snackbarHostState = null,
                                           coroutineScope = null,
                                           idToken = idToken,
                                           onLoggedIn = {
                                               finish()
                                           })
            } else {
                loginViewModel.passwordCaption = "Error: didn't receive credential"
            }
        } catch (e: ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> {
                    Log.d(TAG, "One-tap dialog was closed.") // Don't re-prompt the user.
                    loginViewModel.showOneTapUI = false
                }
                CommonStatusCodes.NETWORK_ERROR -> {
                    Log.d(TAG, "One-tap encountered a network error.") // Try again or just ignore.
                    Toast.makeText(this, R.string.connection_error, Toast.LENGTH_LONG).show()
                }
                else -> {
                    Log.d(
                        TAG, "Couldn't get credential from result." + " (${e.localizedMessage})"
                    )
                    e.printStackTrace()
                    loginViewModel.passwordCaption =
                        "Unable to sign in with Google; an " + "unexpected error occurred"
                }
            }
        }
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

        oneTapClient = Identity.getSignInClient(this)
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

        if (intent.action == getString(R.string.change_password_action)) {
            loginViewModel.login = LoginViewModel.State.CHANGE_PASSWORD
        } else if (intent.action == getString(R.string.link_account_action)) {
            loginViewModel.login = LoginViewModel.State.LINK_ACCOUNT
        } else if (loginViewModel.showOneTapUI) {
            val signInRequest = BeginSignInRequest.builder().setPasswordRequestOptions(
                BeginSignInRequest.PasswordRequestOptions.builder().setSupported(true).build()
            ).setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder().setSupported(
                    true
                ) // Your server's client ID, not your Android client ID.
                    .setServerClientId(
                        getString(
                            R.string.client_id
                        )
                    ) // Only show accounts previously used to sign in.
                    .setFilterByAuthorizedAccounts(true).build()
            ) // Automatically sign in when exactly one credential is retrieved.
                .setAutoSelectEnabled(true).build()
            oneTapClient.beginSignIn(signInRequest).addOnSuccessListener(this) { result ->
                try {
                    loginResultHandler.launch(
                        IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    )
                } catch (e: SendIntentException) {
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }.addOnFailureListener(
                this
            ) { e -> // No Google Accounts found. Try to create an account
                e.localizedMessage?.let { Log.d(TAG, it) }
                val signUpRequest = BeginSignInRequest.builder().setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder().setSupported(
                        true
                    ) // Your server's client ID, not your Android client ID.
                        .setServerClientId(
                            getString(
                                R.string.client_id
                            )
                        ) // Show all accounts on the device.
                        .setFilterByAuthorizedAccounts(false).build()
                ).build()

                oneTapClient.beginSignIn(signUpRequest).addOnSuccessListener(this) { result ->
                    try {
                        loginResultHandler.launch(
                            IntentSenderRequest.Builder(
                                result.pendingIntent.intentSender
                            ).build()
                        )
                    } catch (e: SendIntentException) {
                        Log.e(
                            TAG, "Couldn't start One Tap UI: ${e.localizedMessage}"
                        )
                    }
                }.addOnFailureListener(
                    this
                ) { e1 -> // No Google Accounts found. Just continue presenting the signed-out UI.
                    e1.localizedMessage?.let { Log.d(TAG, it) }
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
        coroutineScope: CoroutineScope,
        paddingValues: PaddingValues
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(paddingValues)
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
        coroutineScope: CoroutineScope,
        paddingValues: PaddingValues
    ) {
        val passwordFocusRequester = FocusRequester()
        val confirmPasswordFocusRequester = FocusRequester()
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(paddingValues)
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
        coroutineScope: CoroutineScope,
        paddingValues: PaddingValues
    ) {
        Column(
            verticalArrangement = centerWithBottomElement,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(paddingValues)
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
            Divider(
                color = MaterialTheme.colorScheme.outline.copy(
                    alpha = ContentAlpha.disabled
                ), modifier = Modifier.fillMaxWidth(0.75f)
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING * 2))

            OutlinedButton(onClick = {
                signInWithGoogle(
                    snackbarHostState, coroutineScope, loginResultHandler
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
        coroutineScope: CoroutineScope,
        paddingValues: PaddingValues
    ) {
        val confirmPasswordFocusRequester = FocusRequester()
        val focusManager = LocalFocusManager.current
        Column(
            verticalArrangement = centerWithBottomElement,
            modifier = Modifier
                .padding(paddingValues)
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
        coroutineScope: CoroutineScope,
        paddingValues: PaddingValues
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(paddingValues)
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
                        snackbarHostState, coroutineScope, linkResultHandler
                    )
                }, imeAction = ImeAction.Done
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            OutlinedButton(onClick = {
                signInWithGoogle(
                    snackbarHostState, coroutineScope, linkResultHandler
                )
            }, enabled = model.uiEnabled) {
                Text(text = getString(R.string.sign_in_w_google))
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun Screen(model: LoginViewModel) {
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        // Remember a SystemUiController
        val systemUiController = rememberSystemUiController()
        val useDarkIcons = !isSystemInDarkTheme()

        DisposableEffect(
            systemUiController, useDarkIcons
        ) { // Update all of the system bar colors to be transparent, and use
            // dark icons if we're in light theme
            systemUiController.setNavigationBarColor(
                color = Color.Transparent, darkIcons = useDarkIcons
            )

            // setStatusBarColor() and setNavigationBarColor() also exist

            onDispose {}
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(topBar = {
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
                            Icons.Default.ArrowBack, getString(
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
        ) {
            AnimatedContent(targetState = model.login, transitionSpec = {
                if (targetState == LoginViewModel.State.LOGIN) {
                    slideIntoContainer(
                        towards = AnimatedContentScope.SlideDirection.Right
                    ) with slideOutOfContainer(
                        towards = AnimatedContentScope.SlideDirection.Right
                    )
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentScope.SlideDirection.Left
                    ) with slideOutOfContainer(
                        towards = AnimatedContentScope.SlideDirection.Left
                    )
                }
            }) { targetState ->
                when (targetState) {
                    LoginViewModel.State.LOGIN -> {
                        Login(
                            model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope,
                            it
                        )
                    }
                    LoginViewModel.State.CREATE_USER -> {
                        CreateUser(
                            model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope,
                            it
                        )
                    }
                    LoginViewModel.State.CHANGE_PASSWORD -> {
                        ChangePassword(
                            model = model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope,
                            it
                        )
                    }
                    LoginViewModel.State.CHOOSE_USERNAME -> {
                        ChooseUsername(
                            model = model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope,
                            paddingValues = it
                        )
                    }
                    LoginViewModel.State.LINK_ACCOUNT -> {
                        LinkAccount(
                            model = model,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = coroutineScope,
                            paddingValues = it
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
        TextField(value = model.firstName,
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
                      autoCorrect = true,
                      imeAction = ImeAction.Next,
                      capitalization = KeyboardCapitalization.Words
                  ),
                  enabled = model.uiEnabled
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun LastNameField(model: LoginViewModel) {
        val focusManager = LocalFocusManager.current
        TextField(value = model.lastName,
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
                      autoCorrect = true,
                      imeAction = ImeAction.Next,
                      capitalization = KeyboardCapitalization.Words
                  ),
                  enabled = model.uiEnabled
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun OldPasswordField(model: LoginViewModel, passwordFocusRequester: FocusRequester) {
        val focusManager = LocalFocusManager.current
        TextField(value = model.oldPassword,
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
                      autoCorrect = false,
                      imeAction = ImeAction.Next,
                      keyboardType = KeyboardType.Password
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

        TextField(value = model.password,
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
                      autoCorrect = false,
                      imeAction = imeAction,
                      keyboardType = KeyboardType.Password
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
                autoCorrect = false,
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password
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
                resultBuilder.addStringAnnotation(
                    tag = annotation.key,
                    annotation = annotation.value,
                    start = spanStart,
                    end = spanEnd
                )
                if (annotation.key == "url") {
                    resultBuilder.addStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary
                        ), spanStart, spanEnd
                    )
                }
            }

            val newText = resultBuilder.toAnnotatedString()
            ClickableText(text = newText, style = TextStyle(
                color = if (model.checkboxError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            ), modifier = Modifier.align(Alignment.CenterVertically), onClick = { offset ->
                val annotation = newText.getStringAnnotations(
                    tag = "url", start = offset, end = offset
                ).firstOrNull()
                if (annotation != null && annotation.item == "tos") {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW, Uri.parse(getString(R.string.tos_url))
                    )
                    startActivity(browserIntent)
                } else {
                    model.agreedToToS = !model.agreedToToS
                }
            })
        }
    }

    private val centerWithBottomElement = object : Arrangement.HorizontalOrVertical {
        override fun Density.arrange(
            totalSize: Int,
            sizes: IntArray,
            layoutDirection: LayoutDirection,
            outPositions: IntArray
        ) {
            val consumedSize = sizes.fold(0) { a, b -> a + b }
            var current = (totalSize - consumedSize).toFloat() / 2
            sizes.forEachIndexed { index, size ->
                if (index == sizes.lastIndex) {
                    outPositions[index] =
                        if (layoutDirection == LayoutDirection.Ltr) totalSize - size
                        else size
                } else {
                    outPositions[index] =
                        if (layoutDirection == LayoutDirection.Ltr) current.roundToInt()
                        else totalSize - current.roundToInt()
                    current += size.toFloat()
                }
            }
        }

        override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
            arrange(totalSize, sizes, LayoutDirection.Ltr, outPositions)
        }
    }


    private fun signInWithPassword(username: String, password: String, token: String) {
        val signInPassword = SignInPassword(username, password)
        val savePasswordRequest =
            SavePasswordRequest.builder().setSignInPassword(signInPassword).build()
        Identity.getCredentialSavingClient(this).savePassword(savePasswordRequest)
            .addOnSuccessListener { result ->
                passwordSaveResultHandler.launch(
                    IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                )
            }.addOnFailureListener { finish() }.addOnCanceledListener { finish() }
        completeSignIn(token)
    }

    private fun signInWithGoogle(
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope,
        resultHandler: ActivityResultLauncher<IntentSenderRequest>
    ) {
        loginViewModel.uiEnabled = false
        val request =
            GetSignInIntentRequest.builder().setServerClientId(getString(R.string.client_id))
                .build()
        Identity.getSignInClient(this).getSignInIntent(request)
            .addOnSuccessListener { result: PendingIntent ->
                try {
                    resultHandler.launch(
                        IntentSenderRequest.Builder(result.intentSender).build()
                    )
                } catch (e: SendIntentException) {
                    Log.e(TAG, "Google Sign-in failed")
                    loginViewModel.uiEnabled = true
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = getString(
                                R.string.google_sign_in_failed
                            ), duration = SnackbarDuration.Short
                        )
                    }
                }
            }.addOnFailureListener { e: Exception? ->
                Log.e(TAG, "Google Sign-in failed", e)
                loginViewModel.uiEnabled = true
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = getString(
                            R.string.google_sign_in_failed
                        ), duration = SnackbarDuration.Short
                    )
                }
            }
    }

    private fun completeSignIn(token: String) {
        loginViewModel.setTokenUploaded(false)
        val result = Intent()
        result.putExtra(MainViewModel.MY_TOKEN, token)
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        val LIST_ELEMENT_PADDING = 10.dp
        val TAG: String = LoginActivity::class.java.name

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
                    autoCorrect = false, imeAction = imeAction
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
            TextField(value = value,
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
