package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.TOKEN_UPLOADED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(private val repository: AttentionRepository, application: Application) :
        AndroidViewModel(application) {

    var outstandingRequests by mutableStateOf(0)

    var currentPreferenceGroup by mutableStateOf<@Composable () -> Unit>(@Composable {})
    var selectedPreferenceGroupIndex by mutableStateOf(0)

    var photo: ImageBitmap? by mutableStateOf(null)

    init {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir, MainViewModel
                    .PFP_FILENAME).readBytes()
            }
            withContext(Dispatchers.Default) {
                photo = BitmapFactory.decodeByteArray(data, 0, data.size)
                    .asImageBitmap()
            }
        }
    }

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