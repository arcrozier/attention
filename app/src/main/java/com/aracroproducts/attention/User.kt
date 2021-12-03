package com.aracroproducts.attention

/**
 * Represents the user of the app - Has the ID and the Firebase token
 */
class User @JvmOverloads constructor(var uid: String? = null, var token: String? = null)

class Friend (val id: String, var name: String, var sent: Int = 0, var received: Int = 0, var
messages:
MutableList<String> = ArrayList())