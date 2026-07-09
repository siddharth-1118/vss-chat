package com.example.chat.features.calling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val targetUser by viewModel.targetUserId.collectAsState()

    var callDuration by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }

    LaunchedEffect(callState) {
        if (callState == CallState.DISCONNECTED) {
            delay(1000)
            onNavigateBack()
        }
    }

    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED) {
            callDuration = 0
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight(0.7f)
        ) {
            Text(
                text = targetUser ?: "Unknown User",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            val statusText = when (callState) {
                CallState.RINGING -> "Ringing..."
                CallState.CONNECTED -> {
                    val minutes = callDuration / 60
                    val seconds = callDuration % 60
                    String.format("%02d:%02d", minutes, seconds)
                }
                CallState.DISCONNECTED -> "Call Ended"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray
            )
        }

        // Active control panel at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (callState == CallState.RINGING) {
                // If it is an incoming call, we show Accept and Decline
                IconButton(
                    onClick = { viewModel.acceptCall() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Green, CircleShape)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Accept Call", tint = Color.White)
                }
                
                IconButton(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Decline Call", tint = Color.White)
                }
            } else if (callState == CallState.CONNECTED) {
                // Active call controls
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (isMuted) Color.Gray else Color.DarkGray, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute mic",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                }

                IconButton(
                    onClick = { /* Toggle speaker */ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.DarkGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Speaker",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
