package com.example.chat.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    phone: String,
    onNavigateToProfile: () -> Unit,
    viewModel: OtpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val resendTimer by viewModel.resendTimer.collectAsState()
    var otpValue by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is OtpUiState.Success) {
            onNavigateToProfile()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Verify $phone") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Waiting to automatically detect an SMS sent to $phone.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = otpValue,
                onValueChange = { 
                    if (it.length <= 6) otpValue = it
                    if (it.length == 6) viewModel.verifyOtp(phone, it)
                },
                modifier = Modifier.width(200.dp),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    letterSpacing = 8.sp,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("------", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState is OtpUiState.Loading) {
                CircularProgressIndicator()
            } else {
                TextButton(
                    onClick = { viewModel.resendOtp(phone) },
                    enabled = resendTimer == 0
                ) {
                    Text(
                        if (resendTimer > 0) "Resend SMS in ${resendTimer}s"
                        else "Resend SMS"
                    )
                }
            }

            if (uiState is OtpUiState.Error) {
                val errorMessage = (uiState as OtpUiState.Error).message
                LaunchedEffect(errorMessage) {
                    snackbarHostState.showSnackbar(errorMessage)
                }
            }
        }
    }
}
