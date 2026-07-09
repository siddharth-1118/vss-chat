package com.example.chat.data.security

import com.example.chat.core.data.PreferenceManager
import com.example.chat.data.local.dao.ContactDao
import com.example.chat.data.repository.ChatRepositoryImpl
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryTracker @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val contactDao: ContactDao,
    private val chatRepository: ChatRepositoryImpl
) {
    private val messageTimestamps = CopyOnWriteArrayList<Long>()
    private val strangerThreadsOpened = ConcurrentHashMap<String, Long>()

    /**
     * Monitors message velocity. If a Tier 0 user sends > 30 messages in 60 seconds, trigger ban.
     */
    suspend fun trackOutgoingMessage() {
        val tier = preferenceManager.trustTier.first()
        if (tier > 0) return

        val now = System.currentTimeMillis()
        messageTimestamps.add(now)

        // Retain only messages from the last 60 seconds
        val boundary = now - 60000
        messageTimestamps.removeAll { it < boundary }

        if (messageTimestamps.size > 30) {
            triggerViolation("message_velocity_exceeded")
        }
    }

    /**
     * Monitors number of stranger chats initiated. If > 10 in 1 hour, trigger ban.
     */
    suspend fun trackThreadOpened(phone: String) {
        val tier = preferenceManager.trustTier.first()
        if (tier > 0) return

        // If the number is a saved contact, it's not a stranger
        val contact = contactDao.getContactByPhone(phone)
        if (contact != null) return

        val now = System.currentTimeMillis()
        strangerThreadsOpened[phone] = now

        // Retain only stranger threads opened in the last 1 hour
        val boundary = now - (60 * 60 * 1000)
        strangerThreadsOpened.entries.removeIf { it.value < boundary }

        if (strangerThreadsOpened.size > 10) {
            triggerViolation("stranger_thread_limit_exceeded")
        }
    }

    private suspend fun triggerViolation(reason: String) {
        chatRepository.triggerSpamViolation(reason)
    }
}
