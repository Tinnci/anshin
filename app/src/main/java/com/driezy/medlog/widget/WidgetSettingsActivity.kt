package com.driezy.medlog.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.WidgetColorSource
import com.driezy.medlog.data.repository.WidgetDensityScale
import com.driezy.medlog.data.repository.WidgetTextScale
import com.driezy.medlog.data.repository.WidgetThemeMode
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.screen.settings.SettingsViewModel
import com.driezy.medlog.ui.theme.MedLogTheme
import com.driezy.medlog.ui.theme.ThemePalette
import com.driezy.medlog.ui.theme.applyMedLogSystemBars
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 小组件设置 Activity—支持两种入口：
 * 1. 桌面长按小组件 → 「小组件设置」（Android 12+ reconfigurable）
 * 2. 主应用「设置」页面 → 「小组件设置」按钮（context.startActivity）
 *
 * SSOT：所有设置均通过 [SettingsViewModel] 写入同一 DataStore。
 */
@AndroidEntryPoint
class WidgetSettingsActivity : ComponentActivity() {

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { refreshPlacedWidgets() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 若从 appwidget configure 流程启动，需返回 RESULT_OK + appWidgetId
        val appWidgetId = intent
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 默认设置为 RESULT_CANCELED，若用户直接退出或通过手势返回，则代表取消创建小组件
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultIntent = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_CANCELED, resultIntent)
        }

        setContent {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            SideEffect {
                applyMedLogSystemBars(darkTheme)
            }

            val isConfigureMode = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

            // ── 配置模式下，若触发系统物理返回键或返回手势，需携带 appWidgetId 明确取消并退出 ────────────────────
            if (isConfigureMode) {
                androidx.activity.compose.BackHandler {
                    val resultIntent = Intent().apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    setResult(Activity.RESULT_CANCELED, resultIntent)
                    finish()
                }
            }

            MedLogTheme(
                darkTheme = darkTheme,
                dynamicColor = uiState.useDynamicColor,
                palette = uiState.themePalette,
            ) {
                WidgetSettingsScreen(
                    isConfigureMode = isConfigureMode,
                    widgetShowActions = uiState.widgetShowActions,
                    widgetThemeMode = uiState.widgetThemeMode,
                    widgetColorSource = uiState.widgetColorSource,
                    widgetPalette = uiState.widgetPalette,
                    widgetDensityScale = uiState.widgetDensityScale,
                    widgetTextScale = uiState.widgetTextScale,
                    onShowActionsChange = { viewModel.setWidgetShowActions(it) },
                    onWidgetThemeModeChange = { viewModel.setWidgetAppearance(themeMode = it) },
                    onWidgetColorSourceChange = { viewModel.setWidgetAppearance(colorSource = it) },
                    onWidgetPaletteChange = { viewModel.setWidgetAppearance(palette = it) },
                    onWidgetDensityScaleChange = { viewModel.setWidgetAppearance(densityScale = it) },
                    onWidgetTextScaleChange = { viewModel.setWidgetAppearance(textScale = it) },
                    onCancel = {
                        val resultIntent = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_CANCELED, resultIntent)
                        finish()
                    },
                    onConfirm = {
                        val resultIntent = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onClose = {
                        finish()
                    },
                )
            }
        }
    }

    private suspend fun refreshPlacedWidgets() {
        val manager = GlanceAppWidgetManager(applicationContext)
        manager.getGlanceIds(MedLogWidget::class.java).forEach { id ->
            MedLogWidget().update(applicationContext, id)
        }
        manager.getGlanceIds(NextDoseWidget::class.java).forEach { id ->
            NextDoseWidget().update(applicationContext, id)
        }
        manager.getGlanceIds(StreakWidget::class.java).forEach { id ->
            StreakWidget().update(applicationContext, id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WidgetSettingsScreen(
    isConfigureMode: Boolean,
    widgetShowActions: Boolean,
    widgetThemeMode: WidgetThemeMode,
    widgetColorSource: WidgetColorSource,
    widgetPalette: ThemePalette,
    widgetDensityScale: WidgetDensityScale,
    widgetTextScale: WidgetTextScale,
    onShowActionsChange: (Boolean) -> Unit,
    onWidgetThemeModeChange: (WidgetThemeMode) -> Unit,
    onWidgetColorSourceChange: (WidgetColorSource) -> Unit,
    onWidgetPaletteChange: (ThemePalette) -> Unit,
    onWidgetDensityScaleChange: (WidgetDensityScale) -> Unit,
    onWidgetTextScaleChange: (WidgetTextScale) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isConfigureMode) {
                            stringResource(R.string.widget_settings_configure_title)
                        } else {
                            stringResource(R.string.widget_settings_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (isConfigureMode) onCancel else onClose) {
                        MedLogIcon(
                            if (isConfigureMode) MedLogIcons.Close else MedLogIcons.ArrowBack,
                            contentDescription = stringResource(
                                if (isConfigureMode) R.string.common_action_cancel else R.string.detail_back,
                            ),
                        )
                    }
                },
                actions = {
                    if (isConfigureMode) {
                        TextButton(onClick = onConfirm) {
                            Text(
                                text = stringResource(R.string.common_action_add),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
        ) {
            WidgetChoiceSection(
                title = stringResource(R.string.widget_settings_theme_mode),
                options = listOf(
                    WidgetThemeMode.SYSTEM to stringResource(R.string.widget_settings_theme_system),
                    WidgetThemeMode.APP to stringResource(R.string.widget_settings_theme_app),
                    WidgetThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    WidgetThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                ),
                selected = widgetThemeMode,
                onSelected = onWidgetThemeModeChange,
            )
            WidgetChoiceSection(
                title = stringResource(R.string.widget_settings_color_source),
                options = listOf(
                    WidgetColorSource.SYSTEM_DYNAMIC to stringResource(R.string.widget_settings_color_dynamic),
                    WidgetColorSource.APP_THEME to stringResource(R.string.widget_settings_color_app),
                    WidgetColorSource.CUSTOM_PALETTE to stringResource(R.string.widget_settings_color_custom),
                ),
                selected = widgetColorSource,
                onSelected = onWidgetColorSourceChange,
            )
            if (widgetColorSource == WidgetColorSource.CUSTOM_PALETTE) {
                WidgetChoiceSection(
                    title = stringResource(R.string.widget_settings_palette),
                    options = ThemePalette.entries.map { it to it.displayName },
                    selected = widgetPalette,
                    onSelected = onWidgetPaletteChange,
                )
            }
            WidgetChoiceSection(
                title = stringResource(R.string.widget_settings_density),
                options = listOf(
                    WidgetDensityScale.COMPACT to stringResource(R.string.settings_ui_density_compact),
                    WidgetDensityScale.STANDARD to stringResource(R.string.settings_ui_density_standard),
                    WidgetDensityScale.COMFORTABLE to stringResource(R.string.settings_ui_density_comfortable),
                ),
                selected = widgetDensityScale,
                onSelected = onWidgetDensityScaleChange,
            )
            WidgetChoiceSection(
                title = stringResource(R.string.widget_settings_text_size),
                options = listOf(
                    WidgetTextScale.STANDARD to stringResource(R.string.settings_text_size_standard),
                    WidgetTextScale.LARGE to stringResource(R.string.settings_text_size_large),
                ),
                selected = widgetTextScale,
                onSelected = onWidgetTextScaleChange,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_settings_show_actions)) },
                supportingContent = {
                    Text(
                        if (widgetShowActions) {
                            stringResource(R.string.widget_settings_show_actions_body)
                        } else {
                            stringResource(R.string.widget_settings_status_body)
                        },
                    )
                },
                leadingContent = {
                    MedLogIcon(
                        MedLogIcons.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = widgetShowActions,
                        onCheckedChange = onShowActionsChange,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> WidgetChoiceSection(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}
