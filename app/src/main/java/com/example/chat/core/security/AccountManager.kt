package com.example.chat.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Serializable
data class UserAccount(
    val userId: String,
    val phone: String,
    val sessionJson: String
)

@Serializable
private data class AccountProfileDto(
    val id: String,
    val phone: String
)

@Singleton
class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "secure_accounts",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _accounts = MutableStateFlow<List<UserAccount>>(emptyList())
    val accounts: StateFlow<List<UserAccount>> = _accounts

    private val _activeUserId = MutableStateFlow<String?>(null)
    val activeUserId: StateFlow<String?> = _activeUserId

    init {
        loadAccounts()
        restoreActiveSession()
    }

    private fun restoreActiveSession() {
        val activeId = _activeUserId.value ?: return
        val account = _accounts.value.find { it.userId == activeId } ?: return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val session = Json.decodeFromString<UserSession>(account.sessionJson)
                supabaseClient.auth.importSession(session)
                try {
                    supabaseClient.realtime.connect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Self-healing check: if local phone is empty, recover it from the remote profiles database
                if (account.phone.isEmpty()) {
                    try {
                        val profile = supabaseClient.postgrest["profiles"]
                            .select {
                                filter {
                                    eq("id", activeId)
                                }
                            }.decodeSingleOrNull<AccountProfileDto>()
                        
                        if (profile != null && profile.phone.isNotEmpty()) {
                            val updatedAccount = account.copy(phone = profile.phone)
                            val currentList = _accounts.value.toMutableList()
                            val index = currentList.indexOfFirst { it.userId == activeId }
                            if (index != -1) {
                                currentList[index] = updatedAccount
                                persistAccounts(currentList, activeId)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadAccounts() {
        try {
            val accountsJson = sharedPreferences.getString("accounts_list", "[]") ?: "[]"
            _accounts.value = Json.decodeFromString(accountsJson)
            _activeUserId.value = sharedPreferences.getString("active_user_id", null)
        } catch (e: Exception) {
            // If data is corrupted or old format, clear it
            _accounts.value = emptyList()
            _activeUserId.value = null
            sharedPreferences.edit().clear().apply()
        }
    }

    fun saveCurrentAccount(userId: String, phone: String) {
        if (phone.isEmpty()) return
        
        var session = supabaseClient.auth.currentSessionOrNull()
        var attempts = 0
        while (session == null && attempts < 10) {
            try {
                Thread.sleep(50)
            } catch (e: Exception) {}
            session = supabaseClient.auth.currentSessionOrNull()
            attempts++
        }

        if (session == null) return
        val newAccount = UserAccount(
            userId = userId,
            phone = phone,
            sessionJson = Json.encodeToString(session)
        )
        
        val currentList = _accounts.value.toMutableList()
        val index = currentList.indexOfFirst { it.userId == userId }
        if (index != -1) {
            currentList[index] = newAccount
        } else {
            if (currentList.size >= 2) {
                // For this logic, we might replace or block, but blueprint says "up to 2"
                currentList.removeAt(0) 
            }
            currentList.add(newAccount)
        }
        
        persistAccounts(currentList, userId)
    }

    private fun persistAccounts(list: List<UserAccount>, activeId: String?) {
        sharedPreferences.edit().apply {
            putString("accounts_list", Json.encodeToString(list))
            putString("active_user_id", activeId)
            apply()
        }
        _accounts.value = list
        _activeUserId.value = activeId
    }

    suspend fun switchAccount(userId: String) {
        val account = _accounts.value.find { it.userId == userId } ?: return
        
        // Update Supabase session
        val session = Json.decodeFromString<UserSession>(account.sessionJson)
        supabaseClient.auth.importSession(session)
        
        persistAccounts(_accounts.value, userId)
    }

    fun logoutCurrent() {
        val currentId = _activeUserId.value ?: return
        val newList = _accounts.value.filter { it.userId != currentId }
        persistAccounts(newList, newList.firstOrNull()?.userId)
    }

    val activeAccountPhone: String
        get() = _accounts.value.find { it.userId == _activeUserId.value }?.phone ?: ""

    val activeAccountPhoneFlow: Flow<String> = combine(_accounts, _activeUserId) { list, activeId ->
        list.find { it.userId == activeId }?.phone ?: ""
    }
}
