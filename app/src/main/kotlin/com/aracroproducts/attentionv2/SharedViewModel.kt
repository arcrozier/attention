package com.aracroproducts.attentionv2

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.common.AttentionRepository
import com.aracroproducts.common.FCM_TOKEN
import com.aracroproducts.common.PreferencesRepository
import com.aracroproducts.common.PreferencesRepository.Companion.MY_TOKEN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SharedViewModel(
    private val repository: AttentionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val applicationScope: CoroutineScope,
    application: Application
) : AndroidViewModel(application) {

    private fun unregisterDevice(token: String, fcmToken: String) {

        viewModelScope.launch {
            try {
                repository.unregisterDevice(token = token, fcmToken = fcmToken)
            } catch (e: Exception) {
                Log.e(
                    this@SharedViewModel::class.java.simpleName,
                    e.stackTraceToString()
                )
            }
        }

    }

    private fun clearAllDatabaseTables() = repository.clearTables()

    fun logout(context: Context, activity: Activity? = null) {
        applicationScope.launch {
            preferencesRepository.let {
                unregisterDevice(
                    it.getValue(stringPreferencesKey(MY_TOKEN), ""),
                    it.getValue(stringPreferencesKey(FCM_TOKEN), "")
                )
            }
            preferencesRepository.bulkEdit { settings ->
                settings.remove(stringPreferencesKey(MY_TOKEN))
                settings.remove(
                    stringPreferencesKey(
                        context.getString(
                            R.string.username_key
                        )
                    )
                )
                settings.remove(
                    stringPreferencesKey(
                        context.getString(
                            R.string.first_name_key
                        )
                    )
                )
                settings.remove(
                    stringPreferencesKey(
                        context.getString(
                            R.string.last_name_key
                        )
                    )
                )
                settings.remove(
                    stringPreferencesKey(
                        context.getString(
                            R.string.email_key
                        )
                    )
                )
            }
            clearAllDatabaseTables()
            ShortcutManagerCompat.removeAllDynamicShortcuts(getApplication())

            activity?.finish()
        }
    }


}