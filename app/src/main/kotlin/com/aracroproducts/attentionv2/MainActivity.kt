package com.aracroproducts.attentionv2

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material.SnackbarResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private val friendModel: MainViewModel by viewModels(factoryProducer = {
        MainViewModelFactory(AttentionRepository(AttentionDB.getDB(this)), application)
    })

    class MainViewModelFactory(
            private val attentionRepository: AttentionRepository,
            private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(attentionRepository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    enum class State {
        NORMAL, CONFIRM, CANCEL, EDIT
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // there isn't really anything we can do
    }

    private fun launchLogin() {
        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
    }

    /**
     * Called when the activity is created
     *
     * @param savedInstanceState    Instance data saved from before the activity was killed
     */
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val tempId = data?.getQueryParameter("username")
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
                            MainViewModel.DialogStatus.AddFriend)
                }
            }
        }

        // this condition is met if the app was launched by someone tapping it on a share sheet
        if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            friendModel.connectionState = getString(R.string.sharing)
            friendModel.message = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && intent.hasExtra(
                            Intent.EXTRA_SHORTCUT_ID)) {
                val username = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID) ?: ""
                lifecycleScope.launch {
                    val friend = friendModel.getFriend(username)
                    friendModel.appendDialogState(
                        MainViewModel.DialogStatus.AddMessageText(friend) { message ->
                            friendModel.message = message
                            friendModel.cardStatus[friend.id] = State.CANCEL
                        })
                }

            }
        }

        if (action == getString(R.string.reopen_failed_alert_action)) {
            lifecycleScope.launch {
                intent.getStringExtra(MainViewModel.EXTRA_RECIPIENT)?.let {
                    val friend = friendModel.getFriend(it)
                    friendModel.appendDialogState(
                            MainViewModel.DialogStatus.AddMessageText(
                                    friend
                            ) { message ->
                                friendModel.message = message
                                friendModel.cardStatus[friend.id] = State.CANCEL
                            })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun checkOverlayDisplay() {
        val userInfo = getSharedPreferences(MainViewModel.USER_INFO, Context.MODE_PRIVATE)

        if (!Settings.canDrawOverlays(application) && !userInfo.getBoolean(
                        MainViewModel.OVERLAY_NO_PROMPT, false
                )
        ) {
            friendModel.appendDialogState(MainViewModel.DialogStatus.OverlayPermission)
        }
    }

    private fun getNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // You can use the API that requires the permission.
                }
                shouldShowRequestPermissionRationale(POST_NOTIFICATIONS) -> {
                    // In an educational UI, explain to the user why your app requires this
                    // permission for a specific feature to behave as expected. In this UI,
                    // include a "cancel" or "no thanks" button that allows the user to
                    // continue using your app without granting the permission.
                    friendModel.appendDialogState(MainViewModel.DialogStatus.PermissionRationale
                        (POST_NOTIFICATIONS))
                }
                else -> {
                    // You can directly ask for the permission.
                    // The registered ActivityResultCallback gets the result of this request.
                    requestPermissionLauncher.launch(
                        POST_NOTIFICATIONS
                    )
                }
            }
        }
    }

    private fun reload() {
        friendModel.isRefreshing = true

        val userInfo = getSharedPreferences(MainViewModel.USER_INFO, Context.MODE_PRIVATE)

        // token is auth token
        val token = userInfo.getString(MainViewModel.MY_TOKEN, null)

        // we want an exception to the login if they opened an add-friend link
        // if opened from a link, the action is ACTION_VIEW, so we delay logging in
        if (token == null && !friendModel.addFriendException) {
            launchLogin()
        } else if (token != null) {
            friendModel.getUserInfo(token) {
                if (!friendModel.addFriendException) launchLogin()
            }
            friendModel.registerDevice()
            getNotificationPermission()
            return
        }

        friendModel.isRefreshing = false
    }

    @ExperimentalFoundationApi
    @Composable
    fun HomeWrapper(model: MainViewModel) {
        val displayDialog: MainViewModel.DialogStatus by model.dialogState
        val showSnackbar = model.isSnackBarShowing

        val friends by model.friends.observeAsState(listOf())
        Home(friends = friends,
                onLongPress = { model.onLongPress() },
                onEditName = { model.onEditName(it) },
                onDeletePrompt = { model.onDeleteFriend(it) },
                dialogState = displayDialog,
                showSnackbar = showSnackbar
        )
    }

    @OptIn(ExperimentalAnimationApi::class)
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
        val cachedFriends by friendModel.cachedFriends.observeAsState(listOf())
        val scaffoldState = rememberScaffoldState()
        val scope = rememberCoroutineScope()
        if (showSnackbar.isNotBlank()) {
            LaunchedEffect(scaffoldState.snackbarHostState) {
                scope.launch { // cancels by default after a short amount of time

                    when (scaffoldState.snackbarHostState.showSnackbar(message = showSnackbar)) {
                        SnackbarResult.Dismissed -> {
                            friendModel.dismissSnackBar()
                        }
                        else -> {}
                    }
                }
            }
        }

        AnimatedContent(targetState = dialogState, transitionSpec = {
            slideIntoContainer(
                    towards = AnimatedContentScope.SlideDirection.Up) with slideOutOfContainer(
                    towards = AnimatedContentScope.SlideDirection.Down
            )
        }) { targetState ->
            when (targetState) {
                is MainViewModel.DialogStatus.AddFriend -> AddFriendDialog()
                is MainViewModel.DialogStatus.OverlayPermission -> OverlaySettingsDialog()
                is MainViewModel.DialogStatus.AddMessageText -> {
                    AddMessageText(friend = targetState.friend, onSend = targetState.onSend)
                }
                is MainViewModel.DialogStatus.FriendName -> {
                    EditFriendNameDialog(
                            friend = targetState.friend
                    )
                }
                is MainViewModel.DialogStatus.ConfirmDelete -> {
                    DeleteFriendDialog(
                            friend = targetState.friend
                    )
                }
                is MainViewModel.DialogStatus.ConfirmDeleteCached ->  {
                    DeleteFriendDialog(friend = targetState.friend)
                }
                is MainViewModel.DialogStatus.PermissionRationale -> {
                    PermissionRationale(permission = targetState.permission) {
                    }
                }
                is MainViewModel.DialogStatus.None -> {}
            }
        }


        Scaffold(scaffoldState = scaffoldState, topBar = {
            TopAppBar(backgroundColor = MaterialTheme.colorScheme.primary, title = {
                Column {
                    Text(
                            getString(R.string.app_name),
                            color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (friendModel.connectionState.isNotBlank()) Text(
                            friendModel.connectionState,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
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
            })
        }, backgroundColor = MaterialTheme.colorScheme.background, floatingActionButton = {
            FloatingActionButton(
                    onClick = {
                        friendModel.appendDialogState(
                                MainViewModel.DialogStatus.AddFriend)
                    }, backgroundColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                        Icons.Filled.Add,
                        contentDescription = getString(R.string.add_friend),
                        tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }) {
            SwipeRefresh(
                    state = rememberSwipeRefreshState(friendModel.isRefreshing),
                    onRefresh = { reload() },
                    modifier = Modifier.padding(it)
            ) {
                LazyColumn(
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxSize()
                ) {
                    items(items = friends,
                            key = { friend ->
                                friend.id
                            }
                    ) { friend ->
                        FriendCard(
                                friend = friend,
                                onLongPress = onLongPress,
                                onEditName = onEditName,
                                onDeletePrompt = onDeletePrompt,
                                modifier = Modifier.animateItemPlacement(),
                                state = friendModel.cardStatus.getOrDefault(friend.id, State
                                    .NORMAL),
                                onStateChange = {newState ->
                                    friendModel.cardStatus[friend.id] = newState
                                }
                        )
                        Divider(
                                color = MaterialTheme.colorScheme.outline.copy(
                                        alpha = ContentAlpha.disabled
                                ), modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )
                    }
                    items(cachedFriends) { cachedFriend ->
                        FriendCard(friend = Friend(cachedFriend.username, cachedFriend.username),
                                onLongPress = {},
                                onEditName = {},
                                onDeletePrompt = {},
                                   state = friendModel.cardStatus.getOrDefault(cachedFriend
                                       .username, State.NORMAL),
                                   onStateChange = { newState ->
                                       friendModel.cardStatus[cachedFriend.username] = newState
                                   }

                        )
                        Divider(
                                color = MaterialTheme.colorScheme.outline.copy(
                                        alpha = ContentAlpha.disabled
                                ), modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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
            OutlinedTextField(colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        R.string.message_label,
                                        friend.name
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EditFriendNameDialog(friend: Friend) {
        var name by rememberSaveable {
            mutableStateOf(friend.name)
        }
        var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { friendModel.popDialogState() }, confirmButton = {
            Button(onClick = {
                val savingName = name.trim()
                if (savingName.isEmpty()) {
                    error = true
                } else {
                    friendModel.confirmEditName(friend.id, savingName, ::launchLogin)
                    friendModel.popDialogState()
                }
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
            OutlinedTextField(colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
                    value = name,
                    onValueChange = { name = it },
                    keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                val editor = getSharedPreferences(
                        MainViewModel.USER_INFO, MODE_PRIVATE
                ).edit()
                editor.putBoolean(MainViewModel.OVERLAY_NO_PROMPT, true)
                editor.apply()
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
                        ), responseListener = { _, response ->
                    if (response.isSuccessful) friendModel.popDialogState()
                }, launchLogin = this::launchLogin
                )
            }, launchLogin = ::launchLogin)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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
                OutlinedTextField(colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                        value = friendModel.addFriendUsername,
                        onValueChange = {
                            friendModel.addFriendUsername = it
                            friendModel.getFriendName(
                                    it, launchLogin = ::launchLogin
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                autoCorrect = false,
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            onAddFriend(username = friendModel.addFriendUsername)
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = getString(R.string.username)) },
                        isError = friendModel.usernameCaption.isNotBlank(),
                        placeholder = { Text(text = getString(R.string.placeholder_name)) })
                Text(
                        text = friendModel.usernameCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp)
                )
            }
        })
    }

    @Composable
    fun PermissionRationale(
        permission: String,
        popDialogState: () -> Unit
    ) {
        AlertDialog(onDismissRequest = {  popDialogState() }, confirmButton = {
            Button(onClick = { popDialogState()
                getNotificationPermission() }) {
                Text(text = getString(android.R.string.ok))
            }
        }, dismissButton = {
            OutlinedButton(onClick = { popDialogState() }) {
                Text(text = getString(android.R.string.cancel))
            }
        }, title = {
            Text(text = when (permission) {
                POST_NOTIFICATIONS -> getString(R.string.notification_rationale_title)
                else -> ""
            })
        }, text = {
            Text(text = when (permission) {
                POST_NOTIFICATIONS -> getString(R.string.notification_rationale)
                else -> ""
            })
        })
    }

    @OptIn(ExperimentalAnimationApi::class)
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

        val receipt = when (friend.last_message_status) {
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
                else -> 3.dp
            }
        }

        var imageBitmap: ImageBitmap? = null
        if (friend.photo != null) {
            val imageDecoded = rememberSaveable {
                Base64.decode(friend.photo, Base64.DEFAULT)
            }
            imageBitmap = rememberSaveable {
                BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size).asImageBitmap()
            }
        }

        Box(modifier = modifier
            .fillMaxWidth(1F)
            .padding(10.dp)
            .requiredHeight(48.dp)
            .combinedClickable(onClick = {
                onStateChange(when (state) {
                    State.NORMAL -> State.CONFIRM
                    State.CONFIRM, State.CANCEL, State.EDIT -> State.NORMAL
                })
            }, onClickLabel = getString(R.string.friend_card_click_label), onLongClick = {
                onStateChange(when (state) {
                    State.NORMAL -> State.EDIT
                    else -> state
                })
                onLongPress()
            }, onLongClickLabel = getString(R.string.friend_card_long_click_label)
            )
        ) {
            Row(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                .fillMaxSize()
                .padding(all = 8.dp)
                .align(Alignment.CenterStart)
                .semantics(mergeDescendants = true) {}) {
                if (imageBitmap != null) Image(
                    bitmap = imageBitmap,
                    contentDescription = getString(R.string.pfp_description, friend.name),
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                ) else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start,
                       modifier = Modifier
                    .semantics(
                        mergeDescendants = true
                    ) {}) {
                    Text(
                        text = friend.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (cached) MaterialTheme.colorScheme.onBackground.copy(
                            alpha = ContentAlpha.medium
                        ) else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .alpha(alpha)
                            .blur(blur)
                    )
                    Text(
                        text = subtitle,
                        color = if (subtitle == getString(
                                R.string.send_error)) MaterialTheme.colorScheme.error.copy(
                            alpha = ContentAlpha.medium
                        )
                        else MaterialTheme.colorScheme.onBackground.copy(
                            alpha = ContentAlpha.medium
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .alpha(alpha)
                            .blur(blur)
                    )
                }
            }

            AnimatedContent(targetState = state, transitionSpec = {
                val enterTransition = when (targetState) {
                    State.EDIT -> {
                        slideIntoContainer(
                                towards = AnimatedContentScope.SlideDirection.Right) + fadeIn()
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
                                towards = AnimatedContentScope.SlideDirection.Left) + fadeOut()
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
                enterTransition with exitTransition
            }) { targetState ->
                when (targetState) {
                    State.NORMAL -> {}
                    State.CONFIRM -> {
                        Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
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
                                friendModel.appendDialogState(MainViewModel.DialogStatus
                                                                  .AddMessageText(friend
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
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
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
                            Spacer(modifier = Modifier.width(LoginActivity.LIST_ELEMENT_PADDING))
                            OutlinedButton(onClick = {
                                onEditName(friend)
                                onStateChange(State.NORMAL)
                            }) {
                                Text(getString(R.string.rename))
                            }
                        }
                    }
                    State.CANCEL -> {
                        val typedValue = TypedValue()
                        resources.getValue(R.integer.default_delay, typedValue, false)
                        val delay: Long by remember {
                            mutableStateOf((PreferenceManager.getDefaultSharedPreferences(
                                    this@MainActivity)
                                    .getString(getString(R.string.delay_key), null)
                                    .let {
                                        it?.toFloatOrNull() ?: typedValue.float
                                    } * 1000).toLong())
                        }
                        var progress by remember { mutableStateOf(0L) }
                        val animatedProgress by animateFloatAsState(
                                targetValue = min(progress.toFloat() / delay, 1f).let {
                                    if (it.isNaN()) 1f
                                    else it
                                }, animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
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
                            @Suppress("UNUSED_VALUE")
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
            Text(text = getString(android.R.string.cancel), modifier = Modifier.align(Alignment.Center))
        }
    }

    public override fun onResume() {
        super.onResume()

        reload()

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

    }
}