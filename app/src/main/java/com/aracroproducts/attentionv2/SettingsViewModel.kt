package com.aracroproducts.attentionv2

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class SettingsViewModel(private val repository: AttentionRepository, application: Application) :
        AndroidViewModel(application) {

    var outstandingRequests by mutableStateOf(0)

    fun clearAllDatabaseTables() = repository.clearTables()
}