package com.example.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val title: String,
    val description: String,
    val avatarUrl: String?,
    val createdBy: String,
    val createdAt: Long
)
