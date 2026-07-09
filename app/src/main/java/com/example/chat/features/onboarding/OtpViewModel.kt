package com.example.chat.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.providers.builtin.OTP
import com.example.chat.core.security.AccountManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val accountManager: AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<OtpUiState>(OtpUiState.Idle)
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private val _resendTimer = MutableStateFlow(60)
    val resendTimer: StateFlow<Int> = _resendTimer.asStateFlow()

    init {
        startResendTimer()
    }

    private fun startResendTimer() {
        viewModelScope.launch {
            _resendTimer.value = 60
            while (_resendTimer.value > 0) {
                delay(1000)
                _resendTimer.value -= 1
            }
        }
    }

    fun verifyOtp(phone: String, token: String) {
        viewModelScope.launch {
            _uiState.value = OtpUiState.Loading
            try {
                supabaseClient.auth.verifyPhoneOtp(
                    type = OtpType.Phone.SMS,
                    phone = phone.removePrefix("+"),
                    token = token
                )
                val user = supabaseClient.auth.currentUserOrNull()
                if (user != null) {
                    accountManager.saveCurrentAccount(user.id, phone)
                }
                _uiState.value = OtpUiState.Success
            } catch (e: Exception) {
                _uiState.value = OtpUiState.Error(e.message ?: "Verification failed")
            }
        }
    }

    fun resendOtp(phone: String) {
        if (_resendTimer.value > 0) return
        viewModelScope.launch {
            try {
                supabaseClient.auth.signInWith(OTP) {
                    this.phone = phone.removePrefix("+")
                }
                startResendTimer()
            } catch (e: Exception) {
                _uiState.value = OtpUiState.Error(e.message ?: "Failed to resend OTP")
            }
        }
    }
}

sealed interface OtpUiState {
    data object Idle : OtpUiState
    data object Loading : OtpUiState
    data object Success : OtpUiState
    data class Error(val message: String) : OtpUiState
}
