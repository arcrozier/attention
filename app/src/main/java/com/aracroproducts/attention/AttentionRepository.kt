package com.aracroproducts.attention

import kotlinx.coroutines.flow.Flow
import java.util.*

// Declares the DAO as a private property in the constructor. Pass in the DAO
// instead of the whole database, because you only need access to the DAO
class AttentionRepository(private val database: AttentionDB) {

    // Room executes all queries on a separate thread.
    // Observed Flow will notify the observer when the data has changed.
    fun getFriends() = database.getFriendDAO().getFriends()

    // By default Room runs suspend queries off the main thread, therefore, we don't need to
    // implement anything else to ensure we're not doing long running database work
    // off the main thread.
    fun insert(vararg friend: Friend) = database.getFriendDAO().insert(*friend)


    fun delete(vararg friend: Friend) = database.getFriendDAO().delete(*friend)

    fun edit(friend: Friend) = database.getFriendDAO().updateFriend(friend)

    fun isFromFriend(message: Message): Boolean {
        assert(message.direction == DIRECTION.Incoming)
        return database.getFriendDAO().isFriend(message.otherId)
    }

    fun getFriend(id: String): Friend = database.getFriendDAO().getFriend(id)

    fun getMessages(friend: Friend): Flow<List<Message>> = database.getMessageDAO()
            .getMessagesFromUser(friend.id)

    fun appendMessage(message: Message, save: Boolean) {
        if (save) {
            val mMessage = Message(timestamp = Calendar.getInstance().timeInMillis, direction =
            message.direction, otherId = message.otherId, message = message.message)
            database.getMessageDAO().insertMessage(mMessage)
        }
        when (message.direction) {
            DIRECTION.Incoming -> database.getFriendDAO().incrementReceived(message.otherId)
            DIRECTION.Outgoing -> database.getFriendDAO().incrementSent(message.otherId)
        }
    }

    fun sendMessage(message: Message, save: Boolean) {
        // TODO handle internet call here
        appendMessage(message, save)
    }
}