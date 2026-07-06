package com.example.chat.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.core.security.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val accountManager: AccountManager
) : ViewModel() {

    fun switchAccount(userId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            accountManager.switchAccount(userId)
            onComplete()
        }
    }
}
