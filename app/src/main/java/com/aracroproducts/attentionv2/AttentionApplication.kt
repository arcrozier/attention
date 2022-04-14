package com.aracroproducts.attentionv2

import android.app.Application
import com.google.android.material.color.DynamicColors

class AttentionApplication: Application() {
    val database by lazy {AttentionDB.getDB(this)}
    val repository by lazy {AttentionRepository(database)}

    override fun onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this)
        super.onCreate()
    }
}