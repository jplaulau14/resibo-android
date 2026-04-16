package com.patslaurel.resibo.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.patslaurel.resibo.ui.check.CheckScreen
import com.patslaurel.resibo.ui.screens.HistoryScreen
import com.patslaurel.resibo.ui.screens.SettingsScreen
import com.patslaurel.resibo.ui.theme.ThemeMode
import com.patslaurel.resibo.ui.theme.ThemePreference

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems =
    listOf(
        BottomNavItem(ResiboRoutes.CHECK, "Check", Icons.Outlined.AutoAwesome),
        BottomNavItem(ResiboRoutes.HISTORY, "History", Icons.Filled.History),
        BottomNavItem(ResiboRoutes.SETTINGS, "Settings", Icons.Filled.Settings),
    )

@Composable
fun ResiboNavGraph(
    navController: NavHostController = rememberNavController(),
    themePreference: ThemePreference,
    currentTheme: ThemeMode,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar =
        currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected =
                                currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                if (currentDestination?.route != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(ResiboRoutes.CHECK) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ResiboRoutes.CHECK,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ResiboRoutes.CHECK) {
                CheckScreen()
            }
            composable(ResiboRoutes.HISTORY) {
                HistoryScreen()
            }
            composable(ResiboRoutes.SETTINGS) {
                SettingsScreen(
                    currentTheme = currentTheme,
                    onThemeChange = { themePreference.setThemeMode(it) },
                )
            }
        }
    }
}
