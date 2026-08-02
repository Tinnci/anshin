package com.driezy.medlog.feature.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.capability.widgets.MedLogWidgetReceiver
import com.driezy.medlog.capability.widgets.NextDoseWidgetReceiver
import com.driezy.medlog.capability.widgets.StreakWidgetReceiver
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.utils.OemWidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SettingsWidgetsContent(
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    msgWidgetPinOem: String,
    msgWidgetPinOk: String,
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.settings_card_widgets),
        subtitle = stringResource(R.string.settings_group_widgets_desc),
        icon = MedLogIcons.Widgets,
    ) {
        val widgetManager = AppWidgetManager.getInstance(context)
        val canPin = widgetManager.isRequestPinAppWidgetSupported
        val oemNeedsPermission = OemWidgetHelper.requiresExtraPermission

        fun requestWidget(receiverClass: Class<*>) {
            if (canPin) {
                widgetManager.requestPinAppWidget(
                    ComponentName(context, receiverClass),
                    null,
                    null,
                )
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (oemNeedsPermission) msgWidgetPinOem else msgWidgetPinOk,
                        duration = SnackbarDuration.Long,
                    )
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        OemWidgetHelper.manualAddGuidance(context),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large)
                .padding(top = MedLogSpacing.Tiny, bottom = MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            if (!canPin) {
                // 桌面不支持直接固定时，显示手动添加引导
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(MedLogSpacing.Medium),
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MedLogIcon(
                            MedLogIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(
                                R.string.settings_widget_no_pin_hint,
                                OemWidgetHelper.manualAddGuidance(context),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                // 可以固定——显示通用提示
                Text(
                    stringResource(R.string.settings_widget_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // OEM 专属权限提醒（小米 / OPPO / vivo）
                if (oemNeedsPermission) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(MedLogSpacing.Medium),
                            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                                verticalAlignment = Alignment.Top,
                            ) {
                                MedLogIcon(
                                    MedLogIcons.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    OemWidgetHelper.permissionNote(context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            OutlinedButton(
                                onClick = { OemWidgetHelper.openPermissionSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    horizontal = MedLogSpacing.Medium,
                                    vertical = MedLogSpacing.Small,
                                ),
                            ) {
                                MedLogIcon(
                                    MedLogIcons.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(MedLogSpacing.Small))
                                Text(
                                    stringResource(R.string.settings_widget_oem_btn),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            // 今日进度小组件显示模式开关（SSOT：与小组件长按设置共享同一 DataStore）
            SettingsSwitchRow(
                title = stringResource(R.string.widget_settings_show_actions),
                subtitle = if (uiState.widgetShowActions) {
                    stringResource(R.string.widget_settings_show_actions_body)
                } else {
                    stringResource(R.string.widget_settings_status_body)
                },
                checked = uiState.widgetShowActions,
                onCheckedChange = { onAction(SettingsUiAction.SetWidgetShowActions(it)) },
                icon = MedLogIcons.TouchApp,
            )

            WidgetPreviewPager(
                items = listOf(
                    WidgetPreviewItem(
                        previewType = WidgetPreviewType.TODAY,
                        name = stringResource(R.string.settings_widget_today_name),
                        description = stringResource(R.string.settings_widget_today_desc),
                        sizes = listOf("2×2", "4×2", "4×4"),
                        canPin = canPin,
                        showActions = uiState.widgetShowActions,
                    ),
                    WidgetPreviewItem(
                        previewType = WidgetPreviewType.NEXT_DOSE,
                        name = stringResource(R.string.settings_widget_next_name),
                        description = stringResource(R.string.settings_widget_next_desc),
                        sizes = listOf("2×2", "4×2"),
                        canPin = canPin,
                        showActions = uiState.widgetShowActions,
                    ),
                    WidgetPreviewItem(
                        previewType = WidgetPreviewType.STREAK,
                        name = stringResource(R.string.settings_widget_streak_name),
                        description = stringResource(R.string.settings_widget_streak_desc),
                        sizes = listOf("2×2", "4×2"),
                        canPin = canPin,
                    ),
                ),
                onAdd = { type ->
                    requestWidget(
                        when (type) {
                            WidgetPreviewType.TODAY -> MedLogWidgetReceiver::class.java
                            WidgetPreviewType.NEXT_DOSE -> NextDoseWidgetReceiver::class.java
                            WidgetPreviewType.STREAK -> StreakWidgetReceiver::class.java
                        },
                    )
                },
            )
        }
    }
}
