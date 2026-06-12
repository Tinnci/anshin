package com.driezy.medlog.ui.navigation

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import kotlinx.serialization.Serializable

// ── Sealed screen routes (type-safe Navigation) ──────────────────────────────

@Serializable sealed interface Route {
    @Serializable data object Welcome       : Route   // 首次启动引导页
    @Serializable data object Home          : Route
    @Serializable data object History       : Route
    @Serializable data object Drugs         : Route
    @Serializable data object Diary         : Route
    @Serializable data object Health        : Route
    @Serializable data object Settings      : Route
    @Serializable data object SettingsReminders    : Route
    @Serializable data object SettingsIntelligence : Route
    @Serializable data object SettingsCloudApi     : Route
    @Serializable data object SettingsWidgets      : Route
    @Serializable data object SettingsData         : Route
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
    val icon: Int,
    val selectedIcon: Int,
    val labelRes: Int,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Route.Home, MedLogIcons.Home, MedLogIcons.HomeSelected, com.driezy.medlog.R.string.tab_today),
    TopLevelDestination(Route.History, MedLogIcons.History, MedLogIcons.HistorySelected, com.driezy.medlog.R.string.tab_history),
    TopLevelDestination(Route.Drugs, MedLogIcons.MedicalServices, MedLogIcons.MedicalServicesSelected, com.driezy.medlog.R.string.tab_drugs),
    TopLevelDestination(Route.Diary, MedLogIcons.EditNote, MedLogIcons.EditNoteSelected, com.driezy.medlog.R.string.tab_diary),
    TopLevelDestination(Route.Health, MedLogIcons.MonitorHeart, MedLogIcons.MonitorHeartSelected, com.driezy.medlog.R.string.tab_health),
)
