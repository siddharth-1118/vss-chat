package com.example.chat.core.di

import android.content.Context
import androidx.room.Room
import com.example.chat.data.local.ChatDatabase
import com.example.chat.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChatDatabase(
        @ApplicationContext context: Context
    ): ChatDatabase {
        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            ChatDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideMessageDao(database: ChatDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideContactDao(database: ChatDatabase): com.example.chat.data.local.dao.ContactDao = database.contactDao()

    @Provides
    fun provideGroupDao(database: ChatDatabase): com.example.chat.data.local.dao.GroupDao = database.groupDao()

    @Provides
    fun provideStatusDao(database: ChatDatabase): com.example.chat.data.local.dao.StatusDao = database.statusDao()
}
