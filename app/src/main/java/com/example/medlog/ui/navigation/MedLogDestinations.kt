package com.example.medlog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// ── Sealed screen routes (type-safe Navigation) ──────────────────────────────

@Serializable sealed interface Route {
    @Serializable data object Home          : Route
    @Serializable data object History       : Route
    @Serializable data object Drugs         : Route
    @Serializable data object Settings      : Route
    @Serializable data class  MedDetail(val medicationId: Long) : Route
    @Serializable data class  AddMedication(val medicationId: Long = -1) : Route
}

// ── Top-level navigation destinations ────────────────────────────────────────

data class TopLevelDestination(
    val route: Route,
    val icon: ImageVector,
    val labelRes: Int,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Route.Home, Icons.Rounded.Home, com.example.medlog.R.string.tab_today),
    TopLevelDestination(Route.History, Icons.Rounded.History, com.example.medlog.R.string.tab_history),
    TopLevelDestination(Route.Drugs, Icons.Rounded.MedicalServices, com.example.medlog.R.string.tab_drugs),
    TopLevelDestination(Route.Settings, Icons.Rounded.Settings, com.example.medlog.R.string.tab_settings),
)
