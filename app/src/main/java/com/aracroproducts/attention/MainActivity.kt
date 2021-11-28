package com.aracroproducts.attention

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

class MainActivity : AppCompatActivity() {
    private val sTAG = javaClass.name
    private var token: String? = null
    private var user: User? = null
    private val networkCallback: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(sTAG, "Received callback from network class")
            val editor = getSharedPreferences(USER_INFO, MODE_PRIVATE).edit()
            val resultCode = intent.getIntExtra(AppServer.EXTRA_RESULT_CODE, AppServer.CODE_NA)
            when (Objects.requireNonNull(intent.action)) {
                AppServer.ACTION_POST_TOKEN -> if (resultCode == AppServer.CODE_SUCCESS) {
                    editor.putBoolean(UPLOADED, true)
                    editor.putString(MY_TOKEN, token)
                    Toast.makeText(this@MainActivity, getString(R.string.user_registered),
                            Toast.LENGTH_SHORT).show()
                } else {
                    editor.putBoolean(UPLOADED, false)
                }
                AppServer.ACTION_SEND_ALERT -> if (resultCode == AppServer.CODE_SUCCESS) {
                    val layout = findViewById<View>(R.id.coordinatorLayout)
                    val snackbar = Snackbar.make(layout, R.string.alert_sent, Snackbar.LENGTH_SHORT)
                    snackbar.show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.general_error),
                            Toast.LENGTH_LONG).show()
                }
            }
            editor.apply()
        }
    }

    /**
     * Callback for retrieving the user's name - passed to
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

    private val editNameCallback = ActivityResultCallback<ActivityResult> {
        if (it.resultCode != Activity.RESULT_OK) return@ActivityResultCallback
        val data = it.data
        Log.d(sTAG, "Received edit name callback")
        val friends = getSharedPreferences(FRIENDS, MODE_PRIVATE)
        val friendList = parseFriends(friends.getString(FRIEND_LIST, null))
        if (friendList == null) {
            Log.w(sTAG, "FriendList was null, unable to edit")
        } else {
            val friendId = data!!.getStringExtra(DialogActivity.EXTRA_USER_ID)
            var friend: Array<String>? = null
            var friendIndex = -1
            var i = 0
            while (i < friendList.size) {
                if (friendList[i][1] == friendId) {
                    friend = friendList[i]
                    friendIndex = i
                    break
                }
                i++
            }
            if (friend == null) {
                Log.e(sTAG, "Could not find requested ID to rename")
            } else {
                friend[0] = data.getStringExtra(MY_NAME).toString()
                friendList[friendIndex] = friend
                val gson = Gson()
                val friendJson = gson.toJson(friendList)
                val friendEditor = friends.edit()
                friendEditor.putString(FRIEND_LIST, friendJson)
                friendEditor.apply()
                populateFriendList()
            }
        }
    }

    private val startNameDialogForResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(), nameCallback)

    private val startEditNameDialogForResult = registerForActivityResult(ActivityResultContracts
            .StartActivityForResult(), editNameCallback)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (GoogleApiAvailability.getInstance()
                        .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show()
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this)
            return
        }
        val prefs = getSharedPreferences(USER_INFO, MODE_PRIVATE)
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val notificationValues = resources.getStringArray(R.array.notification_values)
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
        token = prefs.getString(MY_TOKEN, null)
        if (!settings.contains(getString(R.string.name_key)) || !prefs.contains(MY_ID)) {
            user = User()
            val intent = Intent(this, DialogActivity::class.java)
            startNameDialogForResult.launch(intent)
        } else {
            user = User(prefs.getString(MY_ID, null), token)
        }
        if (user!!.uid != null && user!!.token != null && !prefs.getBoolean(UPLOADED, false)) {
            updateToken(user)
        } else {
            getToken()
        }
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { view: View ->
            Log.d(this.javaClass.name, "Attempting to add")
            val intent = Intent(view.context, Add::class.java)
            startActivity(intent)
        }
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

    /**
     * Helper method that makes a unique user ID
     * @param name  - The name that the ID is based on
     * @return      - The ID
     */
    private fun makeId(name: String?): String {
        val fullString = if (name == null) "" else name + Build.FINGERPRINT
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
                    updateToken(user)
                }
            }

            // Log and toast
            val msg = getString(R.string.msg_token_fmt, token)
            Log.d(sTAG, msg)
        }
    }

    public override fun onResume() {
        super.onResume()
        if (GoogleApiAvailability.getInstance()
                        .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show()
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this)
            return
        }
        populateFriendList()
        val filter = IntentFilter(AppServer.ACTION_SEND_ALERT)
        filter.addAction(AppServer.ACTION_POST_TOKEN)
        val manager = LocalBroadcastManager.getInstance(this)
        manager.registerReceiver(networkCallback, filter)
    }

    public override fun onPause() {
        super.onPause()
        val manager = LocalBroadcastManager.getInstance(this)
        manager.unregisterReceiver(networkCallback)
    }

    /**
     * Helper method to put all the friends into the recycler view
     */
    private fun populateFriendList() {
        val friends = getSharedPreferences(FRIENDS, MODE_PRIVATE)
        val friendJson = friends.getString(FRIEND_LIST, null)
        val friendList = parseFriends(friendJson)
                ?: return
        val friendListView = findViewById<RecyclerView>(R.id.friends_list)
        friendListView.layoutManager = LinearLayoutManager(this)
        val dataset = Array(friendList.size) { arrayOf("", "") }
        for (x in friendList.indices) {
            dataset[x][0] = friendList[x][0]
            dataset[x][1] = friendList[x][1]
        }
        val adapterListener: FriendAdapter.Callback = object : FriendAdapter.Callback {
            override fun onSendAlert(id: String, message: String?) {
                sendAlertToServer(id, message)
            }

            override fun onDeletePrompt(position: Int, name: String) {
                val alertDialog = android.app.AlertDialog.Builder(this@MainActivity).create()
                alertDialog.setTitle(getString(R.string.confirm_delete_title))
                alertDialog.setMessage(getString(R.string.confirm_delete_message, name))
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                        getString(R.string.yes)) { dialogInterface: DialogInterface, _: Int ->
                    deleteFriend(position)
                    dialogInterface.cancel()
                }
                alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(
                        android.R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                alertDialog.show()
            }

            override fun onEditName(id: String) {
                val intent = Intent(this@MainActivity, DialogActivity::class.java)
                intent.putExtra(DialogActivity.EXTRA_EDIT_NAME, true)
                intent.putExtra(DialogActivity.EXTRA_USER_ID, id)
                startEditNameDialogForResult.launch(intent)
            }

            override fun onLongPress() {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
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
        }
        val adapter = FriendAdapter(dataset, adapterListener)
        friendListView.adapter = adapter
    }

    /**
     * Deletes the user's friend with the given index
     * @param index - The index that the friend is at
     */
    private fun deleteFriend(index: Int) {
        val friends = getSharedPreferences(FRIENDS, MODE_PRIVATE)
        val friendJson = friends.getString(FRIEND_LIST, null)
        val friendList: List<Array<String>>?
        if (friendJson == null) {
            Log.w(sTAG, "Friend list was null, unable to delete friend")
            return
        }
        val gson = Gson()
        friendList = parseFriends(friendJson)
        friendList!!.removeAt(index)
        Log.d(sTAG, "Removed friend")
        val editor = friends.edit()
        editor.putString(FRIEND_LIST, gson.toJson(friendList))
        editor.apply()
        populateFriendList()
    }

    /**
     * Sends the user's notification to the server
     * @param id        - The recipient ID
     * @param message   - The message to send to the person
     */
    private fun sendAlertToServer(id: String, message: String?) {
        Log.d(sTAG, "Sending alert to server via AppServer service")
        //PendingIntent pendingIntent = createPendingResult(AppServer.CALLBACK_POST_TOKEN, new Intent(), 0);
        val intent = Intent(this@MainActivity, AppServer::class.java)
        intent.putExtra(AppServer.EXTRA_TO, id)
        intent.putExtra(AppServer.EXTRA_FROM, user!!.uid)
        if (message != null) {
            intent.putExtra(AppServer.EXTRA_MESSAGE, message)
        }
        intent.action = AppServer.ACTION_SEND_ALERT
        // intent.putExtra(AppServer.EXTRA_PENDING_RESULT, pendingIntent);
        AppServer.enqueueWork(this, intent)
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
     * Adds the specified new user to the Aracro Products database
     * @param user  - The user to add to the database
     */
    private fun addUserToDB(user: User?) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String?> ->
            user!!.token = task.result

            //PendingIntent pendingIntent = createPendingResult(AppServer.CALLBACK_POST_TOKEN, new Intent(), 0);
            val intent = Intent(this@MainActivity, AppServer::class.java)
            intent.putExtra(AppServer.EXTRA_TOKEN, user.token)
            intent.putExtra(AppServer.EXTRA_ID, user.uid)
            intent.action = AppServer.ACTION_POST_TOKEN
            //intent.putExtra(AppServer.EXTRA_PENDING_RESULT, pendingIntent);
            AppServer.enqueueWork(this@MainActivity, intent)
            Log.d(sTAG, getString(R.string.log_sending_msg))
        }
    }

    /**
     * Alias for addUserToDB()
     * @param user  - the user to add to the database
     */
    private fun updateToken(user: User?) {
        addUserToDB(user)
        /*DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        if (user.getUid() != null) {
            database.child("users").child(user.getUid()).child("token").setValue(user.getToken());
        }*/
    }

    companion object {
        const val USER_INFO = "user"
        const val FRIENDS = "listen"
        const val MY_ID = "id"
        const val MY_NAME = "name"
        const val MY_TOKEN = "token"
        const val UPLOADED = "uploaded"
        const val FRIEND_LIST = "friends"
        const val OVERLAY_NO_PROMPT = "OverlayDoNotAsk"
        private const val COMPAT_HEAVY_CLICK = 5

        /**
         * Converts a json string of friends into a List of {friend name, friend ID} pairs
         * @param json  - JSON string to parse
         * @return      - The list of friends
         */
        fun parseFriends(json: String?): MutableList<Array<String>>? {
            if (json == null) return null
            val gson = Gson()
            val arrayListType = object : TypeToken<List<Array<String>?>?>() {}.type
            return gson.fromJson(json, arrayListType)
        }
    }
}