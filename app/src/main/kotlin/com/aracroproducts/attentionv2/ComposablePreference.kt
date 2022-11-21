package com.aracroproducts.attentionv2

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.launch


/**
 * Remembers a preference in a DataStore
 *
 * Used with modification from Phil Dukhov (https://stackoverflow.com/a/69266511/7484693)
 *
 * @param key - The key for the preference
 * @param defaultValue - The default value if the key is not found
 * @param onPreferenceChangeListener - A list of functions that take the key and the proposed
 * value and return true if the new value should be persisted and false otherwise. They are
 * called in the order provided to this field. The first listener to return false will stop the
 * persisting and no further listeners will be called. All listeners must return true for the
 * value to be persisted. If there are no listeners, value will be persisted (default).
 *
 * @return a MutableState that can serve as a property delegate and will trigger recompositions
 * when the underlying preference is changed
 */
@Composable
fun <T> rememberPreference(
        key: Preferences.Key<T>,
        defaultValue: T,
        onPreferenceChangeListener: List<(pref: Preferences.Key<T>, newValue: T) -> Boolean> =
                listOf(),
        repository: PreferencesRepository
): MutableState<T> {

    fun shouldPersistChange(newValue: T): Boolean {
        for (preferenceChangeListener in onPreferenceChangeListener) {
            if (!preferenceChangeListener(key, newValue)) return false
        }
        return true
    }

    val coroutineScope = rememberCoroutineScope()
    val state = remember {
        repository.subscribe {
            it[key] ?: defaultValue
        }
    }.collectAsState(initial = defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    if (shouldPersistChange(value)) {
                        coroutineScope.launch {
                            repository.setValue(key, value)
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringPreferenceChange(
        value: String,
        setValue: (String) -> Unit,
        dismissDialog: () -> Unit,
        context: Context,
        title: String,
        keyboardOptions: KeyboardOptions? = null,
        validate: ((String) -> String)? = null
) {

    var newValue by remember { mutableStateOf(value) }
    var message by remember { mutableStateOf("") }


    fun onDone() {
        message = validate?.invoke(newValue) ?: ""
        if (message.isNotBlank()) {
            return
        }
        setValue(newValue)
        dismissDialog()
    }

    AlertDialog(onDismissRequest = { dismissDialog() }, dismissButton = {
        OutlinedButton(onClick = dismissDialog) {
            Text(text = context.getString(android.R.string.cancel))
        }
    }, confirmButton = {
        Button(onClick = {
            onDone()
        }) {
            Text(text = context.getString(android.R.string.ok))
        }

    }, title = {
        Text(text = title)
    }, text = {
        Column {
            OutlinedTextField(value = newValue, onValueChange = { newValue = it }, singleLine =
            true, isError = message.isNotBlank(), keyboardOptions = keyboardOptions?.copy
            (imeAction = ImeAction.Done) ?: KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions { onDone() }
            )
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
        value: Float,
        setValue: (Float) -> Unit,
        dismissDialog: () -> Unit,
        context: Context,
        title: String,
        validate: ((Float) -> String)? = null
) {
    var newValue by remember { mutableStateOf(value.toString()) }
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
                setValue(newValue.toFloat())
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
            message.isNotBlank(), singleLine = true, keyboardOptions = KeyboardOptions
            (keyboardType = KeyboardType.Decimal)
            )
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
        value: Set<String>,
        setValue: (Set<String>) -> Unit,
        dismissDialog: () -> Unit,
        context: Context,
        title: String,
        entriesRes: Int,
        entryValuesRes: Int
) {
    val newValue = remember {
        mutableStateMapOf(*value.map {
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
            setValue(newValue.keys)
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