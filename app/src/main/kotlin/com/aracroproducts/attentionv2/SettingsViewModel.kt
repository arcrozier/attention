package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.content.res.Resources.ID_NULL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.TOKEN_UPLOADED

class SettingsViewModel(private val repository: AttentionRepository, application: Application) :
        AndroidViewModel(application) {

    var outstandingRequests by mutableStateOf(ID_NULL)

    var currentPreferenceGroup by mutableStateOf<@Composable () -> Unit>(@Composable {})
    var selectedPreferenceGroupIndex by mutableStateOf(0)

    fun clearAllDatabaseTables() = repository.clearTables()

    fun unregisterDevice(token: String, fcmToken: String) {
        repository.unregisterDevice(token = token, fcmToken = fcmToken)

        val context = getApplication<Application>()
        val fcmTokenPrefs = context.getSharedPreferences(FCM_TOKEN, Context.MODE_PRIVATE)
        fcmTokenPrefs.edit().apply {
            putBoolean(TOKEN_UPLOADED, false)
            apply()
        }
    }
}