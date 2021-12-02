package com.aracroproducts.attention

/**
 * Represents the user of the app - Has the ID and the Firebase token
 */
class User @JvmOverloads constructor(var uid: String? = null, var token: String? = null)