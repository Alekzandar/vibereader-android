package com.vibereader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * The root UI container handling Bottom Navigation.
 * Note: No imports needed for SessionScreen or ReviewScreen
 * as they share the same 'com.vibereader.ui' package.
 */
@Composable
fun MainScreen(viewModel: SessionViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                NavigationBarItem(
                    selected = currentRoute == "read",
                    onClick = { navController.navigate("read") },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Read") },
                    label = { Text("Read") }
                )

                NavigationBarItem(
                    selected = currentRoute == "archive",
                    onClick = {
                        // Reset the library navigation state when entering the tab
                        viewModel.resetLibraryNavigation()
                        navController.navigate("archive")
                    },
                    icon = { Icon(Icons.Default.CollectionsBookmark, contentDescription = "Library") },
                    label = { Text("Library") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "read",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("read") {
                SessionScreen(viewModel = viewModel)
            }
            composable("archive") {
                ReviewScreen(viewModel = viewModel)
            }
        }
    }
}