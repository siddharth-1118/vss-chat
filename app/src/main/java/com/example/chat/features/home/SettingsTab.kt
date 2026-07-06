package com.example.chat.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.example.chat.worker.BackupWorker
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chat.core.security.AccountManager
import androidx.compose.runtime.collectAsState

@Composable
fun SettingsTab(
    onNavigateToRegistration: () -> Unit,
    accountManager: AccountManager = hiltViewModel<SettingsViewModel>().accountManager
) {
    val context = LocalContext.current
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

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ProfileHeader(
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
                        accountManager.switchAccount(id)
                        showAccountSwitcher = false
                    },
                    onAddAccount = {
                        onNavigateToRegistration()
                        showAccountSwitcher = false
                    }
                )
            }
        }

        item { SettingItem(Icons.Default.VpnKey, "Account", "Security notifications, change number") }
        item { SettingItem(Icons.Default.Chat, "Chats", "Theme, wallpapers, chat history") }
        item { SettingItem(Icons.Default.Notifications, "Notifications", "Message, group & call tones") }
        item { SettingItem(Icons.Default.Storage, "Storage and Data", "Network usage, auto-download") }
        
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
        
        item { SettingItem(Icons.Default.Help, "Help", "Help center, contact us, privacy policy") }
        
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
fun ProfileHeader(onSwitchClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            Text(text = "User Name", style = MaterialTheme.typography.titleLarge)
            Text(text = "Hey there! I am using WhatsApp.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
