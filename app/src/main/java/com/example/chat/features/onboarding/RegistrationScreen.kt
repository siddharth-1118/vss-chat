package com.example.chat.features.onboarding

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onNavigateToOtp: (String) -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val normalizedPhone by viewModel.normalizedPhone.collectAsState()
    var phoneInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is RegistrationUiState.Success) {
            onNavigateToOtp(normalizedPhone)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Enter your phone number") })
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
                text = "WhatsApp Clone will need to verify your phone number.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Phone number") },
                placeholder = { Text("+1 123 456 7890") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = uiState !is RegistrationUiState.Loading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.validateAndPrepare(phoneInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState is RegistrationUiState.Idle || uiState is RegistrationUiState.Error
            ) {
                if (uiState is RegistrationUiState.Validating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("NEXT")
                }
            }

            if (uiState is RegistrationUiState.TurnstilePending) {
                TurnstileDialog(
                    onTokenReceived = { viewModel.onTurnstileTokenReceived(it) },
                    onDismiss = { viewModel.resetState() }
                )
            }

            if (uiState is RegistrationUiState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (uiState is RegistrationUiState.Error) {
                val errorMessage = (uiState as RegistrationUiState.Error).message
                LaunchedEffect(errorMessage) {
                    snackbarHostState.showSnackbar(errorMessage)
                }
            }
        }
    }
}

@Composable
fun TurnstileDialog(
    onTokenReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Security Verification") },
        text = {
            Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                TurnstileWebView(onTokenReceived)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurnstileWebView(onTokenReceived: (String) -> Unit) {
    val context = LocalContext.current
    // Production Note: Use your actual domain and Cloudflare Site Key here.
    val siteKey = "1x00000000000000000000AA" // Test Sitekey (Always passes)
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
            <style>
                body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
            </style>
        </head>
        <body>
            <div class="cf-turnstile" data-sitekey="$siteKey" data-callback="onSuccess"></div>
            <script>
                function onSuccess(token) {
                    Android.onTokenReceived(token);
                }
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onTokenReceived(token: String) {
                        onTokenReceived(token)
                    }
                }, "Android")
                loadDataWithBaseURL("https://your-registered-domain.com", html, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
