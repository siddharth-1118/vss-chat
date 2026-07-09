package com.example.chat.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.chat.features.onboarding.RegistrationScreen
import com.example.chat.features.onboarding.OtpScreen
import com.example.chat.features.onboarding.ProfileSetupScreen
import com.example.chat.features.home.MainHubScreen
import com.example.chat.core.data.PreferenceManager

sealed class Route(val route: String) {
    data object Registration : Route("registration")
    data object Otp : Route("otp/{phone}") {
        fun createRoute(phone: String) = "otp/$phone"
    }
    data object ProfileSetup : Route("profile_setup")
    data object MainHub : Route("main_hub")
    data object Chat : Route("chat/{contactId}/{contactName}") {
        fun createRoute(contactId: String, contactName: String) = "chat/$contactId/$contactName"
    }
    data object ContactPicker : Route("contact_picker")
    data object CreateGroup : Route("create_group")
    data object StatusViewer : Route("status_viewer")
    data object Call : Route("call")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    preferenceManager: com.example.chat.core.data.PreferenceManager,
    supabaseClient: io.github.jan.supabase.SupabaseClient,
    signalingManager: com.example.chat.data.calling.CallSignalingManager
) {
    val context = LocalContext.current
    val isOnboardingComplete by preferenceManager.isOnboardingComplete.collectAsState(initial = false)
    val startDestination = if (isOnboardingComplete) Route.MainHub.route else Route.Registration.route

    // Listen to active incoming calls globally to display CallScreen and notification service
    LaunchedEffect(Unit) {
        signalingManager.activeCall.collect { payload ->
            if (payload != null) {
                val isIncoming = payload.senderId != signalingManager.myPhone
                if (payload.type == "OFFER" && isIncoming) {
                    val serviceIntent = android.content.Intent(context, com.example.chat.core.service.CallNotificationService::class.java).apply {
                        putExtra("callerName", payload.senderId)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    navController.navigate(Route.Call.route)
                } else if (payload.type == "HANGUP" || payload.type == "ANSWER") {
                    val serviceIntent = android.content.Intent(context, com.example.chat.core.service.CallNotificationService::class.java)
                    context.stopService(serviceIntent)
                }
            } else {
                val serviceIntent = android.content.Intent(context, com.example.chat.core.service.CallNotificationService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Route.Registration.route) {
            RegistrationScreen(
                onNavigateToProfile = {
                    navController.navigate(Route.ProfileSetup.route) {
                        popUpTo(Route.Registration.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.Otp.route) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            OtpScreen(
                phone = phone,
                onNavigateToProfile = {
                    navController.navigate(Route.ProfileSetup.route) {
                        popUpTo(Route.Registration.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.ProfileSetup.route) {
            ProfileSetupScreen(
                onProfileComplete = {
                    navController.navigate(Route.MainHub.route) {
                        popUpTo(Route.ProfileSetup.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.MainHub.route) {
            com.example.chat.features.home.MainHubScreen(
                onNavigateToChat = { id, name ->
                    navController.navigate(Route.Chat.createRoute(id, name))
                },
                onNavigateToContactPicker = {
                    navController.navigate(Route.ContactPicker.route)
                },
                onNavigateToRegistration = {
                    navController.navigate(Route.Registration.route)
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Route.CreateGroup.route)
                },
                onNavigateToStatusViewer = {
                    navController.navigate(Route.StatusViewer.route)
                }
            )
        }
        composable(Route.Chat.route) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            val contactName = backStackEntry.arguments?.getString("contactName") ?: ""
            com.example.chat.features.chat.ChatScreen(
                contactName = contactName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCall = {
                    navController.navigate(Route.Call.route)
                },
                supabaseClient = supabaseClient
            )
        }
        composable(Route.ContactPicker.route) {
            com.example.chat.features.contacts.ContactPickerScreen(
                onNavigateBack = { navController.popBackStack() },
                onContactSelected = { id, name ->
                    navController.navigate(Route.Chat.createRoute(id, name)) {
                        popUpTo(Route.ContactPicker.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.CreateGroup.route) {
            com.example.chat.features.home.CreateGroupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Route.StatusViewer.route) {
            com.example.chat.features.status.StatusViewerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Route.Call.route) {
            com.example.chat.features.calling.CallScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
