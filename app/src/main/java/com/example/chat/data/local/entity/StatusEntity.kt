package com.example.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey
    val statusId: String,
    val userId: String,
    val mediaUrl: String,
    val caption: String?,
    val timestamp: Long
)
