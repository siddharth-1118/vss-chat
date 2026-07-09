package com.example.chat.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onNavigateToProfile: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var countryCodeInput by remember { mutableStateOf("91") }
    var phoneInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is RegistrationUiState.Success) {
            onNavigateToProfile()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Offline Device Registration") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Register securely without SMS OTP. The app will mathematically validate your phone structure, active carrier network, and device limits.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = countryCodeInput,
                    onValueChange = { countryCodeInput = it },
                    label = { Text("Country Code") },
                    placeholder = { Text("91") },
                    prefix = { Text("+") },
                    modifier = Modifier.weight(0.35f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = uiState !is RegistrationUiState.Loading
                )

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("9876543210") },
                    modifier = Modifier.weight(0.65f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    enabled = uiState !is RegistrationUiState.Loading
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.registerUser(countryCodeInput, phoneInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is RegistrationUiState.Loading
            ) {
                if (uiState is RegistrationUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("REGISTER DEVICE")
                }
            }

            if (uiState is RegistrationUiState.Error) {
                val errorMessage = (uiState as RegistrationUiState.Error).message
                LaunchedEffect(errorMessage) {
                    snackbarHostState.showSnackbar(errorMessage)
                    viewModel.resetState()
                }
            }
        }
    }
}
