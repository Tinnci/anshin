package com.driezy.medlog.ui.screen.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.BuildConfig
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.HomeHeroStyle
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.ThemePalette
import com.driezy.medlog.ui.utils.OemWidgetHelper
import com.driezy.medlog.widget.MedLogWidgetReceiver
import com.driezy.medlog.widget.NextDoseWidgetReceiver
import com.driezy.medlog.widget.StreakWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsHomeAppearanceContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    palettePreviewDark: Boolean,
) {
    // ── 外观与首页 ─────────────────────────────────────────
    SettingsCard(
    title = stringResource(R.string.settings_group_appearance_home),
    subtitle = stringResource(R.string.settings_group_appearance_home_desc),
    icon = MedLogIcons.Palette,
    ) {
    // ―― 主题模式 ――
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                MedLogIcons.DarkMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        val themeModes = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT  to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK   to stringResource(R.string.settings_theme_dark),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            themeModes.forEachIndexed { index, (mode, label) ->
                ToggleButton(
                    checked = uiState.themeMode == mode,
                    onCheckedChange = { viewModel.setThemeMode(mode) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        themeModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                MedLogIcons.LocalFlorist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_palette_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_palette_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            ThemePalette.entries.forEach { palette ->
                ThemePaletteChip(
                    palette = palette,
                    selected = uiState.themePalette == palette,
                    darkTheme = palettePreviewDark,
                    onClick = { viewModel.setThemePalette(palette) },
                )
            }
        }
    }
    // ―― Material You 动态颜色（Android 12+）――
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_dynamic_color_title),
            subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
            checked = uiState.useDynamicColor,
            onCheckedChange = viewModel::setUseDynamicColor,
            icon = MedLogIcons.ColorLens,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                MedLogIcons.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_display_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_display_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DisplayOptionGroup(
            title = stringResource(R.string.settings_font_mode_title),
            options = listOf(
                FontMode.SYSTEM to stringResource(R.string.settings_font_mode_system),
                FontMode.ANSHIN to stringResource(R.string.settings_font_mode_anshin),
            ),
            selected = uiState.fontMode,
            onSelected = viewModel::setFontMode,
        )
        DisplayOptionGroup(
            title = stringResource(R.string.settings_text_size_title),
            options = listOf(
                AppTextScale.SMALL to stringResource(R.string.settings_text_size_small),
                AppTextScale.STANDARD to stringResource(R.string.settings_text_size_standard),
                AppTextScale.LARGE to stringResource(R.string.settings_text_size_large),
                AppTextScale.EXTRA_LARGE to stringResource(R.string.settings_text_size_extra_large),
            ),
            selected = uiState.appTextScale,
            onSelected = viewModel::setAppTextScale,
        )
        DisplayOptionGroup(
            title = stringResource(R.string.settings_ui_density_title),
            options = listOf(
                UiDensityScale.COMPACT to stringResource(R.string.settings_ui_density_compact),
                UiDensityScale.STANDARD to stringResource(R.string.settings_ui_density_standard),
                UiDensityScale.COMFORTABLE to stringResource(R.string.settings_ui_density_comfortable),
            ),
            selected = uiState.uiDensityScale,
            onSelected = viewModel::setUiDensityScale,
        )
    }
    SettingsSectionDivider(
        title = stringResource(R.string.settings_card_today),
        icon = MedLogIcons.ViewAgenda,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
    ) {
        Text(
            text = stringResource(R.string.settings_home_hero_style_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DisplayOptionGroup(
            title = stringResource(R.string.settings_home_hero_style_title),
            options = listOf(
                HomeHeroStyle.ACTION to stringResource(R.string.settings_home_hero_style_action),
                HomeHeroStyle.PROGRESS to stringResource(R.string.settings_home_hero_style_progress),
                HomeHeroStyle.TIMELINE to stringResource(R.string.settings_home_hero_style_timeline),
            ),
            selected = uiState.homeHeroStyle,
            onSelected = viewModel::setHomeHeroStyle,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    SettingsSwitchRow(
        title = stringResource(R.string.settings_auto_collapse_title),
        subtitle = stringResource(R.string.settings_auto_collapse_subtitle),
        checked = uiState.autoCollapseCompletedGroups,
        onCheckedChange = viewModel::setAutoCollapseCompletedGroups,
        icon = MedLogIcons.UnfoldLess,
    )
    }
}
