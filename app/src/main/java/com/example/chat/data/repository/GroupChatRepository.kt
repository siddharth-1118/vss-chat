package com.example.chat.data.repository

import com.example.chat.data.local.dao.GroupDao
import com.example.chat.data.local.dao.MessageDao
import com.example.chat.data.local.entity.GroupEntity
import com.example.chat.data.local.entity.GroupMemberEntity
import com.example.chat.data.local.entity.MessageEntity
import com.example.chat.data.local.entity.MessageStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID
import com.example.chat.core.security.AccountManager
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GroupMessagePayload(
    val id: String,
    val groupId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val messageType: String = "TEXT",
    val remoteUrl: String? = null,
    val fileSize: Long? = null
)

@Serializable
data class GroupInvitePayload(
    val groupId: String,
    val title: String,
    val description: String,
    val createdBy: String,
    val members: List<String>
)

@Singleton
class GroupChatRepository @Inject constructor(
    private val groupDao: GroupDao,
    private val messageDao: MessageDao,
    private val supabaseClient: SupabaseClient,
    private val accountManager: AccountManager
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val myPhone: String
        get() = accountManager.activeAccountPhone

    fun getAllGroups(): Flow<List<GroupEntity>> = groupDao.getAllGroupsFlow()

    suspend fun createGroup(title: String, description: String, memberIds: List<String>) {
        val groupId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val group = GroupEntity(
            groupId = groupId,
            title = title,
            description = description,
            avatarUrl = null,
            createdBy = myPhone,
            createdAt = timestamp
        )

        // Save locally
        groupDao.insertGroup(group)
        val membersList = (memberIds + myPhone).distinct()
        val memberEntities = membersList.map {
            GroupMemberEntity(groupId, it, if (it == myPhone) "ADMIN" else "MEMBER")
        }
        groupDao.insertMembers(memberEntities)

        // Invite other members via their private channels
        memberIds.forEach { memberId ->
            repositoryScope.launch {
                try {
                    val inviteChannel = supabaseClient.realtime.channel("chat:$memberId")
                    try {
                        inviteChannel.subscribe()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    inviteChannel.broadcast(
                        event = "group_invite",
                        message = GroupInvitePayload(groupId, title, description, myPhone, membersList)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Subscribe to the new group channel
        subscribeToGroupChannel(groupId)
    }

    suspend fun sendGroupMessage(
        groupId: String,
        content: String,
        messageType: String = "TEXT",
        mediaUrl: String? = null,
        remoteUrl: String? = null,
        fileSize: Long? = null
    ) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val messageEntity = MessageEntity(
            id = messageId,
            senderId = myPhone,
            receiverId = groupId, // Group ID as receiver
            content = content,
            timestamp = timestamp,
            status = MessageStatus.SENDING,
            messageType = messageType,
            mediaUrl = mediaUrl,
            remoteUrl = remoteUrl,
            fileSize = fileSize
        )

        messageDao.insertMessage(messageEntity)

        try {
            val channel = supabaseClient.realtime.channel("group:$groupId")
            try {
                channel.subscribe()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            channel.broadcast(
                event = "new_group_message",
                message = GroupMessagePayload(
                    id = messageId,
                    groupId = groupId,
                    senderId = myPhone,
                    content = content,
                    timestamp = timestamp,
                    messageType = messageType,
                    remoteUrl = remoteUrl,
                    fileSize = fileSize
                )
            )
            messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun subscribeToGroupChannel(groupId: String) {
        val channel = supabaseClient.realtime.channel("group:$groupId")

        channel.broadcastFlow<GroupMessagePayload>(event = "new_group_message")
            .onEach { payload: GroupMessagePayload ->
                if (payload.senderId != myPhone) {
                    repositoryScope.launch {
                        val message = MessageEntity(
                            id = payload.id,
                            senderId = payload.senderId,
                            receiverId = payload.groupId, // groupId is the receiver
                            content = payload.content,
                            timestamp = payload.timestamp,
                            status = MessageStatus.DELIVERED,
                            messageType = payload.messageType,
                            remoteUrl = payload.remoteUrl,
                            fileSize = payload.fileSize
                        )
                        messageDao.insertMessage(message)
                    }
                }
            }
            .launchIn(repositoryScope)

        repositoryScope.launch {
            channel.subscribe()
        }
    }

    private var activeInviteChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun listenForGroupInvites() {
        repositoryScope.launch {
            accountManager.activeAccountPhoneFlow
                .distinctUntilChanged()
                .collect { myPhone ->
                    activeInviteChannel?.let { oldChannel ->
                        try {
                            oldChannel.unsubscribe()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (myPhone.isEmpty()) return@collect

                    val inviteChannel = supabaseClient.realtime.channel("chat:$myPhone")
                    activeInviteChannel = inviteChannel

                    inviteChannel.broadcastFlow<GroupInvitePayload>(event = "group_invite")
                        .onEach { invite: GroupInvitePayload ->
                            repositoryScope.launch {
                                val group = GroupEntity(
                                    groupId = invite.groupId,
                                    title = invite.title,
                                    description = invite.description,
                                    avatarUrl = null,
                                    createdBy = invite.createdBy,
                                    createdAt = System.currentTimeMillis()
                                )
                                groupDao.insertGroup(group)

                                val members = invite.members.map { memberId ->
                                    GroupMemberEntity(invite.groupId, memberId, if (memberId == invite.createdBy) "ADMIN" else "MEMBER")
                                }
                                groupDao.insertMembers(members)

                                // Auto-subscribe to the newly invited group
                                subscribeToGroupChannel(invite.groupId)
                            }
                        }
                        .launchIn(this)

                    // Auto-subscribe to all existing groups on startup or phone switch
                    repositoryScope.launch {
                        try {
                            val groups = groupDao.getAllGroupsFlow().first()
                            groups.forEach { group ->
                                subscribeToGroupChannel(group.groupId)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    try {
                        supabaseClient.realtime.connect()
                        inviteChannel.subscribe()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
    }
}
