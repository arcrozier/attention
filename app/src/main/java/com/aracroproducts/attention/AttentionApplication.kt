package com.aracroproducts.attention

import android.app.Application

class AttentionApplication: Application() {
    val database by lazy {AttentionDB.getDB(this)}
    val repository by lazy {AttentionRepository(database)}
}