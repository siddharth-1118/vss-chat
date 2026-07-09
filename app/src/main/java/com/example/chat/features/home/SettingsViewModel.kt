package com.example.chat.features.home

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.data.PreferenceManager
import com.example.chat.core.security.AccountManager
import com.example.chat.data.local.dao.MessageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val accountManager: AccountManager,
    private val messageDao: MessageDao,
    private val preferenceManager: PreferenceManager,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    val displayName: StateFlow<String> = preferenceManager.displayName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "User Name"
        )

    val statusMessage: StateFlow<String> = preferenceManager.statusMessage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Hey there! I am using WhatsApp."
        )

    val isDarkMode: StateFlow<Boolean> = preferenceManager.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val trustTier: StateFlow<Int> = preferenceManager.trustTier
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val registrationTimestamp: StateFlow<Long> = preferenceManager.registrationTimestamp
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setDarkMode(enabled)
        }
    }

    fun switchAccount(userId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            accountManager.switchAccount(userId)
            onComplete()
        }
    }

    /**
     * Updates display name and status locally, then pushes display name upsert to remote Supabase.
     */
    fun updateProfile(name: String, status: String) {
        viewModelScope.launch {
            try {
                preferenceManager.saveProfileLocally(name, status)
                val myId = supabaseClient.auth.currentUserOrNull()?.id
                if (myId != null) {
                    supabaseClient.postgrest["profiles"].upsert(
                        mapOf(
                            "id" to myId,
                            "display_name" to name,
                            "avatar_url" to ""
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reads all chat messages from Room, formats them into a text file,
     * and triggers ACTION_SEND via FileProvider to send email backups.
     */
    fun exportChatLogsToEmail(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messages = messageDao.getAllMessagesFlow().first()
                val backupBuilder = StringBuilder()
                backupBuilder.append("VSS Chat Backup - Exported on ${Date()}\n\n")

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                messages.reversed().forEach { msg ->
                    val timeStr = sdf.format(Date(msg.timestamp))
                    backupBuilder.append("[$timeStr] ${msg.senderId} -> ${msg.receiverId}: ${msg.content}\n")
                }

                val backupFile = File(context.cacheDir, "vss_chat_backup.txt")
                backupFile.writeText(backupBuilder.toString())

                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    backupFile
                )

                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "VSS Chat Backup")
                    putExtra(Intent.EXTRA_TEXT, "Attached is your VSS chat backup log file.")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(emailIntent, "Send Backup Email").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
