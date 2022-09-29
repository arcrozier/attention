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
import android.util.TypedValue
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.aracroproducts.attentionv2.LoginActivity.Companion.LIST_ELEMENT_PADDING
import com.aracroproducts.attentionv2.LoginActivity.Companion.UsernameField
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.MY_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.USER_INFO
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Integer.max

/**
 * The class for the settings menu in the app
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels(factoryProducer = {
        SettingsViewModelFactory(AttentionRepository(AttentionDB.getDB(this)), application)
    })


    private val cropResultHandler = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            val uri = UCrop.getOutput(data) ?: return@let
            assert(uri == Uri.fromFile(File(filesDir, TEMP_PFP)))
            viewModel.uploadImage(uri, this) { launchLogin(this) }
        }
    }

    // Registers a photo picker activity launcher in single-select mode.
    private val pickMedia =
            registerForActivityResult(
                    ActivityResultContracts.PickVisualMedia()) { uri -> // Callback is invoked after the user selects a media item or closes the
                // photo picker.
                if (uri != null) { // crop then upload https://github.com/Yalantis/uCrop
                    // https://stackoverflow.com/questions/3879992/how-to-get-bitmap-from-an-uri
                    val cropIntent =
                            UCrop.of(uri, Uri.fromFile(File(filesDir, TEMP_PFP)))
                                    .withAspectRatio(1f, 1f)
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
            private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(attentionRepository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) {
            BooleanPreference(
                    key = getString(R.string.override_dnd_key), context = this
            ).value = false
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
        val coroutineScope = rememberCoroutineScope()

        if (viewModel.uploadDialog) {
            UploadDialog(
                    uploading = viewModel.uploading,
                    uploadStatus = viewModel.uploadStatus,
                    shouldRetry = viewModel.shouldRetryUpload,
                    onCancel = viewModel.onCancel,
                    dismissDialog = { viewModel.uploadDialog = false },
                    retry = viewModel::uploadImage,
                    uri = viewModel.uri
            )
        }

        val userInfoChangeListener =
                UserInfoChangeListener(this, viewModel, snackbarHostState, coroutineScope)
        val preferences = listOf(Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () ->
        Unit>(Pair(R.string.account) @Composable {
            Icon(Icons.Outlined.ManageAccounts, null)
        }) @Composable {
            SplitPreference(largePreference = {
                DialoguePreference(preference = StringPreference(
                        getString(R.string.username_key), this
                ),
                        title = R.string.username,
                        dialog = { preference, dismissDialog, context, title ->
                            var newValue by remember { mutableStateOf(preference.value) }
                            var loading by remember { mutableStateOf(false) }
                            var error by remember { mutableStateOf(false) }
                            var usernameCaption by remember { mutableStateOf("") }
                            val token = context.getSharedPreferences(
                                    USER_INFO, Context.MODE_PRIVATE
                            ).getString(MY_TOKEN, null)
                            if (token == null) {
                                val loginIntent =
                                        Intent(context, LoginActivity::class.java)
                                context.startActivity(loginIntent)
                                return@DialoguePreference
                            }
                            AlertDialog(onDismissRequest = { if (!loading) dismissDialog() },
                                    dismissButton = {
                                        OutlinedButton(onClick = {
                                            if (!loading) dismissDialog()
                                        }, enabled = !loading) {
                                            Text(text = context.getString(android.R.string.cancel))
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            loading = true
                                            AttentionRepository(
                                                    AttentionDB.getDB(
                                                            this
                                                    )
                                            ).editUser(token = token,
                                                    username = newValue,
                                                    responseListener = { _, response, _ ->
                                                        loading = false
                                                        error =
                                                                !response.isSuccessful
                                                        when (response.code()) {
                                                            200 -> {
                                                                usernameCaption =
                                                                        ""
                                                                preference.value =
                                                                        newValue
                                                                dismissDialog()
                                                            }
                                                            400 -> {
                                                                usernameCaption =
                                                                        getString(
                                                                                R.string.username_in_use)
                                                            }
                                                            403 -> {
                                                                usernameCaption =
                                                                        ""
                                                                dismissDialog()
                                                                launchLogin(this)
                                                            }
                                                            else -> {
                                                                usernameCaption =
                                                                        getString(
                                                                                R.string.unknown_error)
                                                            }
                                                        }
                                                    },
                                                    errorListener = { _, _ ->
                                                        loading = false
                                                        error = true
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                    context.getString(
                                                                            R.string.disconnected
                                                                    ),
                                                                    duration = SnackbarDuration.Long
                                                            )
                                                        }
                                                    })
                                        }, content = {
                                            Text(
                                                    text = if (loading) getString(
                                                            R.string.saving) else getString(
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
                                                onValueChanged = { newValue = it },
                                                newUsername = true,
                                                enabled = !loading,
                                                error = error,
                                                caption = usernameCaption,
                                                this
                                        )
                                    })
                        },
                        icon = {
                            Box(modifier = Modifier
                                    .fillMaxSize()
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
                    val username = PreferenceManager.getDefaultSharedPreferences(this)
                            .getString(MainViewModel.MY_ID, null)
                    if (username != null) {
                        val sharingIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val shareBody = getString(R.string.share_text, username)
                            putExtra(Intent.EXTRA_TEXT, shareBody)
                        }
                        startActivity(Intent.createChooser(sharingIntent, null))
                    }
                }, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.Share, contentDescription = getString(R.string.share))
                }
            })

            DialoguePreference(
                    preference = StringPreference(getString(R.string.email_key), this),
                    icon = {
                        Icon(Icons.Outlined.AlternateEmail, null)
                    },
                    title = R.string.email,
                    dialog = { preference, dismissDialog, context, title ->
                        StringPreferenceChange(preference, dismissDialog, context, title) {
                            if (!(it.isEmpty() || android.util.Patterns.EMAIL_ADDRESS
                                    .matcher(it).matches())) {
                                getString(R.string.invalid_email)
                            } else {
                                ""
                            }
                        }
                    },
                    onPreferenceChanged = userInfoChangeListener
            )
            DialoguePreference(
                    preference = StringPreference(
                            getString(R.string.first_name_key), this
                    ),
                    title = R.string.first_name,
                    dialog = { preference, dismissDialog, context, title ->
                        StringPreferenceChange(
                                preference = preference,
                                dismissDialog = dismissDialog,
                                context = context,
                                title = title
                        )
                    },
                    onPreferenceChanged = userInfoChangeListener
            )
            DialoguePreference(
                    preference = StringPreference(
                            getString(R.string.last_name_key), this
                    ),
                    title = R.string.last_name,
                    dialog = { preference, dismissDialog, context, title ->
                        StringPreferenceChange(
                                preference = preference,
                                dismissDialog = dismissDialog,
                                context = context,
                                title = title
                        )
                    },
                    onPreferenceChanged = userInfoChangeListener
            )
            Preference(preference = EphemeralPreference(
                    "", null
            ),
                    icon = {
                        Icon(Icons.Outlined.Password, null)
                    },
                    title = R.string.password, summary = null, onPreferenceClicked = {
                val intent = Intent(this, LoginActivity::class.java)
                intent.action = getString(R.string.change_password_action)
                startActivity(intent)
                true
            })
            Preference(
                    preference = EphemeralPreference(
                            getString(R.string.link_account_key), null
                    ),
                    icon = {
                        Image(painter = painterResource(id = R.drawable.ic_btn_google),
                                contentDescription = getString(R.string.google_logo),
                                modifier = Modifier.fillMaxSize())
                    },
                    title = R.string.link_account,
                    summary = null,
                    onPreferenceClicked = {
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.action = getString(R.string.link_account_action)
                        startActivity(intent)
                        true
                    },
                    enabled = BooleanPreference(getString(R.string.password_key), this).getValue(
                            true)
            )
            DialoguePreference(
                    preference = EphemeralPreference(getString(R.string.logout_key), null),
                    icon = {
                        Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error)
                    },
                    title = R.string.confirm_logout_title,
                    titleColor = MaterialTheme.colorScheme.error,
                    summary = null,
            ) { _, dismissDialog, context, title ->
                AlertDialog(onDismissRequest = { dismissDialog() }, title = {
                    Text(text = title)
                }, text = {
                    Text(text = getString(R.string.confirm_logout_message))
                }, confirmButton = {
                    Button(
                            onClick = {
                                val userInfo = context.getSharedPreferences(
                                        USER_INFO, Context.MODE_PRIVATE
                                )
                                val fcmTokenPrefs = context.getSharedPreferences(
                                        FCM_TOKEN, Context.MODE_PRIVATE
                                )
                                viewModel.unregisterDevice(
                                        userInfo.getString(MY_TOKEN, null) ?: "",
                                        fcmTokenPrefs.getString(FCM_TOKEN, null) ?: ""
                                )
                                userInfo.edit().apply {
                                    remove(MY_TOKEN)
                                    apply()
                                }
                                PreferenceManager.getDefaultSharedPreferences(context).edit()
                                        .apply {
                                            remove(getString(R.string.username_key))
                                            remove(getString(R.string.first_name_key))
                                            remove(getString(R.string.last_name_key))
                                            remove(getString(R.string.email_key))
                                            apply()
                                        }
                                viewModel.clearAllDatabaseTables()
                                finish()
                                context.startActivity(
                                        Intent(
                                                context, LoginActivity::class.java
                                        )
                                )
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

        }, Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>(
                Pair(R.string.app_preference_category) @Composable {
                    Icon(Icons.Outlined.Tune, null)
                })
        @Composable {
            val defaultDelay = TypedValue()
            resources.getValue(R.integer.default_delay, defaultDelay, false)
            DialoguePreference(
                    preference = FloatPreference(getString(R.string.delay_key), this),
                    icon = {
                        Icon(Icons.Outlined.Timer, null)
                    },
                    title = R.string.delay_title,
                    default = defaultDelay.float
            ) { preference, dismissDialog, context, title ->
                FloatPreferenceChange(
                        preference = preference,
                        dismissDialog = dismissDialog,
                        context = context,
                        title = title
                )
            }
        }, Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>(
                Pair(R.string.notifications_title) @Composable {
                    Icon(Icons.Outlined.Notifications, null)
                })
        @Composable {
            DialoguePreference(preference = StringSetPreference(
                    key = getString(R.string.vibrate_preference_key), context = this
            ),
                    icon = {
                        Icon(Icons.Outlined.Vibration, null)
                    },
                    title = R.string.vibrate_preference,
                    dialog = { preference, dismissDialog, context, title ->
                        MultiSelectListPreferenceChange(
                                preference = preference,
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
                        ), resources.getStringArray(R.array.notification_values)
                        )
                    })
            DialoguePreference(preference = StringSetPreference(
                    key = getString(R.string.ring_preference_key), context = this
            ),
                    icon = {
                        Icon(Icons.Outlined.NotificationsActive, null)
                    },
                    title = R.string.ring_preference,
                    dialog = { preference, dismissDialog, context, title ->
                        MultiSelectListPreferenceChange(
                                preference = preference,
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
                        ), resources.getStringArray(R.array.notification_values)
                        )
                    })
            val showDNDAlert = rememberSaveable {
                mutableStateOf(false)
            }
            if (showDNDAlert.value) {
                AlertDialog(onDismissRequest = { showDNDAlert.value = false }, confirmButton = {
                    Button(onClick = {
                        val intent = Intent(
                                ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                        )
                        startActivity(intent)
                        showDNDAlert.value = false
                        BooleanPreference(key = getString(R.string.override_dnd_key), this).value =
                                true
                    }, content = {
                        Text(text = getString(R.string.open_settings))
                    })
                }, dismissButton = {
                    OutlinedButton(onClick = { showDNDAlert.value = false }) {}
                })
            }
            Preference(preference = BooleanPreference(
                    key = getString(R.string.override_dnd_key), context = this
            ),
                    icon = {
                        Icon(Icons.Outlined.DoNotDisturbOn, null)
                    },
                    title = R.string.override_dnd, default = false, summary = {
                if (it) {
                    getString(R.string.override_summary_on)
                } else {
                    getString(R.string.override_summary_off)
                }
            }, action = {
                CheckboxAction(value = it)
            }, onPreferenceChanged = object : ComposablePreferenceChangeListener<Boolean> {
                override fun onPreferenceChange(
                        preference: ComposablePreference<Boolean>, newValue: Boolean
                ): Boolean {
                    if (newValue) {
                        return if ((getSystemService(
                                        NOTIFICATION_SERVICE
                                ) as NotificationManager).isNotificationPolicyAccessGranted
                        ) {
                            true
                        } else {
                            showDNDAlert.value = true
                            false
                        }

                    }
                    return false
                }
            })
        }, Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>(Pair(R.string
                .legal_title) @Composable {
            Icon(Icons.Outlined.Gavel, null)
        })
        @Composable {
            Preference(preference = EphemeralPreference("", null),
                    icon = { Icon(Icons.Outlined.Gavel, null) },
                    title = R.string.terms_of_service,
                    summary = null,
                    onPreferenceClicked = {
                        val browserIntent = Intent(
                                Intent.ACTION_VIEW, Uri.parse(getString(R.string.tos_url))
                        )
                        startActivity(browserIntent)
                        false
                    })
            Preference(preference = EphemeralPreference("", null),
                    icon = {
                        Icon(Icons.Outlined.Policy, null)
                    },
                    title = R.string.privacy_policy,
                    summary = null,
                    onPreferenceClicked = {
                        val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(getString(R.string.privacy_policy_url))
                        )
                        startActivity(browserIntent)
                        false
                    })

            Preference(
                    preference = EphemeralPreference(
                            key = "", value = getString(R.string.version_name)
                    ), title = R.string.app_version, enabled = false
            )
        }

        )
        PreferenceScreen(
                preferences = preferences,
                selected = viewModel.selectedPreferenceGroupIndex,
                screenClass = calculateWindowSizeClass(activity = this).widthSizeClass,
                onGroupSelected = { key, preferenceGroup ->
                    viewModel.selectedPreferenceGroupIndex = key
                    viewModel.currentPreferenceGroup = preferenceGroup
                },
                snackbarHostState = snackbarHostState
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PreferenceScreen(
            preferences: List<Pair<Pair<Int, (@Composable () -> Unit)?>, @Composable () -> Unit>>,
            selected: Int,
            screenClass: WindowWidthSizeClass,
            onGroupSelected: (key: Int, preferences: @Composable () -> Unit) -> Unit,
            snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    ) {

        // Remember a SystemUiController
        val systemUiController = rememberSystemUiController()
        val useDarkIcons = !isSystemInDarkTheme()
        val scrim = MaterialTheme.colorScheme.scrim

        DisposableEffect(systemUiController, useDarkIcons) {
            // Update all of the system bar colors to be transparent, and use
            // dark icons if we're in light theme
            systemUiController.setNavigationBarColor(
                    color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Color.Transparent
                    else scrim,
                    darkIcons = useDarkIcons
            )

            // setStatusBarColor() and setNavigationBarColor() also exist

            onDispose {}
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(topBar = {
            LargeTopAppBar(colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
                    title = {
                        Text(
                                getString(R.string.title_activity_settings),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = {
                            onBackPressedDispatcher.onBackPressed()
                        }) {
                            Icon(
                                    Icons.Default.ArrowBack, getString(
                                    R.string.back
                            )
                            )
                        }
                    })
        },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                    modifier = Modifier
                            .selectableGroup()
                            .waterfallPadding(),
                    contentPadding = it
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
        }
    }

    @Composable
    fun UploadDialog(
            uploading: Boolean,
            uploadStatus: String,
            shouldRetry: Boolean,
            onCancel: (() -> Unit)?,
            dismissDialog: () -> Unit,
            retry: (Uri, Context, () -> Unit) -> Unit,
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
                        retry(uri, this) {
                            launchLogin(this)
                        }
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
            Column(
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center,
                        modifier = Modifier
                                .weight(1f, fill = false)
                                .onGloballyPositioned {
                                    if (uri == null) return@onGloballyPositioned
                                    lifecycleScope.launch {
                                        bitmap = viewModel
                                                .getImageBitmap(uri, this@SettingsActivity, it.size,
                                                        false)
                                                ?.asImageBitmap()
                                    }
                                }) {
                    if (uploading) {
                        bitmap?.let {
                            Image(
                                    bitmap = it,
                                    contentDescription = getString(
                                            R.string.your_pfp_description
                                    ),
                                    modifier = Modifier.fillMaxSize(),
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
                        CircularProgressIndicator()
                    } else {
                        bitmap?.let {
                            Image(
                                    bitmap = it, contentDescription = getString(
                                    R.string.your_pfp_description
                            ), modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Text(
                        text = uploadStatus,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                )
            }
        })
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
        assert(!selected || tablet)
        if (tablet) {
            Box(
                    modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(0.9f)
                            .height(73.dp)
                            .clip(
                                    RoundedCornerShape(12.dp)
                            )
                            .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                            )
                            .selectable(selected = selected, onClick = onClick),
                    contentAlignment = Alignment.Center
            ) {
                Layout(content = {
                    icon?.invoke()
                    Text(
                            text = getString(title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            overflow = TextOverflow.Ellipsis,
                    )
                }, modifier = Modifier.fillMaxSize(), measurePolicy = { measurables,
                                                                        constraints ->
                    /*
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
                        iconPlaceable = measurables[0].measure(Constraints.fixed(ICON_SIZE.toPx()
                                .toInt(),
                                ICON_SIZE.toPx().toInt()))
                        text = measurables[1].measure(Constraints(
                                0,
                                constraints.maxWidth - iconPlaceable.width - paddingSize,
                                0,
                                constraints.maxHeight))

                        totalWidth = if (constraints.hasBoundedWidth) constraints.maxHeight
                        else iconPlaceable.width + paddingSize + text.width
                        totalHeight = if (constraints.hasBoundedHeight) constraints.maxHeight
                        else max(iconPlaceable.height, text.height)
                    }

                    layout(totalWidth, totalHeight) {
                        val textX = max((totalWidth - text.width) / 2,
                                (iconPlaceable?.width ?: -paddingSize) + paddingSize)
                        val textY = (totalHeight - text.height) / 2
                        text.place(x = textX, y = textY)
                        iconPlaceable?.place(
                                x = 0,
                                y = (totalHeight - iconPlaceable.height) / 2)
                    }
                })

            }
        } else {
            if (!first) Divider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp)
            Text(
                    text = getString(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = LIST_ELEMENT_PADDING, bottom
                    = LIST_ELEMENT_PADDING),
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
                            .weight(1f, fill = true),
                    contentAlignment = Alignment.CenterStart
            ) {
                largePreference()
            }
            Divider(
                    modifier = Modifier
                            .fillMaxHeight(0.6f)
                            .width(1.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium)
            )
            Box(
                    modifier = modifier
                            .padding(start = SPLIT_PREFERENCE_PADDING)
                            .size(PREFERENCE_HEIGHT),
                    contentAlignment = Alignment.Center
            ) {
                smallPreference()
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun <T> DialoguePreference(
            preference: ComposablePreference<T>,
            title: Int,
            modifier: Modifier = Modifier,
            action: (@Composable (value: T) -> Unit)? = null,
            summary: ((value: T) -> String)? = { value ->
                value.toString()
            },
            icon: (@Composable BoxScope
            .() -> Unit)? = null,
            reserveIconSpace: Boolean = true,
            titleColor: Color = MaterialTheme.colorScheme.onSurface,
            disabledTitleColor: Color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = ContentAlpha.medium
            ),
            titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
            summaryColor: Color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = ContentAlpha.medium
            ),
            summaryStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
            onPreferenceChanged: ComposablePreferenceChangeListener<T> = object :
                    ComposablePreferenceChangeListener<T> {
                override fun onPreferenceChange(
                        preference: ComposablePreference<T>, newValue: T
                ): Boolean {
                    return true
                }
            },
            enabled: Boolean = true,
            default: T? = null,
            dialog: @Composable (
                    preference: ComposablePreference<T>, dismissDialog: () -> Unit, context: Context, title: String
            ) -> Unit
    ) {
        val editing = rememberSaveable {
            mutableStateOf(false)
        }
        AnimatedContent(targetState = editing, transitionSpec = {
            slideIntoContainer(
                    towards = AnimatedContentScope.SlideDirection.Up
            ) with slideOutOfContainer(
                    towards = AnimatedContentScope.SlideDirection.Down
            )
        }) { targetState ->
            if (targetState.value) {
                dialog(preference = preference, dismissDialog = {
                    editing.value = false
                }, context = this@SettingsActivity, title = getString(title))
            }
        }
        Preference(
                preference = preference,
                title = title,
                action = action,
                summary = summary,
                modifier = modifier,
                icon = icon,
                reserveIconSpace = reserveIconSpace,
                titleColor = titleColor,
                disabledTitleColor = disabledTitleColor,
                titleStyle = titleStyle,
                summaryColor = summaryColor,
                summaryStyle = summaryStyle,
                onPreferenceChanged = onPreferenceChanged,
                enabled = enabled,
                onPreferenceClicked = {
                    editing.value = true
                    true
                },
                default = default
        )
    }

    @Composable
    fun <T> Preference(
            preference: ComposablePreference<T>,
            title: Int,
            modifier: Modifier = Modifier,
            action: (@Composable (value: T) -> Unit)? = null,
            summary: ((value: T) -> String)? = { value ->
                value.toString()
            },
            icon: (@Composable BoxScope
            .() -> Unit)? = null,
            reserveIconSpace: Boolean = true,
            titleColor: Color = MaterialTheme.colorScheme.onSurface,
            disabledTitleColor: Color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = ContentAlpha.medium
            ),
            titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
            summaryColor: Color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = ContentAlpha.medium
            ),
            summaryStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
            onPreferenceClicked: (preference: ComposablePreference<T>) -> Boolean = {
                false
            },
            onPreferenceChanged: ComposablePreferenceChangeListener<T> = object :
                    ComposablePreferenceChangeListener<T> {
                override fun onPreferenceChange(
                        preference: ComposablePreference<T>, newValue: T
                ): Boolean {
                    return true
                }
            },
            enabled: Boolean = true,
            default: T? = null
    ) {
        preference.onPreferenceChangeListener.add(onPreferenceChanged)
        val value = if (default != null) preference.getValue(default) else preference.value
        Row(
                modifier = modifier
                        .fillMaxWidth()
                        .height(73.dp)
                        .clickable(enabled = enabled, onClick = {
                            onPreferenceClicked(preference)
                        }),
                verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null || reserveIconSpace) {
                val iconSpot: @Composable BoxScope.() -> Unit = icon ?: { }
                Box(
                        modifier = Modifier
                                .padding(ICON_PADDING)
                                .size(ICON_SIZE),
                        contentAlignment = Alignment.Center,
                        content = iconSpot
                )
            }
            Column(modifier = modifier
                    .fillMaxHeight()
                    .weight(1f, fill = true),
                    verticalArrangement = Arrangement
                            .Center) {
                Text(
                        text = getString(title), style = titleStyle, color = if (enabled) titleColor
                else disabledTitleColor
                )
                if (summary != null) Text(
                        text = summary(preference.value), style = summaryStyle, color = summaryColor
                )
            }
            if (action != null) Box(
                    modifier = Modifier.size(PREFERENCE_HEIGHT), contentAlignment = Alignment.Center
            ) { action(value) }
        }
    }

    class UserInfoChangeListener(
            private val context: Activity,
            private val model: SettingsViewModel,
            private val snackbarHostState: SnackbarHostState,
            private val coroutineScope: CoroutineScope
    ) : ComposablePreferenceChangeListener<String> {
        private val attentionRepository = AttentionRepository(AttentionDB.getDB(context))


        private fun onResponse(code: Int, newValue: Any?, key: String) {
            model.outstandingRequests--
            val message = when (code) {
                200 -> {
                    StringPreference(key, context).value = newValue.toString()
                    if (model.outstandingRequests == 0) {
                        R.string.saved
                    }
                    null
                }
                400 -> {
                    R.string.invalid_email
                }
                403 -> {
                    launchLogin(context)
                    R.string.confirm_logout_title
                }
                429 -> {
                    R.string.rate_limited
                }
                else -> {
                    R.string.unknown_error
                }
            }
            if (message != null) coroutineScope.launch {
                snackbarHostState.showSnackbar(
                        context.getString(message),
                        withDismissAction = false,
                        duration = SnackbarDuration.Long
                )
            }
        }

        private fun onError() {
            model.outstandingRequests--
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                        context.getString(R.string.disconnected), duration = SnackbarDuration.Long
                )
            }
        }

        override fun onPreferenceChange(
                preference: ComposablePreference<String>, newValue: String
        ): Boolean {
            val token = context.getSharedPreferences(
                    USER_INFO, Context.MODE_PRIVATE
            ).getString(MY_TOKEN, null)
            if (token != null) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                            context.getString(R.string.saving),
                            actionLabel = context.getString(android.R.string.ok),
                            withDismissAction = true,
                            duration = SnackbarDuration.Indefinite
                    )
                }
                model.outstandingRequests++
                when (preference.key) {
                    context.getString(R.string.first_name_key) -> {
                        attentionRepository.editUser(token = token,
                                firstName = newValue,
                                responseListener = { _, response, _ ->
                                    onResponse(
                                            response.code(),
                                            newValue,
                                            preference.key
                                    )
                                },
                                errorListener = { _, _ ->
                                    onError()
                                })
                    }
                    context.getString(R.string.last_name_key) -> {
                        attentionRepository.editUser(token = token,
                                lastName = newValue,
                                responseListener = { _, response, _ ->
                                    onResponse(
                                            response.code(),
                                            newValue,
                                            preference.key
                                    )
                                },
                                errorListener = { _, _ ->
                                    onError()
                                })
                    }
                    context.getString(R.string.email_key) -> {
                        attentionRepository.editUser(token = token,
                                email = newValue,
                                responseListener = { _, response, _ ->
                                    onResponse(
                                            response.code(),
                                            newValue,
                                            preference.key
                                    )
                                },
                                errorListener = { _, _ ->
                                    onError()
                                })
                    }
                }
            } else {
                launchLogin(context)
            }
            return false
        }

    }

    companion object {
        val PREFERENCE_HEIGHT = 73.dp
        val SPLIT_PREFERENCE_PADDING = 5.dp
        val ICON_SIZE = 24.dp
        val ICON_PADDING = 16.dp
        const val TEMP_PFP = "${MainViewModel.PFP_FILENAME}_temp"
        const val UPLOAD_GRAY_INTENSITY = 0.5f

        private fun launchLogin(context: Context) {
            val loginIntent = Intent(context, LoginActivity::class.java)
            context.startActivity(loginIntent)
        }
    }
}