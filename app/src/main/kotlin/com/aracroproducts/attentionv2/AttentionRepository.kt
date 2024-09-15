package com.aracroproducts.attentionv2

import okhttp3.MultipartBody
import java.io.InputStream
import java.util.Calendar
import kotlin.concurrent.thread

// Declares the DAO as a private property in the constructor. Pass in the DAO
// instead of the whole database, because you only need access to the DAO
class AttentionRepository(private val database: AttentionDB) {

    private val apiInterface = APIClient.getClient().create(APIV2::class.java)

    // Room executes all queries on a separate thread.
    // Observed Flow will notify the observer when the data has changed.
    fun getFriends() = database.getFriendDAO().getFriends()

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    suspend fun insert(vararg friend: Friend) {
        database.getFriendDAO().insert(*friend)
    }

    fun clearTables() {
        thread {
            database.clearAllTables()
        }
    }

    suspend fun delete(
        friend: Friend,
        token: String
    ): GenericResult<Void> {
        return apiInterface.deleteFriend(friend.id, authHeader(token))
    }

    suspend fun edit(
        friend: Friend,
        token: String
    ): GenericResult<Void> {
        return apiInterface.editFriendName(friend.id, friend.name, authHeader(token))
    }

    suspend fun getFriend(id: String): Friend =
        database.getFriendDAO().getFriend(id) ?: Friend(id, name = "")

    suspend fun cacheFriend(username: String) {

        database.getCachedFriendDAO().insert(
            CachedFriend(username)
        )
    }

    fun getCachedFriends() = database.getCachedFriendDAO().getCachedFriends()

    suspend fun getCachedFriendsSnapshot(): List<CachedFriend> =
        database.getCachedFriendDAO().getCachedFriendsSnapshot()

    suspend fun deleteCachedFriend(username: String) {

        database.getCachedFriendDAO().delete(CachedFriend(username))

    }

    suspend fun getTopKFriends() = database.getFriendDAO().getTopKFriends()

    fun getMessages(friend: Friend) = database.getMessageDAO().getMessagesFromUser(friend.id)

    suspend fun appendMessage(message: Message, save: Boolean = false) {
        if (save) {
            val mMessage = Message(
                timestamp = Calendar.getInstance().timeInMillis,
                direction = message.direction,
                otherId = message.otherId,
                message = message.message
            )

            database.getMessageDAO().insertMessage(mMessage)
        }

        when (message.direction) {
            DIRECTION.Incoming -> database.getFriendDAO().incrementReceived(message.otherId)
            DIRECTION.Outgoing -> {
                database.getFriendDAO().incrementSent(message.otherId)
                database.getFriendDAO().scaleImportance()
            }
        }

    }

    suspend fun getName(
        token: String,
        username: String
    ): GenericResult<NameResult> {
        return apiInterface.getName(username, authHeader(token))
    }

    suspend fun sendMessage(
        message: Message,
        token: String
    ): GenericResult<AlertResult> {
        assert(message.direction == DIRECTION.Outgoing)
        appendMessage(message)
        return apiInterface.sendAlert(message.otherId, message.message, authHeader(token))
    }

    suspend fun registerDevice(
        token: String, fcmToken: String
    ): GenericResult<Void> {
        return apiInterface.registerDevice(fcmToken, authHeader(token))
    }

    suspend fun unregisterDevice(
        token: String, fcmToken: String
    ): GenericResult<Void> {
        return apiInterface.unregisterDevice(fcmToken, authHeader(token))
    }

    suspend fun editPhoto(
        token: String,
        photo: InputStream,
        uploadCallbacks: ((Float) -> Unit)? = null
    ): GenericResult<Void> {
        return apiInterface.editPhoto(
            photo = photo.let {
                MultipartBody.Part.createFormData(
                    "photo", "pfp", ProgressRequestBody(
                        photo, "image", uploadCallbacks
                    )
                )
            }, token = authHeader(token)
        )
    }

    suspend fun editUser(
        token: String,
        username: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        password: String? = null,
        oldPassword: String? = null,
        email: String? = null
    ): GenericResult<TokenResult> {
        return apiInterface.editUser(
            username = username,
            firstName = firstName,
            lastName = lastName,
            email = email,
            password = password,
            oldPassword = oldPassword,
            token = authHeader(token)
        )
    }

    suspend fun downloadUserInfo(
        token: String
    ): GenericResult<UserDataResult> {
        return apiInterface.getUserInfo(authHeader(token))
    }

    suspend fun updateUserInfo(friends: List<Friend>) {
        database.getFriendDAO().insert(*friends.toTypedArray())
        val keepIDs: Array<String> = Array(friends.size) { index ->
            friends[index].id
        }
        database.getFriendDAO().keepOnly(*keepIDs)

    }

    suspend fun addFriend(
        username: String, name: String, token: String
    ): GenericResult<Void> {
        val result = apiInterface.addFriend(username, authHeader(token))
        insert(Friend(username, name))
        return result
    }

    suspend fun alertDelivered(username: String?, alertId: String?) {

        database.getFriendDAO()
            .setMessageStatus(MessageStatus.DELIVERED.value, alertId = alertId, id = username)

    }

    suspend fun alertRead(username: String?, alertId: String?) {

        database.getFriendDAO()
            .setMessageStatus(MessageStatus.READ.value, alertId = alertId, id = username)

    }

    suspend fun alertError(username: String?) {
        database.getFriendDAO()
            .setMessageStatus(MessageStatus.ERROR.value, id = username, alertId = null)
    }

    suspend fun alertSending(username: String?) {
        database.getFriendDAO()
            .setMessageStatus(MessageStatus.SENDING.value, id = username, alertId = null)
    }

    suspend fun sendDeliveredReceipt(
        alertId: String, from: String, authToken: String
    ): GenericResult<Void> {
        return apiInterface.alertDelivered(alertId, from, authHeader(authToken))
    }

    suspend fun sendReadReceipt(
        alertId: String, from: String, fcmToken: String, authToken: String
    ): GenericResult<Void> {
        return apiInterface.alertRead(alertId, from, fcmToken, authHeader(authToken))
    }

    suspend fun registerUser(
        username: String,
        password: String,
        firstName: String,
        lastName: String,
        email: String
    ): GenericResult<Void> {
        return apiInterface.registerUser(firstName, lastName, username, password, email)
    }

    suspend fun signInWithGoogle(
        userIdToken: String,
        username: String? = null,
        agree: String? = null
    ): TokenResult {
        return apiInterface.googleSignIn(userIdToken, username, agree)
    }

    suspend fun getAuthToken(
        username: String,
        password: String
    ): TokenResult {
        return apiInterface.getToken(username, password)
    }

    suspend fun linkGoogleAccount(
        password: String,
        googleToken: String,
        token: String
    ): GenericResult<Void> {
        return apiInterface.linkAccount(password, googleToken, authHeader(token))
    }


    private fun authHeader(token: String): String {
        return "Token $token"
    }
}