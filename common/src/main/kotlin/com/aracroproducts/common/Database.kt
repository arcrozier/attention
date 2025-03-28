package com.aracroproducts.common

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.aracroproducts.common.AttentionDB.Companion.DB_V7
import com.google.gson.annotations.SerializedName


@Database(
    version = DB_V7,
    entities = [Friend::class, PendingFriend::class, Message::class, CachedFriend::class, ConversationId::class]
)
abstract class AttentionDB : RoomDatabase() {

    abstract fun getFriendDAO(): FriendDAO

    abstract fun getPendingFriendDAO(): PendingFriendDAO

    abstract fun getMessageDAO(): MessageDA0

    abstract fun getCachedFriendDAO(): CachedFriendDAO

    abstract fun getConversationIdDAO(): ConversationIDDao

    companion object {
        const val DB_V7 = 7
        private const val DB_NAME = "attention_database"

        @Volatile
        private var INSTANCE: AttentionDB? = null

        fun getDB(context: Context): AttentionDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, AttentionDB::class.java, DB_NAME
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance // return instance
                instance
            }
        }
    }
}

@Entity
data class CachedFriend(
    @PrimaryKey val username: String
)

@Entity
data class Friend(
    @SerializedName("username") @PrimaryKey val username: String,
    @SerializedName("name") val name: String,
    @SerializedName("sent") val sent: Int = 0,
    @SerializedName("received") val received: Int = 0,
    @SerializedName("last_message_id_sent") val lastMessageSentId: String? = null,
    @SerializedName("last_message_status")
    val lastMessageStatus: MessageStatus? = null,
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("importance") val importance: Float = 0f,
)

enum class Purpose {
    SILENCE, REPLY, DISMISS, DEFAULT
}

@Entity(
    indices = [Index(value = ["friend", "purpose"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = Friend::class,
        parentColumns = ["username"],
        childColumns = ["friend"],
        onDelete = ForeignKey.Companion.CASCADE,
        onUpdate = ForeignKey.Companion.CASCADE
    )
    ]
)
data class ConversationId(
    @PrimaryKey(autoGenerate = true) val conversationId: Int = 0,
    val friend: String,
    val purpose: Purpose = Purpose.DEFAULT
)

@Entity
data class PendingFriend(
    @SerializedName("username") @PrimaryKey val username: String,
    @SerializedName("name") val name: String,
    @SerializedName("photo") val photo: String?
)

@Entity
data class Message(
    @PrimaryKey(autoGenerate = true) val messageId: Int? = null,
    val timestamp: Long,
    val otherId: String,
    val direction: DIRECTION,
    val message: String?
)

enum class DIRECTION { Outgoing, Incoming }

enum class MessageStatus() {
    @SerializedName("Sending")
    SENDING,

    @SerializedName("Sent")
    SENT,

    @SerializedName("Delivered")
    DELIVERED,

    @SerializedName("Read")
    READ,

    @SerializedName("Error")
    ERROR;
}

const val IMPORTANCE_SCALE = 0.95f
const val MAX_IMPORTANT_PEOPLE = 5

@Dao
interface FriendDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg friend: Friend)

    @Delete
    suspend fun delete(vararg friend: Friend)

    @Update
    suspend fun updateFriend(friend: Friend)

    @Query("UPDATE Friend SET received = received + 1 WHERE username = :id")
    suspend fun incrementReceived(id: String)

    @Query("UPDATE Friend SET sent = sent + 1, importance = importance + 1 WHERE username = :id")
    suspend fun incrementSent(id: String)

    @Query("UPDATE Friend SET importance = importance * $IMPORTANCE_SCALE")
    suspend fun scaleImportance()

    @Query("SELECT * FROM Friend ORDER BY importance DESC LIMIT $MAX_IMPORTANT_PEOPLE")
    suspend fun getTopKFriends(): List<Friend>

    @Query(
        "UPDATE Friend SET lastMessageStatus = :status WHERE username = :id AND (lastMessageSentId = :alertId OR :alertId IS NULL)"
    )
    suspend fun setMessageStatus(status: MessageStatus?, id: String?, alertId: String?)

    @Query("UPDATE Friend SET lastMessageSentId = :alertId WHERE username = :id")
    suspend fun setLastMessageSentId(alertId: String, id: String)

    @Query("SELECT * FROM Friend ORDER BY importance DESC, sent DESC")
    fun getFriends(): LiveData<List<Friend>>

    @Query("DELETE FROM Friend WHERE username NOT IN (:idList)")
    suspend fun keepOnly(vararg idList: String)

    @Query("SELECT * FROM Friend WHERE username = :id")
    suspend fun getFriend(id: String): Friend?
}

@Dao
interface ConversationIDDao {
    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun addConversation(conversationId: ConversationId)

    @Query("SELECT * FROM ConversationId WHERE friend = :friend AND purpose = :purpose")
    suspend fun getConversationId(friend: String, purpose: Purpose): ConversationId?

    @Transaction
    suspend fun getOrInsert(friend: String, purpose: Purpose): ConversationId {
        val res = getConversationId(friend, purpose)
        if (res == null) {
            addConversation(ConversationId(friend = friend, purpose = purpose))
            return getConversationId(friend, purpose)!!
        }
        return res
    }
}

@Dao
interface PendingFriendDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg pendingFriend: PendingFriend)

    @Query("SELECT * FROM PendingFriend")
    fun getPendingFriends(): LiveData<List<PendingFriend>>

    @Query("DELETE FROM PendingFriend WHERE username NOT IN (:username)")
    suspend fun keepOnly(vararg username: String)

    @Query("DELETE FROM PendingFriend WHERE username = :username")
    suspend fun delete(username: String)
}

@Dao
interface CachedFriendDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(vararg friend: CachedFriend)

    @Delete
    suspend fun delete(vararg friend: CachedFriend)

    @Query("SELECT * FROM CachedFriend")
    fun getCachedFriends(): LiveData<List<CachedFriend>>

    @Query("SELECT * FROM CachedFriend")
    suspend fun getCachedFriendsSnapshot(): List<CachedFriend>
}

@Dao
interface MessageDA0 {
    @Query("SELECT * FROM Message WHERE otherId = :userId ORDER BY timestamp DESC")
    fun getMessagesFromUser(userId: String): LiveData<List<Message>>

    @Insert
    suspend fun insertMessage(message: Message)
}
