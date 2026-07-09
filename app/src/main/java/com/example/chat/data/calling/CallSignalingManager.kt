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
    private val myPhone: String
        get() = accountManager.activeAccountPhone

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
