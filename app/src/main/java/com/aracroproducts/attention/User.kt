package com.aracroproducts.attention

import androidx.room.*
import com.aracroproducts.attention.AttentionDB.Companion.DB_V1
import kotlinx.coroutines.flow.Flow

/**
 * Represents the user of the app - Has the ID and the Firebase token
 */
class User @JvmOverloads constructor(var uid: String? = null, var token: String? = null)

@Database(
        version = DB_V1,
        entities = [Friend::class, Message::class]
)
abstract class AttentionDB: RoomDatabase() {

    abstract fun getFriendDAO(): FriendDAO

    abstract fun getMessageDAO(): MessageDA0

    companion object {
        const val DB_V1 = 1
    }
}

@Entity
data class Friend (
        @PrimaryKey val id: String,
        val name: String,
        val sent: Int = 0,
        val received: Int = 0)

@Entity
data class Message (
        @PrimaryKey(autoGenerate = true) val messageId: Int? = null,
        val timestamp: Long,
        val from: String,
        val message: String
)

@Dao
interface FriendDAO {
    @Insert
    fun insertAll(vararg friend: Friend)

    @Delete
    fun delete(friend: Friend)

    @Update
    fun editName(friend: Friend)

    @Query("SELECT * FROM Friend")
    fun getFriends(): Flow<List<Friend>>
}

@Dao
interface MessageDA0 {
    @Query("SELECT * FROM Message WHERE `from` = :userId ORDER BY timestamp")
    fun getMessagesFromUser(userId: String): Flow<List<Message>>

    @Insert
    fun insertMessage(message: Message)
}