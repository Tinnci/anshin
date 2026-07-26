package com.driezy.medlog.ui.screen.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomePage4(
    uiState: WelcomeUiState,
    isCurrentPage: Boolean,
    onToggleSymptomDiary: (Boolean) -> Unit,
    onToggleDrugInteractionCheck: (Boolean) -> Unit,
    onToggleDrugDatabase: (Boolean) -> Unit,
    onToggleHealthModule: (Boolean) -> Unit,
    onToggleTimePeriodMode: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val profile = rememberWelcomeLayoutProfile()
    val (titleY, titleAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(0, profile.constrained, profile.motionEnabled),
    )
    val (contentY, contentAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(1, profile.constrained, profile.motionEnabled),
    )

    WelcomePageScaffold(modifier = Modifier.padding(welcomePagePadding(profile))) {
        Text(
            stringResource(R.string.welcome_p4_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                translationY = titleY
                alpha = titleAlpha
            },
        )
        if (profile.showSupportingText) {
            Text(
                stringResource(R.string.welcome_p4_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    translationY = titleY
                    alpha = titleAlpha
                },
            )
        }
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = contentY
                    alpha = contentAlpha
                },
        ) {
            FeatureToggleRow(
                title = stringResource(R.string.welcome_p4_symptom_title),
                description = stringResource(R.string.welcome_p4_symptom_desc),
                icon = MedLogIcons.EditNote,
                checked = uiState.enableSymptomDiary,
                showDescription = profile.showSupportingText,
                onCheckedChange = onToggleSymptomDiary,
            )
            FeatureDivider()
            FeatureToggleRow(
                title = stringResource(R.string.welcome_p4_drugs_title),
                description = stringResource(R.string.welcome_p4_drugs_desc),
                icon = MedLogIcons.MedicalServices,
                checked = uiState.enableDrugDatabase,
                showDescription = profile.showSupportingText,
                onCheckedChange = onToggleDrugDatabase,
            )
            FeatureDivider()
            FeatureToggleRow(
                title = stringResource(R.string.welcome_p4_interaction_title),
                description = stringResource(R.string.welcome_p4_interaction_desc),
                icon = MedLogIcons.Warning,
                checked = uiState.enableDrugInteractionCheck,
                showDescription = profile.showSupportingText,
                onCheckedChange = onToggleDrugInteractionCheck,
            )
            FeatureDivider()
            FeatureToggleRow(
                title = stringResource(R.string.welcome_p4_health_title),
                description = stringResource(R.string.welcome_p4_health_desc),
                icon = MedLogIcons.MonitorHeart,
                checked = uiState.enableHealthModule,
                showDescription = profile.showSupportingText,
                onCheckedChange = onToggleHealthModule,
            )
            FeatureDivider()
            FeatureToggleRow(
                title = stringResource(R.string.welcome_p4_timeperiod_title),
                description = stringResource(R.string.welcome_p4_timeperiod_desc),
                icon = MedLogIcons.Schedule,
                checked = uiState.enableTimePeriodMode,
                showDescription = profile.showSupportingText,
                onCheckedChange = onToggleTimePeriodMode,
            )
        }
        ThemeModeCard(
            selected = uiState.themeMode,
            onSelected = onThemeModeChange,
            modifier = Modifier.graphicsLayer {
                translationY = contentY
                alpha = contentAlpha
            },
        )
    }
}

@Composable
private fun FeatureDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun FeatureToggleRow(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    showDescription: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = if (showDescription) {
            {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
        } else {
            null
        },
        leadingContent = {
            MedLogIcon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeCard(selected: ThemeMode, onSelected: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.welcome_p4_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.welcome_p4_theme_light),
        ThemeMode.DARK to stringResource(R.string.welcome_p4_theme_dark),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MedLogIcon(
                    MedLogIcons.DarkMode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.welcome_p4_theme_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                modes.forEachIndexed { index, (mode, label) ->
                    ToggleButton(
                        checked = selected == mode,
                        onCheckedChange = { onSelected(mode) },
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
        }
    }
}
