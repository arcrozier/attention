package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
        private val attentionRepository: AttentionRepository, private val
        application: Application
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
                            "Should not attempt to log in while " +
                                    "addFriendException is true"
                        )
                    }
                    friendModel.swapDialogState(Triple(
                        MainViewModel.DialogStatus.ADD_FRIEND, null,
                    ) {})
                }
            }
        }

        // this condition is met if the app was launched by someone tapping it on a share sheet
        if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            friendModel.connectionState = getString(R.string.sharing)
            friendModel.message = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                intent.hasExtra(Intent.EXTRA_SHORTCUT_ID)
            ) {
                val username = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID) ?: ""
                friendModel.appendDialogState(
                    Triple(
                        MainViewModel.DialogStatus.ADD_MESSAGE_TEXT,
                        Friend(username, "")
                    ) {})
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
                MainViewModel.OVERLAY_NO_PROMPT,
                false
            )
        ) {
            friendModel.appendDialogState(Triple(
                MainViewModel.DialogStatus.OVERLAY_PERMISSION,
                null
            ) {})
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
            return
        }

        friendModel.isRefreshing = false
    }

    @ExperimentalFoundationApi
    @Composable
    fun HomeWrapper(model: MainViewModel) {
        val displayDialog: Triple<MainViewModel.DialogStatus, Friend?, (String) -> Unit> by model
            .dialogState
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

    @ExperimentalFoundationApi
    @Composable
    fun Home(
        friends: List<Friend>, onLongPress: () -> Unit, onEditName: (friend: Friend) -> Unit,
        onDeletePrompt: (friend: Friend) -> Unit,
        dialogState: Triple<MainViewModel.DialogStatus, Friend?, (String) -> Unit>,
        showSnackbar: String
    ) {
        val cachedFriends by friendModel.cachedFriends.observeAsState(listOf())
        val scaffoldState = rememberScaffoldState()
        val scope = rememberCoroutineScope()
        if (showSnackbar.isNotBlank()) {
            LaunchedEffect(scaffoldState.snackbarHostState) {
                scope.launch {
                    // cancels by default after a short amount of time

                    when (scaffoldState.snackbarHostState.showSnackbar(message = showSnackbar)) {
                        SnackbarResult.Dismissed -> {
                            friendModel.dismissSnackBar()
                        }
                        else -> {}
                    }
                }
            }
        }
        when (dialogState.first) {
            MainViewModel.DialogStatus.ADD_FRIEND -> AddFriendDialog()
            MainViewModel.DialogStatus.OVERLAY_PERMISSION -> OverlaySettingsDialog()
            MainViewModel.DialogStatus.ADD_MESSAGE_TEXT -> dialogState.second?.let {
                AddMessageText(friend = it, onSend = dialogState.third)
            }
            MainViewModel.DialogStatus.FRIEND_NAME -> dialogState.second?.let {
                EditFriendNameDialog(
                    friend = it
                )
            }
            MainViewModel.DialogStatus.CONFIRM_DELETE -> dialogState.second?.let {
                DeleteFriendDialog(
                    friend = it
                )
            }
            MainViewModel.DialogStatus.CONFIRM_DELETE_CACHED -> dialogState.second?.let {
                DeleteFriendDialog(friend = it)
            }
            MainViewModel.DialogStatus.NONE -> {}
        }

        Scaffold(scaffoldState = scaffoldState,
            topBar = {
                TopAppBar(
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    title = {
                        Column {
                            Text(
                                getString(R.string.app_name),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            if (friendModel.connectionState.isNotBlank()) Text(
                                friendModel.connectionState,
                                style = MaterialTheme.typography
                                    .labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(
                                applicationContext,
                                SettingsActivity::class.java
                            )
                            startActivity(intent)
                        }) {
                            Icon(
                                Icons.Filled.Settings, contentDescription = getString(
                                    R
                                        .string.action_settings
                                ), tint = MaterialTheme.colorScheme.onPrimary
                            )

                        }
                    }
                )
            },
            backgroundColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        friendModel.appendDialogState(Triple(
                            MainViewModel.DialogStatus.ADD_FRIEND,
                            null
                        ) {})
                    },
                    backgroundColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = getString(R.string.add_friend),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        ) {
            SwipeRefresh(
                state = rememberSwipeRefreshState(friendModel.isRefreshing),
                onRefresh = { reload() },
                modifier = Modifier.padding(it)
            )
            {
                LazyColumn(
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxSize()
                ) {
                    items(friends) { friend ->
                        FriendCard(
                            friend = friend,
                            onLongPress = onLongPress,
                            onEditName = onEditName,
                            onDeletePrompt = onDeletePrompt
                        )
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(
                                alpha = ContentAlpha
                                    .disabled
                            ), modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )
                    }
                    items(cachedFriends) { cachedFriend ->
                        FriendCard(friend = Friend(cachedFriend.username, cachedFriend.username),
                            onLongPress
                            = {}, onEditName = {}, onDeletePrompt = {})
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(
                                alpha = ContentAlpha
                                    .disabled
                            ), modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun AddMessageText(friend: Friend, onSend: (message: String) -> Unit) {

        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
            confirmButton = {
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
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    friendModel.popDialogState()
                }) {
                    Text(text = getString(R.string.cancel))
                }
            },
            title = {
                Text(
                    text = getString(R.string.add_message), color = MaterialTheme
                        .colorScheme.onSurfaceVariant
                )
            },
            text = {
                OutlinedTextField(
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor =
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    value = friendModel.message,
                    onValueChange = { friendModel.message = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    label = { Text(text = getString(R.string.message_label, friend.name)) },
                    placeholder = { Text(text = getString(R.string.message_hint)) }
                )
            }
        )
    }

    @Composable
    fun DeleteFriendDialog(friend: Friend, cached: Boolean = false) {
        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
            confirmButton = {
                Button(onClick = {
                    friendModel.popDialogState()
                    if (cached) friendModel.confirmDeleteCachedFriend(friend)
                    else friendModel.confirmDeleteFriend(friend = friend, ::launchLogin)
                }) {
                    Text(text = getString(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    friendModel.popDialogState()
                }) {
                    Text(text = getString(R.string.cancel))
                }
            },
            title = {
                Text(
                    text = getString(R.string.confirm_delete_title), color = MaterialTheme
                        .colorScheme.onSurfaceVariant
                )
            },
            text = {
                Text(
                    text = getString(R.string.confirm_delete_message, friend.name),
                    color = MaterialTheme
                        .colorScheme.onSurfaceVariant
                )
            }
        )
    }

    @Composable
    fun EditFriendNameDialog(friend: Friend) {
        var name by rememberSaveable {
            mutableStateOf(friend.name)
        }
        var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
            confirmButton = {
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
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    friendModel.popDialogState()
                }) {
                    Text(text = getString(R.string.cancel))
                }
            },
            title = {
                Text(
                    text = getString(R.string.rename), color = MaterialTheme
                        .colorScheme.onSurfaceVariant
                )
            },
            text = {
                OutlinedTextField(
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor =
                        MaterialTheme.colorScheme.onSurfaceVariant
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
                    placeholder = { Text(text = getString(R.string.new_name)) }
                )
            }
        )
    }

    @Composable
    fun OverlaySettingsDialog() {
        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
            confirmButton = {
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
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    val editor = getSharedPreferences(
                        MainViewModel
                            .USER_INFO, MODE_PRIVATE
                    ).edit()
                    editor.putBoolean(MainViewModel.OVERLAY_NO_PROMPT, true)
                    editor.apply()
                    friendModel.popDialogState()
                }) {
                    Text(text = getString(R.string.do_not_ask_again))
                }
            },
            title = {
                Text(
                    text = getString(R.string.draw_title), color = MaterialTheme
                        .colorScheme.onSurfaceVariant
                )
            },
            text = {
                Text(
                    text = getString(R.string.draw_message), color = MaterialTheme
                        .colorScheme.onSurfaceVariant
                )
            }
        )
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

    @Composable
    fun AddFriendDialog() {
        AlertDialog(onDismissRequest = {
            if (friendModel.addFriendException) {
                friendModel.addFriendException = false
                reload()
            }
            friendModel.popDialogState()
        },
            dismissButton = {
                OutlinedButton(onClick = {
                    friendModel.popDialogState()
                }) {
                    Text(getString(R.string.cancel))
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddFriend(friendModel.addFriendUsername)
                }) {
                    Text(getString(android.R.string.ok))
                }
            },
            title = {
                Text(
                    text = getString(R.string.add_friend), color = MaterialTheme
                        .colorScheme.onSurfaceVariant
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = friendModel.newFriendName,
                        color = if (friendModel.friendNameLoading)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = ContentAlpha.medium
                            )
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    OutlinedTextField(
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor =
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                        placeholder = { Text(text = getString(R.string.placeholder_name)) }
                    )
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

    @OptIn(ExperimentalAnimationApi::class)
    @ExperimentalFoundationApi
    @Composable
    fun FriendCard(
        friend: Friend, onLongPress: () -> Unit, onEditName: (friend: Friend) -> Unit,
        onDeletePrompt: (friend: Friend) -> Unit, cached: Boolean = false
    ) {
        var state by remember { mutableStateOf(State.NORMAL) }
        var message: String? by remember { mutableStateOf(null) }
        val transition = updateTransition(state, label = "friend state transition")

        val receipt = when (friend.last_message_status) {
            MessageStatus.SENT -> getString(R.string.sent)
            MessageStatus.DELIVERED -> getString(R.string.delivered)
            MessageStatus.READ -> getString(R.string.read)
            else -> ""
        }

        var sendingStatus: String? by remember { mutableStateOf(null)}

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

        Box(
            modifier = Modifier
                .fillMaxWidth(1F)
                .padding(10.dp)
                .requiredHeight(48.dp)
                .combinedClickable(
                    onClick = {
                        state = when (state) {
                            State.NORMAL -> State.CONFIRM
                            State.CONFIRM, State.CANCEL, State.EDIT -> State.NORMAL
                        }
                    },
                    onClickLabel = getString(R.string.friend_card_click_label),
                    onLongClick = {
                        state = when (state) {
                            State.NORMAL -> State.EDIT
                            else -> state
                        }
                        onLongPress()
                    },
                    onLongClickLabel = getString(R.string.friend_card_long_click_label)
                )
        ) {
            Column(modifier = Modifier
                .align(Alignment.CenterStart)
                .semantics(mergeDescendants = true) {}) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (cached) MaterialTheme.colorScheme.onBackground.copy(
                        alpha =
                        ContentAlpha.medium
                    ) else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .alpha(alpha)
                        .blur(blur)
                )
                Text(
                    text = subtitle,
                    color = if (subtitle == getString(R.string.send_error))
                        MaterialTheme.colorScheme.error.copy(alpha = ContentAlpha.medium)
                        else MaterialTheme.colorScheme
                            .onBackground.copy(
                        alpha = ContentAlpha.medium
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .alpha(alpha)
                        .blur(blur)
                )
            }
            AnimatedVisibility(
                visible = state == State.EDIT,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.End, modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconButton(onClick = { state = State.NORMAL }) {
                        Icon(
                            Icons.Filled.Close,
                            tint = MaterialTheme.colorScheme.onBackground,
                            contentDescription = getString(R.string.cancel)
                        )
                    }
                    Button(
                        onClick = {
                            if (cached) friendModel.onDeleteCachedFriend(friend)
                            else onDeletePrompt(friend)
                            state = State.NORMAL
                        }, colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                    ) {
                        Text(getString(R.string.delete))
                    }
                    Spacer(modifier = Modifier.width(LoginActivity.LIST_ELEMENT_PADDING))
                    OutlinedButton(onClick = {
                        onEditName(friend)
                        state = State.NORMAL
                    }) {
                        Text(getString(R.string.rename))
                    }
                }
            }
            AnimatedVisibility(
                visible = state == State.CONFIRM, enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconButton(onClick = { state = State.NORMAL }) {
                        Icon(
                            Icons.Filled.Close,
                            tint = MaterialTheme.colorScheme.onBackground,
                            contentDescription = getString(R.string.cancel)
                        )
                    }
                    Button(
                        onClick = {
                            state = State.CANCEL
                            message = null
                        }, colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme
                                .colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(getString(R.string.confirm_alert))
                    }
                    OutlinedButton(onClick = {
                        friendModel.appendDialogState(Triple(
                            MainViewModel
                                .DialogStatus.ADD_MESSAGE_TEXT, friend
                        ) {
                            message = it
                            state = State.CANCEL
                        })
                    }) {
                        Text(getString(R.string.add_message))
                    }
                }
            }
            AnimatedVisibility(
                visible = state == State.CANCEL, enter = fadeIn(),
                exit = fadeOut()
            ) {

                var progress by remember { mutableStateOf(0) }
                val animatedProgress by animateFloatAsState(
                    targetValue = progress.toFloat() / UNDO_INTERVALS,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
                )
                var progressEnabled by remember { mutableStateOf(true) }
                var triggered by remember { mutableStateOf(false) }

                LaunchedEffect(progressEnabled) {
                    while (progress < UNDO_INTERVALS && progressEnabled) {
                        progress = min(progress + 1, UNDO_INTERVALS.toInt())
                        delay(UNDO_TIME / UNDO_INTERVALS)
                    }
                }

                if (progress >= UNDO_INTERVALS && !triggered) {
                    @Suppress("UNUSED_VALUE")
                    triggered = true
                    friendModel.sendAlert(
                        friend.id,
                        message = message,
                        launchLogin = ::launchLogin,
                        onError = {
                            sendingStatus = getString(R.string.send_error)
                        },
                        onSuccess = {
                            sendingStatus = null
                        })
                    state = State.NORMAL
                    progressEnabled = false
                    sendingStatus = getString(R.string.sending)
                }
                CancelBar(progress = animatedProgress,
                    modifier = Modifier
                        .clickable {
                            progressEnabled = false
                            state = State.NORMAL
                            progress = 0
                        }
                        .fillMaxSize())
            }
        }
    }

    @Composable
    fun CancelBar(
        progress: Float,
        modifier: Modifier = Modifier
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
            ) {
            }
            Text(text = getString(R.string.cancel), modifier = Modifier.align(Alignment.Center))
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
        ) {
            // check for Google Play Services
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show()
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this)
            return false
        }
        return true
    }

    companion object {
        // time (in milliseconds) that the user has to cancel sending an alert
        private const val UNDO_TIME: Long = 3500
        private const val UNDO_INTERVALS: Long = 10


    }
}