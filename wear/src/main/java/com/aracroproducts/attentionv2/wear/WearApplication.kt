package com.aracroproducts.attentionv2.wear

import com.aracroproducts.common.AttentionApplicationBase

class WearApplication : AttentionApplicationBase() {
    override val mainActivity: Class<*> = HomeActivity::class.java

    override val baseUrl: String = BuildConfig.BASE_URL

    override val alertActivity: Class<*>? = null
}