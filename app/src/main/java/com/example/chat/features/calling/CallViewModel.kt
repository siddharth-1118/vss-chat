package com.example.chat.features.calling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.calling.CallSignalingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CallState {
    RINGING, CONNECTED, DISCONNECTED
}

@HiltViewModel
class CallViewModel @Inject constructor(
    private val signalingManager: CallSignalingManager
) : ViewModel() {

    private val _callState = MutableStateFlow(CallState.DISCONNECTED)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _targetUserId = MutableStateFlow<String?>(null)
    val targetUserId: StateFlow<String?> = _targetUserId.asStateFlow()

    init {
        signalingManager.listenForIncomingSignaling()
            .onEach { payload ->
                when (payload.type) {
                    "OFFER" -> {
                        _targetUserId.value = payload.senderId
                        _callState.value = CallState.RINGING
                    }
                    "ANSWER" -> {
                        _callState.value = CallState.CONNECTED
                    }
                    "HANGUP" -> {
                        _callState.value = CallState.DISCONNECTED
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun startCall(receiverId: String) {
        viewModelScope.launch {
            _targetUserId.value = receiverId
            _callState.value = CallState.RINGING
            signalingManager.sendSignaling(receiverId, "OFFER", sdp = "v=0\no=- 12345 12345 IN IP4 127.0.0.1...")
        }
    }

    fun acceptCall() {
        val target = _targetUserId.value ?: return
        viewModelScope.launch {
            _callState.value = CallState.CONNECTED
            signalingManager.sendSignaling(target, "ANSWER", sdp = "v=0\no=- 12345 12345 IN IP4 127.0.0.1...")
        }
    }

    fun endCall() {
        val target = _targetUserId.value
        viewModelScope.launch {
            _callState.value = CallState.DISCONNECTED
            if (target != null) {
                signalingManager.sendSignaling(target, "HANGUP")
            }
            _targetUserId.value = null
        }
    }
}
