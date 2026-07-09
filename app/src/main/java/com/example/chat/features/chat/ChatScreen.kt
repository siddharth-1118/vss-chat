package com.example.chat.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chat.data.local.entity.MessageEntity
import com.example.chat.data.local.entity.MessageStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import java.text.SimpleDateFormat
import java.util.*

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactName: String,
    onNavigateBack: () -> Unit,
    onNavigateToCall: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    callViewModel: com.example.chat.features.calling.CallViewModel = hiltViewModel(),
    supabaseClient: SupabaseClient
) {
    val messages by viewModel.messages.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val isContactSaved by viewModel.isContactSaved.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val myPhone = viewModel.myPhone

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendMediaMessage(uri, "IMAGE", 1024L)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(contactName) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            callViewModel.startCall(viewModel.targetId)
                            onNavigateToCall()
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                        }
                        IconButton(onClick = {
                            callViewModel.startCall(viewModel.targetId)
                            onNavigateToCall()
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF128C7E),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                if (!isContactSaved) {
                    SpamReportBanner(onReport = viewModel::reportAndBlock)
                }
            }
        },
        bottomBar = {
            ChatInputBar(
                text = messageText,
                onTextChange = viewModel::onMessageChange,
                onSend = viewModel::sendMessage,
                onAttachClick = { pickerLauncher.launch("image/*") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFECE5DD))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isOutgoing = message.senderId == myPhone
                    MessageBubble(message = message, isOutgoing = isOutgoing)
                }
            }
        }
    }
}

@Composable
fun SpamReportBanner(onReport: () -> Unit) {
    Surface(
        color = Color(0xFFFFF3F3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "This sender is not in your contacts.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black
                )
                Text(
                    text = "Be careful with links or requests for money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
            Button(
                onClick = onReport,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Report & Block", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity, isOutgoing: Boolean) {
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOutgoing) Color(0xFFDCF8C6) else Color.White
    val shape = if (isOutgoing) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black
            )
            
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                if (isOutgoing) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageStatusIcon(status = message.status)
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.SENDING -> Icon(
            Icons.Default.Schedule, 
            contentDescription = null, 
            modifier = Modifier.size(14.dp),
            tint = Color.Gray
        )
        MessageStatus.SENT -> Icon(
            Icons.Default.Check, 
            contentDescription = null, 
            modifier = Modifier.size(14.dp),
            tint = Color.Gray
        )
        MessageStatus.DELIVERED -> Icon(
            Icons.Default.DoneAll, 
            contentDescription = null, 
            modifier = Modifier.size(14.dp),
            tint = Color.Gray
        )
        MessageStatus.READ -> Icon(
            Icons.Default.DoneAll, 
            contentDescription = null, 
            modifier = Modifier.size(14.dp),
            tint = Color(0xFF34B7F1)
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Type a message") },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 4,
                leadingIcon = {
                    IconButton(onClick = onAttachClick) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach File")
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FloatingActionButton(
                onClick = onSend,
                containerColor = Color(0xFF075E54),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
