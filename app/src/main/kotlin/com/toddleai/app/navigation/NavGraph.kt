package com.toddleai.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toddleai.app.ToddleAISessionViewModel
import com.toddleai.app.ui.screens.AnalyzingScreen
import com.toddleai.app.ui.screens.CaptureScreen
import com.toddleai.app.ui.screens.ChatScreen
import com.toddleai.app.ui.screens.ResultsScreen
import com.toddleai.app.ui.screens.SettingsScreen
import com.toddleai.app.ui.screens.WelcomeScreen

sealed class Route(val route: String) {
    data object Welcome : Route("welcome")
    data object Capture : Route("capture")
    data object Analyzing : Route("analyzing")
    data object Results : Route("results")
    data object Chat : Route("chat")
    data object Settings : Route("settings")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val sessionViewModel: ToddleAISessionViewModel = viewModel()
    val childName by sessionViewModel.childName.collectAsState()
    val childAgeMonthsInput by sessionViewModel.childAgeMonthsInput.collectAsState()
    val onboardingVisible by sessionViewModel.onboardingVisible.collectAsState()
    val analysisResult by sessionViewModel.analysisResult.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Route.Welcome.route,
    ) {
        composable(Route.Welcome.route) {
            WelcomeScreen(
                childName = childName,
                childAgeMonthsInput = childAgeMonthsInput,
                onboardingVisible = onboardingVisible,
                onChildNameChange = sessionViewModel::updateChildName,
                onChildAgeMonthsChange = sessionViewModel::updateChildAgeMonthsInput,
                onRecordWalkingVideo = {
                    sessionViewModel.beginCaptureSession()
                    navController.navigate(Route.Capture.route)
                },
                onImportWalkingVideo = { uri ->
                    sessionViewModel.beginImportedVideoSession(uri)
                    navController.navigate(Route.Analyzing.route)
                },
                onOpenSettings = { navController.navigate(Route.Settings.route) },
                onDismissOnboarding = sessionViewModel::dismissOnboarding,
            )
        }

        composable(Route.Capture.route) {
            CaptureScreen(
                sessionViewModel = sessionViewModel,
                onBack = {
                    navController.navigate(Route.Welcome.route) {
                        popUpTo(Route.Welcome.route) { inclusive = false }
                    }
                },
                onContinue = { navController.navigate(Route.Analyzing.route) },
            )
        }

        composable(Route.Analyzing.route) {
            AnalyzingScreen(
                sessionViewModel = sessionViewModel,
                onComplete = {
                    navController.navigate(Route.Results.route) {
                        popUpTo(Route.Capture.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.Results.route) {
            val result = analysisResult
            if (result != null) {
                ResultsScreen(
                    result = result,
                    childName = childName.ifBlank { "Your child" },
                    childAgeMonths = sessionViewModel.childAgeMonths() ?: 0,
                    inferenceBackend = sessionViewModel.runtimeBackendLabel(),
                    onAskToddleAI = {
                        sessionViewModel.setAssistantQuestion("Explain this recording")
                        navController.navigate(Route.Chat.route)
                    },
                    onRecordAnother = {
                        sessionViewModel.beginCaptureSession()
                        navController.navigate(Route.Capture.route) {
                            popUpTo(Route.Welcome.route) { inclusive = false }
                        }
                    },
                    onBack = {
                        navController.navigate(Route.Welcome.route) {
                            popUpTo(Route.Welcome.route) { inclusive = false }
                        }
                    },
                )
            }
        }

        composable(Route.Chat.route) {
            ChatScreen(
                sessionViewModel = sessionViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
