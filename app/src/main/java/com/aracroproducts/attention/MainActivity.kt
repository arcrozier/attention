package com.aracroproducts.attention

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {
    private val sTAG = javaClass.name
    private var token: String? = null
    private var user: User? = null
    private val friendModel: MainViewModel by viewModels()

    enum class State {
        NORMAL, CONFIRM, CANCEL, EDIT
    }

    /**
     * Callback for retrieving the user's name from a pop-up dialog - passed to
     * registerForActivityResult
     */
    private val nameCallback: ActivityResultCallback<ActivityResult> =
            ActivityResultCallback<ActivityResult> {
                if (it.resultCode != Activity.RESULT_OK) return@ActivityResultCallback
                val prefs = getSharedPreferences(USER_INFO, MODE_PRIVATE)
                val editor = prefs.edit()
                val settingsEditor = PreferenceManager.getDefaultSharedPreferences(this).edit()
                settingsEditor.putString(getString(R.string.name_key),
                        it.data?.getStringExtra(MY_NAME))
                editor.putString(MY_ID, makeId(it.data?.getStringExtra(MY_NAME)))
                editor.apply()
                settingsEditor.apply()
                user!!.uid = prefs.getString(MY_ID, null)
                addUserToDB(user)
            }

    /**
     * Callback for getting the user's name after they edit it in a pop-up dialog - passed to
     * registerForActivityResult
     */
    private val editNameCallback = ActivityResultCallback<ActivityResult> {
        if (it.resultCode != Activity.RESULT_OK) return@ActivityResultCallback

        // data returned from the activity
        val data = it.data
        Log.d(sTAG, "Received edit name callback")
        if (friendModel == null) updateFriendMap()
        if (friendMap == null) {
            Log.w(sTAG, "FriendList was null, unable to edit")
        } else {
            val friendId = data?.getStringExtra(DialogActivity.EXTRA_USER_ID)
                    ?: throw IllegalArgumentException("An ID to edit must be provided")
            friendMap?.getOrPut(friendId, fun(): Friend { return Friend(id = friendId, name = "") })
                    ?.name = data.getStringExtra(MY_NAME).toString()
            saveFriendMap()
            populateFriendList()

        }
    }

    /**
     * Can call launch(Intent) on this to start the activity specified by the intent with the
     * result passed to nameCallback (an Intent to launch DialogActivity)
     */
    private val startNameDialogForResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(), nameCallback)

    /**
     * Can call launch(Intent) on this to start the activity specified by the intent with the
     * result passed to editNameCallback
     * Used with DialogActivity, with extra DialogActivity.EXTRA_EDIT_NAME set to true and
     * extra DialogActivity.EXTRA_USER_ID set to the user id whose name you want to id
     */
    private val startEditNameDialogForResult = registerForActivityResult(ActivityResultContracts
            .StartActivityForResult(), editNameCallback)

    /**
     * Called when the activity is created
     *
     * @param savedInstanceState    Instance data saved from before the activity was killed
     */
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Creates a notification channel for displaying failed-to-send notifications
        createFailedAlertNotificationChannel(this)

        /*
        // Set up UI
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
         */
        if (GoogleApiAvailability.getInstance()
                        .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            // check for Google Play Services
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show()
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this)
            return
        }

        // Load user preferences and data
        val prefs = getSharedPreferences(USER_INFO, MODE_PRIVATE)
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val notificationValues = resources.getStringArray(R.array.notification_values)
        // Set up default preferences
        if (!settings.contains(getString(R.string.ring_preference_key))) {
            val settingsEditor = settings.edit()
            val ringAllowed: MutableSet<String> = HashSet()
            ringAllowed.add(notificationValues[2])
            settingsEditor.putStringSet(getString(R.string.ring_preference_key), ringAllowed)
            settingsEditor.apply()
        }
        if (!settings.contains(getString(R.string.vibrate_preference_key))) {
            val settingsEditor = settings.edit()
            val vibrateAllowed: MutableSet<String> = HashSet()
            vibrateAllowed.add(notificationValues[1])
            vibrateAllowed.add(notificationValues[2])
            settingsEditor.putStringSet(getString(R.string.vibrate_preference_key), vibrateAllowed)
            settingsEditor.apply()
        }
        if (!prefs.contains(UPLOADED)) {
            val editor = prefs.edit()
            editor.putBoolean(UPLOADED, false)
            editor.apply()
        }

        // Load the friendMap
        friendModel.onLaunch(this)

        setContent {
            MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors else lightColors) {
                Home(friendModel)
            }
        }


        // Verify Firebase token and continue configuring settings
        token = prefs.getString(MY_TOKEN, null)
        if (!settings.contains(getString(R.string.name_key)) || !prefs.contains(MY_ID)) {
            user = User()
            val intent = Intent(this, DialogActivity::class.java)
            startNameDialogForResult.launch(intent)
        } else {
            user = User(prefs.getString(MY_ID, null), token)
        }
        if (user!!.uid != null && user!!.token != null && !prefs.getBoolean(UPLOADED, false)) {
            addUserToDB(user)
        } else {
            getToken()
        }

        /*
        // Configure "add friend" button
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { view: View ->
            Log.d(this.javaClass.name, "Attempting to add")
            val intent = Intent(view.context, Add::class.java)
            startActivity(intent)
        }
         */

        // Request permission to draw overlays
        if (!Settings.canDrawOverlays(this) && !prefs.contains(OVERLAY_NO_PROMPT)) {
            val alertDialog = AlertDialog.Builder(this).create()
            alertDialog.setTitle(getString(R.string.draw_title))
            alertDialog.setMessage(getString(R.string.draw_message))
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(
                    R.string.open_settings)) { _: DialogInterface?, _: Int ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + applicationContext.packageName))
                startActivity(intent)
            }
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(
                    R.string.do_not_ask_again)) { _: DialogInterface?, _: Int ->
                val editor = prefs.edit()
                editor.putBoolean(OVERLAY_NO_PROMPT, true)
                editor.apply()
            }
            alertDialog.show()
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun Home(viewModel: MainViewModel) {
        Scaffold(
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
            LazyColumn {
                items(viewModel.friends.values.toMutableList()) { friend ->
                    FriendCard(friend = friend)

                }
            }
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun FriendCard(friend: Friend) {
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
                        Button(onClick = { onDeletePrompt(friend.id, friend.name) }, colors =
                        ButtonDefaults
                                .buttonColors(backgroundColor = MaterialTheme.colors.error,
                                        contentColor = MaterialTheme.colors.onError)) {
                                Text(getString(R.string.delete))
                        }
                        Button(onClick = {onEditName(friend.id)}, colors = ButtonDefaults
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
                            sendAlertToServer(friend.id, message = message)
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
        val previewViewModel = MainViewModel("1" to Friend("1", "Grace"), "2" to Friend("1","Anita"))
        MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors else lightColors) {
            Home(previewViewModel)
        }
    }

    /**
     * Helper method that makes a unique user ID
     * @param name  - The name that the ID is based on
     * @return      - The ID
     */
    private fun makeId(name: String?): String {
        val fullString = (name ?: "") + Build.FINGERPRINT
        val salt = byteArrayOf(69, 42, 0, 37, 10, 127, 34, 85, 83, 24, 98, 75, 49, 8,
                67) // very secure salt but this isn't a cryptographic application so it doesn't really matter
        return try {
            val secretKeyFactory: SecretKeyFactory =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA512") //not available to Android 7.1 and lower
                    else SecretKeyFactory.getInstance(
                            "PBKDF2withHmacSHA1") // available since API 10
            val spec = PBEKeySpec(fullString.toCharArray(), salt, 32, 64)
            val key = secretKeyFactory.generateSecret(spec)
            val hashed = key.encoded
            val builder = StringBuilder()
            for (letter in hashed) {
                builder.append(letter.toInt())
            }
            builder.toString()
        } catch (e: InvalidKeySpecException) {
            e.printStackTrace()
            throw RuntimeException()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            throw RuntimeException()
        }
    }

    /**
     * Helper method that gets the Firebase token
     */
    private fun getToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String?> ->
            if (!task.isSuccessful) {
                Log.w(sTAG, "getInstanceId failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new Instance ID token
            token = task.result
            Log.d(sTAG, "Got token! $token")
            user!!.token = token
            val preferences = getSharedPreferences(USER_INFO, MODE_PRIVATE)
            if (token != preferences.getString(MY_TOKEN, "")) {
                if (user!!.uid == null && preferences.getBoolean(UPLOADED, false)) {
                    preferences.edit().putBoolean(UPLOADED, false).apply()
                } else {
                    addUserToDB(user)
                }
            }

            // Log and toast
            val msg = getString(R.string.msg_token_fmt, token)
            Log.d(sTAG, msg)
        }
    }

    public override fun onResume() {
        super.onResume()
        // if Google API isn't available, do this - it's from the docs, should be correct
        if (GoogleApiAvailability.getInstance()
                        .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show()
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this)
            return
        }
        friendModel.onLaunch(this)
    }

    // Prompts the user to confirm deleting the friend
    fun onDeletePrompt(id: String, name: String) {
        val alertDialog = android.app.AlertDialog.Builder(this).create()
        alertDialog.setTitle(getString(R.string.confirm_delete_title))
        alertDialog.setMessage(getString(R.string.confirm_delete_message, name))
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                getString(R.string.yes)) { dialogInterface: DialogInterface, _: Int ->
            friendModel.onDeleteFriend(id, this)
            dialogInterface.cancel()
        }
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(
                android.R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        alertDialog.show()
    }

    // Shows the edit name dialog
    fun onEditName(id: String) {
        val intent = Intent(this@MainActivity, DialogActivity::class.java)
        intent.putExtra(DialogActivity.EXTRA_EDIT_NAME, true)
        intent.putExtra(DialogActivity.EXTRA_USER_ID, id)
        startEditNameDialogForResult.launch(intent)
    }

    // Provides haptic feedback
    fun onLongPress() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                    getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect: VibrationEffect =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(
                            VibrationEffect.EFFECT_HEAVY_CLICK) else VibrationEffect.createOneShot(
                            100, COMPAT_HEAVY_CLICK)
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(100)
        }
    }

    /**
     * Sends the user's notification to the server
     * @param id        - The recipient ID
     * @param message   - The message to send to the person (if null, goes as regular alert)
     */
    private fun sendAlertToServer(id: String, message: String?) {
        Log.d(sTAG, "Sending alert to server via AppWorker")

        // set up the input data
        val data = Data.Builder().putString(AppWorker.TO, id).putString(AppWorker.FROM, user!!.uid)
                .putString(AppWorker.MESSAGE, message).build()
        // should only run when connected to internet
        val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        // configure work request
        val workRequest = OneTimeWorkRequestBuilder<AppWorker.MessageWorker>().apply {
            setInputData(data)
            setConstraints(constraints)
            setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }.build()

        // enqueue the work
        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(workRequest)

        // get the result from the work
        workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observe(this, {
                    val layout = findViewById<View>(R.id.coordinatorLayout)
                    if (it != null && it.state == WorkInfo.State.SUCCEEDED) {
                        // Success! Let the user know, but not a big deal
                        val snackbar =
                                Snackbar.make(layout, R.string.alert_sent, Snackbar.LENGTH_SHORT)
                        snackbar.show()
                    } else if (it != null && it.state == WorkInfo.State.FAILED) {
                        // Something went wrong. Show the user, since they're still in the app
                        val name = AlertHandler.getFriendNameForID(this, id)
                        val code = it.outputData.getInt(AppWorker.RESULT_CODE,
                                AppWorker.CODE_BAD_REQUEST)
                        val text = if (code == AppWorker.CODE_BAD_REQUEST)
                            getString(R.string.alert_failed_bad_request, name)
                        else
                            getString(R.string.alert_failed_server_error, name)
                        Snackbar.make(layout, text, Snackbar.LENGTH_LONG).show()
                    }
                })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if (id == R.id.action_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Adds the specified new user to the Aracro Products database. If the user already exists,
     * updates the token associated with it
     *
     * @param user  - The user to add to the database
     */
    private fun addUserToDB(user: User?) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String?> ->
            user!!.token = task.result
            Log.d(sTAG, getString(R.string.log_sending_msg))
            val data = Data.Builder()
                    .putString(AppWorker.TOKEN, user.token)
                    .putString(AppWorker.ID, user.uid)
                    .build()
            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val workRequest = OneTimeWorkRequestBuilder<AppWorker.TokenWorker>().apply {
                setInputData(data)
                setConstraints(constraints)
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }.build()
            val workManager = WorkManager.getInstance(this)
            workManager.enqueue(workRequest)

            workManager.getWorkInfoByIdLiveData(workRequest.id)
                    .observe(this, {
                        val editor = getSharedPreferences(USER_INFO, MODE_PRIVATE).edit()
                        if (it != null && it.state == WorkInfo.State.SUCCEEDED) {
                            editor.putBoolean(UPLOADED, true)
                            editor.putString(MY_TOKEN, token)
                            Toast.makeText(this@MainActivity, getString(R.string.user_registered),
                                    Toast.LENGTH_SHORT).show()
                        } else if (it != null && it.state == WorkInfo.State.FAILED) {
                            editor.putBoolean(UPLOADED, false)
                        }
                        editor.apply()
                    })
        }
    }


    companion object {
        const val USER_INFO = "user"
        const val FRIENDS = "listen"
        const val MY_ID = "id"
        const val MY_NAME = "name"
        const val MY_TOKEN = "token"
        const val UPLOADED = "uploaded"
        const val FRIEND_LIST = "friends"
        private const val FRIENDS_MAP_VERSION = "friend map"
        const val OVERLAY_NO_PROMPT = "OverlayDoNotAsk"
        private const val COMPAT_HEAVY_CLICK = 5
        private const val FRIEND_V1 = 1
        private const val FRIEND_MAP_NOT_SUPPORTED = 0
        // time (in milliseconds) that the user has to cancel sending an alert
        private const val UNDO_TIME: Long = 3500
        private const val UNDO_INTERVALS: Long = 10

        /**
         * Helper function to create the notification channel for the failed alert
         *
         * @param context   A context for getting strings
         */
        fun createFailedAlertNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name: CharSequence = context.getString(R.string.alert_failed_channel_name)
                val description = context.getString(R.string.alert_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel =
                        NotificationChannel(AppWorker.FAILED_ALERT_CHANNEL_ID, name, importance)
                channel.description = description
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun getFriendMap(context: Context): MutableMap<String, Friend> {
            val friends = context.getSharedPreferences(FRIENDS, MODE_PRIVATE)
            val friendJson = friends.getString(FRIEND_LIST, "")

            // checks if it was last saved as a map or a list
            val map = friends.getInt(FRIENDS_MAP_VERSION, FRIEND_MAP_NOT_SUPPORTED)
            val editor = friends.edit()
            if (friendJson == "") {
                if (map == FRIEND_MAP_NOT_SUPPORTED) {
                    editor.putInt(FRIENDS_MAP_VERSION, FRIEND_V1)
                    editor.apply()
                }
                return HashMap()
            }
            val gson = Gson()
            // Deserialize the map
            return when (map) {
                FRIEND_V1 -> {
                    val mapType = object : TypeToken<HashMap<String, Friend>>() {}.type
                    gson.fromJson(friendJson, mapType)
                }
                else -> {
                    val listType = object : TypeToken<ArrayList<Array<String?>?>>() {}.type
                    val list: List<Array<String?>?> = gson.fromJson(friendJson, listType)
                    val newFriendMap: MutableMap<String, Friend> = HashMap()
                    for (pair in list) {
                        if (pair == null) continue
                        val id = pair[0] ?: continue
                        val name = pair[1] ?: continue

                        newFriendMap[id] = Friend(id, name)
                    }
                    // save the map again
                    editor.putString(FRIEND_LIST, gson.toJson(newFriendMap))
                    editor.putInt(FRIENDS_MAP_VERSION, FRIEND_V1)
                    editor.apply()
                    newFriendMap
                }
            }
        }
    }
}