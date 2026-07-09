package com.example.chat.data.calling

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.example.chat.core.security.AccountManager
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SignalingPayload(
    val senderId: String,
    val receiverId: String,
    val type: String, // "OFFER", "ANSWER", "ICE_CANDIDATE", "HANGUP"
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)

@Singleton
class CallSignalingManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val accountManager: AccountManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val myPhone: String
        get() = accountManager.activeAccountPhone

    private val _activeCall = MutableStateFlow<SignalingPayload?>(null)
    val activeCall: StateFlow<SignalingPayload?> = _activeCall.asStateFlow()

    fun setActiveCall(payload: SignalingPayload?) {
        _activeCall.value = payload
    }

    init {
        scope.launch {
            accountManager.activeAccountPhoneFlow
                .distinctUntilChanged()
                .collect { phone ->
                    if (phone.isEmpty()) return@collect
                    
                    val cleanMyId = phone.removePrefix("+")
                    val channel = supabaseClient.realtime.channel("call:$cleanMyId")
                    
                    try {
                        supabaseClient.realtime.connect()
                        channel.subscribe()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    channel.broadcastFlow<SignalingPayload>(event = "signaling")
                        .collect { payload ->
                            if (payload.receiverId == phone && payload.senderId != phone) {
                                when (payload.type) {
                                    "OFFER" -> _activeCall.value = payload
                                    "HANGUP" -> _activeCall.value = null
                                    "ANSWER" -> {
                                        if (_activeCall.value?.type == "OFFER" && _activeCall.value?.receiverId == payload.senderId) {
                                            _activeCall.value = payload
                                        }
                                    }
                                }
                            }
                        }
                }
        }
    }

    fun listenForIncomingSignaling(): Flow<SignalingPayload> {
        val cleanMyId = myPhone.removePrefix("+")
        val channel = supabaseClient.realtime.channel("call:$cleanMyId")
        
        scope.launch {
            channel.subscribe()
        }

        return channel.broadcastFlow<SignalingPayload>(event = "signaling")
            .filter { payload -> payload.receiverId == myPhone && payload.senderId != myPhone }
    }

    suspend fun sendSignaling(receiverId: String, type: String, sdp: String? = null, candidate: String? = null) {
        val cleanReceiverId = receiverId.removePrefix("+")
        val channel = supabaseClient.realtime.channel("call:$cleanReceiverId")
        try {
            channel.subscribe()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        channel.broadcast(
            event = "signaling",
            message = SignalingPayload(
                senderId = myPhone,
                receiverId = receiverId,
                type = type,
                sdp = sdp,
                candidate = candidate
            )
        )
    }
}
