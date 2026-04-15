package com.patslaurel.resibo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.patslaurel.resibo.ui.screens.HomeScreen

@Composable
fun ResiboNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = ResiboRoutes.HOME,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(ResiboRoutes.HOME) {
            HomeScreen()
        }
        // Additional destinations (NoteScreen, TraceScreen, SettingsScreen) land in T025.
    }
}
