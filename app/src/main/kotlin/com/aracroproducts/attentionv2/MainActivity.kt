package com.aracroproducts.attentionv2

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.KeyEvent.KEYCODE_ENTER
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.waterfallPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Integer.max
import kotlin.collections.set
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private val friendModel: MainViewModel by viewModels(factoryProducer = {
        MainViewModelFactory(
            AttentionRepository(AttentionDB.getDB(this)),
            (application as AttentionApplication).container.settingsRepository,
            (application as AttentionApplication).container.applicationScope,
            application as AttentionApplication
        )
    })

    class MainViewModelFactory(
        private val attentionRepository: AttentionRepository,
        private val preferencesRepository: PreferencesRepository,
        private val applicationScope: CoroutineScope,
        private val application: AttentionApplication
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(
                    attentionRepository, preferencesRepository, applicationScope, application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    enum class State {
        NORMAL, CONFIRM, CANCEL, EDIT
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { // there isn't really anything we can do
        }

    // TODO test what actually happens a) if a user gets signed out in the background as they send an alert b) a user gets signed out but replies to an alert in the notification
    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        it.data?.extras?.getString(MainViewModel.MY_TOKEN)?.let { token ->
            reload(token)
        }
        friendModel.waitForLoginResult = false

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it.data?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            it.data?.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }

        if (intent != null) {
            handleIntent(intent, null)
        }
    }

    private fun launchLogin(resume: Intent? = null) {
        friendModel.waitForLoginResult = true
        friendModel.logout(this)
        loginLauncher.launch(Intent(this, LoginActivity::class.java).putExtra(Intent.EXTRA_INTENT, resume))
    }

    /**
     * Called when the activity is created
     *
     * @param savedInstanceState    Instance data saved from before the activity was killed
     */
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(0xFFFFFFFF.toInt(), 0x000000FF))

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HarmonizedTheme {
                    HomeWrapper(model = friendModel)
                }
            } else {
                AppTheme {
                    HomeWrapper(model = friendModel)
                }
            }
        }

        // Creates a notification channel for displaying failed-to-send notifications
        MainViewModel.createFailedAlertNotificationChannel(this)

        handleIntent(intent, savedInstanceState)

        if (!checkPlayServices()) return

        friendModel.loadUserPrefs()

        checkOverlayDisplay()
    }

    private fun handleIntent(intent: Intent?, savedInstanceState: Bundle? = null) {
        val action: String? = intent?.action
        val data: Uri? = intent?.data

        friendModel.addFriendException = action == Intent.ACTION_VIEW && savedInstanceState == null


        if (friendModel.addFriendException) {
            val tempId = data?.getQueryParameter(USERNAME_QUERY_PARMETER)
            if (tempId == null) {
                friendModel.showSnackBar(getString(R.string.bad_add_link))
            } else {
                synchronized(this) {
                    friendModel.addFriendUsername = tempId
                    friendModel.getFriendName(tempId) {
                        throw IllegalStateException(
                            "Should not attempt to log in while " + "addFriendException is true"
                        )
                    }
                    friendModel.swapDialogState(
                        MainViewModel.DialogStatus.AddFriend
                    )
                }
            }
        }

        // this condition is met if the app was launched by someone tapping it on a share sheet
        if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            friendModel.connectionState = getString(R.string.sharing)
            friendModel.message = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && intent.hasExtra(
                    Intent.EXTRA_SHORTCUT_ID
                )
            ) {
                val username = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID) ?: ""
                lifecycleScope.launch {
                    val friend = friendModel.getFriend(username)
                    friendModel.appendDialogState(MainViewModel.DialogStatus.AddMessageText(friend) { message ->
                        friendModel.message = message
                        friendModel.cardStatus[friend.id] = State.CANCEL
                    })
                }

            }
        }

        if (action == getString(R.string.reopen_failed_alert_action) || action == Intent.ACTION_SENDTO) {
            lifecycleScope.launch {
                intent.getStringExtra(MainViewModel.EXTRA_RECIPIENT)?.let {
                    val friend = friendModel.getFriend(it)
                    if (action == Intent.ACTION_SENDTO) {
                        ShortcutManagerCompat.reportShortcutUsed(this@MainActivity, friend.id)
                    }
                    friendModel.appendDialogState(MainViewModel.DialogStatus.AddMessageText(
                        friend
                    ) { message ->
                        friendModel.message = message
                        friendModel.cardStatus[friend.id] = State.CANCEL
                    })
                }
            }
        }
    }

    private fun reload(token: String? = null) { // token is auth token
        friendModel.getUserInfo(onAuthError = {
            if (!friendModel.addFriendException) {
                launchLogin()
            }
        }, onSuccess = {
            getNotificationPermission()
            friendModel.registerDevice()
        }, token = token)
    }

    private fun getNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    application, POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> { // You can use the API that requires the permission.
                }

                shouldShowRequestPermissionRationale(POST_NOTIFICATIONS) -> { // In an educational UI, explain to the user why your app requires this
                    // permission for a specific feature to behave as expected. In this UI,
                    // include a "cancel" or "no thanks" button that allows the user to
                    // continue using your app without granting the permission.
                    friendModel.appendDialogState(
                        MainViewModel.DialogStatus.PermissionRationale(POST_NOTIFICATIONS)
                    )
                }

                else -> { // You can directly ask for the permission.
                    // The registered ActivityResultCallback gets the result of this request.
                    requestPermissionLauncher.launch(
                        POST_NOTIFICATIONS
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun checkOverlayDisplay() {
        friendModel.checkOverlayPermission()
    }


    @ExperimentalFoundationApi
    @Composable
    fun HomeWrapper(model: MainViewModel) {
        val displayDialog: MainViewModel.DialogStatus by model.dialogState
        val showSnackbar = model.isSnackBarShowing

        val friends by model.friends.observeAsState(listOf())
        Home(
            friends = friends,
            onLongPress = { model.onLongPress() },
            onEditName = { model.onEditName(it) },
            onDeletePrompt = { model.onDeleteFriend(it) },
            dialogState = displayDialog,
            showSnackbar = showSnackbar
        )
    }

    @OptIn(
        ExperimentalMaterial3Api::class
    )
    @ExperimentalFoundationApi
    @Composable
    fun Home(
        friends: List<Friend>,
        onLongPress: () -> Unit,
        onEditName: (friend: Friend) -> Unit,
        onDeletePrompt: (friend: Friend) -> Unit,
        dialogState: MainViewModel.DialogStatus,
        showSnackbar: String
    ) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                MaterialTheme.colorScheme.scrim.toArgb(),
                MaterialTheme.colorScheme.scrim.toArgb()
            )
        )

        val cachedFriends by friendModel.cachedFriends.observeAsState(listOf())
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
        val scope = rememberCoroutineScope()
        if (showSnackbar.isNotBlank()) {
            LaunchedEffect(showSnackbar) {
                scope.launch { // cancels by default after a short amount of time

                    when (snackbarHostState.showSnackbar(message = showSnackbar)) {
                        SnackbarResult.Dismissed -> {
                            friendModel.dismissSnackBar()
                        }

                        else -> {}
                    }
                }
            }
        }

        when (dialogState) {
            is MainViewModel.DialogStatus.AddFriend -> AddFriendDialog()
            is MainViewModel.DialogStatus.OverlayPermission -> OverlaySettingsDialog()
            is MainViewModel.DialogStatus.AddMessageText -> {
                AddMessageText(friend = dialogState.friend, onSend = dialogState.onSend)
            }

            is MainViewModel.DialogStatus.FriendName -> {
                EditFriendNameDialog(
                    friend = dialogState.friend
                )
            }

            is MainViewModel.DialogStatus.ConfirmDelete -> {
                DeleteFriendDialog(
                    friend = dialogState.friend
                )
            }

            is MainViewModel.DialogStatus.ConfirmDeleteCached -> {
                DeleteFriendDialog(friend = dialogState.friend)
            }

            is MainViewModel.DialogStatus.PermissionRationale -> {
                PermissionRationale(permission = dialogState.permission) {}
            }

            is MainViewModel.DialogStatus.None -> {}
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(topBar = {
            LargeTopAppBar(colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                scrolledContainerColor = MaterialTheme.colorScheme.primary
            ), title = {
                Column {
                    Text(
                        getString(R.string.app_name)
                    )
                    if (friendModel.connectionState.isNotBlank()) Text(
                        friendModel.connectionState,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }, actions = {
                IconButton(onClick = {
                    val intent = Intent(
                        applicationContext, SettingsActivity::class.java
                    )
                    startActivity(intent)
                }) {
                    Icon(
                        Icons.Filled.Settings, contentDescription = getString(
                            R.string.action_settings
                        ), tint = MaterialTheme.colorScheme.onPrimary
                    )

                }
            }, scrollBehavior = scrollBehavior
            )
        },
            contentColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.nestedScroll(
                scrollBehavior.nestedScrollConnection
            ),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        friendModel.appendDialogState(
                            MainViewModel.DialogStatus.AddFriend
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                ) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = getString(R.string.add_friend),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = friendModel.isRefreshing,
                onRefresh = { reload() },
            ) {
                AnimatedContent(
                    targetState = friends.isEmpty() && !friendModel.isRefreshing && friendModel.connected,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    }, label = "friendsList"
                ) { targetState ->
                    when (targetState) {
                        true -> {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .waterfallPadding()
                                    .padding(paddingValues)
                                    .consumeWindowInsets(paddingValues)
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = getString(R.string.no_friends),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .alpha(ContentAlpha.disabled),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }

                        }

                        false -> {
                            LazyColumn(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .waterfallPadding()
                                    .fillMaxSize()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = paddingValues
                            ) {
                                // TODO pending friends
                                items(items = friends, key = { friend ->
                                    friend.id
                                }) { friend ->
                                    FriendCard(friend = friend,
                                        onLongPress = onLongPress,
                                        onEditName = onEditName,
                                        onDeletePrompt = onDeletePrompt,
                                        modifier = Modifier.animateItem(),
                                        state = friendModel.cardStatus.getOrDefault(
                                            friend.id, State.NORMAL
                                        ),
                                        onStateChange = { newState ->
                                            friendModel.cardStatus[friend.id] = newState
                                        })
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(
                                            alpha = ContentAlpha.disabled
                                        ), modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                                    )
                                }
                                items(cachedFriends) { cachedFriend ->
                                    FriendCard(friend = Friend(
                                        cachedFriend.username, cachedFriend.username
                                    ),
                                        onLongPress = {},
                                        onEditName = {},
                                        onDeletePrompt = {},
                                        state = friendModel.cardStatus.getOrDefault(
                                            cachedFriend.username, State.NORMAL
                                        ),
                                        onStateChange = { newState ->
                                            friendModel.cardStatus[cachedFriend.username] =
                                                newState
                                        }

                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(
                                            alpha = ContentAlpha.disabled
                                        ), modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                                    )
                                }
                                item {
                                    Spacer(
                                        modifier = Modifier.height(
                                            WindowInsets.Companion.navigationBars.getBottom(
                                                LocalDensity.current
                                            ).dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    @Composable
    fun AddMessageText(friend: Friend, onSend: (message: String) -> Unit) {

        AlertDialog(onDismissRequest = { friendModel.popDialogState() }, confirmButton = {
            Button(onClick = {
                val message = friendModel.message
                friendModel.message = ""
                if (friendModel.connectionState == getString(R.string.sharing)) {
                    friendModel.connectionState = ""
                }
                friendModel.popDialogState()
                onSend(message)
            }) {
                Text(text = getString(R.string.send))
            }
        }, dismissButton = {
            OutlinedButton(onClick = {
                friendModel.popDialogState()
            }) {
                Text(text = getString(android.R.string.cancel))
            }
        }, title = {
            Text(
                text = getString(R.string.add_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }, text = {
            OutlinedTextField(colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
                value = friendModel.message,
                onValueChange = { friendModel.message = it },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                label = {
                    Text(
                        text = getString(
                            R.string.message_label, friend.name
                        )
                    )
                },
                placeholder = { Text(text = getString(R.string.message_hint)) })
        })
    }

    @Composable
    fun DeleteFriendDialog(friend: Friend, cached: Boolean = false) {
        AlertDialog(onDismissRequest = { friendModel.popDialogState() }, confirmButton = {
            Button(onClick = {
                friendModel.popDialogState()
                if (cached) friendModel.confirmDeleteCachedFriend(friend)
                else friendModel.confirmDeleteFriend(friend = friend, ::launchLogin)
            }) {
                Text(text = getString(R.string.delete))
            }
        }, dismissButton = {
            OutlinedButton(onClick = {
                friendModel.popDialogState()
            }) {
                Text(text = getString(android.R.string.cancel))
            }
        }, title = {
            Text(
                text = getString(R.string.confirm_delete_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }, text = {
            Text(
                text = getString(R.string.confirm_delete_message, friend.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        })
    }

    @Composable
    fun EditFriendNameDialog(friend: Friend) {
        var name by rememberSaveable {
            mutableStateOf(friend.name)
        }
        var error by remember { mutableStateOf(false) }

        fun done() {
            val savingName = name.trim()
            if (savingName.isEmpty()) {
                error = true
            } else {
                friendModel.confirmEditName(friend.id, savingName, ::launchLogin)
                friendModel.popDialogState()
            }
        }
        AlertDialog(onDismissRequest = { friendModel.popDialogState() }, confirmButton = {
            Button(onClick = {
                done()
            }) {
                Text(text = getString(R.string.save))
            }
        }, dismissButton = {
            OutlinedButton(onClick = {
                friendModel.popDialogState()
            }) {
                Text(text = getString(android.R.string.cancel))
            }
        }, title = {
            Text(
                text = getString(R.string.rename),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }, text = {
            OutlinedTextField(colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
                value = name,
                onValueChange = { name = filterSpecialChars(it) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    done()
                }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent {
                        if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER) {
                            done()
                            true
                        } else false
                    },
                label = { Text(text = getString(R.string.name)) },
                isError = error,
                placeholder = { Text(text = getString(R.string.new_name)) })
        })
    }

    @Composable
    fun OverlaySettingsDialog() {
        AlertDialog(onDismissRequest = { friendModel.popDialogState() }, confirmButton = {
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + applicationContext.packageName)
                )
                friendModel.popDialogState()
                startActivity(intent)
            }) {
                Text(text = getString(R.string.open_settings))
            }
        }, dismissButton = {
            OutlinedButton(onClick = {
                friendModel.setPreference(
                    booleanPreferencesKey(MainViewModel.OVERLAY_NO_PROMPT), true
                )
                friendModel.popDialogState()
            }) {
                Text(text = getString(R.string.do_not_ask_again))
            }
        }, title = {
            Text(
                text = getString(R.string.draw_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }, text = {
            Text(
                text = getString(R.string.draw_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        })
    }

    private fun onAddFriend(username: String) {
        val savingName = username.trim()
        if (savingName.isBlank()) {
            friendModel.usernameCaption = getString(R.string.empty_username)
        } else {
            friendModel.getFriendName(username, responseListener = {
                friendModel.onAddFriend(
                    Friend(
                        username, it
                    ), onSuccess = {
                        friendModel.popDialogState()
                            friendModel.newFriendName = ""
                            friendModel.addFriendUsername = ""
                            reload()
                    }, launchLogin = this::launchLogin
                )
            }, launchLogin = ::launchLogin)
        }
    }

    @Composable
    fun AddFriendDialog() {
        AlertDialog(onDismissRequest = {
            if (friendModel.addFriendException) {
                friendModel.addFriendException = false
                reload()
            }
            friendModel.popDialogState()
        }, dismissButton = {
            OutlinedButton(onClick = {
                friendModel.popDialogState()
            }) {
                Text(getString(android.R.string.cancel))
            }
        }, confirmButton = {
            Button(onClick = {
                onAddFriend(friendModel.addFriendUsername)
            }) {
                Text(getString(android.R.string.ok))
            }
        }, title = {
            Text(
                text = getString(R.string.add_friend),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }, text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = friendModel.newFriendName,
                    color = if (friendModel.friendNameLoading) MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = ContentAlpha.medium
                    )
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedTextField(colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                    value = friendModel.addFriendUsername,
                    onValueChange = {
                        friendModel.addFriendUsername = filterUsername(it)
                        friendModel.getFriendName(
                            it, launchLogin = ::launchLogin
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    supportingText = {
                        Text(
                            text = friendModel.usernameCaption,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardActions = KeyboardActions(onDone = {
                        onAddFriend(username = friendModel.addFriendUsername)
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent {
                            if (it.nativeKeyEvent.keyCode == KEYCODE_ENTER) {
                                onAddFriend(username = friendModel.addFriendUsername)
                                true
                            } else false
                        },
                    singleLine = true,
                    label = { Text(text = getString(R.string.username)) },
                    isError = friendModel.usernameCaption.isNotBlank(),
                    placeholder = { Text(text = getString(R.string.placeholder_name)) })

            }
        })
    }

    @Composable
    fun PermissionRationale(
        permission: String, popDialogState: () -> Unit
    ) {
        AlertDialog(onDismissRequest = { popDialogState() }, confirmButton = {
            Button(onClick = {
                popDialogState()
                getNotificationPermission()
            }) {
                Text(text = getString(android.R.string.ok))
            }
        }, dismissButton = {
            OutlinedButton(onClick = { popDialogState() }) {
                Text(text = getString(android.R.string.cancel))
            }
        }, title = {
            Text(
                text = when (permission) {
                    POST_NOTIFICATIONS -> getString(R.string.notification_rationale_title)
                    else -> ""
                }
            )
        }, text = {
            Text(
                text = when (permission) {
                    POST_NOTIFICATIONS -> getString(R.string.notification_rationale)
                    else -> ""
                }
            )
        })
    }

    @ExperimentalFoundationApi
    @Composable
    fun FriendCard(
        friend: Friend,
        onLongPress: () -> Unit,
        onEditName: (friend: Friend) -> Unit,
        onDeletePrompt: (friend: Friend) -> Unit,
        modifier: Modifier = Modifier,
        cached: Boolean = false,
        state: State,
        onStateChange: (State) -> Unit
    ) {
        var message: String? by remember { mutableStateOf(null) }
        val transition = updateTransition(state, label = "friend state transition")

        val receipt = when (friend.lastMessageStatus) {
            MessageStatus.SENT -> getString(R.string.sent)
            MessageStatus.DELIVERED -> getString(R.string.delivered)
            MessageStatus.READ -> getString(R.string.read)
            else -> ""
        }

        var sendingStatus: String? by remember { mutableStateOf(null) }

        val subtitle = sendingStatus ?: receipt

        val alpha by transition.animateFloat(label = "friend alpha transition") {
            when (it) {
                State.NORMAL -> 1F
                else -> 0.5F
            }
        }
        val blur by transition.animateDp(label = "friend blur transition") {
            when (it) {
                State.NORMAL -> 0.dp
                else -> 4.dp
            }
        }

        var imageBitmap: Bitmap? by rememberSaveable {
            mutableStateOf(null)
        }

        LaunchedEffect(key1 = friend.photo) {
            if (friend.photo != null) {
                launch(context = Dispatchers.Default) {
                    val imageDecoded = Base64.decode(friend.photo, Base64.DEFAULT)
                    imageBitmap = BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)
                }
            }
        }

        var loc: Float by rememberSaveable {
            mutableFloatStateOf(0f)
        }
        val interactionSource = remember { MutableInteractionSource() }
        var animating: Boolean by rememberSaveable {
            mutableStateOf(false)
        }

        LaunchedEffect(interactionSource, state) {
            interactionSource.interactions.collect { interaction ->
                Log.d(this@MainActivity::class.java.name, state.name)
                if (state != State.NORMAL || animating) return@collect
                when (interaction) {
                    is PressInteraction.Press -> {
                        loc = interaction.pressPosition.x
                        animating = true
                    }
                }
            }
        }
        Box(
            modifier = modifier
                .fillMaxWidth(1F)
                .padding(10.dp)
                .requiredHeight(48.dp)
                .combinedClickable(
                    onClick = {
                        onStateChange(
                            when (state) {
                                State.NORMAL -> State.CONFIRM
                                State.CONFIRM, State.CANCEL, State.EDIT -> State.NORMAL
                            }
                        )
                    },
                    onClickLabel = getString(R.string.friend_card_click_label),
                    onLongClick = {
                        onStateChange(
                            when (state) {
                                State.NORMAL -> State.EDIT
                                else -> state
                            }
                        )
                        onLongPress()
                    },
                    onLongClickLabel = getString(R.string.friend_card_long_click_label),
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                )
        ) {
            Row(horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .alpha(alpha)
                    .blur(blur)
                    .padding(start = ICON_SPACING, end = ICON_SPACING)
                    .fillMaxSize()
                    .align(Alignment.CenterStart)
                    .semantics(mergeDescendants = true) {}) {
                imageBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = getString(R.string.pfp_description, friend.name),
                        modifier = Modifier
                            .size(ICON_SIZE)
                            .clip(CircleShape)
                    )
                } ?: Spacer(modifier = Modifier.width(ICON_SIZE))
                Spacer(modifier = Modifier.width(ICON_SPACING))
                Column(verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .semantics(
                            mergeDescendants = true
                        ) {}
                        .weight(1f, fill = true)) {
                    Text(
                        text = friend.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (cached) MaterialTheme.colorScheme.onBackground.copy(
                            alpha = ContentAlpha.medium
                        ) else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (subtitle.isNotBlank()) Text(
                        text = subtitle,
                        color = if (subtitle == getString(
                                R.string.send_error
                            )
                        ) MaterialTheme.colorScheme.error.copy(
                            alpha = ContentAlpha.medium
                        )
                        else MaterialTheme.colorScheme.onBackground.copy(
                            alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            AnimatedContent(targetState = state, transitionSpec = {
                val enterTransition = when (targetState) {
                    State.EDIT -> {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right
                        ) + fadeIn()
                    }

                    State.CONFIRM -> {
                        scaleIn() + fadeIn()
                    }

                    State.CANCEL -> {
                        fadeIn()
                    }

                    else -> {
                        fadeIn()
                    }
                }
                val exitTransition = when (initialState) {
                    State.EDIT -> {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left
                        ) + fadeOut()
                    }

                    State.CONFIRM -> {
                        scaleOut() + fadeOut()
                    }

                    State.CANCEL -> {
                        fadeOut()
                    }

                    else -> {
                        fadeOut()
                    }
                }
                enterTransition togetherWith exitTransition
            }, modifier = Modifier.centerAt(x = loc), label = "Friend card") { targetState ->
                if (transition.currentState == transition.targetState) {
                    animating = false
                }
                when (targetState) {
                    State.NORMAL -> {}
                    State.CONFIRM -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onStateChange(State.NORMAL) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    contentDescription = getString(android.R.string.cancel)
                                )
                            }
                            Button(
                                onClick = {
                                    onStateChange(State.CANCEL)
                                    message = null
                                }, colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(getString(R.string.confirm_alert))
                            }
                            OutlinedButton(onClick = {
                                friendModel.appendDialogState(MainViewModel.DialogStatus.AddMessageText(
                                    friend
                                ) {
                                    message = it
                                    onStateChange(State.CANCEL)
                                })
                            }) {
                                Text(getString(R.string.add_message))
                            }
                        }
                    }

                    State.EDIT -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onStateChange(State.NORMAL) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    contentDescription = getString(android.R.string.cancel)
                                )
                            }
                            Button(
                                onClick = {
                                    if (cached) friendModel.onDeleteCachedFriend(friend)
                                    else onDeletePrompt(friend)
                                    onStateChange(State.NORMAL)
                                }, colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text(getString(R.string.delete))
                            }
                            OutlinedButton(onClick = {
                                onEditName(friend)
                                onStateChange(State.NORMAL)
                            }) {
                                Text(getString(R.string.rename))
                            }
                        }
                    }

                    State.CANCEL -> {
                        val delay: Long by remember {
                            mutableStateOf((friendModel.delay * 1000).toLong())
                        }
                        var progress by remember { mutableStateOf(0L) }
                        val animatedProgress by animateFloatAsState(
                            targetValue = min(progress.toFloat() / delay, 1f).let {
                                if (it.isNaN()) 1f
                                else it
                            }, animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                            label = "Send progress"
                        )
                        var progressEnabled by remember { mutableStateOf(true) }
                        var triggered by remember { mutableStateOf(false) }

                        LaunchedEffect(progressEnabled) {
                            while (progress < delay && progressEnabled) {
                                progress += DELAY_INTERVAL
                                delay(DELAY_INTERVAL)
                            }
                        }

                        if (progress >= delay && !triggered) {
                            triggered = true

                            friendModel.sendAlert(friend,
                                body = message,
                                launchLogin = ::launchLogin,
                                onError = {
                                    sendingStatus = getString(R.string.send_error)
                                },
                                onSuccess = {
                                    sendingStatus = null
                                })
                            onStateChange(State.NORMAL)
                            progressEnabled = false
                            sendingStatus = getString(R.string.sending)
                        }
                        CancelBar(progress = animatedProgress, modifier = Modifier
                            .clickable {
                                progressEnabled = false
                                onStateChange(State.NORMAL)
                                progress = 0
                            }
                            .fillMaxSize())
                    }
                }
            }
        }
    }

    @Composable
    fun CancelBar(
        progress: Float, modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .clip(shape = RoundedCornerShape(5.dp))
                .background(color = MaterialTheme.colorScheme.inversePrimary)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(shape = RoundedCornerShape(5.dp))
                    .background(color = MaterialTheme.colorScheme.primary)
            ) {}
            Text(
                text = getString(android.R.string.cancel),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    public override fun onResume() {
        super.onResume()

        if (!friendModel.waitForLoginResult) reload()

        // if Google API isn't available, do this - it's from the docs, should be correct
        if (!checkPlayServices()) return
    }

    private fun checkPlayServices(): Boolean {
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS
        ) { // check for Google Play Services
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show()
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this)
            return false
        }
        return true
    }

    companion object {
        private const val DELAY_INTERVAL: Long = 100
        private val BUTTON_SPACING = 16.dp
        val ICON_SPACING = 8.dp
        val ICON_SIZE = 40.dp
        const val USERNAME_QUERY_PARMETER = "username"

        /**
         * Positions the element such that its center is at (x, y). If this would cause the
         * element to extend outside the bounds of its constraints, positions the element the
         * closest it can such that it does not overflow. If a parameter is not provided, the
         * element is centered along that axis.
         *
         * @param x: the x-coordinate to position the object at (defaults to the composable being
         * centered horizontally within its parent)
         * @param y: the y-coordinate to position the object at (defaults to the composable being
         * centered vertically within its parent)
         *
         * more about the layout modifier can be found here:
         * https://developer.android.com/jetpack/compose/layouts/custom
         */
        fun Modifier.centerAt(x: Float = Float.NaN, y: Float = Float.NaN) =
            layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val xPos = if (x.isNaN()) (constraints.maxWidth - placeable.width) / 2
                else max(
                    0, min(
                        (x - placeable.width.toFloat() / 2).toInt(),
                        constraints.maxWidth - placeable.width
                    )
                )
                val yPos = if (y.isNaN()) ((constraints.maxHeight - placeable.height) / 2)
                else max(
                    0, min(
                        (y - placeable.height.toFloat() / 2).toInt(),
                        constraints.maxHeight - placeable.height
                    )
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(
                        xPos, yPos
                    )
                }

            }
    }
}