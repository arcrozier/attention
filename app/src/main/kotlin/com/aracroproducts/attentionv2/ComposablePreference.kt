package com.aracroproducts.attentionv2

import android.content.Context
import androidx.compose.material.AlertDialog
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.preference.PreferenceManager
import java.lang.UnsupportedOperationException

abstract class ComposablePreference<T>(val key: String) {
    abstract var value: T
    val onPreferenceChangeListener: MutableList<ComposablePreferenceChangeListener<T>> =
            ArrayList()

    fun shouldPersistChange(newValue: T): Boolean {
        for (preferenceChangeListener in onPreferenceChangeListener) {
            if (!preferenceChangeListener.onPreferenceChange(this, newValue)) return false
        }
        return true
    }
}

class EphemeralPreference<T>(key: String, override var value: T) : ComposablePreference<T>(key)

/*
class NonPersistentPreference<T>(key: String) : ComposablePreference<Nothing>(key) {
    override var value: Nothing
        get() = throw UnsupportedOperationException("Cannot get value of a non-persistent " +
                "preference")
        set(value) = throw UnsupportedOperationException("Cannot set value of a non-persistent " +
                "preference")
}
 */

class BooleanPreference(key: String, val context: Context) : ComposablePreference<Boolean>(key) {
    override var value
        get() = getValue(false)
        set(value) {
            if (!shouldPersistChange(value)) return
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putBoolean(key, value)
                apply()
            }
        }

    fun getValue(default: Boolean): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, default)
    }
}

class StringPreference(key: String, val context: Context) : ComposablePreference<String>(key) {
    override var value: String
        get() = getValue("")
        set(value) {
            if (!shouldPersistChange(value)) return
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putString(key, value)
                apply()
            }
        }

    fun getValue(default: String): String {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(key, default)
                ?: default
    }
}

class StringSetPreference(key: String, val context: Context) : ComposablePreference<Set<String>>
(key) {
    override var value: Set<String>
        get() = getValue(HashSet())
        set(value) {
            if (!shouldPersistChange(value)) return
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putStringSet(key, value)
                apply()
            }
        }

    fun getValue(default: Set<String>): Set<String> {
        return PreferenceManager.getDefaultSharedPreferences(context).getStringSet(key, default)
                ?: default
    }
}

class FloatPreference(key: String, val context: Context) : ComposablePreference<Float>(key) {
    override var value: Float
        get() = getValue(0f)
        set(value) {
            if (!shouldPersistChange(value)) return
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putFloat(key, value)
                apply()
            }
        }

    fun getValue(default: Float): Float {
        return PreferenceManager.getDefaultSharedPreferences(context).getFloat(key, default)
    }
}

interface ComposablePreferenceChangeListener<T> {
    fun onPreferenceChange(preference: ComposablePreference<T>, newValue: T): Boolean
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringPreferenceChange(preference: ComposablePreference<String>, dismissDialog: () -> Unit,
                           context:
Context, title: String) {
    var newValue by remember { mutableStateOf(preference.value)}
    AlertDialog(onDismissRequest = { dismissDialog() }, dismissButton = {
        OutlinedButton(onClick = dismissDialog) {
            Text(text = context.getString(R.string.cancel))
        }
    }, confirmButton = {
        preference.value = newValue
        dismissDialog()
    }, title = {
        Text(text = title)
    },
            text = {
        OutlinedTextField(value = newValue, onValueChange = { newValue = it })
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatPreferenceChange(preference: ComposablePreference<Float>, dismissDialog: () -> Unit,
                           context:
                           Context, title: String) {
    var newValue by remember { mutableStateOf(preference.value.toString())}
    var valid by remember {
        mutableStateOf(true)
    }
    AlertDialog(onDismissRequest = { dismissDialog() }, dismissButton = {
        OutlinedButton(onClick = dismissDialog) {
            Text(text = context.getString(R.string.cancel))
        }
    }, confirmButton = {
        try {
            preference.value = newValue.toFloat()
            dismissDialog()
        } catch (e: NumberFormatException) {
            valid = false
        }
    }, title = {
        Text(text = title)
    },
            text = {
                OutlinedTextField(value = newValue, onValueChange = { newValue = it }, isError =
                !valid)
                if (!valid) {
                    Text(text = context.getString(R.string.invalid_float), style = MaterialTheme
                            .typography.labelSmall, color = MaterialTheme.colorScheme.onSurface
                            .copy(alpha = ContentAlpha.medium))
                }
            })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectListPreferenceChange(preference: ComposablePreference<Set<String>>, dismissDialog: () ->
Unit,
                          context:
                          Context, title: String) {
    var newValue by remember { mutableStateOf(preference.value)}
    var valid by remember {
        mutableStateOf(true)
    }
    AlertDialog(onDismissRequest = { dismissDialog() }, dismissButton = {
        OutlinedButton(onClick = dismissDialog) {
            Text(text = context.getString(R.string.cancel))
        }
    }, confirmButton = {
        try {
            preference.value = newValue
            dismissDialog()
        } catch (e: NumberFormatException) {
            valid = false
        }
    }, title = {
        Text(text = title)
    },
            text = {
                // TODO
                if (!valid) {
                    Text(text = context.getString(R.string.invalid_float), style = MaterialTheme
                            .typography.labelSmall, color = MaterialTheme.colorScheme.onSurface
                            .copy(alpha = ContentAlpha.medium))
                }
            })
}