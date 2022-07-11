package com.aracroproducts.attentionv2

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import androidx.preference.children
import androidx.preference.forEachIndexed

class SplitPreference(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : PreferenceCategory(
    context, attrs, defStyleAttr, defStyleRes
) {

    init {
        widgetLayoutResource = R.layout.split_preference
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isClickable = false

        children.forEachIndexed { index, preference ->
            if (index == 1) {

            }
        }
    }
}