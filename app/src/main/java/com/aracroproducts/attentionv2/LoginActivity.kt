package com.aracroproducts.attentionv2

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

class LoginActivity : AppCompatActivity() {

    val loginViewModel : LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)


    }

    @Composable
    fun Screen(model: LoginViewModel) {
        if (model.login) {
            Login(model)
        } else {
            CreateUser(model)
        }
    }

    @Composable
    fun Login(model: LoginViewModel) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        Column(verticalArrangement = Arrangement.Center) {
            TextField(
                    value = model.username,
                    onValueChange = { model.username = it },
                    label = {Text(text = getString(R.string.username))})
            TextField(
                    value = model.password,
                    onValueChange = {model.password = it},
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
                    }
            )
        }
    }

    @Composable
    fun CreateUser(model: LoginViewModel) {
        var passwordHidden by remember {
            mutableStateOf(true)
        }
        Column(verticalArrangement = Arrangement.Center) {
            TextField(value = model.username, onValueChange = { value ->
                model.username = value.substring(0, 150).filter {
                    it.isLetterOrDigit() or (it == '@') or (it == '_') or (it == '-') or (it ==
                            '+') or (it == '.')
                }
            })
            TextField(
                    value = model.password,
                    onValueChange = {model.password = it},
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
                    }
            )
            TextField(
                    value = model.confirmPassword,
                    onValueChange = { model.confirmPassword = it},
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
                        Text(text = getString(R.string.password))
                    })
        }
    }
}