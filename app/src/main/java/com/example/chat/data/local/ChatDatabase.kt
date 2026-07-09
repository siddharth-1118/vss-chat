package com.example.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.chat.data.local.dao.MessageDao
import com.example.chat.data.local.dao.ContactDao
import com.example.chat.data.local.dao.GroupDao
import com.example.chat.data.local.dao.StatusDao
import com.example.chat.data.local.entity.MessageEntity
import com.example.chat.data.local.entity.ContactEntity
import com.example.chat.data.local.entity.GroupEntity
import com.example.chat.data.local.entity.GroupMemberEntity
import com.example.chat.data.local.entity.StatusEntity

@Database(
    entities = [
        MessageEntity::class, 
        ContactEntity::class, 
        GroupEntity::class, 
        GroupMemberEntity::class, 
        StatusEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun groupDao(): GroupDao
    abstract fun statusDao(): StatusDao

    companion object {
        const val DATABASE_NAME = "chat_db"
    }
}
