package com.example.chat.data.repository

import android.content.Context
import com.example.chat.data.local.dao.MessageDao
import com.example.chat.data.local.entity.MessageEntity
import com.example.chat.data.local.entity.MessageStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.example.chat.core.security.AccountManager

class RateLimitException(message: String) : Exception(message)

@Serializable
data class MessagePayload(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val messageType: String = "TEXT",
    val remoteUrl: String? = null,
    val fileSize: Long? = null,
    val replyToMessageId: String? = null,
    val isEdited: Boolean = false,
    val isCommand: Boolean = false,
    val commandName: String? = null
)

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val contactDao: com.example.chat.data.local.dao.ContactDao,
    private val supabaseClient: SupabaseClient,
    private val accountManager: AccountManager,
    @ApplicationContext private val context: Context
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val currentUserId: String
        get() = supabaseClient.auth.currentUserOrNull()?.id ?: ""

    val currentUserPhone: String
        get() = accountManager.activeAccountPhone

    // Rate limiting state
    private val messageCount = AtomicInteger(0)
    private var lastResetTime = System.currentTimeMillis()

    // Telemetry and security state
    val isBannedState = MutableStateFlow(false)
    private val blockingUsers = mutableSetOf<String>()

    fun getMessages(contactId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForThread(contactId)

    suspend fun sendMessage(
        receiverId: String,
        content: String,
        messageType: String = "TEXT",
        mediaUrl: String? = null,
        remoteUrl: String? = null,
        fileSize: Long? = null,
        replyToMessageId: String? = null
    ) {
        checkRateLimit()

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val messageEntity = MessageEntity(
            id = messageId,
            senderId = currentUserPhone,
            receiverId = receiverId,
            content = content,
            timestamp = timestamp,
            status = MessageStatus.SENDING,
            messageType = messageType,
            mediaUrl = mediaUrl,
            remoteUrl = remoteUrl,
            fileSize = fileSize,
            replyToMessageId = replyToMessageId
        )

        // 1. Insert locally as SENDING
        messageDao.insertMessage(messageEntity)

        try {
            // 2. Transmit via Broadcast
            val cleanReceiverId = receiverId.removePrefix("+")
            val channel = supabaseClient.realtime.channel("chat:$cleanReceiverId")
            channel.broadcast(
                event = "new_message",
                message = MessagePayload(
                    id = messageId,
                    senderId = currentUserPhone,
                    receiverId = receiverId,
                    content = content,
                    timestamp = timestamp,
                    messageType = messageType,
                    remoteUrl = remoteUrl,
                    fileSize = fileSize,
                    replyToMessageId = replyToMessageId
                )
            )
            
            // 3. Update status to SENT
            messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun editMessage(messageId: String, newContent: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        
        // Edit window check: max 15 minutes (900000 ms)
        if (System.currentTimeMillis() - message.timestamp > 900000) {
            throw IllegalStateException("Edit window has expired (15 minutes limit)")
        }

        // Update locally
        messageDao.editMessageContent(messageId, newContent)

        // Broadcast edit command
        try {
            val cleanReceiverId = message.receiverId.removePrefix("+")
            val channel = supabaseClient.realtime.channel("chat:$cleanReceiverId")
            channel.broadcast(
                event = "command",
                message = MessagePayload(
                    id = messageId,
                    senderId = currentUserPhone,
                    receiverId = message.receiverId,
                    content = newContent,
                    timestamp = System.currentTimeMillis(),
                    isCommand = true,
                    commandName = "EDIT"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteForEveryone(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return

        // Update locally
        messageDao.deleteMessage(messageId)

        // Broadcast delete command
        try {
            val cleanReceiverId = message.receiverId.removePrefix("+")
            val channel = supabaseClient.realtime.channel("chat:$cleanReceiverId")
            channel.broadcast(
                event = "command",
                message = MessagePayload(
                    id = messageId,
                    senderId = currentUserPhone,
                    receiverId = message.receiverId,
                    content = "This message was deleted",
                    timestamp = System.currentTimeMillis(),
                    isCommand = true,
                    commandName = "DELETE"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
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

        // 3. Broadcast block alert command to the target so their local count increments
        try {
            val cleanTargetId = targetUserId.removePrefix("+")
            val channel = supabaseClient.realtime.channel("chat:$cleanTargetId")
            channel.broadcast(
                event = "command",
                message = MessagePayload(
                    id = UUID.randomUUID().toString(),
                    senderId = currentUserPhone,
                    receiverId = targetUserId,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    isCommand = true,
                    commandName = "BLOCK"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun trackIncomingBlock(senderId: String) {
        blockingUsers.add(senderId)
        if (blockingUsers.size >= 2) {
            triggerSpamViolation("blocked_by_multiple_users")
        }
    }

    suspend fun triggerSpamViolation(reason: String) {
        try {
            val safeUserId = currentUserId.removePrefix("+")
            supabaseClient.postgrest.rpc(
                function = "ban_user",
                parameters = mapOf("reason" to reason, "user_id" to safeUserId)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isBannedState.value = true
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

    suspend fun isContactBlocked(phone: String): Boolean {
        return contactDao.getContactByPhone(phone)?.isBlocked ?: false
    }

    suspend fun getContactByPhone(phone: String) = contactDao.getContactByPhone(phone)

    private var activeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun initIncomingMessageListener() {
        repositoryScope.launch {
            accountManager.activeAccountPhoneFlow
                .distinctUntilChanged()
                .collect { myPhone ->
                    // Unsubscribe previous channel if any
                    activeChannel?.let { oldChannel ->
                        try {
                            oldChannel.unsubscribe()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (myPhone.isEmpty()) return@collect

                    val cleanMyId = myPhone.removePrefix("+")
                    val channel = supabaseClient.realtime.channel("chat:$cleanMyId")
                    activeChannel = channel
                    
                    channel.broadcastFlow<MessagePayload>(event = "new_message")
                        .onEach { payload ->
                            repositoryScope.launch {
                                val entity = MessageEntity(
                                    id = payload.id,
                                    senderId = payload.senderId,
                                    receiverId = payload.receiverId,
                                    content = payload.content,
                                    timestamp = payload.timestamp,
                                    status = MessageStatus.DELIVERED,
                                    messageType = payload.messageType,
                                    remoteUrl = payload.remoteUrl,
                                    fileSize = payload.fileSize,
                                    replyToMessageId = payload.replyToMessageId
                                )
                                messageDao.insertMessage(entity)
                                
                                // Show a local heads-up notification for the incoming message
                                showLocalNotification(payload)
                                
                                // Trigger download worker if message is a rich media type
                                if (payload.remoteUrl != null) {
                                    val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.chat.data.media.MediaDownloadWorker>()
                                        .setInputData(
                                            androidx.work.workDataOf(
                                                "remoteUrl" to payload.remoteUrl,
                                                "messageId" to payload.id
                                            )
                                        )
                                            .build()
                                    androidx.work.WorkManager.getInstance(context).enqueue(downloadRequest)
                                }

                                sendDeliveryReceipt(payload.senderId, payload.id)
                            }
                        }
                        .launchIn(this)

                    channel.broadcastFlow<MessagePayload>(event = "command")
                        .onEach { payload ->
                            repositoryScope.launch {
                                if (payload.isCommand) {
                                    when (payload.commandName) {
                                        "DELETE" -> {
                                            messageDao.deleteMessage(payload.id)
                                        }
                                        "EDIT" -> {
                                            messageDao.editMessageContent(payload.id, payload.content)
                                        }
                                        "BLOCK" -> {
                                            trackIncomingBlock(payload.senderId)
                                        }
                                    }
                                }
                            }
                        }
                        .launchIn(this)

                    try {
                        supabaseClient.realtime.connect()
                        channel.subscribe()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
    }

    private suspend fun sendDeliveryReceipt(senderId: String, messageId: String) {
        try {
            val cleanSenderId = senderId.removePrefix("+")
            val channel = supabaseClient.realtime.channel("chat:$cleanSenderId")
            channel.broadcast(
                event = "delivery_receipt",
                message = mapOf("messageId" to messageId, "status" to MessageStatus.DELIVERED.name)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun showLocalNotification(payload: MessagePayload) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "chat_messages"
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Chat message notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Look up contact display name or fallback to phone
            val senderName = contactDao.getContactByPhone(payload.senderId)?.displayName ?: "+${payload.senderId}"

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(senderName)
                .setContentText(payload.content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val notificationId = payload.senderId.hashCode()
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
