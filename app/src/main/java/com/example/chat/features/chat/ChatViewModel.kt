package com.example.chat.features.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.local.entity.MessageEntity
import com.example.chat.data.repository.ChatRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepositoryImpl,
    private val mediaUploadManager: com.example.chat.data.media.MediaUploadManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])
    val targetId: String get() = contactId
    val myPhone: String get() = repository.currentUserPhone
    
    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _isContactSaved = MutableStateFlow(true)
    val isContactSaved: StateFlow<Boolean> = _isContactSaved.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = repository.getMessages(contactId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        repository.initIncomingMessageListener()
        checkContactStatus()
    }

    private fun checkContactStatus() {
        viewModelScope.launch {
            val contact = repository.getContactByPhone(contactId)
            _isContactSaved.value = contact != null
        }
    }

    fun onMessageChange(text: String) {
        _messageText.value = text
    }

    fun sendMessage() {
        val content = _messageText.value.trim()
        if (content.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(contactId, content)
                _messageText.value = ""
            } catch (e: com.example.chat.data.repository.RateLimitException) {
                _errorEvent.emit(e.message ?: "Rate limit exceeded")
            } catch (e: Exception) {
                _errorEvent.emit("Failed to send: ${e.message}")
            }
        }
    }

    fun reportAndBlock() {
        viewModelScope.launch {
            repository.reportAndBlock(contactId, contactId) // Using phone as ID if real ID unknown
            _errorEvent.emit("User reported and blocked")
        }
    }

    fun sendMediaMessage(uri: android.net.Uri, messageType: String, fileSize: Long) {
        viewModelScope.launch {
            try {
                val messageId = java.util.UUID.randomUUID().toString()
                
                // Cache local cache dir
                val localPath = mediaUploadManager.cacheMediaLocally(uri, messageId)
                
                // Upload to Storage
                val remoteUrl = mediaUploadManager.uploadMedia(uri, repository.currentUserId, messageId)
                
                repository.sendMessage(
                    receiverId = contactId,
                    content = "Shared a $messageType",
                    messageType = messageType,
                    mediaUrl = localPath,
                    remoteUrl = remoteUrl,
                    fileSize = fileSize
                )
            } catch (e: Exception) {
                _errorEvent.emit("Failed to upload media: ${e.message}")
            }
        }
    }
}
