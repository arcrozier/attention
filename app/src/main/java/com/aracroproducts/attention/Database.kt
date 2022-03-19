package com.aracroproducts.attention

import android.content.Context
import androidx.room.*
import com.aracroproducts.attention.AttentionDB.Companion.DB_V1
import kotlinx.coroutines.flow.Flow
import java.util.*
import android.util.Base64
import java.security.*

/**
 * Represents the user of the app - Has the ID and the Firebase token
 */
class User @JvmOverloads constructor(var uid: PublicKey? = null, var pk: PrivateKey? = null, var
        token: String? = null)

@Database(
        version = DB_V1,
        entities = [Friend::class, Message::class]
)
abstract class AttentionDB: RoomDatabase() {

    abstract fun getFriendDAO(): FriendDAO

    abstract fun getMessageDAO(): MessageDA0

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
data class Friend (
        @PrimaryKey val id: String,
        val name: String,
        val sent: Int = 0,
        val received: Int = 0)

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
    @Insert
    fun insert(vararg friend: Friend)

    @Delete
    fun delete(vararg friend: Friend)

    @Update
    fun updateFriend(friend: Friend)

    @Query("UPDATE Friend SET received = received + 1 WHERE id = :id")
    fun incrementReceived(id: String)

    @Query("UPDATE Friend SET sent = sent + 1 WHERE id = :id")
    fun incrementSent(id: String)

    @Query("SELECT * FROM Friend ORDER BY sent DESC")
    fun getFriends(): Flow<List<Friend>>

    @Query("SELECT EXISTS (SELECT 1 FROM Friend WHERE id = :id)")
    fun isFriend(id: String): Boolean

    @Query("SELECT * FROM Friend WHERE id = :id")
    fun getFriend(id: String): Friend
}

@Dao
interface MessageDA0 {
    @Query("SELECT * FROM Message WHERE otherId = :userId ORDER BY timestamp DESC")
    fun getMessagesFromUser(userId: String): Flow<List<Message>>

    @Insert
    fun insertMessage(message: Message)
}