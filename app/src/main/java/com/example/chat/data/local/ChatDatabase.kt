package com.example.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.chat.data.local.dao.MessageDao
import com.example.chat.data.local.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, com.example.chat.data.local.entity.ContactEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): com.example.chat.data.local.dao.MessageDao
    abstract fun contactDao(): com.example.chat.data.local.dao.ContactDao

    companion object {
        const val DATABASE_NAME = "chat_db"
    }
}
