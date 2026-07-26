package com.driezy.medlog.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.HomeHeroStyle
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.ThemePalette

@Composable
internal fun SettingsAppearanceContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    palettePreviewDark: Boolean,
) {
    val profile = rememberSettingsLayoutProfile()
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium)) {
        ThemeSettingsCard(
            uiState = uiState,
            viewModel = viewModel,
            palettePreviewDark = palettePreviewDark,
            showSupportingText = profile.showSupportingText,
        )
        DisplaySettingsCard(uiState = uiState, viewModel = viewModel)
        TodaySettingsCard(
            uiState = uiState,
            viewModel = viewModel,
            showSupportingText = profile.showSupportingText,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeSettingsCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    palettePreviewDark: Boolean,
    showSupportingText: Boolean,
) {
    SettingsCard(
        title = stringResource(R.string.settings_theme),
        subtitle = stringResource(R.string.settings_palette_subtitle),
        icon = MedLogIcons.Palette,
    ) {
        val modes = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modes.forEachIndexed { index, (mode, label) ->
                ToggleButton(
                    checked = uiState.themeMode == mode,
                    onCheckedChange = { viewModel.setThemeMode(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large)
                .padding(top = MedLogSpacing.Small, bottom = MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            SettingsFieldLabel(
                title = stringResource(R.string.settings_palette_title),
                subtitle = if (showSupportingText) {
                    stringResource(R.string.settings_palette_subtitle)
                } else {
                    null
                },
                icon = MedLogIcons.LocalFlorist,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                items(ThemePalette.entries, key = { it.name }) { palette ->
                    ThemePaletteChip(
                        palette = palette,
                        selected = uiState.themePalette == palette,
                        darkTheme = palettePreviewDark,
                        onClick = { viewModel.setThemePalette(palette) },
                    )
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = uiState.useDynamicColor,
                onCheckedChange = viewModel::setUseDynamicColor,
                icon = MedLogIcons.ColorLens,
            )
        }
    }
}

@Composable
private fun DisplaySettingsCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(
        title = stringResource(R.string.settings_display_title),
        subtitle = stringResource(R.string.settings_display_subtitle),
        icon = MedLogIcons.Notes,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
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
    }
}

@Composable
private fun TodaySettingsCard(uiState: SettingsUiState, viewModel: SettingsViewModel, showSupportingText: Boolean) {
    SettingsCard(
        title = stringResource(R.string.settings_card_today),
        subtitle = stringResource(R.string.settings_home_hero_style_subtitle),
        icon = MedLogIcons.ViewAgenda,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            if (showSupportingText) {
                Text(
                    stringResource(R.string.settings_home_hero_style_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        SettingsSwitchRow(
            title = stringResource(R.string.settings_auto_collapse_title),
            subtitle = stringResource(R.string.settings_auto_collapse_subtitle),
            checked = uiState.autoCollapseCompletedGroups,
            onCheckedChange = viewModel::setAutoCollapseCompletedGroups,
            icon = MedLogIcons.UnfoldLess,
        )
    }
}

@Composable
private fun SettingsFieldLabel(title: String, subtitle: String?, icon: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        MedLogIcon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
