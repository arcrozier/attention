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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
    onPreferenceChangeListener: MutableList<(pref: Preferences.Key<T>, newValue: T) -> Boolean> =
        ArrayList()
): MutableState<T> {

    fun shouldPersistChange(newValue: T): Boolean {
        for (preferenceChangeListener in onPreferenceChangeListener) {
            if (!preferenceChangeListener(key, newValue)) return false
        }
        return true
    }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = remember {
        context.dataStore.data
            .map {
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
                            context.dataStore.edit {
                                it[key] = value
                            }
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

class EphemeralPreference<T>(override var value: T) : MutableState<T> {

    override fun component1(): T {
        return value
    }

    override fun component2(): (T) -> Unit = {
        value = it
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
    var message by remember { mutableStateOf( "" )}


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
            message.isNotBlank(), singleLine = true)
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