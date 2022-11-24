package com.aracroproducts.attentionv2

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PreferencesRepository (private val dataStore: DataStore<Preferences>) {

    suspend fun <T> setValue(key: Preferences.Key<T>, value: T) {
        dataStore.edit {
            it[key] = value
        }
    }

    suspend fun bulkEdit(transform: (MutablePreferences) -> Unit) {
        dataStore.edit(transform = transform)
    }

    suspend fun <T> getValue(key: Preferences.Key<T>, default: T): T {
        return dataStore.data.first()[key] ?: default
    }

    suspend fun <T> getValue(key: Preferences.Key<T>): T? {
        return dataStore.data.first()[key]
    }

    fun <R> subscribe(transform: suspend (Preferences) -> R): Flow<R> {
        return dataStore.data.map(transform = transform)
    }

    suspend fun contains(key: Preferences.Key<*>): Boolean {
        return key in dataStore.data.first()
    }
}