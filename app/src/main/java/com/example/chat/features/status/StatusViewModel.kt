package com.example.chat.features.status

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.local.dao.StatusDao
import com.example.chat.data.local.entity.StatusEntity
import com.example.chat.data.media.MediaUploadManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject

@Serializable
data class StatusDto(
    val status_id: String,
    val user_phone: String,
    val media_url: String,
    val caption: String?,
    val timestamp: Long
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val statusDao: StatusDao,
    private val mediaUploadManager: MediaUploadManager,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val currentUserId: String
        get() = supabaseClient.auth.currentUserOrNull()?.id ?: ""

    private val currentUserPhone: String
        get() = supabaseClient.auth.currentUserOrNull()?.phone ?: ""

    // 24 hour threshold
    private val expiryThreshold: Long
        get() = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

    val activeStatuses: StateFlow<List<StatusEntity>> = statusDao.getActiveStatusesFlow(expiryThreshold)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        cleanupExpiredStatuses()
        syncStatusesFromCloud()
    }

    fun cleanupExpiredStatuses() {
        viewModelScope.launch {
            statusDao.deleteExpiredStatuses(expiryThreshold)
        }
    }

    fun syncStatusesFromCloud() {
        viewModelScope.launch {
            try {
                // Fetch statuses from Supabase global table
                val response = supabaseClient.postgrest["statuses"]
                    .select {
                        filter {
                            gt("timestamp", expiryThreshold)
                        }
                    }.decodeList<StatusDto>()

                response.forEach { dto ->
                    statusDao.insertStatus(
                        StatusEntity(
                            statusId = dto.status_id,
                            userId = dto.user_phone, // Stores phone number (no +)
                            mediaUrl = dto.media_url,
                            caption = dto.caption,
                            timestamp = dto.timestamp
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadStatus(uri: Uri, caption: String?) {
        viewModelScope.launch {
            try {
                val statusId = UUID.randomUUID().toString()
                
                // 1. Upload to Supabase Storage bucket 'status_uploads'
                val safePhone = currentUserPhone.removePrefix("+").replace(" ", "").replace("-", "")
                val path = "$safePhone/$statusId"
                val bucket = supabaseClient.storage.from("status_uploads")
                
                val cachedPath = mediaUploadManager.cacheMediaLocally(uri, statusId)
                
                val bytes = java.io.File(cachedPath).readBytes()
                bucket.upload(path = path, data = bytes, upsert = true)
                
                // Public or authenticated URL reference
                val mediaUrl = path

                // 2. Insert locally
                statusDao.insertStatus(
                    StatusEntity(
                        statusId = statusId,
                        userId = safePhone,
                        mediaUrl = mediaUrl,
                        caption = caption,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // 3. Write record to Supabase statuses table
                supabaseClient.postgrest["statuses"].insert(
                    StatusDto(
                        status_id = statusId,
                        user_phone = safePhone,
                        media_url = mediaUrl,
                        caption = caption,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
