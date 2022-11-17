package com.aracroproducts.attentionv2

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.preference.PreferenceManager

abstract class ComposablePreference<T>(val key: String) {
    abstract var value: T
    val onPreferenceChangeListener: MutableList<ComposablePreferenceChangeListener<T>> = ArrayList()

    fun shouldPersistChange(newValue: T): Boolean {
        for (preferenceChangeListener in onPreferenceChangeListener) {
            if (!preferenceChangeListener.onPreferenceChange(this, newValue)) return false
        }
        return true
    }

    abstract fun getValue(default: T): T
}

class EphemeralPreference<T>(key: String, override var value: T) : ComposablePreference<T>(key) {
    override fun getValue(default: T): T {
        return value
    }
}

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

    override fun getValue(default: Boolean): Boolean {
        return try {
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, default)
        } catch (_: java.lang.ClassCastException) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putBoolean(key, default)
                apply()
            }
            default
        }
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

    override fun getValue(default: String): String {
        return try {
            PreferenceManager.getDefaultSharedPreferences(context).getString(key, default)
            ?: default
        } catch (_: java.lang.ClassCastException) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putString(key, default)
                apply()
            }
            default
        }
    }
}

class StringSetPreference(key: String, val context: Context) :
    ComposablePreference<Set<String>>(key) {
    override var value: Set<String>
        get() = getValue(HashSet())
        set(value) {
            if (!shouldPersistChange(value)) return
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putStringSet(key, value)
                apply()
            }
        }

    override fun getValue(default: Set<String>): Set<String> {
        return try {
            PreferenceManager.getDefaultSharedPreferences(context).getStringSet(key, default)
            ?: default
        } catch (_: java.lang.ClassCastException) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putStringSet(key, default)
                apply()
            }
            default
        }
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

    override fun getValue(default: Float): Float {
        return try {
            PreferenceManager.getDefaultSharedPreferences(context).getFloat(key, default)
        } catch (_: java.lang.ClassCastException) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putFloat(key, default)
                apply()
            }
            default
        }
    }
}

interface ComposablePreferenceChangeListener<T> {
    fun onPreferenceChange(preference: ComposablePreference<T>, newValue: T): Boolean
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringPreferenceChange(
    preference: ComposablePreference<String>,
    dismissDialog: () -> Unit,
    context: Context,
    title: String,
    validate: ((String) -> String)? = null
) {
    var newValue by remember { mutableStateOf(preference.value) }
    var message by remember { mutableStateOf( "" )}
    AlertDialog(onDismissRequest = { dismissDialog() }, dismissButton = {
        OutlinedButton(onClick = dismissDialog) {
            Text(text = context.getString(android.R.string.cancel))
        }
    }, confirmButton = {
        Button(onClick = {
            message = validate?.invoke(newValue) ?: ""
            if (message.isBlank()) {
                return@Button
            }
            preference.value = newValue
            dismissDialog()
        }) {
            Text(text = context.getString(android.R.string.ok))
        }

    }, title = {
        Text(text = title)
    }, text = {
        Column {
            OutlinedTextField(value = newValue, onValueChange = { newValue = it })
            Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = ContentAlpha.medium)
            )
        }

    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatPreferenceChange(
    preference: ComposablePreference<Float>,
    dismissDialog: () -> Unit,
    context: Context,
    title: String,
    validate: ((Float) -> String)? = null
) {
    var newValue by remember { mutableStateOf(preference.value.toString()) }
    var message by remember {
        mutableStateOf("")
    }
    AlertDialog(onDismissRequest = { dismissDialog() }, dismissButton = {
        OutlinedButton(onClick = dismissDialog) {
            Text(text = context.getString(android.R.string.cancel))
        }
    }, confirmButton = {
        Button(onClick = {
            try {
                val temp = newValue.toFloat()
                message = validate?.invoke(temp) ?: ""
                if (message.isNotBlank()) {
                    return@Button
                }
                preference.value = newValue.toFloat()
                dismissDialog()
            } catch (e: NumberFormatException) {
                message = context.getString(R.string.invalid_float)
            }
        }) {
            Text(text = context.getString(android.R.string.ok))
        }

    }, title = {
        Text(text = title)
    }, text = {
        Column {
            OutlinedTextField(value = newValue, onValueChange = { newValue = it }, isError =
            message.isNotBlank())
                Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = ContentAlpha.medium)
                )
            }
        })
}

@Composable
fun MultiSelectListPreferenceChange(
    preference: ComposablePreference<Set<String>>,
    dismissDialog: () -> Unit,
    context: Context,
    title: String,
    entriesRes: Int,
    entryValuesRes: Int
) {
    val newValue = remember {
        mutableStateMapOf(*preference.value.map {
            Pair(
                it, true
            )
        }.toTypedArray())
    }
    val entries = context.resources.getStringArray(entriesRes)
    val entryValues = context.resources.getStringArray(entryValuesRes)

    AlertDialog(onDismissRequest = { dismissDialog() }, dismissButton = {
        OutlinedButton(onClick = dismissDialog) {
            Text(text = context.getString(android.R.string.cancel))
        }
    }, confirmButton = {
        Button(onClick = {
            preference.value = newValue.keys
            dismissDialog()
        }) {
            Text(text = context.getString(android.R.string.ok))
        }

    }, title = {
        Text(text = title)
    }, text = {
        Column {
            for ((index, entry) in entries.withIndex()) {
                Row(
                    modifier = Modifier.toggleable(value = newValue.containsKey(entry),
                                                   onValueChange = {
                                                       if (it) {
                                                           newValue[entry] = true
                                                       } else {
                                                           newValue.remove(entry)
                                                       }
                                                   })
                ) {
                    Checkbox(
                        checked = newValue.containsKey(entry), onCheckedChange = null/*{
                                if (it) {
                                    newValue[entry] = true
                                } else {
                                    newValue.remove(entry)
                                }
                            }*/
                    )
                    Text(text = entryValues[index])
                }
            }
        }

    })
}

fun multiselectListPreferenceSummary(
    value: Set<String>, entries: Array<String>, values: Array<String>
): String {
    val summaryList: MutableList<String> = ArrayList(value.size)
    for (i in values.indices) {
        if (value.contains(values[i])) summaryList.add(entries[i])
    }
    return summaryList
        .joinToString(", ")
}

@Composable
fun CheckboxAction(value: Boolean) {
    Switch(checked = value, onCheckedChange = {})
}