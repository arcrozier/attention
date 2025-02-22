package com.aracroproducts.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

private const val USER_PREFERENCES = "settings"
private var INSTANCE: DataStore<Preferences>? = null
private val INSTANCE_LOCK: Lock = ReentrantLock()

fun getDataStore(appContext: Context): DataStore<Preferences> {
    return INSTANCE ?: synchronized(INSTANCE_LOCK) {
        val instance =
            PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }),
                                              migrations = listOf(
                                                  SharedPreferencesMigration(
                                                      appContext,
                                                      USER_PREFERENCES
                                                  ),
                                                  SharedPreferencesMigration(
                                                      appContext,
                                                      USER_INFO
                                                  ),
                                                  SharedPreferencesMigration(
                                                      appContext,
                                                      FCM_TOKEN
                                                  )
                                              ),
                                              scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                                              produceFile = {
                                                  appContext.preferencesDataStoreFile(
                                                      USER_PREFERENCES
                                                  )
                                              })
        INSTANCE = instance
        instance
    }
}
