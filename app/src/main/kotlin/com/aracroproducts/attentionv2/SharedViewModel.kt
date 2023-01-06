package com.aracroproducts.attentionv2

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SharedViewModel(
    private val repository: AttentionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val applicationScope: CoroutineScope,
    application: Application
) : AndroidViewModel(application) {

    private fun unregisterDevice(token: String, fcmToken: String) {
        repository.unregisterDevice(token = token, fcmToken = fcmToken)

        viewModelScope.launch {
            preferencesRepository.setValue(
                booleanPreferencesKey(MainViewModel.TOKEN_UPLOADED),
                false
            )
        }

    }

    private fun clearAllDatabaseTables() = repository.clearTables()

    fun logout(context: Context, activity: Activity? = null) {
        applicationScope.launch {
            preferencesRepository.let {
                unregisterDevice(
                    it.getValue(stringPreferencesKey(MainViewModel.MY_TOKEN), ""),
                    it.getValue(stringPreferencesKey(MainViewModel.FCM_TOKEN), "")
                )
            }
            preferencesRepository.bulkEdit { settings ->
                settings.remove(stringPreferencesKey(MainViewModel.MY_TOKEN))
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