package com.example.chat.data.repository

import com.example.chat.data.local.dao.MessageDao
import com.example.chat.data.local.entity.MessageEntity
import com.example.chat.data.local.entity.MessageStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.createChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.decodePayload
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

class RateLimitException(message: String) : Exception(message)

@Serializable
data class MessagePayload(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long
)

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val contactDao: com.example.chat.data.local.dao.ContactDao,
    private val supabaseClient: SupabaseClient
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val currentUserId: String
        get() = supabaseClient.auth.currentUserOrNull()?.id ?: ""

    // Rate limiting state
    private val messageCount = AtomicInteger(0)
    private var lastResetTime = System.currentTimeMillis()

    fun getMessages(contactId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForThread(contactId)

    suspend fun sendMessage(receiverId: String, content: String) {
        checkRateLimit()

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val messageEntity = MessageEntity(
            id = messageId,
            senderId = currentUserId,
            receiverId = receiverId,
            content = content,
            timestamp = timestamp,
            status = MessageStatus.SENDING
        )

        // 1. Insert locally as SENDING
        messageDao.insertMessage(messageEntity)

        try {
            // 2. Transmit via Broadcast
            val channel = supabaseClient.realtime.createChannel("chat:$receiverId")
            channel.broadcast(
                event = "new_message",
                payload = MessagePayload(
                    id = messageId,
                    senderId = currentUserId,
                    receiverId = receiverId,
                    content = content,
                    timestamp = timestamp
                )
            )
            
            // 3. Update status to SENT
            messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
        } catch (e: Exception) {
            // Log error, message stays as SENDING (could implement retry)
            e.printStackTrace()
        }
    }

    private fun checkRateLimit() {
        val now = System.currentTimeMillis()
        if (now - lastResetTime > 60000) {
            messageCount.set(0)
            lastResetTime = now
        }
        if (messageCount.incrementAndGet() > 15) {
            throw RateLimitException("Slow down! Max 15 messages per minute.")
        }
    }

    suspend fun reportAndBlock(targetPhone: String, targetUserId: String) {
        try {
            // 1. Supabase RPC to log spam
            supabaseClient.postgrest.rpc(
                function = "report_spam",
                parameters = mapOf("target_id" to targetUserId)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 2. Local block
        contactDao.updateBlockedStatus(targetPhone, true)
    }

    suspend fun isContactBlocked(phone: String): Boolean {
        return contactDao.getContactByPhone(phone)?.isBlocked ?: false
    }

    suspend fun getContactByPhone(phone: String) = contactDao.getContactByPhone(phone)

    fun initIncomingMessageListener() {
        val myId = currentUserId
        if (myId.isEmpty()) return

        val channel = supabaseClient.realtime.createChannel("chat:$myId")
        
        channel.broadcastFlow<MessagePayload>(event = "new_message")
            .onEach { payload ->
                val entity = MessageEntity(
                    id = payload.id,
                    senderId = payload.senderId,
                    receiverId = payload.receiverId,
                    content = payload.content,
                    timestamp = payload.timestamp,
                    status = MessageStatus.DELIVERED // Automatically delivered once received
                )
                messageDao.insertMessage(entity)
                
                // Optional: Send "DELIVERED" receipt back to sender
                sendDeliveryReceipt(payload.senderId, payload.id)
            }
            .launchIn(repositoryScope)

        repositoryScope.launch {
            channel.subscribe()
        }
    }

    private suspend fun sendDeliveryReceipt(senderId: String, messageId: String) {
        try {
            val channel = supabaseClient.realtime.createChannel("chat:$senderId")
            channel.broadcast(
                event = "delivery_receipt",
                payload = mapOf("messageId" to messageId, "status" to MessageStatus.DELIVERED.name)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
