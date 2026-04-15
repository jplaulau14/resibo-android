package com.patslaurel.resibo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.patslaurel.resibo.ui.playground.PlaygroundScreen
import com.patslaurel.resibo.ui.screens.HomeScreen
import com.patslaurel.resibo.ui.screens.NoteScreen
import com.patslaurel.resibo.ui.screens.SettingsScreen
import com.patslaurel.resibo.ui.screens.TraceScreen

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
            HomeScreen(
                onOpenNote = { navController.navigate(ResiboRoutes.NOTE) },
                onOpenTrace = { navController.navigate(ResiboRoutes.TRACE) },
                onOpenSettings = { navController.navigate(ResiboRoutes.SETTINGS) },
                onOpenPlayground = { navController.navigate(ResiboRoutes.PLAYGROUND) },
            )
        }
        composable(ResiboRoutes.NOTE) {
            NoteScreen(onBack = { navController.popBackStack() })
        }
        composable(ResiboRoutes.TRACE) {
            TraceScreen(onBack = { navController.popBackStack() })
        }
        composable(ResiboRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(ResiboRoutes.PLAYGROUND) {
            PlaygroundScreen(onBack = { navController.popBackStack() })
        }
    }
}
