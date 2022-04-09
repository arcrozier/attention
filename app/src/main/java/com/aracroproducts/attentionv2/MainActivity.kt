package com.aracroproducts.attentionv2

import android.app.Application
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.android.volley.ClientError
import com.android.volley.NoConnectionError
import com.aracroproducts.attentionv2.MainViewModel.Companion.MY_NAME
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val sTAG = javaClass.name
    private val friendModel: MainViewModel by viewModels()

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

        // Creates a notification channel for displaying failed-to-send notifications
        MainViewModel.createFailedAlertNotificationChannel(this)

        val userInfo = getSharedPreferences(MainViewModel.USER_INFO, Context.MODE_PRIVATE)

        // token is auth token
        val token = userInfo.getString(MainViewModel.MY_TOKEN, null)
        val action: String? = intent?.action
        val data: Uri? = intent?.data

        friendModel.addFriendException = action == Intent.ACTION_VIEW

        // we want an exception to the login if they opened an add-friend link
        // if opened from a link, the action is ACTION_VIEW, so we delay logging in
        if (token == null && friendModel.addFriendException) {
            launchLogin()
        } else if (token != null) {
            friendModel.getUserInfo(token) {
                if (!friendModel.addFriendException) launchLogin()
            }
        }

        if (friendModel.addFriendException) {
            val tempId = data?.getQueryParameter("username")
            if (tempId == null) {
                friendModel.showSnackBar(getString(R.string.bad_add_link))
            } else {
                friendModel.addFriendUsername = tempId
            }
        }

        if (!Settings.canDrawOverlays(application) && !userInfo.getBoolean(
                        MainViewModel.OVERLAY_NO_PROMPT,
                        false)) {
            friendModel.appendDialogState(Triple(MainViewModel.DialogStatus.OVERLAY_PERMISSION,
                    null) {})
        }

        // TODO if opened from a share context, save the data shared, add a message to the top
        //  bar letting them know they are sharing content, and if they open an add message
        //  thing, pre-populate it with the saved data (after sending, reset the data?)

        if (!checkPlayServices()) return

        friendModel.loadUserPrefs()

        setContent {
            MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors else lightColors) {
                HomeWrapper(model = friendModel)
            }
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun HomeWrapper(model: MainViewModel) {
        val displayDialog: Triple<MainViewModel.DialogStatus, Friend?, (String) -> Unit> by model
                .dialogState
        val showSnackbar = model.isSnackBarShowing

        Home(
                friends = model.friends.value ?: listOf(),
                onLongPress = { model.onLongPress() },
                onEditName = { model.onEditName(it) },
                onDeletePrompt = { model.onDeleteFriend(it) },
                dialogState = displayDialog,
                showSnackbar = showSnackbar
        )
    }

    @ExperimentalFoundationApi
    @Composable
    fun Home(friends: List<Friend>, onLongPress: () -> Unit, onEditName: (friend: Friend) -> Unit,
             onDeletePrompt: (friend: Friend) -> Unit,
             dialogState: Triple<MainViewModel.DialogStatus, Friend?, (String) -> Unit>,
             showSnackbar: String) {


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
                        friend = it)
            }
            MainViewModel.DialogStatus.CONFIRM_DELETE -> dialogState.second?.let {
                DeleteFriendDialog(
                        friend = it)
            }
            MainViewModel.DialogStatus.CONFIRM_DELETE_CACHED -> dialogState.second?.let {
                DeleteFriendDialog(friend = it)
            }
            MainViewModel.DialogStatus.NONE -> {}
        }

        Scaffold(scaffoldState = scaffoldState,
                topBar = {
                    TopAppBar(
                            backgroundColor = MaterialTheme.colors.primary,
                            title = {
                                Column {
                                    Text(getString(R.string.app_name))
                                    Text(friendModel.connectionState, style = MaterialTheme
                                            .typography.caption)
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    val intent = Intent(applicationContext,
                                            SettingsActivity::class.java)
                                    startActivity(intent)
                                }) {
                                    Icon(Icons.Filled.Settings, contentDescription = getString(R
                                            .string.action_settings))

                                }
                            }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        friendModel.appendDialogState(Triple(MainViewModel.DialogStatus.ADD_FRIEND,
                                null) {})
                    },
                            backgroundColor = MaterialTheme.colors.secondary) {
                        Icon(Icons.Filled.Add, contentDescription = getString(R.string.add_friend))
                    }
                }
        ) {
            LazyColumn(Modifier.background(MaterialTheme.colors.background)) {
                items(friends) { friend ->
                    FriendCard(
                            friend = friend,
                            onLongPress = onLongPress,
                            onEditName = onEditName,
                            onDeletePrompt = onDeletePrompt
                    )
                }
                items(friendModel.cachedFriends.value ?: listOf()) { cachedFriend ->
                    FriendCard(friend = Friend(cachedFriend.username, cachedFriend.username), onLongPress
                    = {}, onEditName = {}, onDeletePrompt = {})
                }
            }
        }
    }

    @Composable
    fun AddMessageText(friend: Friend, onSend: (message: String) -> Unit) {
        var message by rememberSaveable {
            mutableStateOf("")
        }
        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
                confirmButton = {
                    Button(onClick = {
                        friendModel.popDialogState()
                        onSend(message)
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
                title = { Text(text = getString(R.string.rename)) },
                text = {
                    OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    capitalization = KeyboardCapitalization.Words),
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
                        Text(text = getString(R.string.do_not_ask_again))
                    }
                },
                title = { Text(text = getString(R.string.confirm_delete_title)) },
                text = { Text(text = getString(R.string.confirm_delete_message)) }
        )
    }

    @Composable
    fun EditFriendNameDialog(friend: Friend) {
        var name by rememberSaveable {
            mutableStateOf("")
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
                title = { Text(text = getString(R.string.rename)) },
                text = {
                    OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    capitalization = KeyboardCapitalization.Words),
                            singleLine = true,
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
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + applicationContext.packageName))
                        friendModel.popDialogState()
                        startActivity(intent)
                    }) {
                        Text(text = getString(R.string.open_settings))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        val editor = getSharedPreferences(MainViewModel
                                .USER_INFO, MODE_PRIVATE).edit()
                        editor.putBoolean(MainViewModel.OVERLAY_NO_PROMPT, true)
                        editor.apply()
                        friendModel.popDialogState()
                    }) {
                        Text(text = getString(R.string.do_not_ask_again))
                    }
                },
                title = { Text(text = getString(R.string.draw_title)) },
                text = { Text(text = getString(R.string.draw_message)) }
        )
    }

    private fun onAddFriend(username: String) {
        val savingName = username.trim()
        if (savingName.isBlank()) {
            friendModel.usernameCaption = getString(R.string.empty_username)
        } else {
            friendModel.getFriendName(username, responseListener = {
                friendModel.onAddFriend(Friend(username, it.getString("name")
                )) {
                    friendModel.popDialogState()
                }
            }, launchLogin = ::launchLogin)
        }
    }

    @Composable
    fun AddFriendDialog() {
        AlertDialog(onDismissRequest = {
            friendModel.popDialogState()
        },
                buttons = {
                    Row(
                            modifier = Modifier.padding(all = 8.dp),
                            horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(onClick = {
                            friendModel.popDialogState()
                        },
                                modifier = Modifier.fillMaxWidth()) {
                            Text(getString(R.string.cancel))
                        }
                        Button(onClick = {
                            onAddFriend(friendModel.addFriendUsername)
                        },
                                modifier = Modifier.fillMaxWidth()) {
                            Text(getString(android.R.string.ok))
                        }

                    }
                },
                title = { Text(text = getString(R.string.add_friend)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(text = friendModel.newFriendName,
                                color = if (friendModel.friendNameLoading)
                                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                                else Color.Unspecified
                        )
                        OutlinedTextField(
                                value = friendModel.addFriendUsername,
                                onValueChange = {
                                    friendModel.addFriendUsername = it
                                    friendModel.getFriendName(friendModel.addFriendUsername, launchLogin =
                                    ::launchLogin)
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
                                singleLine = true,
                                label = { Text(text = getString(R.string.username)) },
                                isError = friendModel.usernameCaption.isNotBlank(),
                                placeholder = { Text(text = getString(R.string.placeholder_name)) }
                        )
                        Text(
                                text = friendModel.usernameCaption,
                                color = MaterialTheme.colors.onSurface.copy(
                                        alpha = ContentAlpha.medium),
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                })
    }

    @ExperimentalFoundationApi
    @Composable
    fun FriendCard(friend: Friend, onLongPress: () -> Unit, onEditName: (friend: Friend) -> Unit,
                   onDeletePrompt: (friend: Friend) -> Unit, cached: Boolean = false) {
        var state by remember { mutableStateOf(State.NORMAL) }
        var message: String? by remember { mutableStateOf(null) }

        Box(modifier = Modifier
                .fillMaxWidth(1F)
                .padding(10.dp)
                .combinedClickable(onClick = {
                    state = when (state) {
                        State.NORMAL -> State.CONFIRM
                        State.CONFIRM, State.CANCEL, State.EDIT -> State.NORMAL
                    }
                }, onLongClick = {
                    state = when (state) {
                        State.NORMAL -> State.EDIT
                        else -> state
                    }
                    onLongPress()
                })) {
            Column {
                Text(text = friend.name,
                        style = MaterialTheme.typography.subtitle1,
                        color = if (cached) MaterialTheme.colors.onSurface.copy(alpha =
                        ContentAlpha.medium) else Color.Unspecified,
                        modifier = Modifier
                                .alpha(if (state == State.NORMAL) 1F else 0.5F)
                                .blur(if (state ==
                                        State.NORMAL) 0.dp else 5.dp))
                when {
                    friend.last_message_read -> {
                        Text(text = getString(R.string.read),
                                color = MaterialTheme.colors.onSurface.copy(
                                        alpha = ContentAlpha.medium),
                                style = MaterialTheme.typography.caption)
                    }
                    friend.last_message_sent_id != null -> {
                        Text(text = getString(R.string.sent),
                                color = MaterialTheme.colors.onSurface.copy(
                                        alpha = ContentAlpha.medium),
                                style = MaterialTheme.typography.caption)
                    }
                    else -> {
                        Text(text = "",
                                color = MaterialTheme.colors.onSurface.copy(
                                        alpha = ContentAlpha.medium),
                                style = MaterialTheme.typography.caption)
                    }
                }
            }
            when (state) {
                State.EDIT -> {
                    Row {
                        IconButton(onClick = { state = State.NORMAL }) {
                            Icon(Icons.Filled.Close,
                                    contentDescription = getString(R.string.cancel))
                        }
                        Button(onClick = { onDeletePrompt(friend) }, colors =
                        ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.error,
                                        contentColor = MaterialTheme.colors.onError)) {
                            Text(getString(R.string.delete))
                        }
                        OutlinedButton(onClick = { onEditName(friend) }) {
                            Text(getString(R.string.rename))
                        }
                    }
                }
                State.CONFIRM -> {
                    Row {
                        IconButton(onClick = { state = State.NORMAL }) {
                            Icon(Icons.Filled.Close,
                                    contentDescription = getString(R.string.cancel))
                        }
                        Button(onClick = {
                            state = State.CANCEL
                            message = null
                        }) {
                            Text(getString(R.string.confirm_alert))
                        }
                        OutlinedButton(onClick = {
                            friendModel.appendDialogState(Triple(MainViewModel
                                    .DialogStatus.ADD_MESSAGE_TEXT, friend) {
                                message = it
                                state = State.CANCEL
                            })
                        }) {
                            Text(getString(R.string.add_message))
                        }
                    }
                }
                State.CANCEL -> {
                    var progress by remember { mutableStateOf(0f) }
                    val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
                    )

                    val delay = object : CountDownTimer(UNDO_TIME, UNDO_TIME / UNDO_INTERVALS) {
                        override fun onTick(p0: Long) {
                            progress = (UNDO_TIME - p0).toFloat() / UNDO_TIME
                        }

                        override fun onFinish() {
                            friendModel.sendAlert(friend.id,
                                    message = message,
                                    launchLogin = ::launchLogin)
                        }
                    }

                    LinearProgressIndicator(progress = animatedProgress,
                            modifier = Modifier.clickable {
                                delay.cancel()
                                state = State.NORMAL
                            })


                    delay.start()
                }
                else -> {}
            }

        }
    }

    @ExperimentalFoundationApi
    @Preview
    @Composable
    fun PreviewHome() {
        MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors else lightColors) {
            Home(listOf(Friend("1", "Grace"), Friend("2", "Anita")), {}, {}, {},
                    Triple(MainViewModel.DialogStatus.NONE, null) {}, "")
        }
    }

    public override fun onResume() {
        super.onResume()
        // if Google API isn't available, do this - it's from the docs, should be correct
        if (!checkPlayServices()) return
    }

    private fun checkPlayServices(): Boolean {
        if (GoogleApiAvailability.getInstance()
                        .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
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