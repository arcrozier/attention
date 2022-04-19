package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.*
import com.android.volley.ClientError
import com.android.volley.NoConnectionError
import com.android.volley.VolleyError
import com.google.android.material.snackbar.Snackbar

/**
 * The class for the settings menu in the app
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels(factoryProducer = {
        SettingsViewModelFactory(AttentionRepository(AttentionDB.getDB(this)), application)
    })

    class SettingsViewModelFactory(
        private val attentionRepository: AttentionRepository, private val
        application: Application
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
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment(viewModel = viewModel))
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class UserInfoChangeListener(
        private val context: Context, private val settingsFragment: SettingsFragment, private
        val model: SettingsViewModel, val findPreference: (String) -> EditTextPreference?
    ) :
        Preference.OnPreferenceChangeListener {
        private val attentionRepository = AttentionRepository(AttentionDB.getDB(context))
        private val networkSingleton = NetworkSingleton.getInstance(context)
        private val token = context.getSharedPreferences(
            MainViewModel.USER_INFO, Context
                .MODE_PRIVATE
        ).getString(MainViewModel.MY_TOKEN, null)


        private fun onResponse(newValue: Any?, key: String) {
            findPreference(key)?.text = newValue.toString()
            if (--model.outstandingRequests == 0) {
                settingsFragment.view?.let {
                    val snackBar = Snackbar.make(
                        it, R.string.saved, Snackbar
                            .LENGTH_LONG
                    )
                    snackBar.setAction(android.R.string.ok) {
                        snackBar.dismiss()
                    }.show()
                }
            }
        }

        private fun onError(error: VolleyError) {
            model.outstandingRequests--
            when (error) {
                is ClientError -> {
                    if (error.networkResponse.statusCode == 403) {
                        settingsFragment.view?.let {
                            Snackbar.make(
                                it, R.string.confirm_logout_title, Snackbar
                                    .LENGTH_SHORT
                            ).show()
                        }
                        MainViewModel.launchLogin(context)
                    }
                    else {
                        settingsFragment.view?.let {
                            Snackbar.make(it, R.string.invalid_email, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                is NoConnectionError -> {
                    settingsFragment.view?.let {
                        Snackbar.make(
                            it, R.string.disconnected, Snackbar
                                .LENGTH_LONG
                        ).show()
                    }
                }
                else -> {
                    settingsFragment.view?.let {
                        Snackbar.make(
                            it, R.string.connection_error, Snackbar
                                .LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            if (token != null) {
                model.outstandingRequests++
                when (preference.key) {
                    context.getString(R.string.first_name_key) -> {
                        attentionRepository.editUser(token = token,
                            singleton = networkSingleton,
                            firstName = newValue.toString(),
                            responseListener = {
                                onResponse(newValue, preference.key)
                            },
                            errorListener = { error ->
                                onError(error)
                            })
                    }
                    context.getString(R.string.last_name_key) -> {
                        attentionRepository.editUser(token = token,
                            singleton = networkSingleton,
                            lastName = newValue.toString(),
                            responseListener = {
                                onResponse(newValue, preference.key)
                            },
                            errorListener = { error ->
                                onError(error)
                            })
                    }
                    context.getString(R.string.email_key) -> {
                        attentionRepository.editUser(token = token,
                            singleton = networkSingleton,
                            email = newValue.toString(),
                            responseListener = {
                                onResponse(newValue, preference.key)
                            },
                            errorListener = { error ->
                                onError(error)
                            })
                    }
                }
                settingsFragment.view?.let {
                    Snackbar.make(it, R.string.saving, Snackbar.LENGTH_INDEFINITE)
                        .show()
                }
            } else {
                MainViewModel.launchLogin(context)
            }
            return false
        }

    }

    /**
     * A fragment for individual settings panels
     */
    class SettingsFragment(private val viewModel: SettingsViewModel) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val localContext = context ?: return
            val userInfoChangeListener = UserInfoChangeListener(localContext, this, viewModel,
                    findPreference = this::findPreference)
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
                                val shareBody =
                                    "Add me on Attention! https://attention.aracroproducts" +
                                            ".com/app/add?username=$username$"
                                putExtra(Intent.EXTRA_TEXT, shareBody)
                            }
                            startActivity(Intent.createChooser(sharingIntent, null))
                        }
                        true
                    }
                usernamePreference.summary =
                    PreferenceManager.getDefaultSharedPreferences(localContext)
                        .getString(
                            getString(R.string.username_key),
                            getString(R.string.no_username)
                        )
            }

            val logoutPreference: Preference? = findPreference(getString(R.string.logout_key))
            if (logoutPreference != null) {
                logoutPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(localContext).apply {
                        setTitle(getString(R.string.confirm_logout_title))
                        setMessage(getString(R.string.confirm_logout_message))
                        setPositiveButton(R.string.confirm_logout_title) { dialog, _ ->
                            localContext.getSharedPreferences(
                                MainViewModel.USER_INFO,
                                Context.MODE_PRIVATE
                            ).edit().apply {
                                putString(MainViewModel.MY_TOKEN, null)
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
                            localContext.startActivity(
                                Intent(
                                    localContext,
                                    LoginActivity::class.java
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

            val vibratePreference = findPreference(getString(R.string.vibrate_preference_key)) as
                    MultiSelectListPreference?
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

            val ringPreference = findPreference(getString(R.string.ring_preference_key)) as
                    MultiSelectListPreference?
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
        }

        private fun MultiSelectListPreference.setSummaryFromValues(values: Set<*>) {
            summary = values.joinToString(", ") { entries[findIndexOfValue(it as? String ?: "")] }
        }
    }
}