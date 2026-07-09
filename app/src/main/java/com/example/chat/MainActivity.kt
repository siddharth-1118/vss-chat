package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.chat.core.navigation.AppNavGraph
import com.example.chat.core.data.PreferenceManager
import com.example.chat.data.repository.ChatRepositoryImpl
import com.example.chat.data.repository.GroupChatRepository
import com.example.chat.ui.theme.ChatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var supabaseClient: io.github.jan.supabase.SupabaseClient

    @Inject
    lateinit var chatRepository: ChatRepositoryImpl

    @Inject
    lateinit var groupChatRepository: GroupChatRepository

    @Inject
    lateinit var callSignalingManager: com.example.chat.data.calling.CallSignalingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start real-time listeners
        chatRepository.initIncomingMessageListener()
        groupChatRepository.listenForGroupInvites()

        setContent {
            val isDarkTheme by preferenceManager.isDarkMode.collectAsState(initial = false)
            ChatTheme(darkTheme = isDarkTheme) {
                val isBanned by chatRepository.isBannedState.collectAsState()

                if (isBanned) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "BANNED",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your account has been permanently locked due to telemetry policy violations.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        AppNavGraph(
                            navController = navController,
                            preferenceManager = preferenceManager,
                            supabaseClient = supabaseClient,
                            signalingManager = callSignalingManager
                        )
                    }
                }
            }
        }
    }
}
