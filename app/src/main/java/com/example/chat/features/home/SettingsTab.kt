package com.example.chat.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chat.core.security.AccountManager
import com.example.chat.worker.BackupWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun SettingsTab(
    onNavigateToRegistration: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountManager = viewModel.accountManager
    val accounts by accountManager.accounts.collectAsState()
    val activeUserId by accountManager.activeUserId.collectAsState()
    var showAccountSwitcher by remember { mutableStateOf(false) }

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Handle result if needed
    }

    val displayName by viewModel.displayName.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }

    var showAccountDialog by remember { mutableStateOf(false) }
    var showChatsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var messageSoundsEnabled by remember { mutableStateOf(true) }

    if (showProfileDialog) {
        var editName by remember { mutableStateOf(displayName) }
        var editStatus by remember { mutableStateOf(statusMessage) }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editStatus,
                        onValueChange = { editStatus = it },
                        label = { Text("Status") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateProfile(editName, editStatus)
                    showProfileDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAccountDialog) {
        val tier by viewModel.trustTier.collectAsState()
        val regTime by viewModel.registrationTimestamp.collectAsState()
        val formattedReg = remember(regTime) {
            if (regTime == 0L) "Not registered" 
            else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(regTime))
        }

        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Account Details") },
            text = {
                Column {
                    Text("Trust Tier: ${if (tier == 1) "Tier 1 (Full Trust)" else "Tier 0 (Low Trust - 48h eval)"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Registered on: $formattedReg")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Verification Mode: Phone OTP SMS")
                }
            },
            confirmButton = {
                Button(onClick = { showAccountDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showChatsDialog) {
        val isDark by viewModel.isDarkMode.collectAsState()

        AlertDialog(
            onDismissRequest = { showChatsDialog = false },
            title = { Text("Chats Settings") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Theme")
                    Switch(
                        checked = isDark,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showChatsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = { Text("Notifications") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conversation Tones")
                    Switch(
                        checked = messageSoundsEnabled,
                        onCheckedChange = { messageSoundsEnabled = it }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showNotificationsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showStorageDialog) {
        val cacheSize = remember {
            val size = context.cacheDir.walkTopDown().map { it.length() }.sum()
            val df = java.text.DecimalFormat("#.##")
            if (size > 1024 * 1024) "${df.format(size / (1024.0 * 1024.0))} MB"
            else "${df.format(size / 1024.0)} KB"
        }

        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Storage and Data") },
            text = {
                Column {
                    Text("Cache Used: $cacheSize")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                context.cacheDir.deleteRecursively()
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                            }
                            showStorageDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear Cache Files", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help") },
            text = {
                Column {
                    Text("App Version: 2.26.1 (Premium Hybrid Release)")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@vsschat.com")
                                putExtra(Intent.EXTRA_SUBJECT, "VSS Chat Support Request")
                            }
                            context.startActivity(intent)
                        } catch (e: java.lang.Exception) {
                            e.printStackTrace()
                        }
                        showHelpDialog = false
                    }) {
                        Text("Contact Support")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ProfileHeader(
                name = displayName,
                status = statusMessage,
                onHeaderClick = { showProfileDialog = true },
                onSwitchClick = { showAccountSwitcher = true }
            )
        }
        item { HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray) }
        
        if (showAccountSwitcher) {
            item {
                AccountSwitcher(
                    accounts = accounts,
                    activeUserId = activeUserId,
                    onSwitch = { id -> 
                        scope.launch {
                            accountManager.switchAccount(id)
                            showAccountSwitcher = false
                        }
                    },
                    onAddAccount = {
                        onNavigateToRegistration()
                        showAccountSwitcher = false
                    }
                )
            }
        }

        item {
            Column(modifier = Modifier.clickable { showAccountDialog = true }) {
                SettingItem(Icons.Default.VpnKey, "Account", "Security notifications, change number")
            }
        }
        item {
            Column(modifier = Modifier.clickable { showChatsDialog = true }) {
                SettingItem(Icons.Default.Chat, "Chats", "Theme, wallpapers, chat history")
            }
        }
        item {
            Column(modifier = Modifier.clickable { showNotificationsDialog = true }) {
                SettingItem(Icons.Default.Notifications, "Notifications", "Message, group & call tones")
            }
        }
        item {
            Column(modifier = Modifier.clickable { showStorageDialog = true }) {
                SettingItem(Icons.Default.Storage, "Storage and Data", "Network usage, auto-download")
            }
        }
        
        item { 
            Column(modifier = Modifier.clickable { 
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account == null) {
                    launcher.launch(googleSignInClient.signInIntent)
                } else {
                    BackupWorker.triggerImmediateBackup(context)
                }
            }) {
                SettingItem(
                    icon = Icons.Default.Backup, 
                    title = "Backups", 
                    subtitle = "Google Drive, local encryption (Tap to backup now)"
                )
            }
        }
        
        item { 
            Column(modifier = Modifier.clickable { 
                viewModel.exportChatLogsToEmail(context)
            }) {
                SettingItem(
                    icon = Icons.Default.Email, 
                    title = "Email Backup", 
                    subtitle = "Export all chats as text log to your email"
                )
            }
        }
        
        item {
            Column(modifier = Modifier.clickable { showHelpDialog = true }) {
                SettingItem(Icons.Default.Help, "Help", "Help center, contact us, privacy policy")
            }
        }
        
        item {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { launcher.launch(googleSignInClient.signInIntent) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E))
                ) {
                    Text("Connect Google Drive for Backups")
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(
    name: String,
    status: String,
    onHeaderClick: () -> Unit,
    onSwitchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHeaderClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleLarge)
            Text(text = status, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        IconButton(onClick = onSwitchClick) {
            Icon(Icons.Default.SwitchAccount, contentDescription = "Switch Account", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AccountSwitcher(
    accounts: List<com.example.chat.core.security.UserAccount>,
    activeUserId: String?,
    onSwitch: (String) -> Unit,
    onAddAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Text("Switch Account", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        
        accounts.forEach { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSwitch(account.userId) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = account.userId == activeUserId, onClick = { onSwitch(account.userId) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = account.phone, style = MaterialTheme.typography.bodyLarge)
            }
        }
        
        if (accounts.size < 2) {
            TextButton(onClick = onAddAccount, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Account")
            }
        }
    }
}

@Composable
fun SettingItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(24.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
