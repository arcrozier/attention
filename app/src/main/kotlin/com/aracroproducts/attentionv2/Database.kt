package com.aracroproducts.attentionv2

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import com.aracroproducts.attentionv2.AttentionDB.Companion.DB_V3
import com.google.gson.annotations.SerializedName

class Converters {
    @TypeConverter
    fun toMessageStatus(value: String?): MessageStatus? {
        if (value != null) return MessageStatus.messageStatusForValue(value)
        return null
    }

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus?): String? {
        if (status != null) return status.value
        return null
    }
}

@Database(
    version = DB_V3, entities = [Friend::class, Message::class, CachedFriend::class]
)
abstract class AttentionDB : RoomDatabase() {

    abstract fun getFriendDAO(): FriendDAO

    abstract fun getMessageDAO(): MessageDA0

    abstract fun getCachedFriendDAO(): CachedFriendDAO

    companion object {
        const val DB_V3 = 3
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
    @SerializedName("friend") @PrimaryKey val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("sent") val sent: Int = 0,
    @SerializedName("received") val received: Int = 0,
    @SerializedName("last_message_id_sent") val last_message_sent_id: String? = null,
    @SerializedName("last_message_status") @TypeConverters(Converters::class)
    val last_message_status: MessageStatus? = null,
    @SerializedName("photo") val photo: String? = null,
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

enum class MessageStatus(val value: String) {
    @SerializedName("Sent")
    SENT("Sent"),

    @SerializedName("Delivered")
    DELIVERED("Delivered"),

    @SerializedName("Read")
    READ("Read");

    companion object {

        fun messageStatusForValue(value: String): MessageStatus? {
            return values().find { status ->
                status.value == value
            }
        }
    }
}

@Dao
interface FriendDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg friend: Friend)

    @Delete
    suspend fun delete(vararg friend: Friend)

    @Update
    suspend fun updateFriend(friend: Friend)

    @Query("UPDATE Friend SET received = received + 1 WHERE id = :id")
    suspend fun incrementReceived(id: String)

    @Query("UPDATE Friend SET sent = sent + 1 WHERE id = :id")
    suspend fun incrementSent(id: String)

    @Query("UPDATE Friend SET last_message_sent_id = :message_id WHERE id = :id")
    suspend fun setMessageAlert(message_id: String?, id: String)

    @Query(
        "UPDATE Friend SET last_message_status = :status WHERE id = :id AND " + "last_message_sent_id =" + " :alert_id"
    )
    suspend fun setMessageStatus(status: MessageStatus?, id: String?, alert_id: String?)

    @Query("SELECT * FROM Friend ORDER BY sent DESC")
    fun getFriends(): LiveData<List<Friend>>

    @Query("DELETE FROM Friend WHERE id NOT IN (:idList)")
    suspend fun keepOnly(vararg idList: String)

    @Query("SELECT * FROM Friend ORDER BY sent DESC")
    suspend fun getFriendsSnapshot(): List<Friend>

    @Query("SELECT * FROM Friend WHERE id = :id")
    suspend fun getFriend(id: String): Friend
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