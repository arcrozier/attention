package com.aracroproducts.attention

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.aracroproducts.attention.MainViewModel.Companion.MY_NAME
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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
        val displayDialog: Pair<MainViewModel.DialogStatus, Friend?> by model.dialogState
        val showSnackbar = model.isSnackBarShowing

        Home(
                friends = model.friends.value ?: listOf(),
                onLongPress = {model.onLongPress()},
                onEditName = {model.onEditName(it)},
                onDeletePrompt = {model.onDeletePrompt(it)},
                dialogState = displayDialog,
                showSnackbar = showSnackbar
        )
    }

    @ExperimentalFoundationApi
    @Composable
    fun Home(friends: List<Friend>, onLongPress: () -> Unit, onEditName: (friend: Friend) -> Unit,
             onDeletePrompt: (friend: Friend) -> Unit,
             dialogState: Pair<MainViewModel.DialogStatus, Friend?>, showSnackbar: Boolean) {


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
        // TODO: Read off dialog state and overlay state and display dialogs appropriately
        // Note: Settings dialog should display after other dialogs are resolved
        // https://coflutter.com/jetpack-compose-how-to-show-dialog/
        Scaffold(scaffoldState = scaffoldState,
                topBar = {
                    TopAppBar (
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

    @ExperimentalFoundationApi
    @Composable
    fun FriendCard(friend: Friend, onLongPress: () -> Unit, onEditName: (friend: Friend) -> Unit,
                   onDeletePrompt: (friend: Friend) -> Unit) {
        var state by remember { mutableStateOf(State.NORMAL) }
        var message: String? by remember { mutableStateOf(null)}

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
                        Button(onClick = { state = State.NORMAL}, colors = ButtonDefaults.buttonColors(
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
                        Button(onClick = {onEditName(friend)}, colors = ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.secondary,
                                        contentColor = MaterialTheme.colors.onSecondary)) {
                            Text(getString(R.string.rename))
                        }
                    }
                }
                State.CONFIRM -> {
                    Row {
                        Button(onClick = { state = State.NORMAL}, colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.background,
                                contentColor = MaterialTheme.colors.onBackground
                        )) {

                            Icon(Icons.Filled.Close,
                                    contentDescription = getString(R.string.cancel))
                        }
                        Button(onClick = { state = State.CANCEL
                            message = null
                                         }, colors =
                        ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.error,
                                        contentColor = MaterialTheme.colors.onError)) {
                            Text(getString(R.string.confirm_alert))
                        }
                        Button(onClick = {
                            val context = applicationContext
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
                            builder.show()}, colors = ButtonDefaults
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

                    LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.clickable {
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
                    Pair(MainViewModel.DialogStatus.NONE, null), false)
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