package com.aracroproducts.attentionv2

import android.content.Context
import androidx.preference.PreferenceManager

abstract class ComposablePreference<T>(val key: String) {
    abstract var value: T
}

class EphemeralPreference<T>(key: String, override var value: T) : ComposablePreference<T>(key)

class BooleanPreference(key: String, val context: Context) : ComposablePreference<Boolean>(key) {
    override var value
        get() = getValue(false)
        set(value) {
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
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putFloat(key, value)
                apply()
            }
        }

    fun getValue(default: Float): Float {
        return PreferenceManager.getDefaultSharedPreferences(context).getFloat(key, default)
    }
}