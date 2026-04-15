package com.patslaurel.resibo.ui.navigation

/**
 * Named routes for Resibo's NavGraph.
 *
 * Kept as plain string constants for now. When the graph grows (T025+), migrate to
 * Navigation 2.8's type-safe routes (Kotlin Serialization-backed `@Serializable` objects).
 */
object ResiboRoutes {
    const val HOME = "home"
    const val NOTE = "note"
    const val TRACE = "trace"
    const val SETTINGS = "settings"
}
