package dev.blazelight.sdflasher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.blazelight.sdflasher.ui.screens.FlashScreen
import dev.blazelight.sdflasher.ui.screens.HomeScreen
import dev.blazelight.sdflasher.ui.viewmodel.FlashViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Flash : Screen("flash")
}

@Composable
fun FlashNavHost(
    navController: NavHostController = rememberNavController()
) {
    // Share a single ViewModel instance across all screens
    val viewModel: FlashViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToFlash = {
                    navController.navigate(Screen.Flash.route)
                }
            )
        }

        composable(Screen.Flash.route) {
            FlashScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
