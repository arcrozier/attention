package com.aracroproducts.attention

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.aracroproducts.attention.MainViewModel.Companion.MY_NAME
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val sTAG = javaClass.name
    private val friendModel: MainViewModel by viewModels()

    enum class State {
        NORMAL, CONFIRM, CANCEL, EDIT
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
             showSnackbar:
             Boolean) {


        val scaffoldState = rememberScaffoldState()
        val scope = rememberCoroutineScope()
        if (showSnackbar) {
            LaunchedEffect(scaffoldState.snackbarHostState) {
                scope.launch {
                    val result = scaffoldState.snackbarHostState.showSnackbar(message = getString(R
                            .string
                            .alert_sent))  // cancels by default after a short amount of time

                    when (result) {
                        SnackbarResult.Dismissed -> {
                            friendModel.dismissSnackBar()
                        }
                        else -> {}
                    }
                }
            }
        }
        when (dialogState.first) {
            MainViewModel.DialogStatus.USERNAME -> UserNameDialog()
            MainViewModel.DialogStatus.OVERLAY_PERMISSION -> OverlaySettingsDialog()
            MainViewModel.DialogStatus.ADD_MESSAGE_TEXT -> dialogState.second?.let {
                AddMessageText(friend = it, onSend = dialogState.third) }
            MainViewModel.DialogStatus.FRIEND_NAME -> dialogState.second?.let {EditFriendNameDialog(
                    friend = it)}
            MainViewModel.DialogStatus.CONFIRM_DELETE -> dialogState.second?.let {DeleteFriendDialog(
                    friend = it)}
            MainViewModel.DialogStatus.NONE -> {}
        }

        Scaffold(scaffoldState = scaffoldState,
                topBar = {
                    TopAppBar(
                            backgroundColor = MaterialTheme.colors.primary,
                            title = { Text(getString(R.string.app_name)) },
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
                        Log.d(this.javaClass.name, "Attempting to add")
                        val intent = Intent(this, Add::class.java)
                        startActivity(intent)
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
            }
        }
    }

    @Composable
    fun AddMessageText(friend: Friend, onSend: (message: String) -> Unit) {
        // on ok call onSend(textfield value)
        var message by rememberSaveable {
            mutableStateOf("")
        }
        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
                confirmButton = {
                    TextButton(onClick = {
                            friendModel.popDialogState()
                        onSend(message)
                    }) {
                        Text( text = getString(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        friendModel.popDialogState()
                    }) {
                        Text(text = getString(R.string.cancel))
                    }
                },
                title = {Text(text = getString(R.string.rename))},
                text = { OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Words),
                        singleLine = false,
                        label = { Text(text = getString(R.string.message_label, friend.name)) },
                        placeholder = { Text(text = getString(R.string.message_hint)) }
                )}
        )
    }

    @Composable
    fun DeleteFriendDialog(friend: Friend) {
        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
                confirmButton = {
                    TextButton(onClick = {
                        friendModel.popDialogState()
                        friendModel.confirmDeleteFriend(friend = friend)
                    }) {
                        Text(text = getString(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        friendModel.popDialogState()
                    }) {
                        Text(text = getString(R.string.do_not_ask_again))
                    }
                },
                title = {Text(text = getString(R.string.confirm_delete_title))},
                text = { Text(text = getString(R.string.confirm_delete_message))}
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
                    TextButton(onClick = {
                        val savingName = name.trim()
                        if (savingName.isEmpty()) {
                            error = true
                        } else {
                            friendModel.confirmEditName(friend.id, savingName)
                            friendModel.popDialogState()
                        }
                    }) {
                        Text( text = getString(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        friendModel.popDialogState()
                    }) {
                        Text(text = getString(R.string.cancel))
                    }
                },
                title = {Text(text = getString(R.string.rename))},
                text = { OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Words),
                        singleLine = true,
                        label = { Text(text = getString(R.string.name)) },
                        isError = error,
                        placeholder = { Text(text = getString(R.string.new_name)) }
                )}
        )
    }

    @Composable
    fun OverlaySettingsDialog() {
        AlertDialog(onDismissRequest = { friendModel.popDialogState() },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + applicationContext.packageName))
                        friendModel.popDialogState()
                        startActivity(intent)
                    }) {
                        Text(text = getString(R.string.open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { val editor = getSharedPreferences(MainViewModel.USER_INFO, MODE_PRIVATE).edit()
                        editor.putBoolean(MainViewModel.OVERLAY_NO_PROMPT, true)
                        editor.apply()
                        friendModel.popDialogState()
                    }) {
                        Text(text = getString(R.string.do_not_ask_again))
                    }
                },
                title = {Text(text = getString(R.string.draw_title))},
                text = { Text(text = getString(R.string.draw_message))}
                )
    }

    @Composable
    fun UserNameDialog() {
        var name by rememberSaveable {
            mutableStateOf("")
        }
        var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = {},
                buttons = {
                    Row(
                            modifier = Modifier.padding(all = 8.dp),
                            horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = {
                            val savingName = name.trim()
                            if (savingName.isEmpty()) {
                                error = true
                            } else {
                                val editor = PreferenceManager
                                        .getDefaultSharedPreferences(this@MainActivity)
                                        .edit()
                                editor.putString(MY_NAME, savingName)
                                editor.apply()
                                friendModel.popDialogState()
                            }
                        },
                                modifier = Modifier.fillMaxWidth()) {
                            Text(getString(android.R.string.ok))
                        }
                    }
                },
                title = { Text(text = getString(R.string.name_dialog_title)) },
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
                            placeholder = { Text(text = getString(R.string.placeholder_name)) }
                    )
                })
    }

    @ExperimentalFoundationApi
    @Composable
    fun FriendCard(friend: Friend, onLongPress: () -> Unit, onEditName: (friend: Friend) -> Unit,
                   onDeletePrompt: (friend: Friend) -> Unit) {
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
            Text(text = friend.name,
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier
                            .alpha(if (state == State.NORMAL) 1F else 0.5F)
                            .blur(if (state ==
                                    State.NORMAL) 0.dp else 5.dp))
            when (state) {
                State.EDIT -> {
                    Row {
                        Button(onClick = { state = State.NORMAL },
                                colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.background,
                                        contentColor = MaterialTheme.colors.onBackground
                                )) {

                            Icon(Icons.Filled.Close,
                                    contentDescription = getString(R.string.cancel))
                        }
                        Button(onClick = { onDeletePrompt(friend) }, colors =
                        ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.error,
                                        contentColor = MaterialTheme.colors.onError)) {
                            Text(getString(R.string.delete))
                        }
                        Button(onClick = { onEditName(friend) }, colors = ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.secondary,
                                        contentColor = MaterialTheme.colors.onSecondary)) {
                            Text(getString(R.string.rename))
                        }
                    }
                }
                State.CONFIRM -> {
                    Row {
                        Button(onClick = { state = State.NORMAL },
                                colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.background,
                                        contentColor = MaterialTheme.colors.onBackground
                                )) {

                            Icon(Icons.Filled.Close,
                                    contentDescription = getString(R.string.cancel))
                        }
                        Button(onClick = {
                            state = State.CANCEL
                            message = null
                        }, colors =
                        ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.error,
                                        contentColor = MaterialTheme.colors.onError)) {
                            Text(getString(R.string.confirm_alert))
                        }
                        Button(onClick = {
                            /*val context = applicationContext
                            val builder = AlertDialog.Builder(context)
                            builder.setTitle(getString(R.string.add_message))
                            val input = EditText(context)
                            input.setHint(R.string.message_hint)
                            input.inputType =
                                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or
                                            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
                            builder.setView(input)
                            builder.setPositiveButton(
                                    android.R.string.ok) { _: DialogInterface?, _: Int ->
                                message = input.text.toString()
                                state = State.CANCEL
                            }
                            builder.setNegativeButton(
                                    android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                            builder.show() */
                                         friendModel.appendDialogState(Triple(MainViewModel
                                                 .DialogStatus.ADD_MESSAGE_TEXT, friend) {
                                             message = it
                                             state = State.CANCEL
                                         })
                        }, colors = ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.secondary,
                                        contentColor = MaterialTheme.colors.onSecondary)) {
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
                            friendModel.sendAlert(friend.id, message = message)
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
                    Triple(MainViewModel.DialogStatus.NONE, null) {}, false)
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