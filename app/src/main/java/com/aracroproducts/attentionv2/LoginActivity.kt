package com.aracroproducts.attentionv2

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import kotlinx.coroutines.CoroutineScope
import kotlin.math.min
import kotlin.math.roundToInt

class LoginActivity : AppCompatActivity() {

    private val loginViewModel: LoginViewModel by viewModels(factoryProducer = {
        LoginViewModelFactory(AttentionRepository(AttentionDB.getDB(this)), application)
    })

    class LoginViewModelFactory(
            private val attentionRepository: AttentionRepository, private val
            application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(attentionRepository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == getString(R.string.change_password_action)) {
            loginViewModel.login = LoginViewModel.State.CHANGE_PASSWORD
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

    override fun onBackPressed() {
        if (loginViewModel.login != LoginViewModel.State.CHANGE_PASSWORD)
            moveTaskToBack(true)
        else {
            super.onBackPressed()
        }
    }

    @Composable
    fun Screen(model: LoginViewModel) {
        val scaffoldState = rememberScaffoldState()
        val coroutineScope = rememberCoroutineScope()


        Scaffold(
                topBar = {
                    if (model.login == LoginViewModel.State.CHANGE_PASSWORD) {
                        TopAppBar(
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                title = {
                                    Text(getString(R.string.app_name), color = MaterialTheme
                                            .colorScheme.onPrimary)
                                },
                                navigationIcon = {
                                    IconButton(onClick = this::onBackPressed) {
                                        Icon(Icons.Default.ArrowBack, getString(R.string
                                                .back),
                                                tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                        )
                    } else {
                        TopAppBar(
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                title = {
                                    Text(getString(R.string.app_name), color = MaterialTheme
                                            .colorScheme.onPrimary)
                                },
                        )
                    }
                },
                scaffoldState = scaffoldState,
                backgroundColor = MaterialTheme.colorScheme.background
        ) {
            when (model.login) {
                LoginViewModel.State.LOGIN -> {
                    Login(model, scaffoldState = scaffoldState, coroutineScope = coroutineScope, it)
                }
                LoginViewModel.State.CREATE_USER -> {
                    CreateUser(
                            model, scaffoldState = scaffoldState,
                            coroutineScope = coroutineScope, it
                    )
                }
                LoginViewModel.State.CHANGE_PASSWORD -> {
                    ChangePassword(
                            model = model, scaffoldState = scaffoldState,
                            coroutineScope = coroutineScope, it
                    )
                }
            }
        }
    }

    @Composable
    fun ChangePassword(
            model: LoginViewModel, scaffoldState: ScaffoldState, coroutineScope:
            CoroutineScope, paddingValues: PaddingValues
    ) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        val passwordFocusRequester = FocusRequester()
        val confirmPasswordFocusRequester = FocusRequester()
        Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
        Column(
                verticalArrangement = Arrangement.Center, modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                    value = model.oldPassword,
                    onValueChange = {
                        model.oldPassword = it.filter { letter ->
                            letter != '\n'
                        }
                        model.passwordCaption = ""
                    },
                    visualTransformation = if (passwordHidden)
                        PasswordVisualTransformation() else
                        VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = { passwordHidden = !passwordHidden }) {
                            val visibilityIcon =
                                    if (passwordHidden) Icons.Filled.Visibility else Icons
                                            .Filled.VisibilityOff
                            val description = if (passwordHidden)
                                getString(R.string.show_password) else
                                getString(R.string.hide_password)
                            Icon(imageVector = visibilityIcon, contentDescription = description)
                        }
                    },
                    isError = model.passwordCaption.isNotBlank(),
                    label = {
                        Text(text = getString(R.string.password))
                    },
                    singleLine = true,
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
            if (model.passwordCaption.isNotBlank()) {
                Text(
                        text = model.passwordCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.password,
                    onValueChange = {
                        model.password = it.filter { letter ->
                            letter != '\n'
                        }
                        model.newPasswordCaption = ""
                    },
                    visualTransformation = if (passwordHidden)
                        PasswordVisualTransformation() else
                        VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = { passwordHidden = !passwordHidden }) {
                            val visibilityIcon =
                                    if (passwordHidden) Icons.Filled.Visibility else Icons
                                            .Filled.VisibilityOff
                            val description = if (passwordHidden)
                                getString(R.string.show_password) else
                                getString(R.string.hide_password)
                            Icon(imageVector = visibilityIcon, contentDescription = description)
                        }
                    },
                    label = {
                        Text(text = getString(R.string.new_password))
                    },
                    modifier = Modifier.focusRequester(passwordFocusRequester),
                    isError = model.newPasswordCaption.isNotBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(onNext = {
                        confirmPasswordFocusRequester.requestFocus()
                    }),
                    enabled = model.uiEnabled
            )
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.confirmPassword,
                    onValueChange = {
                        model.confirmPassword = it.filter { letter ->
                            letter != '\n'
                        }
                        model.confirmPasswordCaption = ""
                    },
                    visualTransformation = if (passwordHidden)
                        PasswordVisualTransformation() else
                        VisualTransformation.None,
                    trailingIcon = {
                        lateinit var description: String
                        lateinit var visibilityIcon: ImageVector
                        if (model.confirmPassword == model.password) {
                            description = getString(R.string.passwords_match)
                            visibilityIcon = Icons.Filled.Check
                        } else {
                            description = getString(R.string.passwords_different)
                            visibilityIcon = Icons.Filled.Error
                        }

                        Icon(imageVector = visibilityIcon, contentDescription = description)
                    },
                    label = {
                        Text(text = getString(R.string.confirm_password))
                    },
                    isError = model.confirmPasswordCaption.isNotBlank(),
                    singleLine = true,
                    modifier = Modifier.focusRequester(confirmPasswordFocusRequester),
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(
                            onDone = {
                                model.changePassword(
                                        scaffoldState = scaffoldState,
                                        scope = coroutineScope
                                ) {
                                    finish()
                                }
                            }
                    ),
                    enabled = model.uiEnabled
            )
            if (model.confirmPasswordCaption.isNotBlank()) {
                Text(
                        text = model.confirmPasswordCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Button(
                    onClick = {
                        model.changePassword(
                                scaffoldState = scaffoldState,
                                scope = coroutineScope
                        ) {
                            finish()
                        }
                    },
                    enabled = model.uiEnabled,
                    modifier = Modifier.requiredHeight(56.dp)
            ) {
                Box {
                    Text(text = getString(R.string.change_password), modifier = Modifier.align
                    (Alignment.Center))
                    if (!model.uiEnabled) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    @Composable
    fun Login(model: LoginViewModel, scaffoldState: ScaffoldState, coroutineScope:
    CoroutineScope, paddingValues: PaddingValues) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        Column(
                verticalArrangement = centerWithBottomElement, horizontalAlignment = Alignment
                .CenterHorizontally, modifier =
        Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.username,
                    onValueChange = {
                        model.username = it.filter { letter ->
                            letter != '\n'
                        }
                        model.passwordCaption = ""
                    },
                    label = { Text(text = getString(R.string.username)) },
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false, imeAction = ImeAction
                            .Next
                    ),
                    enabled = model.uiEnabled,
                    isError = model.passwordCaption.isNotBlank(),
            )
            if (model.usernameCaption.isNotBlank()) {
                Text(
                        text = model.usernameCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.password,
                    onValueChange = {
                        model.password = it.filter { letter ->
                            letter != '\n'
                        }
                        model.passwordCaption = ""
                    },
                    visualTransformation = if (passwordHidden)
                        PasswordVisualTransformation() else
                        VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = { passwordHidden = !passwordHidden }) {
                            val visibilityIcon =
                                    if (passwordHidden) Icons.Filled.Visibility else Icons
                                            .Filled.VisibilityOff
                            val description = if (passwordHidden)
                                getString(R.string.show_password) else
                                getString(R.string.hide_password)
                            Icon(imageVector = visibilityIcon, contentDescription = description)
                        }
                    },
                    label = {
                        Text(text = getString(R.string.password))
                    },
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                            onDone = {
                                model.login(
                                        scaffoldState = scaffoldState,
                                        scope = coroutineScope
                                ) { finish() }
                            }
                    ),
                    enabled = model.uiEnabled,
                    isError = model.passwordCaption.isNotBlank()
            )
            if (model.passwordCaption.isNotBlank()) {
                Text(
                        text = model.passwordCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Button(
                    onClick = {
                        model.login(
                                scaffoldState = scaffoldState,
                                scope = coroutineScope
                        ) { finish() }
                    },
                    enabled = model.uiEnabled,
                    modifier = Modifier.requiredHeight(56.dp)
            ) {
                Box {
                    Text(text = getString(R.string.login), modifier = Modifier.align
                    (Alignment.Center))
                    if (!model.uiEnabled) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            TextButton(onClick = { model.login = LoginViewModel.State.CREATE_USER }) {
                Text(text = getString(R.string.create_user))
            }
        }

    }

    @Composable
    fun CreateUser(
            model: LoginViewModel, scaffoldState: ScaffoldState,
            coroutineScope: CoroutineScope, paddingValues: PaddingValues
    ) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        val confirmPasswordFocusRequester = FocusRequester()
        Column(
                verticalArrangement = centerWithBottomElement, modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    // use model is error - reset on change
                    value = model.username,
                    onValueChange = { value ->
                        model.username = value.substring(0, min(value.length, 150)).filter {
                            it.isLetterOrDigit() or (it == '@') or (it == '_') or (it == '-') or (it ==
                                    '+') or (it == '.')
                        }
                        model.usernameCaption = ""
                    },
                    isError = model.usernameCaption.isNotBlank(),
                    singleLine = true,
                    label = { Text(text = getString(R.string.username)) },
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Next
                    ),
                    enabled = model.uiEnabled
            )
            if (model.usernameCaption.isNotBlank()) {
                Text(
                        text = model.usernameCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.firstName,
                    onValueChange = {
                        model.firstName = it.filter { letter ->
                            letter != '\n'
                        }
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
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.lastName,
                    onValueChange = {
                        model.lastName = it.filter { letter ->
                            letter != '\n'
                        }
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
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.email,
                    onValueChange = {
                        model.email = it.filter { letter ->
                            letter != '\n'
                        }
                        model.emailCaption = ""
                    },
                    isError = !(model.email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS
                            .matcher(model.email)
                            .matches()),
                    singleLine = true,
                    label = { Text(text = getString(R.string.email)) },
                    keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                    )
            )
            if (model.emailCaption.isNotBlank()) {
                Text(
                        text = model.emailCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.password,
                    onValueChange = {
                        model.password = it.filter { letter ->
                            letter != '\n'
                        }
                        model.passwordCaption = ""
                    },
                    visualTransformation = if (passwordHidden)
                        PasswordVisualTransformation() else
                        VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = { passwordHidden = !passwordHidden }) {
                            val visibilityIcon =
                                    if (passwordHidden) Icons.Filled.Visibility else Icons
                                            .Filled.VisibilityOff
                            val description = if (passwordHidden)
                                getString(R.string.show_password) else
                                getString(R.string.hide_password)
                            Icon(imageVector = visibilityIcon, contentDescription = description)
                        }
                    },
                    isError = model.passwordCaption.isNotBlank(),
                    label = {
                        Text(text = getString(R.string.password))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(onNext = {
                        confirmPasswordFocusRequester.requestFocus()
                    }),
                    enabled = model.uiEnabled
            )
            if (model.passwordCaption.isNotBlank()) {
                Text(
                        text = model.passwordCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            TextField(
                    value = model.confirmPassword,
                    onValueChange = {
                        model.confirmPassword = it.filter { letter ->
                            letter != '\n'
                        }
                        model.confirmPasswordCaption = ""
                    },
                    visualTransformation = if (passwordHidden)
                        PasswordVisualTransformation() else
                        VisualTransformation.None,
                    trailingIcon = {
                        lateinit var description: String
                        lateinit var visibilityIcon: ImageVector
                        if (model.confirmPassword == model.password) {
                            description = getString(R.string.passwords_match)
                            visibilityIcon = Icons.Filled.Check
                        } else {
                            description = getString(R.string.passwords_different)
                            visibilityIcon = Icons.Filled.Error
                        }

                        Icon(imageVector = visibilityIcon, contentDescription = description)
                    },
                    label = {
                        Text(text = getString(R.string.confirm_password))
                    },
                    modifier = Modifier.focusRequester(confirmPasswordFocusRequester),
                    isError = model.confirmPasswordCaption.isNotBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(
                            onDone = {
                                model.createUser(
                                        scaffoldState = scaffoldState,
                                        scope = coroutineScope
                                ) {
                                    Log.d(javaClass.name, "Logged in!")
                                    finish()
                                }
                            }
                    ),
                    enabled = model.uiEnabled
            )
            if (model.confirmPasswordCaption.isNotBlank()) {
                Text(
                        text = model.confirmPasswordCaption,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(LIST_ELEMENT_PADDING))
            Button(
                    onClick = {
                        model.createUser(
                                scaffoldState = scaffoldState,
                                scope = coroutineScope
                        ) {
                            Log.d(javaClass.name, "Logged in!")
                            finish()
                        }
                    },
                    enabled = model.uiEnabled,
                    modifier = Modifier.requiredHeight(56.dp)
            ) {
                Box {
                    Text(text = getString(R.string.create_user), modifier = Modifier.align
                    (Alignment.Center))
                    if (!model.uiEnabled) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            TextButton(onClick = { model.login = LoginViewModel.State.LOGIN }) {
                Text(text = getString(R.string.login))
            }
        }

    }

    private val centerWithBottomElement = object : Arrangement.HorizontalOrVertical {
        override fun Density.arrange(
                totalSize: Int, sizes: IntArray,
                layoutDirection: LayoutDirection, outPositions: IntArray
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

    companion object {
        val LIST_ELEMENT_PADDING = 10.dp
    }
}
