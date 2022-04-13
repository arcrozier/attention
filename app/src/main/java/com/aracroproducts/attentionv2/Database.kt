package com.aracroproducts.attentionv2

import android.content.Context
import androidx.room.*
import com.aracroproducts.attentionv2.AttentionDB.Companion.DB_V1
import kotlinx.coroutines.flow.Flow

@Database(
        version = DB_V1,
        entities = [Friend::class, Message::class, CachedFriend::class]
)
abstract class AttentionDB: RoomDatabase() {

    abstract fun getFriendDAO(): FriendDAO

    abstract fun getMessageDAO(): MessageDA0

    abstract fun getCachedFriendDAO(): CachedFriendDAO

    companion object {
        const val DB_V1 = 1
        private const val DB_NAME = "attention_database"
        @Volatile
        private var INSTANCE: AttentionDB? = null

        fun getDB(context: Context): AttentionDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AttentionDB::class.java,
                        DB_NAME
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}

@Entity
data class CachedFriend (
        @PrimaryKey val username: String
        )

@Entity
data class Friend (
        @PrimaryKey val id: String,
        val name: String,
        val sent: Int = 0,
        val received: Int = 0,
        val last_message_sent_id: String? = null,
        val last_message_read: Boolean = false
)

data class Name (
        @ColumnInfo(name = "name") val name: String?
        )

@Entity
data class Message (
        @PrimaryKey(autoGenerate = true) val messageId: Int? = null,
        val timestamp: Long,
        val otherId: String,
        val direction: DIRECTION,
        val message: String?
)

enum class DIRECTION {Outgoing, Incoming}

@Dao
interface FriendDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg friend: Friend)

    @Delete
    fun delete(vararg friend: Friend)

    @Update
    fun updateFriend(friend: Friend)

    @Query("SELECT name FROM Friend WHERE id = :id")
    fun getFriendName(id: String): Name

    @Query("UPDATE Friend SET received = received + 1 WHERE id = :id")
    fun incrementReceived(id: String)

    @Query("UPDATE Friend SET sent = sent + 1 WHERE id = :id")
    fun incrementSent(id: String)

    @Query("UPDATE Friend SET last_message_sent_id = :message_id WHERE id = :id")
    fun setMessageAlert(message_id: String, id: String)

    @Query("UPDATE Friend SET last_message_read = :read WHERE id = :id AND last_message_sent_id =" +
            " :alert_id")
    fun setMessageRead(read: Boolean, id: String?, alert_id: String?)

    @Query("SELECT * FROM Friend ORDER BY sent DESC")
    fun getFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM Friend ORDER BY sent DESC")
    suspend fun getFriendsSnapshot(): List<Friend>

    @Query("SELECT * FROM Friend WHERE id = :id")
    fun getFriend(id: String): Friend
}

@Dao
interface CachedFriendDAO {
    @Insert
    fun insert(vararg friend: CachedFriend)

    @Delete
    fun delete(vararg friend: CachedFriend)

    @Query("SELECT * FROM CachedFriend")
    fun getCachedFriends(): Flow<List<CachedFriend>>

    @Query("SELECT * FROM CachedFriend")
    suspend fun getCachedFriendsSnapshot(): List<CachedFriend>
}

@Dao
interface MessageDA0 {
    @Query("SELECT * FROM Message WHERE otherId = :userId ORDER BY timestamp DESC")
    fun getMessagesFromUser(userId: String): Flow<List<Message>>

    @Insert
    fun insertMessage(message: Message)
}