package com.driezy.medlog.capability.widgets

import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.AppearancePreferenceState
import com.driezy.medlog.data.repository.AppearancePreferences
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.HomeHeroStyle
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.data.repository.WidgetColorSource
import com.driezy.medlog.data.repository.WidgetDensityScale
import com.driezy.medlog.data.repository.WidgetPreferenceState
import com.driezy.medlog.data.repository.WidgetPreferences
import com.driezy.medlog.data.repository.WidgetTextScale
import com.driezy.medlog.data.repository.WidgetThemeMode
import com.driezy.medlog.feature.settings.SettingsUiAction
import com.driezy.medlog.feature.settings.SettingsWidgetViewModel
import com.driezy.medlog.testing.MainDispatcherRule
import com.driezy.medlog.ui.theme.ThemePalette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 小组件外观偏好的行为守护。
 *
 * 验证主题模式、配色来源、密度与文字缩放是四个互不影响的维度，
 * 且设置动作会持久化偏好并刷新已放置的小组件。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAppearancePreferencesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val widgetPreferences = RecordingWidgetPreferences()
    private val appearancePreferences = FixedAppearancePreferences()
    private val widgetRefresher = FakeWidgetRefresher()

    @Test
    fun `theme mode color source density and text scale are independent preferences`() = runTest {
        val viewModel = newViewModel()

        viewModel.onAction(SettingsUiAction.SetWidgetAppearance(themeMode = WidgetThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(WidgetThemeMode.DARK, widgetPreferences.state.value.themeMode)
        assertEquals(WidgetColorSource.SYSTEM_DYNAMIC, widgetPreferences.state.value.colorSource)
        assertEquals(WidgetDensityScale.STANDARD, widgetPreferences.state.value.densityScale)
        assertEquals(WidgetTextScale.STANDARD, widgetPreferences.state.value.textScale)

        viewModel.onAction(SettingsUiAction.SetWidgetAppearance(colorSource = WidgetColorSource.CUSTOM_PALETTE))
        viewModel.onAction(SettingsUiAction.SetWidgetAppearance(palette = ThemePalette.MEADOW))
        viewModel.onAction(SettingsUiAction.SetWidgetAppearance(densityScale = WidgetDensityScale.COMPACT))
        viewModel.onAction(SettingsUiAction.SetWidgetAppearance(textScale = WidgetTextScale.LARGE))
        advanceUntilIdle()

        val stored = widgetPreferences.state.value
        assertEquals("Theme mode must survive later appearance edits.", WidgetThemeMode.DARK, stored.themeMode)
        assertEquals(WidgetColorSource.CUSTOM_PALETTE, stored.colorSource)
        assertEquals(ThemePalette.MEADOW.name, stored.paletteName)
        assertEquals(WidgetDensityScale.COMPACT, stored.densityScale)
        assertEquals(WidgetTextScale.LARGE, stored.textScale)
    }

    @Test
    fun `widget settings state exposes appearance controls and refreshes placed widgets`() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(SettingsUiAction.SetWidgetAppearance(densityScale = WidgetDensityScale.COMFORTABLE))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(WidgetDensityScale.COMFORTABLE, uiState.widgetDensityScale)
        assertEquals(WidgetThemeMode.SYSTEM, uiState.widgetThemeMode)
        assertEquals(WidgetColorSource.SYSTEM_DYNAMIC, uiState.widgetColorSource)
        assertEquals(WidgetTextScale.STANDARD, uiState.widgetTextScale)
        assertTrue("Appearance changes must refresh placed widgets.", widgetRefresher.refreshCallCount >= 1)
    }

    @Test
    fun `toggling widget actions refreshes placed widgets`() = runTest {
        val viewModel = newViewModel()

        viewModel.onAction(SettingsUiAction.SetWidgetShowActions(false))
        advanceUntilIdle()

        assertEquals(false, widgetPreferences.state.value.showActions)
        assertEquals(1, widgetRefresher.refreshCallCount)
    }

    private fun newViewModel() = SettingsWidgetViewModel(
        preferences = widgetPreferences,
        appearancePreferences = appearancePreferences,
        widgetRefresher = widgetRefresher,
    )

    private class RecordingWidgetPreferences : WidgetPreferences {
        val state = MutableStateFlow(
            WidgetPreferenceState(
                showActions = true,
                themeMode = WidgetThemeMode.SYSTEM,
                colorSource = WidgetColorSource.SYSTEM_DYNAMIC,
                paletteName = ThemePalette.ANSHIN.name,
                densityScale = WidgetDensityScale.STANDARD,
                textScale = WidgetTextScale.STANDARD,
            ),
        )

        override val widgets: Flow<WidgetPreferenceState> = state

        override suspend fun updateWidgetShowActions(enabled: Boolean) {
            state.value = state.value.copy(showActions = enabled)
        }

        override suspend fun updateWidgetAppearance(
            themeMode: WidgetThemeMode?,
            colorSource: WidgetColorSource?,
            paletteName: String?,
            densityScale: WidgetDensityScale?,
            textScale: WidgetTextScale?,
        ) {
            state.value = state.value.copy(
                themeMode = themeMode ?: state.value.themeMode,
                colorSource = colorSource ?: state.value.colorSource,
                paletteName = paletteName ?: state.value.paletteName,
                densityScale = densityScale ?: state.value.densityScale,
                textScale = textScale ?: state.value.textScale,
            )
        }
    }

    private class FixedAppearancePreferences : AppearancePreferences {
        override val appearance: Flow<AppearancePreferenceState> = MutableStateFlow(
            AppearancePreferenceState(
                themeMode = ThemeMode.SYSTEM,
                useDynamicColor = false,
                themePaletteName = ThemePalette.ANSHIN.name,
                fontMode = FontMode.SYSTEM,
                appTextScale = AppTextScale.STANDARD,
                uiDensityScale = UiDensityScale.STANDARD,
                autoCollapseCompletedGroups = false,
                homeHeroStyle = HomeHeroStyle.ACTION,
            ),
        )

        override suspend fun updateThemeMode(themeMode: ThemeMode) = Unit
        override suspend fun updateUseDynamicColor(enabled: Boolean) = Unit
        override suspend fun updateThemePalette(paletteName: String) = Unit
        override suspend fun updateFontMode(fontMode: FontMode) = Unit
        override suspend fun updateAppTextScale(scale: AppTextScale) = Unit
        override suspend fun updateUiDensityScale(scale: UiDensityScale) = Unit
        override suspend fun updateAutoCollapseCompletedGroups(enabled: Boolean) = Unit
        override suspend fun updateHomeHeroStyle(style: HomeHeroStyle) = Unit
    }
}
