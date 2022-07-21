package com.aracroproducts.attentionv2

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import androidx.preference.children
import androidx.preference.forEachIndexed

class SplitPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : LinearLayout(
    context, attrs, defStyleAttr, defStyleRes
) {

    init {
        orientation = HORIZONTAL
        showDividers = SHOW_DIVIDER_MIDDLE
    }
}