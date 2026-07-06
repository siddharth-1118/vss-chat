package com.example.chat.features.onboarding

sealed interface RegistrationUiState {
    data object Idle : RegistrationUiState
    data object Validating : RegistrationUiState
    data object TurnstilePending : RegistrationUiState
    data object Loading : RegistrationUiState
    data object Success : RegistrationUiState
    data class Error(val message: String) : RegistrationUiState
}
