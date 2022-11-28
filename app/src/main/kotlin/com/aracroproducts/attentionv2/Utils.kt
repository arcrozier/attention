package com.aracroproducts.attentionv2

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import kotlin.math.min


fun filterUsername(username: String): String {
    return username.substring(0, min(username.length, 150)).filter {
        it.isLetterOrDigit() or (it == '@') or (it == '_') or (it == '-') or (it == '+') or (it == '.')
    }
}

fun filterSpecialChars(string: String): String {
    return string.filter { letter ->
        letter != '\n' && letter != '\t' && letter != '\r'
    }
}

fun launchLogin(context: Context) {
    ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    val loginIntent = Intent(context, LoginActivity::class.java)
    context.startActivity(loginIntent)
}