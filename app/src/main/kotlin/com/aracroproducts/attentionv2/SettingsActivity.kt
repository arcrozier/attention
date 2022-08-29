package com.aracroproducts.attentionv2

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
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
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.aracroproducts.attentionv2.LoginActivity.Companion.UsernameField
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.MY_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.USER_INFO
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The class for the settings menu in the app
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels(factoryProducer = {
        SettingsViewModelFactory(AttentionRepository(AttentionDB.getDB(this)), application)
    })


    private val cropResultHandler = registerForActivityResult(ActivityResultContracts
            .StartActivityForResult()) { result ->
        result.data?.let { data ->
            val uri = UCrop.getOutput(data) ?: return@let
            viewModel.uploadImage(uri, this) { launchLogin(this)}
        }
    }

    // Registers a photo picker activity launcher in single-select mode.
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            // crop then upload https://github.com/Yalantis/uCrop
            // https://stackoverflow.com/questions/3879992/how-to-get-bitmap-from-an-uri
            val cropIntent = UCrop.of(uri, Uri.fromFile(File(filesDir, TEMP_PFP)))
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
            UploadDialog(uploading = viewModel.uploading, uploadStatus = viewModel.uploadStatus,
                    shouldRetry = viewModel.shouldRetryUpload,
                    onCancel = viewModel.onCancel,
                    dismissDialog = { viewModel.uploadDialog = false },
                    retry = viewModel::uploadImage, uri = viewModel.uri)
        }

        val userInfoChangeListener =
                UserInfoChangeListener(this, viewModel, snackbarHostState, coroutineScope)
        val preferences = listOf(Pair<Int, @Composable () -> Unit>(R.string.account) @Composable {
            SplitPreference(largePreference = {
                DialoguePreference(preference = StringPreference(
                        getString(R.string.username_key), this
                ), title = R.string.username,
                        dialog = { preference, dismissDialog, context, title ->
                            var newValue by remember { mutableStateOf(preference.value) }
                            var loading by remember { mutableStateOf(false) }
                            var error by remember { mutableStateOf(false) }
                            var usernameCaption by remember { mutableStateOf("") }
                            val token = context.getSharedPreferences(
                                    USER_INFO, Context.MODE_PRIVATE
                            ).getString(MY_TOKEN, null)
                            if (token == null) {
                                val loginIntent = Intent(context, LoginActivity::class.java)
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
                                    }, confirmButton = {
                                Button(onClick = {
                                    loading = true
                                    AttentionRepository(AttentionDB.getDB(this)).editUser(
                                            token = token,
                                            username = newValue,
                                            responseListener = { _, response, _ ->
                                                loading = false
                                                error = !response.isSuccessful
                                                when (response.code()) {
                                                    200 -> {
                                                        usernameCaption = ""
                                                        preference.value = newValue
                                                        dismissDialog()
                                                    }
                                                    400 -> {
                                                        usernameCaption =
                                                                getString(R.string.username_in_use)
                                                    }
                                                    403 -> {
                                                        usernameCaption = ""
                                                        dismissDialog()
                                                        launchLogin(this)
                                                    }
                                                    else -> {
                                                        usernameCaption =
                                                                getString(R.string.unknown_error)
                                                    }
                                                }
                                            },
                                            errorListener = { _, _ ->
                                                loading = false
                                                error = true
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                            context.getString(
                                                                    R.string.disconnected),
                                                            duration = SnackbarDuration.Long
                                                    )
                                                }
                                            })
                                }, content = {
                                    Text(text = if (loading) getString(R.string.saving) else
                                        getString(android.R.string.ok))
                                })

                            }, title = {
                                Text(text = title)
                            }, text = {
                                UsernameField(value = newValue, onValueChanged = { newValue = it },
                                        newUsername = true, enabled = !loading, error = error,
                                        caption = usernameCaption, this)
                            })
                        },
                        icon = {
                            Box(modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        // Launch the photo picker and allow the user to choose only images.
                                        // https://developer.android.com/training/data-storage/shared/photopicker
                                        pickMedia.launch(PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }) {
                                viewModel.photo?.let {
                                    Image(bitmap = it,
                                            contentDescription = getString(
                                                    R.string.your_pfp_description),
                                            modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .align(Alignment.Center))
                                }
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
                    Icon(Icons.Default.Share, contentDescription = getString(R.string.share))
                }
            })

            DialoguePreference(
                    preference = StringPreference(getString(R.string.email_key), this),
                    title = R.string.email,
                    dialog = { preference, dismissDialog, context, title ->
                        StringPreferenceChange(preference, dismissDialog, context, title)
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
            ), title = R.string.password, summary = null, onPreferenceClicked = {
                val intent = Intent(this, LoginActivity::class.java)
                intent.action = getString(R.string.change_password_action)
                startActivity(intent)
                true
            })
            Preference(preference = EphemeralPreference(
                    getString(R.string.link_account_key), null
            ),
                    title = R.string.link_account,
                    summary = null,
                    onPreferenceClicked = {
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.action = getString(R.string.link_account_action)
                        startActivity(intent)
                        true
                    },
                    enabled = BooleanPreference(getString(R.string.password_key), this)
                            .getValue(true)
            )
            DialoguePreference(
                    preference = EphemeralPreference(getString(R.string.logout_key), null),
                    title = R.string.confirm_logout_title,
                    titleColor = MaterialTheme.colorScheme.error
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
                                val fcmTokenPrefs =
                                        context.getSharedPreferences(FCM_TOKEN,
                                                Context.MODE_PRIVATE)
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

        }, Pair<Int, @Composable () -> Unit>(R.string.app_preference_category) @Composable {
            DialoguePreference(
                    preference = FloatPreference(getString(R.string.delay_key), this),
                    title = R.string.delay_title,
                    default = 3.5f
            ) { preference, dismissDialog, context, title ->
                FloatPreferenceChange(
                        preference = preference,
                        dismissDialog = dismissDialog,
                        context = context,
                        title = title
                )
            }
        }, Pair<Int, @Composable () -> Unit>(R.string.notifications_title) @Composable {
            DialoguePreference(preference = StringSetPreference(
                    key = getString(R.string.vibrate_preference_key), context = this
            ),
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
            ), title = R.string.override_dnd, default = false, summary = {
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
                                        NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted) {
                            true
                        } else {
                            showDNDAlert.value = true
                            false
                        }

                    }
                    return false
                }
            })
        }, Pair<Int, @Composable () -> Unit>(R.string.legal_title) @Composable {
            Preference(preference = EphemeralPreference("", null),
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
            preferences: List<Pair<Int, @Composable () -> Unit>>,
            selected: Int,
            screenClass: WindowWidthSizeClass,
            onGroupSelected: (key: Int, preferences: @Composable () -> Unit) -> Unit,
            snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(topBar = {
            LargeTopAppBar(colors = TopAppBarDefaults.largeTopAppBarColors(containerColor =
            MaterialTheme.colorScheme.primaryContainer), title = {
                Text(
                        getString(R.string.title_activity_settings),
                        color = MaterialTheme.colorScheme.onPrimary
                )
            },
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    scrollBehavior = scrollBehavior,
                            navigationIcon = {
                IconButton(onClick = {
                    onBackPressedDispatcher.onBackPressed()
                }) {
                    Icon(
                            Icons.Default.ArrowBack, getString(
                            R.string.back
                    ), tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            })
        },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                    modifier = Modifier
                            .selectableGroup()
                            .padding(it)
            ) {
                items(items = preferences, key = { preference -> preference.first }) { preference ->
                    PreferenceGroup(
                            title = preference.first,
                            onClick = {
                                onGroupSelected(preference.first, preference.second)
                            },
                            selected = preference.first == selected,
                            tablet = screenClass != WindowWidthSizeClass.Compact,
                            preferences = preference.second,
                    )
                }
            }
        }
    }

    @Composable
    fun UploadDialog(uploading: Boolean, uploadStatus: String, shouldRetry: Boolean, onCancel: (
    () -> Unit)?, dismissDialog: () -> Unit, retry: (Uri, Context, () -> Unit) -> Unit, uri: Uri?) {
        var bitmap: ImageBitmap? by remember{
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
                    OutlinedButton(onClick = { retry(uri, this) {
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
            Column(verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f, fill = false).onGloballyPositioned {
                        if (uri == null) return@onGloballyPositioned
                        lifecycleScope.launch(Dispatchers.IO) {
                            bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(
                                    ImageDecoder.createSource(contentResolver, uri)
                                ) { decoder, _, _ ->
                                    decoder.setTargetSize(it.size.width, it.size.height) }
                            } else {
                                BitmapFactory.Options().run {
                                    var input = contentResolver.openInputStream(uri)
                                    inJustDecodeBounds = true
                                    BitmapFactory.decodeStream(input, null, this)
                                    input?.close()
                                    // Calculate inSampleSize
                                    val (height: Int, width: Int) = run { outHeight to outWidth }
                                    var inSampleSize = 1

                                    if (height > it.size.height || width > it.size.width) {

                                        val halfHeight: Int = height / 2
                                        val halfWidth: Int = width / 2

                                        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                                        // height and width larger than the requested height and width.
                                        while (halfHeight / inSampleSize >= it.size.height && halfWidth / inSampleSize >= it.size.width) {
                                            inSampleSize *= 2
                                        }
                                    }

                                    // Decode bitmap with inSampleSize set
                                    inJustDecodeBounds = false
                                    input = contentResolver.openInputStream(uri)

                                    BitmapFactory.decodeStream(input, null, this)
                                }
                            }?.asImageBitmap()
                        }
                }) {
                    if (uploading) {
                        bitmap?.let {
                            Image(bitmap = it, contentDescription = getString(R.string
                                    .your_pfp_description), modifier = Modifier.fillMaxSize(),
                                    colorFilter =
                                    ColorFilter.tint(Color
                            (UPLOAD_GRAY_INTENSITY, UPLOAD_GRAY_INTENSITY, UPLOAD_GRAY_INTENSITY, 1f),
                                    BlendMode.Screen))
                        }
                        CircularProgressIndicator()
                    } else {
                        bitmap?.let {
                            Image(bitmap = it, contentDescription = getString(R.string
                                    .your_pfp_description), modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                Text(text = uploadStatus, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            }
        })
    }

    @Composable
    fun PreferenceGroup(
            title: Int,
            selected: Boolean,
            tablet: Boolean,
            onClick: () -> Unit,
            preferences: @Composable () -> Unit
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
                Text(
                        text = getString(title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            Text(
                    text = getString(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
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
                        .height(PREFERENCE_HEIGHT)
        ) {
            Box(modifier = modifier
                    .padding(end = PREFERENCE_PADDING)
                    .fillMaxSize(),
                    contentAlignment = Alignment.CenterStart) {
                largePreference()
            }
            Divider(modifier = Modifier
                    .fillMaxHeight(0.95f)
                    .width(Dp.Hairline), color =
            MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium))
            Box(modifier = modifier
                    .padding(start = PREFERENCE_PADDING)
                    .size(PREFERENCE_HEIGHT),
                    contentAlignment = Alignment.Center) {
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
            titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
            summaryColor: Color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = ContentAlpha.medium
            ),
            summaryStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall,
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
            titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
            summaryColor: Color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = ContentAlpha.medium
            ),
            summaryStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall,
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
                        })
        ) {
            if (icon != null || reserveIconSpace) {
                val iconSpot: @Composable BoxScope.() -> Unit = icon ?: { }
                Box(
                        modifier = Modifier
                                .size(24.dp)
                                .padding(16.dp),
                        contentAlignment = Alignment.Center,
                        content = iconSpot
                )
            }
            Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
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
        val PREFERENCE_PADDING = 5.dp
        const val TEMP_PFP = "${MainViewModel.PFP_FILENAME}_temp"
        const val UPLOAD_GRAY_INTENSITY = 0.5f

        private fun launchLogin(context: Context) {
            val loginIntent = Intent(context, LoginActivity::class.java)
            context.startActivity(loginIntent)
        }
    }

    /**
     * A fragment for individual settings panels
     *
    class SettingsFragment(private val viewModel: SettingsViewModel) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.root_preferences, rootKey)
    val localContext = activity ?: return
    val userInfoChangeListener = UserInfoChangeListener(
    localContext, this, viewModel, findPreference = this::findPreference
    )
    val firstName: EditTextPreference? = findPreference(getString(R.string.first_name_key))
    if (firstName != null) {
    firstName.onPreferenceChangeListener = userInfoChangeListener
    }

    val lastName: Preference? = findPreference(getString(R.string.last_name_key))
    if (lastName != null) {
    lastName.onPreferenceChangeListener = userInfoChangeListener
    }

    val email: Preference? = findPreference(getString(R.string.email_key))
    if (email != null) {
    email.onPreferenceChangeListener = userInfoChangeListener
    }

    val usernamePreference: Preference? = findPreference(getString(R.string.username_key))
    if (usernamePreference != null) {
    usernamePreference.onPreferenceClickListener =
    Preference.OnPreferenceClickListener {
    val username =
    PreferenceManager.getDefaultSharedPreferences(localContext)
    .getString(MainViewModel.MY_ID, null)
    if (username != null) {
    val sharingIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    val shareBody = getString(R.string.share_text, username)
    putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    startActivity(Intent.createChooser(sharingIntent, null))
    }
    true
    }
    usernamePreference.summary =
    PreferenceManager.getDefaultSharedPreferences(localContext).getString(
    getString(R.string.username_key), getString(R.string.no_username)
    )
    }

    val logoutPreference: Preference? = findPreference(getString(R.string.logout_key))
    if (logoutPreference != null) {
    logoutPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
    AlertDialog.Builder(localContext).apply {
    setTitle(getString(R.string.confirm_logout_title))
    setMessage(getString(R.string.confirm_logout_message))
    setPositiveButton(R.string.confirm_logout_title) { dialog, _ ->
    val userInfo = localContext.getSharedPreferences(
    MainViewModel.USER_INFO, Context.MODE_PRIVATE
    )
    val fcmTokenPrefs =
    context.getSharedPreferences(FCM_TOKEN, Context.MODE_PRIVATE)
    viewModel.unregisterDevice(
    userInfo.getString(MY_TOKEN, null) ?: "",
    fcmTokenPrefs.getString(FCM_TOKEN, null) ?: ""
    )
    userInfo.edit().apply {
    putString(MY_TOKEN, null)
    apply()
    }
    PreferenceManager.getDefaultSharedPreferences(localContext).edit()
    .apply {
    putString(getString(R.string.username_key), null)
    putString(getString(R.string.first_name_key), null)
    putString(getString(R.string.last_name_key), null)
    putString(getString(R.string.email_key), null)
    apply()
    }
    viewModel.clearAllDatabaseTables()
    activity?.finish()
    localContext.startActivity(
    Intent(
    localContext, LoginActivity::class.java
    )
    )
    dialog.dismiss()
    }
    setNegativeButton(android.R.string.cancel, null)
    show()
    }
    true
    }
    }

    val delayPreference =
    findPreference(getString(R.string.delay_key)) as EditTextPreference?
    delayPreference?.setOnBindEditTextListener {
    it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    val vibratePreference =
    findPreference(getString(
    R.string.vibrate_preference_key)) as MultiSelectListPreference?
    if (vibratePreference != null) {
    vibratePreference.onPreferenceChangeListener =
    Preference.OnPreferenceChangeListener { _, newValue ->
    vibratePreference.setSummaryFromValues(
    (newValue as? Set<*>) ?: HashSet<String>()
    )
    true
    }
    vibratePreference.setSummaryFromValues(vibratePreference.values)
    }

    val ringPreference =
    findPreference(
    getString(R.string.ring_preference_key)) as MultiSelectListPreference?
    if (ringPreference != null) {
    ringPreference.onPreferenceChangeListener =
    Preference.OnPreferenceChangeListener { _, newValue ->
    ringPreference.setSummaryFromValues(
    (newValue as? Set<*>) ?: HashSet<String>()
    )
    true
    }
    ringPreference.setSummaryFromValues(ringPreference.values)
    }

    val dndPreference =
    findPreference(getString(R.string.override_dnd_key)) as SwitchPreference?
    val manager = context?.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (!manager.isNotificationPolicyAccessGranted) {
    dndPreference?.isChecked = false
    }
    dndPreference?.onPreferenceChangeListener =
    Preference.OnPreferenceChangeListener { _, newValue ->
    if (newValue is Boolean) {
    if (newValue) {
    if (manager.isNotificationPolicyAccessGranted) {
    return@OnPreferenceChangeListener true
    } else {
    AlertDialog.Builder(localContext).apply {
    setTitle(R.string.allow_dnd_title)
    setMessage(R.string.allow_dnd_message)
    setPositiveButton(R.string.open_settings) { dialog, _ ->
    val intent = Intent(
    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
    )
    startActivity(intent)
    dialog.cancel()
    }
    setNegativeButton(android.R.string.cancel) { dialog, _ ->
    dialog.cancel()
    }
    show()
    }
    return@OnPreferenceChangeListener false
    }
    }
    return@OnPreferenceChangeListener true
    }
    throw IllegalArgumentException(
    "Non-boolean value provided to switch on-change " + "listener!"
    )
    } // on change - check whether we actually can override DND
    // see https://developer.android.com/reference/android/app/NotificationManager#isNotificationPolicyAccessGranted()
    // if not, display a prompt asking the user to go to settings
    }

    private fun MultiSelectListPreference.setSummaryFromValues(values: Set<*>) {
    summary = values.joinToString(", ") { entries[findIndexOfValue(it as? String ?: "")] }
    }
    }*/
}