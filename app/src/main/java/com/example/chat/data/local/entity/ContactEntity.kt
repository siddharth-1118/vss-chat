package com.example.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val phone: String, // E.164 format as PK
    val displayName: String,
    val avatarUrl: String?,
    val isAppUser: Boolean = false,
    val isBlocked: Boolean = false,
    val lastSynced: Long = System.currentTimeMillis()
)
