package com.aracroproducts.attentionv2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.android.volley.ClientError
import com.android.volley.NoConnectionError
import com.android.volley.VolleyError
import com.google.android.material.snackbar.Snackbar

/**
 * The class for the settings menu in the app
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        //if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment(viewModel = viewModel))
                    .commit()
        //}
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class UserInfoChangeListener(private val context: Context, private val view: View?, private
    val model: SettingsViewModel) :
            Preference.OnPreferenceChangeListener {
        private val attentionRepository = AttentionRepository(AttentionDB.getDB(context))
        private val networkSingleton = NetworkSingleton.getInstance(context)
        private val token = context.getSharedPreferences(MainViewModel.USER_INFO, Context
                .MODE_PRIVATE).getString(MainViewModel.MY_TOKEN, null)


        private val defaultPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        private fun onResponse(newValue: Any?, key: String) {
            defaultPrefs.edit().apply {
                putString(key, newValue.toString())
                apply()
            }
            if (--model.outstandingRequests == 0) {
                view?.let {
                    Snackbar.make(it, R.string.saved, Snackbar
                            .LENGTH_LONG).show()
                }
            }
        }

        private fun onError(error: VolleyError) {
            model.outstandingRequests--
            when (error) {
                is ClientError -> {
                    MainViewModel.launchLogin(context)
                }
                is NoConnectionError -> {
                    view?.let {
                        Snackbar.make(it, R.string.disconnected, Snackbar
                                .LENGTH_LONG).show()
                    }
                }
                else -> {
                    view?.let {
                        Snackbar.make(it, R.string.connection_error, Snackbar
                                .LENGTH_LONG).show()
                    }
                }
            }
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            if (token != null) {
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
                view?.let {
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
            val userInfoChangeListener = UserInfoChangeListener(localContext, view, viewModel)
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
                usernamePreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
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
                usernamePreference.summary = PreferenceManager.getDefaultSharedPreferences(localContext)
                    .getString(getString(R.string.username_key), getString(R.string.no_username))
            }
            /*

             */

            val vibratePreference = findPreference(getString(R.string.vibrate_preference_key)) as
                    MultiSelectListPreference?
            if (vibratePreference != null) {
                vibratePreference.onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { _, newValue ->
                            vibratePreference.setSummaryFromValues((newValue as? Set<*>) ?:
                            HashSet<String>())
                            true
                        }
                vibratePreference.setSummaryFromValues(vibratePreference.values)
            }

            val ringPreference = findPreference(getString(R.string.ring_preference_key)) as
                    MultiSelectListPreference?
            if (ringPreference != null) {
                ringPreference.onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { _, newValue ->
                            ringPreference.setSummaryFromValues((newValue as? Set<*>) ?:
                            HashSet<String>())
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