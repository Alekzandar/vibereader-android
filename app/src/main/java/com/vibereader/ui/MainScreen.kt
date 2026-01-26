package com.vibereader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Timer
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

/**
 * MainScreen handles the high-level navigation of the app.
 * It coordinates between the Active Session and the Relational Archive.
 * Note: Explicit imports for SessionScreen and ReviewScreen removed as they
 * are located in the same package (com.vibereader.ui).
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Session : Screen("session", "Reading", Icons.Default.Timer)
    object Archive : Screen("archive", "Archive", Icons.Default.CollectionsBookmark)
}

val navItems = listOf(Screen.Session, Screen.Archive)

@Composable
fun MainScreen(viewModel: SessionViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                navItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            // Reset the Archive drill-down state so the user lands on the list
                            viewModel.selectArchiveSession(null)

                            navController.navigate(screen.route) {
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
        NavHost(
            navController = navController,
            startDestination = Screen.Session.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Session.route) {
                // Calls SessionScreen.kt in the same package
                SessionScreen(viewModel = viewModel)
            }
            composable(Screen.Archive.route) {
                // Calls ReviewScreen.kt in the same package
                ReviewScreen(viewModel = viewModel)
            }
        }
    }
}