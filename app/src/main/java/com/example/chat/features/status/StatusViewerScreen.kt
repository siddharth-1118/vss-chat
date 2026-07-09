package com.example.chat.features.status

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chat.data.local.entity.StatusEntity
import kotlinx.coroutines.delay

@Composable
fun StatusViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel()
) {
    val statuses by viewModel.activeStatuses.collectAsState()
    var currentIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }

    if (statuses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active statuses")
        }
        return
    }

    val currentStatus = statuses.getOrNull(currentIndex) ?: return

    // Auto advancement logic
    LaunchedEffect(currentIndex) {
        progress = 0f
        var currentProgress = 0f
        while (currentProgress < 1f) {
            delay(50)
            currentProgress += 0.01f
            progress = currentProgress
        }
        if (currentIndex < statuses.lastIndex) {
            currentIndex++
        } else {
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Status Content Card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (currentIndex < statuses.lastIndex) {
                        currentIndex++
                    } else {
                        onNavigateBack()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Status by ${currentStatus.userId}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = currentStatus.caption ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }

        // Top progress indicator and close button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            // Horizontal segment loaders
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                statuses.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentIndex -> 1f
                        index == currentIndex -> progress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { segmentProgress },
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User info and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentStatus.userId,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
