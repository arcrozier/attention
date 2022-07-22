package com.aracroproducts.attentionv2

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.*
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.MY_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.USER_INFO
import com.google.android.material.snackbar.Snackbar

/**
 * The class for the settings menu in the app
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels(factoryProducer = {
        SettingsViewModelFactory(AttentionRepository(AttentionDB.getDB(this)), application)
    })

    class SettingsViewModelFactory(
        private val attentionRepository: AttentionRepository, private val application: Application
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
        setContentView(R.layout.settings_activity)
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsFragment(viewModel = viewModel)).commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PreferenceScreen(preferences: @Composable () -> Unit) {
        Scaffold(topBar = {
            TopAppBar(backgroundColor = MaterialTheme.colorScheme.primary, title = {
                Text(
                    getString(R.string.title_activity_settings),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }, navigationIcon = {
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
        }) {
            preferences()
        }
    }

    @Composable
    fun PreferenceGroup(title: Int, preferences: @Composable () -> Unit) {

    }

    @Composable
    fun <T> Preference(
        preference: ComposablePreference<T>,
        title: Int,
        action: (@Composable (value: T) -> Unit)? = null,
        summary: (key: String) -> String = {
            getSharedPreferences(USER_INFO, Context.MODE_PRIVATE).getString(it, null) ?: ""
        },
        modifier: Modifier = Modifier,
        icon: (@Composable BoxScope
        .() -> Unit)? = null,
        reserveIconSpace: Boolean = true,
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
        summaryColor: Color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = ContentAlpha.medium
        ),
        summaryStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall,
        onPreferenceClicked: (key: String, value: MutableState<T>) -> Boolean = { _, _ ->
            false
        },
        onPreferenceChanged: Preference.OnPreferenceChangeListener = Preference
                .OnPreferenceChangeListener { _, _ ->
                    true
                },
        enabled: Boolean = true,

    ) {
        val value = preference.value
        Row(modifier = modifier
            .fillMaxWidth()
            .height(73.dp).clickable(enabled = enabled, onClick = {

            })) {
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
                Text(text = getString(title), style = titleStyle, color = titleColor)
                Text(text = summary(preference.key), style = summaryStyle, color = summaryColor)
            }
            if (action != null) action(value)
        }
    }

    class UserInfoChangeListener(
        private val context: Activity,
        private val settingsFragment: SettingsFragment,
        private val model: SettingsViewModel,
        val findPreference: (String) -> EditTextPreference?
    ) : Preference.OnPreferenceChangeListener {
        private val attentionRepository = AttentionRepository(AttentionDB.getDB(context))


        private fun onResponse(code: Int, newValue: Any?, key: String) {
            model.outstandingRequests--
            when (code) {
                200 -> {
                    findPreference(key)?.text = newValue.toString()
                    if (model.outstandingRequests == 0) {
                        settingsFragment.view?.let {
                            Snackbar.make(
                                it, R.string.saved, Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                400 -> {
                    settingsFragment.view?.let {
                        Snackbar.make(it, R.string.invalid_email, Snackbar.LENGTH_LONG).show()
                    }
                }
                403 -> {
                    settingsFragment.view?.let {
                        Snackbar.make(
                            it, R.string.confirm_logout_title, Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    launchLogin()
                }
            }
        }

        private fun onError() {
            model.outstandingRequests--

            settingsFragment.view?.let {
                Snackbar.make(
                    it, R.string.disconnected, Snackbar.LENGTH_LONG
                ).show()

            }
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            val token = context.getSharedPreferences(
                MainViewModel.USER_INFO, Context.MODE_PRIVATE
            ).getString(MY_TOKEN, null)
            if (token != null) {
                settingsFragment.view?.let {
                    val snackBar = Snackbar.make(it, R.string.saving, Snackbar.LENGTH_INDEFINITE)
                    snackBar.setAction(android.R.string.ok) {
                        snackBar.dismiss()
                    }.show()
                }
                model.outstandingRequests++
                when (preference.key) {
                    context.getString(R.string.first_name_key) -> {
                        attentionRepository.editUser(token = token,
                                                     firstName = newValue.toString(),
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
                                                     lastName = newValue.toString(),
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
                                                     email = newValue.toString(),
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
                launchLogin()
            }
            return false
        }

        private fun launchLogin() {
            val loginIntent = Intent(context, LoginActivity::class.java)
            context.startActivity(loginIntent)
        }

    }

    /**
     * A fragment for individual settings panels
     */
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
                        val username = PreferenceManager.getDefaultSharedPreferences(localContext)
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
                        setNegativeButton(R.string.cancel, null)
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
                findPreference(getString(R.string.vibrate_preference_key)) as MultiSelectListPreference?
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
                findPreference(getString(R.string.ring_preference_key)) as MultiSelectListPreference?
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
                                    setNegativeButton(R.string.cancel) { dialog, _ ->
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
    }
}