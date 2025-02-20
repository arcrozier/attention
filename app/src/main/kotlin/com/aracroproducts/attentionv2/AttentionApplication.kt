package com.aracroproducts.attentionv2

import com.aracroproducts.common.AttentionApplicationBase

class AttentionApplication : AttentionApplicationBase() {
    override val mainActivity = MainActivity::class.java

    override val alertActivity = Alert::class.java

    override val baseUrl = BuildConfig.BASE_URL
}