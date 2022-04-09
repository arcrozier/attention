package com.aracroproducts.attentionv2

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

class LoginActivity : AppCompatActivity() {

    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        if (intent.action == getString(R.string.change_password_action)) {
            loginViewModel.login = LoginViewModel.State.CHANGE_PASSWORD
        }

        setContent {
            MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors else lightColors) {
                Screen(model = loginViewModel)
            }
        }
    }

    @Composable
    fun Screen(model: LoginViewModel) {
        val scaffoldState = rememberScaffoldState()
        val coroutineScope = rememberCoroutineScope()

        Scaffold(
                topBar = {
                    TopAppBar(
                            backgroundColor = MaterialTheme.colors.primary,
                            title = { Text(getString(R.string.app_name)) },
                    )
                },
        ) {
            when (model.login) {
                LoginViewModel.State.LOGIN -> {
                    Login(model, scaffoldState = scaffoldState, coroutineScope = coroutineScope)
                }
                LoginViewModel.State.CREATE_USER -> {
                    CreateUser(model, scaffoldState = scaffoldState,
                            coroutineScope = coroutineScope)
                }
                LoginViewModel.State.CHANGE_PASSWORD -> {
                    ChangePassword(model = model, scaffoldState = scaffoldState,
                            coroutineScope = coroutineScope)
                }
            }
        }
    }

    @Composable
    fun ChangePassword(model: LoginViewModel, scaffoldState: ScaffoldState, coroutineScope:
    CoroutineScope) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        Column(verticalArrangement = Arrangement.Center) {

            TextField(
                    value = model.oldPassword,
                    onValueChange = {
                        model.oldPassword = it
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
                    keyboardOptions = KeyboardOptions(autoCorrect = false,
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Password
                    ),
                    enabled = model.uiEnabled
            )
            if (model.passwordCaption.isNotBlank()) {
                Text(
                        text = model.passwordCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            TextField(
                    value = model.password,
                    onValueChange = {
                        model.password = it
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
                    isError = model.newPasswordCaption.isNotBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false,
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Password
                    ),
                    enabled = model.uiEnabled
            )
            TextField(
                    value = model.confirmPassword,
                    onValueChange = {
                        model.confirmPassword = it
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
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(
                            onDone = {
                                model.changePassword(scaffoldState = scaffoldState,
                                        scope = coroutineScope) {
                                    finish()
                                }
                            }
                    ),
                    enabled = model.uiEnabled
            )
            if (model.confirmPasswordCaption.isNotBlank()) {
                Text(
                        text = model.confirmPasswordCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Button(
                    onClick = {
                        model.changePassword(scaffoldState = scaffoldState,
                                scope = coroutineScope) {
                            finish()
                        }
                    },
                    enabled = model.uiEnabled,

                    ) {
                Text(text = getString(R.string.change_password))
                if (!model.uiEnabled) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Composable
    fun Login(model: LoginViewModel, scaffoldState: ScaffoldState, coroutineScope: CoroutineScope) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        Column(verticalArrangement = Arrangement.Center) {
            TextField(
                    value = model.username,
                    onValueChange = {
                        model.username = it
                        model.passwordCaption = ""
                    },
                    label = { Text(text = getString(R.string.username)) },
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction
                            .Next),
                    enabled = model.uiEnabled,
                    isError = model.passwordCaption.isNotBlank(),
            )
            if (model.usernameCaption.isNotBlank()) {
                Text(
                        text = model.usernameCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            TextField(
                    value = model.password,
                    onValueChange = {
                        model.password = it
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
                            imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                            onDone = {
                                model.login(scaffoldState = scaffoldState,
                                        scope = coroutineScope) { finish() }
                            }
                    ),
                    enabled = model.uiEnabled,
                    isError = model.passwordCaption.isNotBlank()
            )
            if (model.passwordCaption.isNotBlank()) {
                Text(
                        text = model.passwordCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Button(
                    onClick = {
                        model.login(scaffoldState = scaffoldState,
                                scope = coroutineScope) { finish() }
                    },
                    enabled = model.uiEnabled,
            ) {
                Text(text = getString(R.string.login))
                if (!model.uiEnabled) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Composable
    fun CreateUser(model: LoginViewModel, scaffoldState: ScaffoldState,
                   coroutineScope: CoroutineScope) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        Column(verticalArrangement = Arrangement.Center) {
            TextField(
                    // use model is error - reset on change
                    value = model.username,
                    onValueChange = { value ->
                        model.username = value.substring(0, 150).filter {
                            it.isLetterOrDigit() or (it == '@') or (it == '_') or (it == '-') or (it ==
                                    '+') or (it == '.')
                        }
                        model.usernameCaption = ""
                    },
                    isError = model.usernameCaption.isNotBlank(),
                    singleLine = true,
                    label = { Text(text = getString(R.string.username)) },
                    keyboardOptions = KeyboardOptions(autoCorrect = false,
                            imeAction = ImeAction.Next),
                    enabled = model.uiEnabled
            )
            if (model.usernameCaption.isNotBlank()) {
                Text(
                        text = model.usernameCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            TextField(
                    value = model.firstName,
                    onValueChange = { model.firstName = it },
                    singleLine = true,
                    label = { Text(text = getString(R.string.first_name)) },
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = true,
                            imeAction = ImeAction.Next,
                            capitalization = KeyboardCapitalization.Words),
                    enabled = model.uiEnabled
            )
            TextField(
                    value = model.lastName,
                    onValueChange = { model.lastName = it },
                    label = { Text(text = getString(R.string.last_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = true,
                            imeAction = ImeAction.Next,
                            capitalization = KeyboardCapitalization.Words),
                    enabled = model.uiEnabled
            )
            TextField(
                    value = model.email,
                    onValueChange = {
                        model.email = it
                        model.emailCaption = ""
                    },
                    isError = !android.util.Patterns.EMAIL_ADDRESS.matcher(model.email).matches(),
                    singleLine = true,
                    label = { Text(text = getString(R.string.email)) },
                    keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                    )
            )
            if (model.emailCaption.isNotBlank()) {
                Text(
                        text = model.emailCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            TextField(
                    value = model.password,
                    onValueChange = {
                        model.password = it
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
                    keyboardOptions = KeyboardOptions(autoCorrect = false,
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Password
                    ),
                    enabled = model.uiEnabled
            )
            if (model.passwordCaption.isNotBlank()) {
                Text(
                        text = model.passwordCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            TextField(
                    value = model.confirmPassword,
                    onValueChange = {
                        model.confirmPassword = it
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
                    keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password
                    ),
                    keyboardActions = KeyboardActions(
                            onDone = {
                                model.createUser(scaffoldState = scaffoldState,
                                        scope = coroutineScope) {
                                    finish()
                                }
                            }
                    ),
                    enabled = model.uiEnabled
            )
            if (model.confirmPasswordCaption.isNotBlank()) {
                Text(
                        text = model.confirmPasswordCaption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
            Button(
                    onClick = {
                        model.createUser(scaffoldState = scaffoldState,
                                scope = coroutineScope) { finish() }
                    },
                    enabled = model.uiEnabled,

                    ) {
                Text(text = getString(R.string.create_user))
                if (!model.uiEnabled) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
