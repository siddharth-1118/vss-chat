package com.example.chat.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.data.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val preferenceManager: PreferenceManager,
    private val accountManager: com.example.chat.core.security.AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun saveProfile(displayName: String) {
        if (displayName.isBlank()) {
            _uiState.value = ProfileUiState.Error("Display name cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val user = supabaseClient.auth.currentUserOrNull() ?: throw Exception("Not authenticated")
                val userId = user.id
                
                // Update profile in Supabase
                supabaseClient.postgrest["profiles"].upsert(
                    mapOf(
                        "id" to userId,
                        "display_name" to displayName,
                        "avatar_url" to "" // Placeholder
                    )
                )

                // Save to local preferences
                preferenceManager.saveProfileLocally(displayName, "Hey there! I am using WhatsApp.")

                // Mark onboarding as complete locally
                preferenceManager.setOnboardingComplete(true)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Failed to save profile")
            }
        }
    }
}

sealed interface ProfileUiState {
    data object Idle : ProfileUiState
    data object Loading : ProfileUiState
    data object Success : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}
