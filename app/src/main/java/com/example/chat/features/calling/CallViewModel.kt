package com.example.chat.features.calling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.calling.CallSignalingManager
import com.example.chat.data.calling.SignalingPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        viewModelScope.launch {
            signalingManager.activeCall.collect { payload ->
                if (payload == null) {
                    _callState.value = CallState.DISCONNECTED
                    _targetUserId.value = null
                } else {
                    val otherParty = if (payload.senderId == signalingManager.myPhone) payload.receiverId else payload.senderId
                    _targetUserId.value = otherParty
                    when (payload.type) {
                        "OFFER" -> _callState.value = CallState.RINGING
                        "ANSWER" -> _callState.value = CallState.CONNECTED
                        "HANGUP" -> _callState.value = CallState.DISCONNECTED
                        else -> {}
                    }
                }
            }
        }
    }

    fun startCall(receiverId: String) {
        viewModelScope.launch {
            val payload = SignalingPayload(
                senderId = signalingManager.myPhone,
                receiverId = receiverId,
                type = "OFFER",
                sdp = "v=0\no=- 12345 12345 IN IP4 127.0.0.1..."
            )
            signalingManager.setActiveCall(payload)
            signalingManager.sendSignaling(receiverId, "OFFER", sdp = payload.sdp)
        }
    }

    fun acceptCall() {
        val target = _targetUserId.value ?: return
        viewModelScope.launch {
            val payload = SignalingPayload(
                senderId = signalingManager.myPhone,
                receiverId = target,
                type = "ANSWER",
                sdp = "v=0\no=- 12345 12345 IN IP4 127.0.0.1..."
            )
            signalingManager.setActiveCall(payload)
            signalingManager.sendSignaling(target, "ANSWER", sdp = payload.sdp)
        }
    }

    fun endCall() {
        val target = _targetUserId.value
        viewModelScope.launch {
            signalingManager.setActiveCall(null)
            if (target != null) {
                signalingManager.sendSignaling(target, "HANGUP")
            }
        }
    }
}
