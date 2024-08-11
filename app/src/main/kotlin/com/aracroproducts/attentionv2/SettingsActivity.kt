package com.aracroproducts.attentionv2

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.waterfallPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aracroproducts.attentionv2.LoginActivity.Companion.LIST_ELEMENT_PADDING
import com.aracroproducts.attentionv2.LoginActivity.Companion.UsernameField
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.gms.auth.api.identity.Identity
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.io.File
import java.lang.Integer.max

/**
 * The class for the settings menu in the app
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels(factoryProducer = {
        val attentionApplication = application as AttentionApplication
        SettingsViewModelFactory(
            attentionApplication.container.repository,
            attentionApplication.container.settingsRepository,
            attentionApplication.container.applicationScope,
            application
        )
    })


    private val cropResultHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            val uri = UCrop.getOutput(data) ?: return@let
            assert(uri == Uri.fromFile(File(filesDir, TEMP_PFP)))
            viewModel.uploadImage(uri, this)
        }
    }

    // Registers a photo picker activity launcher in single-select mode.
    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) { // crop then upload https://github.com/Yalantis/uCrop
            // https://stackoverflow.com/questions/3879992/how-to-get-bitmap-from-an-uri
            val cropIntent =
                UCrop.of(uri, Uri.fromFile(File(filesDir, TEMP_PFP))).withAspectRatio(1f, 1f)
                    .withOptions(UCrop.Options().apply {
                        setCircleDimmedLayer(true)
                        setCompressionFormat(Bitmap.CompressFormat.PNG)
                    }).getIntent(this)
            cropResultHandler.launch(cropIntent)
            Log.d("PhotoPicker", "Selected URI: $uri")
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    class SettingsViewModelFactory(
        private val attentionRepository: AttentionRepository,
        private val preferencesRepository: PreferencesRepository,
        private val externalScope: CoroutineScope,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    attentionRepository, preferencesRepository, externalScope, application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) {
            viewModel.writeToDatastore(
                booleanPreferencesKey(
                    getString(R.string.override_dnd_key)
                ), false
            )
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HarmonizedTheme {
                    PreferenceScreenWrapper()
                }
            } else {
                AppTheme {
                    PreferenceScreenWrapper()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    fun PreferenceScreenWrapper() {
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(viewModel.currentSnackBar) {
            val snackBar = viewModel.currentSnackBar
            if (snackBar != null) {
                snackbarHostState.showSnackbar(
                    message = snackBar.message,
                    actionLabel = snackBar.actionLabel,
                    withDismissAction = snackBar.withDismissAction,
                    duration = snackBar.duration
                )
                viewModel.currentSnackBar = null
            }
        }

        if (viewModel.uploadDialog) {
            UploadDialog(
                uploading = viewModel.uploading,
                uploadStatus = viewModel.uploadStatus,
                shouldRetry = viewModel.shouldRetryUpload,
                uploadSuccess = viewModel.uploadSuccess,
                uploadSuccessCallback = { success -> viewModel.uploadSuccess = success },
                uploadProgress = viewModel.uploadProgress,
                onCancel = viewModel.onCancel,
                dismissDialog = { viewModel.uploadDialog = false },
                retry = viewModel::uploadImage,
                uri = viewModel.uri
            )
        }

        val userInfoChangeListener = viewModel.UserInfoChangeListener(this, viewModel)
        val preferences =
            listOf(Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>(Pair(R.string.account) @Composable {
                Icon(Icons.Outlined.ManageAccounts, null)
            }) @Composable {
                SplitPreference(largePreference = {
                    var usernameValue by rememberPreference(
                        key = stringPreferencesKey(
                            getString(
                                R.string.username_key
                            )
                        ), defaultValue = "", repository = viewModel.preferencesRepository
                    )

                    DialoguePreference(value = usernameValue,
                                       setValue = { newValue ->
                                           usernameValue = newValue
                                       },
                                       title = R.string.username,
                                       dialog = { value, setValue, dismissDialog, context, title ->
                                           var newValue by remember { mutableStateOf(value) }
                                           var loading by remember { mutableStateOf(false) }
                                           var error by remember { mutableStateOf(false) }
                                           var usernameCaption by remember { mutableStateOf("") }

                                           fun done() {
                                               if (newValue.isBlank()) {
                                                   error = true
                                                   usernameCaption =
                                                       context.getString(R.string.empty_username)
                                                   return
                                               }
                                               loading = true
                                               viewModel.changeUsername(username = newValue,
                                                                        setUsername = {
                                                                            it?.let {
                                                                                setValue(it)
                                                                            }
                                                                        },
                                                                        setCaption = {
                                                                            usernameCaption = it
                                                                        },
                                                                        setStatus = { e, l ->
                                                                            error = e
                                                                            loading = l
                                                                        },
                                                                        dismissDialog = dismissDialog,
                                                                        context = this
                                               )
                                           }

                                           AlertDialog(onDismissRequest = { if (!loading) dismissDialog() },
                                                       dismissButton = {
                                                           OutlinedButton(onClick = {
                                                               if (!loading) dismissDialog()
                                                           }, enabled = !loading) {
                                                               Text(
                                                                   text = context.getString(
                                                                       android.R.string.cancel
                                                                   )
                                                               )
                                                           }
                                                       },
                                                       confirmButton = {
                                                           Button(onClick = {
                                                               done()
                                                           }, content = {
                                                               Text(
                                                                   text = if (loading) getString(
                                                                       R.string.saving
                                                                   ) else getString(
                                                                       android.R.string.ok
                                                                   )
                                                               )
                                                           })

                                                       },
                                                       title = {
                                                           Text(text = title)
                                                       },
                                                       text = {
                                                           UsernameField(
                                                               value = newValue,
                                                               onValueChanged = {
                                                                   newValue = it
                                                                   usernameCaption = ""
                                                                   error = false
                                                               },
                                                               newUsername = true,
                                                               enabled = !loading,
                                                               error = error,
                                                               caption = usernameCaption,
                                                               context = this,
                                                               imeAction = ImeAction.Done,
                                                               onDone = ::done,
                                                               reserveCaptionSpace = true
                                                           )
                                                       })
                                       },
                                       icon = {
                                           Box(modifier = Modifier
                                               .fillMaxSize()
                                               .clip(CircleShape)
                                               .clickable { // Launch the photo picker and allow the user to choose only images.
                                                   // https://developer.android.com/training/data-storage/shared/photopicker
                                                   pickMedia.launch(
                                                       PickVisualMediaRequest(
                                                           ActivityResultContracts.PickVisualMedia.ImageOnly
                                                       )
                                                   )
                                               }) {
                                               viewModel.photo?.let {
                                                   Image(
                                                       bitmap = it,
                                                       contentDescription = getString(
                                                           R.string.your_pfp_description
                                                       ),
                                                       modifier = Modifier
                                                           .fillMaxSize()
                                                           .clip(CircleShape)
                                                           .align(Alignment.Center)
                                                   )
                                               } ?: Icon(Icons.Outlined.AccountCircle, null)
                                           }
                                       },
                                       summary = { value ->
                                           value.ifBlank { getString(R.string.no_username) }
                                       })
                }, smallPreference = {
                    IconButton(onClick = {
                        viewModel.launchShareSheet(this)
                    }, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Outlined.Share, contentDescription = getString(R.string.share)
                        )
                    }
                })

                var emailValue by rememberPreference(
                    key = stringPreferencesKey(
                        getString(
                            R.string.email_key
                        )
                    ), defaultValue = "", repository = viewModel.preferencesRepository
                )

                DialoguePreference(
                    value = emailValue,
                    setValue = { newValue ->
                        emailValue = newValue
                    },
                    icon = {
                        Icon(Icons.Outlined.AlternateEmail, null)
                    },
                    title = R.string.email,
                    dialog = { value, setValue, dismissDialog, context, title ->
                        var newValue by remember { mutableStateOf(value) }
                        var loading by remember { mutableStateOf(false) }
                        var emailCaption by remember { mutableStateOf("") }

                        fun done() {
                            emailCaption =
                                if (!(newValue.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(
                                        newValue
                                    ).matches())
                                ) {
                                    getString(R.string.invalid_email)
                                } else {
                                    ""
                                }
                            if (emailCaption.isNotBlank()) {
                                return
                            }
                            loading = true
                            viewModel.changeUsername(email = newValue, setEmail = {
                                it?.let {
                                    setValue(it)
                                }
                            }, setCaption = {
                                emailCaption = it
                            }, setStatus = { _, l ->
                                loading = l
                            }, dismissDialog = dismissDialog, context = this
                            )
                        }

                        AlertDialog(onDismissRequest = { if (!loading) dismissDialog() },
                                    dismissButton = {
                                        OutlinedButton(onClick = {
                                            if (!loading) dismissDialog()
                                        }, enabled = !loading) {
                                            Text(
                                                text = context.getString(
                                                    android.R.string.cancel
                                                )
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            done()
                                        }, content = {
                                            Text(
                                                text = if (loading) getString(
                                                    R.string.saving
                                                ) else getString(
                                                    android.R.string.ok
                                                )
                                            )
                                        })

                                    },
                                    title = {
                                        Text(text = title)
                                    },
                                    text = {
                                        LoginActivity.EmailField(value = newValue,
                                                                 setValue = {
                                                                     newValue = it
                                                                     emailCaption = ""
                                                                 },
                                                                 enabled = !loading,
                                                                 caption = emailCaption,
                                                                 setCaption = { emailCaption = it },
                                                                 context = this,
                                                                 imeAction = ImeAction.Done,
                                                                 onDone = ::done,
                                                                 reserveCaptionSpace = true
                                        )
                                    })
                    },
                )
                var firstNameValue by rememberPreference(
                    key = stringPreferencesKey(
                        getString(
                            R.string.first_name_key
                        )
                    ), defaultValue = "", onPreferenceChangeListener = listOf(
                        userInfoChangeListener::onPreferenceChange
                    ), repository = viewModel.preferencesRepository
                )
                DialoguePreference(value = firstNameValue,
                                   setValue = { newValue ->
                                       firstNameValue = newValue
                                   },
                                   title = R.string.first_name,
                                   dialog = { value, setValue, dismissDialog, context, title ->
                                       StringPreferenceChange(
                                           value = value,
                                           setValue = setValue,
                                           dismissDialog = dismissDialog,
                                           context = context,
                                           title = title,
                                           textFieldLabel = R.string.placeholder_name,
                                           keyboardOptions = KeyboardOptions(
                                               capitalization = KeyboardCapitalization.Words
                                           )
                                       )
                                   })

                var lastNameValue by rememberPreference(
                    key = stringPreferencesKey(
                        getString(
                            R.string.last_name_key
                        )
                    ), defaultValue = "", onPreferenceChangeListener = listOf(
                        userInfoChangeListener::onPreferenceChange
                    ), repository = viewModel.preferencesRepository
                )
                DialoguePreference(
                    value = lastNameValue,
                    setValue = { newValue ->
                        lastNameValue = newValue
                    },
                    title = R.string.last_name,
                    dialog = { value, setValue, dismissDialog, context, title ->
                        StringPreferenceChange(
                            value = value,
                            setValue = setValue,
                            dismissDialog = dismissDialog,
                            context = context,
                            title = title,
                            textFieldLabel = R.string.placeholder_name,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words
                            )
                        )
                    },
                )
                val usesPassword by rememberPreference(
                    key = booleanPreferencesKey(
                        getString(
                            R.string.password_key
                        )
                    ), defaultValue = true, repository = viewModel.preferencesRepository
                )
                Preference(value = null,
                           icon = {
                               Icon(Icons.Outlined.Password, null)
                           },
                           title = R.string.password,
                           summary = null,
                           enabled = usesPassword,
                           onPreferenceClicked = {
                               val intent = Intent(this, LoginActivity::class.java)
                               intent.action = getString(R.string.change_password_action)
                               startActivity(intent)
                           })

                Preference(value = null, icon = { enabled ->
                    Image(
                        painter = painterResource(id = R.drawable.ic_btn_google),
                        contentDescription = getString(R.string.google_logo),
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = if (enabled) null
                        else grayScaleFilter()
                    )
                }, title = R.string.link_account, summary = null, onPreferenceClicked = {
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.action = getString(R.string.link_account_action)
                    startActivity(intent)
                }, enabled = usesPassword
                )
                DialoguePreference(
                    value = null,
                    setValue = { },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = R.string.confirm_logout_title,
                    titleColor = MaterialTheme.colorScheme.error,
                    summary = null,
                ) { _, _, dismissDialog, _, title ->
                    AlertDialog(onDismissRequest = { dismissDialog() }, title = {
                        Text(text = title)
                    }, text = {
                        Text(text = getString(R.string.confirm_logout_message))
                    }, confirmButton = {
                        Button(
                            onClick = {
                                viewModel.logout(this)

                                val oneTapClient = Identity.getSignInClient(this)
                                oneTapClient.signOut()
                                finish()
                                dismissDialog()
                            }, colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(text = getString(R.string.confirm_logout_title))
                        }
                    }, dismissButton = {
                        FilledTonalButton(onClick = { dismissDialog() }) {
                            Text(text = getString(android.R.string.cancel))
                        }

                    })
                }

            },
                   Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>(Pair(R.string.app_preference_category) @Composable {
                       Icon(Icons.Outlined.Tune, null)
                   }) @Composable {
                       var delayValue by rememberPreference(
                           key = floatPreferencesKey(getString(R.string.delay_key)),
                           defaultValue = DEFAULT_DELAY,
                           repository = viewModel.preferencesRepository
                       )
                       DialoguePreference(value = delayValue,
                                          setValue = { newValue -> delayValue = newValue },
                                          icon = {
                                              Icon(Icons.Outlined.Timer, null)
                                          },
                                          summary = {
                                              getString(R.string.delay_summary, it.toString())
                                          },
                                          title = R.string.delay_title
                       ) { value, setValue, dismissDialog, context, title ->
                           FloatPreferenceChange(value = value,
                                                 setValue = setValue,
                                                 dismissDialog = dismissDialog,
                                                 context = context,
                                                 title = title,
                                                 textFieldLabel = R.string.delay_label,
                                                 validate = {
                                                     if (it < 0) {
                                                         getString(R.string.delay_greater_than_zero)
                                                     } else ""
                                                 })
                       }
                   },
                   Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>(Pair(R.string.notifications_title) @Composable {
                       Icon(Icons.Outlined.Notifications, null)
                   }) @Composable {
                       var vibrateValue by rememberPreference(
                           key = stringSetPreferencesKey(getString(R.string.vibrate_preference_key)),
                           defaultValue = HashSet(),
                           repository = viewModel.preferencesRepository
                       )
                       DialoguePreference(value = vibrateValue,
                                          setValue = { newValue -> vibrateValue = newValue },
                                          icon = {
                                              Icon(Icons.Outlined.Vibration, null)
                                          },
                                          title = R.string.vibrate_preference,
                                          dialog = { value, setValue, dismissDialog, context, title ->
                                              MultiSelectListPreferenceChange(
                                                  value = value,
                                                  setValue = setValue,
                                                  dismissDialog = dismissDialog,
                                                  context = context,
                                                  title = title,
                                                  entriesRes = R.array.notification_entries,
                                                  entryValuesRes = R.array.notification_values
                                              )
                                          },
                                          summary = {
                                              multiselectListPreferenceSummary(
                                                  it, resources.getStringArray(
                                                      R.array.notification_entries
                                                  ), resources.getStringArray(
                                                      R.array.notification_values
                                                  )
                                              )
                                          })
                       var ringValue by rememberPreference(
                           key = stringSetPreferencesKey(
                               getString(
                                   R.string.ring_preference_key
                               )
                           ), defaultValue = HashSet(), repository = viewModel.preferencesRepository
                       )

                       DialoguePreference(value = ringValue,
                                          setValue = { newValue -> ringValue = newValue },
                                          icon = {
                                              Icon(Icons.Outlined.NotificationsActive, null)
                                          },
                                          title = R.string.ring_preference,
                                          dialog = { value, setValue, dismissDialog, context, title ->
                                              MultiSelectListPreferenceChange(
                                                  value = value,
                                                  setValue = setValue,
                                                  dismissDialog = dismissDialog,
                                                  context = context,
                                                  title = title,
                                                  entriesRes = R.array.notification_entries,
                                                  entryValuesRes = R.array.notification_values
                                              )
                                          },
                                          summary = {
                                              multiselectListPreferenceSummary(
                                                  it, resources.getStringArray(
                                                      R.array.notification_entries
                                                  ), resources.getStringArray(
                                                      R.array.notification_values
                                                  )
                                              )
                                          })
                       val showDNDAlert = rememberSaveable {
                           mutableStateOf(false)
                       }
                       var overrideDNDValue by rememberPreference(
                           key = booleanPreferencesKey(
                               getString(R.string.override_dnd_key)
                           ),
                           defaultValue = false,
                           onPreferenceChangeListener = listOf { _, newValue ->
                               if (newValue) {
                                   return@listOf if ((getSystemService(
                                           NOTIFICATION_SERVICE
                                       ) as NotificationManager).isNotificationPolicyAccessGranted
                                   ) {
                                       true
                                   } else {
                                       showDNDAlert.value = true
                                       false
                                   }

                               }
                               true
                           },
                           repository = viewModel.preferencesRepository
                       )
                       if (showDNDAlert.value) {
                           AlertDialog(onDismissRequest = { showDNDAlert.value = false },
                                       confirmButton = {
                                           Button(onClick = {
                                               val intent = Intent(
                                                   ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                                               )
                                               showDNDAlert.value = false
                                               startActivity(intent)
                                           }, content = {
                                               Text(text = getString(R.string.open_settings))
                                           })
                                       },
                                       dismissButton = {
                                           OutlinedButton(onClick = {
                                               showDNDAlert.value = false
                                           }) {
                                               Text(text = getString(android.R.string.cancel))
                                           }
                                       }, title = {
                                           Text(text = getString(R.string.allow_dnd_title))
                               }, text = {
                                   Text(text = getString(R.string.allow_dnd_message))
                               })
                       }
                       Preference(value = overrideDNDValue, icon = {
                           Icon(Icons.Outlined.DoNotDisturbOn, null)
                       }, onPreferenceClicked = {
                           overrideDNDValue = !overrideDNDValue
                       }, title = R.string.override_dnd, summary = {
                           if (it) {
                               getString(R.string.override_summary_on)
                           } else {
                               getString(R.string.override_summary_off)
                           }
                       }, action = {
                           CheckboxAction(value = it) { checked ->
                               overrideDNDValue = checked
                           }
                       })
                   },
                   Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>(Pair(
                       R.string.legal_title
                   ) @Composable {
                       Icon(Icons.Outlined.Gavel, null)
                   }) @Composable {
                       Preference(value = null,
                                  icon = { Icon(Icons.Outlined.Gavel, null) },
                                  title = R.string.terms_of_service,
                                  summary = null,
                                  onPreferenceClicked = {
                                      val browserIntent = Intent(
                                          Intent.ACTION_VIEW, Uri.parse(getString(R.string.tos_url))
                                      )
                                      startActivity(browserIntent)
                                  })
                       Preference(value = null, icon = {
                           Icon(Icons.Outlined.Policy, null)
                       }, title = R.string.privacy_policy, summary = null, onPreferenceClicked = {
                           val browserIntent = Intent(
                               Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))
                           )
                           startActivity(browserIntent)
                       })
                       Preference(
                           value = getString(R.string.version_name),
                           title = R.string.app_version,
                           icon = {
                               Icon(Icons.Outlined.Update, null)
                           },
                           enabled = false
                       )

                       Preference(
                               title = R.string.by_ap,
                               value = null,
                               summary = null,
                               onPreferenceClicked = {
                                   val browserIntent = Intent(
                                           Intent.ACTION_VIEW, Uri.parse(getString(R.string.ap_url))
                                   )
                                   startActivity(browserIntent)
                               },
                               icon = {
                                   Icon(ImageVector.vectorResource(id = R.drawable.ap),
                                           null)
                               }
                       )
                   }

            )
        PreferenceScreen(
            preferences = preferences,
            selected = viewModel.selectedPreferenceGroupIndex,
            currentPreferenceGroup = viewModel.currentPreferenceGroup,
            screenClass = calculateWindowSizeClass(activity = this).widthSizeClass,
            onGroupSelected = { key, preferenceGroup ->
                viewModel.selectedPreferenceGroupIndex = key
                viewModel.currentPreferenceGroup = preferenceGroup
            },
            snackbarHostState = snackbarHostState
        )
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
    @Composable
    fun PreferenceScreen(
        preferences: List<Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>>,
        selected: Int,
        currentPreferenceGroup: @Composable () -> Unit,
        screenClass: WindowWidthSizeClass,
        onGroupSelected: (key: Int, preferences: @Composable () -> Unit) -> Unit,
        snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    ) {

        // Remember a SystemUiController
        val systemUiController = rememberSystemUiController()
        val useDarkIcons = !isSystemInDarkTheme()
        val scrim = MaterialTheme.colorScheme.scrim

        if (selected == 0) {
            onGroupSelected(preferences.firstOrNull()?.first?.first ?: 0,
                            preferences.firstOrNull()?.second ?: {})
        }

        val phone = screenClass == WindowWidthSizeClass.Compact

        DisposableEffect(
            systemUiController, useDarkIcons
        ) { // Update all of the system bar colors to be transparent, and use
            // dark icons if we're in light theme
            systemUiController.setNavigationBarColor(
                color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Color.Transparent
                else scrim, darkIcons = useDarkIcons
            )

            // setStatusBarColor() and setNavigationBarColor() also exist

            onDispose {}
        }

        val phoneScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val tabletScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(topBar = {
            if (phone) {
                LargeTopAppBar(colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ), title = {
                    Text(
                        getString(R.string.title_activity_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }, scrollBehavior = phoneScrollBehavior, navigationIcon = {
                    IconButton(onClick = {
                        onBackPressedDispatcher.onBackPressed()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, getString(
                                R.string.back
                            )
                        )
                    }
                })
            } else {
                TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ), title = {
                    Text(
                        getString(R.string.title_activity_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }, scrollBehavior = tabletScrollBehavior, navigationIcon = {
                    IconButton(onClick = {
                        onBackPressedDispatcher.onBackPressed()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, getString(
                                R.string.back
                            )
                        )
                    }
                })
            }
        }, snackbarHost = { SnackbarHost(snackbarHostState) }, modifier = Modifier.nestedScroll(
            if (phone) phoneScrollBehavior.nestedScrollConnection else tabletScrollBehavior.nestedScrollConnection
        ), containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            if (screenClass == WindowWidthSizeClass.Compact) {
                LazyColumn(
                    modifier = Modifier
                        .selectableGroup()
                        .waterfallPadding(),
                    contentPadding = padding
                ) {
                    items(items = preferences,
                          key = { preference -> preference.first.first }) { preference ->
                        PreferenceGroup(
                            title = preference.first.first,
                            icon = preference.first.second,
                            onClick = {
                                onGroupSelected(preference.first.first, preference.second)
                            },
                            selected = preference.first.first == selected,
                            tablet = screenClass != WindowWidthSizeClass.Compact,
                            preferences = preference.second,
                            first = preference.first.first == preferences.firstOrNull()?.first?.first
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .selectableGroup()
                            .waterfallPadding()
                            .fillMaxWidth(0.35f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = padding,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(items = preferences,
                              key = { preference -> preference.first.first }) { preference ->
                            PreferenceGroup(
                                title = preference.first.first,
                                icon = preference.first.second,
                                onClick = {
                                    onGroupSelected(preference.first.first, preference.second)
                                },
                                selected = preference.first.first == selected,
                                tablet = screenClass != WindowWidthSizeClass.Compact,
                                preferences = preference.second,
                                first = preference.first.first == preferences.firstOrNull()?.first?.first
                            )
                        }
                    }
                    AnimatedContent(targetState = currentPreferenceGroup, transitionSpec = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start
                        ) togetherWith fadeOut()
                    }) { targetPreferenceGroup ->
                        Column(modifier = Modifier.weight(1f, true)) {
                            Spacer(modifier = Modifier.height(padding.calculateTopPadding()))
                            targetPreferenceGroup()
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun UploadDialog(
        uploading: Boolean,
        uploadStatus: String,
        shouldRetry: Boolean,
        uploadSuccess: Boolean?,
        uploadSuccessCallback: (Boolean?) -> Unit,
        uploadProgress: Float,
        onCancel: (() -> Unit)?,
        dismissDialog: () -> Unit,
        retry: (Uri, Activity) -> Unit,
        uri: Uri?
    ) {
        var bitmap: ImageBitmap? by remember {
            mutableStateOf(null)
        }
        assert(uri != null)
        AlertDialog(onDismissRequest = { /*Don't let people tap outside*/ }, dismissButton = {
            TextButton(onClick = {
                onCancel?.invoke()
                dismissDialog()
            }) {
                Text(text = getString(android.R.string.cancel))
            }
        }, confirmButton = {
            if (!uploading) {
                if (shouldRetry && uri != null) {
                    OutlinedButton(onClick = {
                        retry(uri, this)
                    }) {
                        Text(text = getString(R.string.retry))
                    }
                } else {
                    Button(onClick = { dismissDialog() }) {
                        Text(text = getString(android.R.string.ok))
                    }
                }
            }
        }, title = {
            Text(text = getString(R.string.upload_pfp))
        }, text = {
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
            ) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    LaunchedEffect(key1 = constraints, key2 = uri) {
                        if (uri != null) {
                            bitmap = viewModel.getImageBitmap(
                                uri,
                                this@SettingsActivity,
                                IntSize(if (constraints.hasBoundedWidth) constraints.maxWidth else with(
                                    density
                                ) {
                                    configuration.screenWidthDp.dp.roundToPx()
                                },
                                        if (constraints.hasBoundedHeight) constraints.maxHeight else with(
                                            density
                                        ) {
                                            configuration.screenWidthDp.dp.roundToPx()
                                        }),
                                true
                            )?.asImageBitmap()
                        }
                    }
                    Crossfade(
                        targetState = uploading, animationSpec = TweenSpec(
                            FADE_DURATION, 0, EaseInOutCubic
                        )
                    ) { targetState ->
                        when (targetState) {
                            true -> {
                                bitmap?.let {
                                    Image(
                                        bitmap = it,
                                        contentScale = ContentScale.Fit,
                                        contentDescription = getString(
                                            R.string.your_pfp_description
                                        ),
                                        modifier = Modifier
                                            .aspectRatio(
                                                ratio = it.height.toFloat() / it.width
                                            )
                                            .shadow(5.dp, shape = RoundedCornerShape(12.dp))
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        colorFilter = ColorFilter.tint(
                                            Color(
                                                UPLOAD_GRAY_INTENSITY,
                                                UPLOAD_GRAY_INTENSITY,
                                                UPLOAD_GRAY_INTENSITY,
                                                1f
                                            ), BlendMode.Screen
                                        )
                                    )
                                }

                                CircularProgressIndicator(
                                    progress = {
                                        uploadProgress
                                    },
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .zIndex(1f)
                                )
                            }
                            false -> {
                                bitmap?.let {
                                    Image(
                                        bitmap = it,
                                        contentDescription = getString(
                                            R.string.your_pfp_description
                                        ),
                                        modifier = Modifier
                                            .aspectRatio(
                                                ratio = it.height.toFloat() / it.width
                                            )
                                            .shadow(5.dp, shape = RoundedCornerShape(12.dp))
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                LaunchedEffect(key1 = uploadSuccess, block = {
                                    if (uploadSuccess == true) {
                                        delay(STATUS_DELAY)
                                        uploadSuccessCallback(null)
                                    }
                                })
                                AnimatedContent(
                                    targetState = uploadSuccess,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxSize(0.25f)
                                        .aspectRatio(1f)
                                        .zIndex(1f)
                                ) { success ->
                                    when (success) {
                                        null -> {}
                                        else -> {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(10))
                                                    .background(
                                                        Color.Black.copy(
                                                            alpha = ContentAlpha.disabled
                                                        )
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = if (success) Icons.Default.Check
                                                    else Icons.Default.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    tint = if (success) Color.Green else MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(ICON_PADDING))
                Text(
                    text = uploadStatus,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (uploadSuccess == false) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
        }, properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    @Composable
    fun PreferenceGroup(
        title: Int,
        selected: Boolean,
        tablet: Boolean,
        onClick: () -> Unit,
        preferences: @Composable () -> Unit,
        first: Boolean = false,
        icon: (@Composable () -> Unit)? = null
    ) {
        if (tablet) {

            val alpha by animateFloatAsState(
                label = "selected group background color", targetValue = when (selected) {
                    true -> 1f
                    false -> 0f
                }
            )
            Box(
                modifier = Modifier
                    .padding(
                        start = PREFERENCE_GROUP_PADDING, end = PREFERENCE_GROUP_PADDING
                    )
                    .height(PREFERENCE_GROUP_HEIGHT)
                    .clip(
                        RoundedCornerShape(PREFERENCE_GROUP_RADIUS)
                    )
                    .background(
                        MaterialTheme.colorScheme.inversePrimary.copy(alpha = alpha)
                    )
                    .selectable(selected = selected, onClick = onClick)
                    .padding(
                        start = PREFERENCE_GROUP_PADDING, end = PREFERENCE_GROUP_PADDING
                    ), contentAlignment = Alignment.CenterStart
            ) {
                Layout(content = {
                    icon?.invoke()
                    Text(
                        text = getString(title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                }, modifier = Modifier.fillMaxSize(), measurePolicy = { measurables, constraints ->/*
                    We want to position the text in the center of the box
                    If there is an icon, we want the icon to be left-aligned
                    The icon may require the text to be cut off and/or move to the right

                    [ (ic)   text       ]
                    [  text w/out icon  ]
                    [ (ic) long text... ]
                     */
                    if (measurables.isEmpty()) return@Layout layout(0, 0) {

                    }
                    val paddingSize = ICON_PADDING.toPx().toInt()
                    lateinit var text: Placeable
                    var iconPlaceable: Placeable? = null
                    val totalWidth: Int
                    val totalHeight: Int
                    if (measurables.size == 1) {
                        text = measurables[0].measure(constraints)
                        totalWidth = if (constraints.hasBoundedWidth) constraints.maxWidth
                        else text.width
                        totalHeight = if (constraints.hasBoundedHeight) constraints.maxHeight
                        else text.height
                    } else {
                        iconPlaceable = measurables[0].measure(
                            Constraints.fixed(
                                ICON_SIZE.toPx().toInt(), ICON_SIZE.toPx().toInt()
                            )
                        )
                        text = measurables[1].measure(
                            Constraints(
                                0,
                                constraints.maxWidth - iconPlaceable.width - paddingSize,
                                0,
                                constraints.maxHeight
                            )
                        )

                        totalWidth = if (constraints.hasBoundedWidth) constraints.maxWidth
                        else iconPlaceable.width + paddingSize + text.width
                        totalHeight = if (constraints.hasBoundedHeight) constraints.maxHeight
                        else max(iconPlaceable.height, text.height)
                    }

                    layout(totalWidth, totalHeight) {
                        val textX = max(
                            (totalWidth - text.width) / 2,
                            (iconPlaceable?.width ?: -paddingSize) + paddingSize
                        )
                        val textY = (totalHeight - text.height) / 2
                        text.place(x = textX, y = textY)
                        iconPlaceable?.place(
                            x = 0, y = (totalHeight - iconPlaceable.height) / 2
                        )
                    }
                })

            }
        } else {
            if (!first) HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp)
            Text(
                text = getString(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = 8.dp, top = LIST_ELEMENT_PADDING, bottom = LIST_ELEMENT_PADDING
                ),
                overflow = TextOverflow.Ellipsis
            )
            preferences()
        }
    }

    @Composable
    fun SplitPreference(
        largePreference: @Composable () -> Unit,
        smallPreference: @Composable () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(PREFERENCE_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = modifier
                    .padding(end = SPLIT_PREFERENCE_PADDING)
                    .fillMaxHeight()
                    .weight(1f, fill = true), contentAlignment = Alignment.CenterStart
            ) {
                largePreference()
            }
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium)
            )
            Box(
                modifier = modifier
                    .padding(start = SPLIT_PREFERENCE_PADDING)
                    .size(PREFERENCE_HEIGHT), contentAlignment = Alignment.Center
            ) {
                smallPreference()
            }
        }
    }

    @Composable
    fun <T> DialoguePreference(
        value: T,
        setValue: (T) -> Unit,
        title: Int,
        modifier: Modifier = Modifier,
        action: @Composable ((value: T) -> Unit)? = null,
        summary: ((value: T) -> String)? = { state ->
            state.toString()
        },
        icon: @Composable (BoxScope.(enabled: Boolean) -> Unit)? = null,
        reserveIconSpace: Boolean = true,
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        disabledTitleColor: Color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = ContentAlpha.medium
        ),
        titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
        summaryColor: Color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = ContentAlpha.medium
        ),
        summaryStyle: TextStyle = MaterialTheme.typography.labelLarge,
        enabled: Boolean = true,
        dialog: @Composable (value: T, setValue: (T) -> Unit, dismissDialog: () -> Unit, context: Context, title: String) -> Unit
    ) {
        val editing = rememberSaveable {
            mutableStateOf(false)
        }
        if (editing.value) {
            dialog(value = value, setValue = setValue, dismissDialog = {
                editing.value = false
            }, context = this@SettingsActivity, title = getString(title))
        }
        Preference(
            value = value,
            title = title,
            modifier = modifier,
            action = action,
            summary = summary,
            icon = icon,
            reserveIconSpace = reserveIconSpace,
            titleColor = titleColor,
            disabledTitleColor = disabledTitleColor,
            titleStyle = titleStyle,
            summaryColor = summaryColor,
            onPreferenceClicked = {
                editing.value = true
            },
            summaryStyle = summaryStyle,
            enabled = enabled
        )
    }

    @Composable
    fun <T> Preference(
        value: T,
        title: Int,
        modifier: Modifier = Modifier,
        action: @Composable ((value: T) -> Unit)? = null,
        summary: ((value: T) -> String)? = { state ->
            state.toString()
        },
        icon: @Composable (BoxScope.(enabled: Boolean) -> Unit)? = null,
        reserveIconSpace: Boolean = true,
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        disabledTitleColor: Color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = ContentAlpha.medium
        ),
        titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
        summaryColor: Color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = ContentAlpha.medium
        ),
        onPreferenceClicked: () -> Unit = { },
        summaryStyle: TextStyle = MaterialTheme.typography.labelLarge,
        enabled: Boolean = true,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(73.dp)
                .clickable(enabled = enabled, onClick = {
                    onPreferenceClicked()
                }), verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null || reserveIconSpace) {
                val iconSpot: @Composable BoxScope.(enabled: Boolean) -> Unit = icon ?: { }
                Box(modifier = Modifier
                    .padding(ICON_PADDING)
                    .size(ICON_SIZE)
                    .alpha(if (enabled) ContentAlpha.high else ContentAlpha.disabled),
                    contentAlignment = Alignment.Center,
                    content = {
                        iconSpot(enabled)
                    })
            }
            Column(
                modifier = modifier
                    .fillMaxHeight()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = getString(title), style = titleStyle, color = if (enabled) titleColor
                    else disabledTitleColor
                )
                if (summary != null) Text(
                    text = summary(value), style = summaryStyle, color = summaryColor
                )
            }
            if (action != null) Box(
                modifier = Modifier.size(PREFERENCE_HEIGHT), contentAlignment = Alignment.Center
            ) { action(value) }
        }
    }


    companion object {
        val PREFERENCE_HEIGHT = 73.dp
        val PREFERENCE_GROUP_HEIGHT = 100.dp
        val SPLIT_PREFERENCE_PADDING = 5.dp
        val ICON_SIZE = 24.dp
        val ICON_PADDING = 16.dp
        val PREFERENCE_GROUP_PADDING = 16.dp
        val PREFERENCE_GROUP_RADIUS = 24.dp
        const val TEMP_PFP = "${MainViewModel.PFP_FILENAME}_temp"
        const val UPLOAD_GRAY_INTENSITY = 0.5f
        const val FADE_DURATION = 500
        const val STATUS_DELAY: Long = 5000

        const val DEFAULT_DELAY = 3.5f

        private fun grayScaleFilter(): ColorFilter {
            val grayScaleMatrix = ColorMatrix(
                floatArrayOf(
                    0.33f,
                    0.33f,
                    0.33f,
                    0f,
                    0f,
                    0.33f,
                    0.33f,
                    0.33f,
                    0f,
                    0f,
                    0.33f,
                    0.33f,
                    0.33f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
                )
            )
            return ColorFilter.colorMatrix(grayScaleMatrix)
        }
    }
}