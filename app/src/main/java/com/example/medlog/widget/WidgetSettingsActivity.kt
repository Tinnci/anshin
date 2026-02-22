package com.example.medlog.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.AndroidEntryPoint
import com.example.medlog.R
import com.example.medlog.data.repository.ThemeMode
import com.example.medlog.ui.screen.settings.SettingsViewModel
import com.example.medlog.ui.theme.MedLogTheme
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

        setContent {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiState.themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM -> systemDark
            }

            MedLogTheme(darkTheme = darkTheme, dynamicColor = uiState.useDynamicColor) {
                WidgetSettingsScreen(
                    widgetShowActions = uiState.widgetShowActions,
                    onShowActionsChange = { viewModel.setWidgetShowActions(it) },
                    onClose = {
                        // 若在 configure 流程中，回传 RESULT_OK 让系统放置小组件
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            setResult(Activity.RESULT_OK)
                        }
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
    widgetShowActions: Boolean,
    onShowActionsChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
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
                    Icon(
                        Icons.Rounded.TouchApp,
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
