package com.example.medlog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// ── Sealed screen routes (type-safe Navigation) ──────────────────────────────

@Serializable sealed interface Route {
    @Serializable data object Welcome       : Route   // 首次启动引导页
    @Serializable data object Home          : Route
    @Serializable data object History       : Route
    @Serializable data object Drugs         : Route
    @Serializable data object Diary         : Route
    @Serializable data object Settings      : Route
    @Serializable data class  MedDetail(val medicationId: Long) : Route
    /**
     * @param medicationId  编辑模式下已有记录的 id（-1 代表新增）
     * @param drugName      从药品数据库选中后预填的药品名
     * @param drugCategory  从药品数据库选中后预填的分类
     */
    @Serializable data class AddMedication(
        val medicationId: Long = -1,
        val drugName: String = "",
        val drugCategory: String = "",
    ) : Route
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
    TopLevelDestination(Route.Diary, Icons.Rounded.EditNote, com.example.medlog.R.string.tab_diary),
    TopLevelDestination(Route.Settings, Icons.Rounded.Settings, com.example.medlog.R.string.tab_settings),
)

