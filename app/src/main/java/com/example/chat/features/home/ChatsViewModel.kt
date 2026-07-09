package com.example.chat.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.local.dao.ContactDao
import com.example.chat.data.local.dao.GroupDao
import com.example.chat.data.local.dao.MessageDao
import com.example.chat.data.local.entity.MessageEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

import com.example.chat.core.security.AccountManager

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val contactDao: ContactDao,
    private val accountManager: AccountManager,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val currentUserId: String
        get() = supabaseClient.auth.currentUserOrNull()?.id ?: ""

    val chatSummaries: StateFlow<List<ChatSummary>> = combine(
        messageDao.getAllMessagesFlow(),
        groupDao.getAllGroupsFlow(),
        contactDao.getAllContacts(),
        accountManager.activeAccountPhoneFlow
    ) { messages, groups, contacts, myPhone ->
        if (myPhone.isEmpty()) return@combine emptyList()

        val groupMap = groups.associateBy { it.groupId }
        val contactMap = contacts.associateBy { it.phone }

        // Group messages by conversation ID
        val messagesByThread = messages.groupBy { message ->
            if (groupMap.containsKey(message.receiverId)) {
                message.receiverId
            } else {
                if (message.senderId == myPhone) message.receiverId else message.senderId
            }
        }

        val activeSummaries = messagesByThread.map { (threadId, threadMessages) ->
            val lastMsg = threadMessages.first()
            val isGroup = groupMap.containsKey(threadId)

            val name = if (isGroup) {
                groupMap[threadId]?.title ?: "Group Chat"
            } else {
                contactMap[threadId]?.displayName ?: threadId
            }

            ChatSummary(
                id = threadId,
                name = name,
                lastMessage = lastMsg.content,
                timestamp = formatTimestamp(lastMsg.timestamp),
                unreadCount = 0,
                lastMessageTime = lastMsg.timestamp
            )
        }

        val activeThreads = messagesByThread.keys
        val emptyGroups = groups.filter { !activeThreads.contains(it.groupId) }

        val emptyGroupSummaries = emptyGroups.map { group ->
            ChatSummary(
                id = group.groupId,
                name = group.title,
                lastMessage = "No messages yet",
                timestamp = formatTimestamp(group.createdAt),
                unreadCount = 0,
                lastMessageTime = group.createdAt
            )
        }

        (activeSummaries + emptyGroupSummaries).sortedByDescending { it.lastMessageTime }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
