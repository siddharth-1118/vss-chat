package com.example.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.chat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE senderId = :contactId OR receiverId = :contactId ORDER BY timestamp ASC")
    fun getMessagesForThread(contactId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: com.example.chat.data.local.entity.MessageStatus)

    @Query("UPDATE messages SET mediaUrl = :mediaUrl WHERE id = :id")
    suspend fun updateMessageMediaUrl(id: String, mediaUrl: String)

    @Query("UPDATE messages SET content = :content, isEdited = 1 WHERE id = :id")
    suspend fun editMessageContent(id: String, content: String)

    @Query("UPDATE messages SET content = :deletedText WHERE id = :id")
    suspend fun deleteMessage(id: String, deletedText: String = "This message was deleted")

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>
}
