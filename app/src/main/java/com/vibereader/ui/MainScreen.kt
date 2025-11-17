package com.vibereader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// --- Define Navigation Routes ---
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Session : Screen("session", "Session", Icons.Default.Book)
    object Review : Screen("review", "Review", Icons.Default.List)
}

val items = listOf(Screen.Session, Screen.Review)

// --- Main App Composable with Navigation ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SessionViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination to avoid building a large stack
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // --- Navigation Host (swaps the screens) ---
        NavHost(
            navController = navController,
            startDestination = Screen.Session.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Session.route) {
                // Your existing SessionScreen
                SessionScreen(viewModel = viewModel)
            }
            composable(Screen.Review.route) {
                // Your new ReviewScreen
                ReviewScreen(viewModel = viewModel)
            }
        }
    }
}