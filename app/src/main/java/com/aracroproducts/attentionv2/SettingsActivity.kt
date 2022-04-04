package com.aracroproducts.attentionv2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/**
 * The class for the settings menu in the app
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        //if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment())
                    .commit()
        //}
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * A fragment for individual settings panels
     */
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // TODO add a preference change listener to first name, last name, and email
            // https://developer.android.com/guide/topics/ui/settings/use-saved-values
            // TODO add a preference clicked listener to password
            // https://developer.android.com/guide/topics/ui/settings/customize-your-settings#onpreferenceclicklistener
            // TODO use the updateUserInfo function in the attention repository

            val vibratePreference = findPreference("vibrate_preference") as MultiSelectListPreference?
            if (vibratePreference != null) {
                vibratePreference.onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { _, newValue ->
                            vibratePreference.setSummaryFromValues((newValue as? Set<*>) ?:
                            HashSet<String>())
                            true
                        }
                vibratePreference.setSummaryFromValues(vibratePreference.values)
            }

            val ringPreference = findPreference("ring_preference") as MultiSelectListPreference?
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