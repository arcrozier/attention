package com.aracroproducts.attention

import android.app.Application
import com.google.android.material.color.DynamicColors

class AttentionApplication: Application() {
    val database by lazy {AttentionDB.getDB(this)}
    val repository by lazy {AttentionRepository(database)}

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}