package com.example.chat.features.onboarding

import android.content.Context
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.data.PreferenceManager
import com.example.chat.core.security.AccountManager
import com.example.chat.core.security.FingerprintUtils
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@kotlinx.serialization.Serializable
data class DeviceCheckDto(
    val visitor_id: String? = null
)

@kotlinx.serialization.Serializable
data class ProfileUpsertDto(
    val id: String,
    val phone: String,
    val visitor_id: String,
    val trust_tier: Int
)

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val accountManager: AccountManager,
    private val preferenceManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Idle)
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private val _normalizedPhone = MutableStateFlow("")
    val normalizedPhone: StateFlow<String> = _normalizedPhone.asStateFlow()

    private val phoneUtil = PhoneNumberUtil.getInstance()

    /**
     * Executes the 3-Layer Phone Validation Shield and signs the user in anonymously if validation succeeds.
     */
    fun registerUser(countryCode: String, rawPhone: String) {
        viewModelScope.launch {
            _uiState.value = RegistrationUiState.Loading

            // --- LAYER 1: Local Mathematical Structural Validation (libphonenumber) ---
            val cleanCountryCode = countryCode.trim().removePrefix("+")
            val cleanRawPhone = rawPhone.trim()

            if (cleanCountryCode.isEmpty() || cleanRawPhone.isEmpty()) {
                _uiState.value = RegistrationUiState.Error("Please enter both the country code and phone number.")
                return@launch
            }

            val defaultRegion = phoneUtil.getRegionCodeForCountryCode(cleanCountryCode.toIntOrNull() ?: 91)
            val numberProto = try {
                phoneUtil.parse("$cleanCountryCode$cleanRawPhone", defaultRegion)
            } catch (e: Exception) {
                _uiState.value = RegistrationUiState.Error("Invalid phone structure: ${e.message}")
                return@launch
            }

            if (!phoneUtil.isValidNumber(numberProto)) {
                _uiState.value = RegistrationUiState.Error("Invalid phone number format for the specified country.")
                return@launch
            }

            val numberType = phoneUtil.getNumberType(numberProto)
            if (numberType != PhoneNumberUtil.PhoneNumberType.MOBILE && numberType != PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE) {
                _uiState.value = RegistrationUiState.Error("Registration rejected: Only mobile numbers are supported. Fixed-line and virtual VOIP numbers are blocked.")
                return@launch
            }

            val e164 = phoneUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.E164)
            val cleanPhone = e164.removePrefix("+")
            _normalizedPhone.value = cleanPhone

            // --- LAYER 2: Live Network Telephony Carrier Check ---
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val simState = telephonyManager.simState
            if (simState != TelephonyManager.SIM_STATE_READY) {
                _uiState.value = RegistrationUiState.Error("Registration rejected: No active physical SIM card detected. Offline verification requires an active carrier SIM.")
                return@launch
            }

            val networkCountryIso = telephonyManager.networkCountryIso?.lowercase() ?: ""
            val simCountryIso = telephonyManager.simCountryIso?.lowercase() ?: ""
            val inputCountryIso = phoneUtil.getRegionCodeForNumber(numberProto)?.lowercase() ?: ""

            val resolvedNetworkIso = networkCountryIso.ifEmpty { simCountryIso }
            if (resolvedNetworkIso.isNotEmpty() && inputCountryIso != resolvedNetworkIso) {
                _uiState.value = RegistrationUiState.Error("Registration rejected: Network mismatch. Your SIM/cellular network country ($resolvedNetworkIso) does not match the registered phone country ($inputCountryIso).")
                return@launch
            }

            // --- LAYER 3: Device Fingerprint & Supabase Identity Binding ---
            val visitorId = FingerprintUtils.getVisitorId(context)

            try {
                // Table check: query the profiles table to count existing accounts registered to this device
                val existing = supabaseClient.postgrest["profiles"].select {
                    filter {
                        eq("visitor_id", visitorId)
                    }
                }.decodeList<DeviceCheckDto>()

                if (existing.size >= 15) {
                    _uiState.value = RegistrationUiState.Error("Registration rejected: Device limit reached. A maximum of 15 accounts can be registered per physical device.")
                    return@launch
                }

                // Call Supabase anonymous sign in
                supabaseClient.auth.signInAnonymously()

                // Establish real-time connection
                try {
                    supabaseClient.realtime.connect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val user = supabaseClient.auth.currentUserOrNull() ?: throw Exception("Session could not be established.")
                val userId = user.id

                // Upsert profile row mapped as Tier 0 (Low Trust) using serializable data class
                val profileUpsert = ProfileUpsertDto(
                    id = userId,
                    phone = cleanPhone,
                    visitor_id = visitorId,
                    trust_tier = 0
                )
                supabaseClient.postgrest["profiles"].upsert(profileUpsert)

                // Save current account locally
                accountManager.saveCurrentAccount(userId, cleanPhone)
                preferenceManager.setTrustTier(0)

                _uiState.value = RegistrationUiState.Success
            } catch (e: Exception) {
                _uiState.value = RegistrationUiState.Error("Registration Error: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = RegistrationUiState.Idle
    }
}
