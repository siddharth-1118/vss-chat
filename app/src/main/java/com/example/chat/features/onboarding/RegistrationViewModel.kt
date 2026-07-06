package com.example.chat.features.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.security.FingerprintUtils
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.OTP
import io.github.jan.supabase.postgrest.postgrest
import com.example.chat.core.security.AccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val accountManager: AccountManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Idle)
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private val _normalizedPhone = MutableStateFlow("")
    val normalizedPhone: StateFlow<String> = _normalizedPhone.asStateFlow()

    private val phoneUtil = PhoneNumberUtil.getInstance()

    /**
     * Validates and normalizes the phone number to E.164 format.
     */
    fun validateAndPrepare(phone: String, defaultCountry: String = "US") {
        viewModelScope.launch {
            _uiState.value = RegistrationUiState.Validating
            try {
                val numberProto = phoneUtil.parse(phone, defaultCountry)
                if (phoneUtil.isValidNumber(numberProto)) {
                    val e164 = phoneUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.E164)
                    _normalizedPhone.value = e164
                    _uiState.value = RegistrationUiState.TurnstilePending
                } else {
                    _uiState.value = RegistrationUiState.Error("Invalid phone number format.")
                }
            } catch (e: Exception) {
                _uiState.value = RegistrationUiState.Error("Error parsing phone number: ${e.message}")
            }
        }
    }

    /**
     * Handles the Turnstile token and initiates the registration/login flow via Supabase Edge Functions.
     */
    fun onTurnstileTokenReceived(token: String) {
        viewModelScope.launch {
            _uiState.value = RegistrationUiState.Loading
            val visitorId = FingerprintUtils.getVisitorId(context)
            
            try {
                // Layer 3: Submit to Supabase Edge Controller for fingerprint & token validation
                // This replaces the direct Auth call to enforce our "Registration Security Shield"
                val response = supabaseClient.postgrest.rpc(
                    function = "validate_registration_shield",
                    parameters = mapOf(
                        "phone" to _normalizedPhone.value,
                        "visitor_id" to visitorId,
                        "turnstile_token" to token
                    )
                )

                // If RPC succeeds (doesn't throw), proceed with Supabase OTP Auth
                supabaseClient.auth.signInWith(OTP) {
                    phone = _normalizedPhone.value
                }
                
                _uiState.value = RegistrationUiState.Success
            } catch (e: Exception) {
                _uiState.value = RegistrationUiState.Error("Security Shield Block: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = RegistrationUiState.Idle
    }
}
