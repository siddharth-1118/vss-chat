package com.example.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val isEncrypted: Boolean = true
)

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ
}
