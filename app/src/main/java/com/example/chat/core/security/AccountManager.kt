package com.example.chat.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UserAccount(
    val userId: String,
    val phone: String,
    val accessToken: String,
    val refreshToken: String
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
    }

    private fun loadAccounts() {
        val accountsJson = sharedPreferences.getString("accounts_list", "[]") ?: "[]"
        _accounts.value = Json.decodeFromString(accountsJson)
        _activeUserId.value = sharedPreferences.getString("active_user_id", null)
    }

    fun saveCurrentAccount(userId: String, phone: String) {
        val session = supabaseClient.auth.currentSessionOrNull() ?: return
        val newAccount = UserAccount(
            userId = userId,
            phone = phone,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken
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
        supabaseClient.auth.importSession(
            accessToken = account.accessToken,
            refreshToken = account.refreshToken
        )
        
        persistAccounts(_accounts.value, userId)
    }

    fun logoutCurrent() {
        val currentId = _activeUserId.value ?: return
        val newList = _accounts.value.filter { it.userId != currentId }
        persistAccounts(newList, newList.firstOrNull()?.userId)
    }
}
