package com.driezy.medlog.widget

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.AndroidEntryPoint
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.ui.screen.settings.SettingsViewModel
import com.driezy.medlog.ui.theme.MedLogTheme
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
        // 离开时刷新所有已放置的今日进度小组件以反映最新设置
        lifecycleScope.launch {
            val widget = MedLogWidget()
            val manager = GlanceAppWidgetManager(applicationContext)
            manager.getGlanceIds(MedLogWidget::class.java).forEach { id ->
                widget.update(applicationContext, id)
            }
        }
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
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM -> systemDark
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

            MedLogTheme(darkTheme = darkTheme, dynamicColor = uiState.useDynamicColor) {
                WidgetSettingsScreen(
                    isConfigureMode = isConfigureMode,
                    widgetShowActions = uiState.widgetShowActions,
                    onShowActionsChange = { viewModel.setWidgetShowActions(it) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsScreen(
    isConfigureMode: Boolean,
    widgetShowActions: Boolean,
    onShowActionsChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isConfigureMode)
                            stringResource(R.string.widget_settings_configure_title)
                        else
                            stringResource(R.string.widget_settings_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (isConfigureMode) onCancel else onClose) {
                        MedLogIcon(
                            if (isConfigureMode) MedLogIcons.Close else MedLogIcons.ArrowBack,
                            contentDescription = stringResource(
                                if (isConfigureMode) R.string.common_action_cancel else R.string.detail_back
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
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 8.dp),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_settings_show_actions)) },
                supportingContent = {
                    Text(
                        if (widgetShowActions)
                            stringResource(R.string.widget_settings_show_actions_body)
                        else
                            stringResource(R.string.widget_settings_status_body),
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
