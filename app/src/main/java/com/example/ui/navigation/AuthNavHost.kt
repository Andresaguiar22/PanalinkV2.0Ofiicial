package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavHostController
import com.example.ui.screen.EmailVerificationScreen
import com.example.ui.screen.LoginScreen
import com.example.ui.screen.RegisterScreen
import com.example.ui.screen.WelcomeScreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun AuthNavHost(
    authViewModel: AuthViewModel,
    currentFlow: AuthUiState,
    modifier: Modifier = Modifier,
) {
    val authNavController: NavHostController = rememberNavController()
    val startDest = when (currentFlow) {
        is AuthUiState.NeedsEmailVerification -> "verification/${(currentFlow as AuthUiState.NeedsEmailVerification).email}"
        is AuthUiState.LoggedOut -> "welcome"
        else -> "welcome"
    }



                NavHost(
                    navController = authNavController,
                    startDestination = startDest,
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(350)
                        ) + fadeIn(animationSpec = tween(350))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(350)
                        ) + fadeOut(animationSpec = tween(350))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(350)
                        ) + fadeIn(animationSpec = tween(350))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(350)
                        ) + fadeOut(animationSpec = tween(350))
                    }
                ) {
                    // Welcome Screen
                    composable("welcome") {
                        WelcomeScreen(
                            onNavigateToLogin = { authNavController.navigate("login") { launchSingleTop = true } },
                            onNavigateToRegister = { authNavController.navigate("register") { launchSingleTop = true } }
                        )
                    }

                    // Login Screen
                    composable("login") {
                        LoginScreen(
                            viewModel = authViewModel,
                            onNavigateToRegister = { authNavController.navigate("register") { launchSingleTop = true } }
                        )
                    }

                    // Register Screen
                    composable("register") {
                        RegisterScreen(
                            viewModel = authViewModel,
                            onNavigateToLogin = { authNavController.navigate("login") { launchSingleTop = true } }
                        )
                    }

                    // Email Verification Screen
                    composable(
                        route = "verification/{email}",
                        arguments = listOf(navArgument("email") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val email = backStackEntry.arguments?.getString("email") ?: ""
                        EmailVerificationScreen(
                            viewModel = authViewModel,
                            email = email,
                            onBackToLogin = {
                                authViewModel.logout()
                            }
                        )
                    }
                }

}