package com.driezy.medlog.ui.screen.welcome

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


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
    val (titleY, titleAlpha) = rememberSlideEntry(isCurrentPage, 20f, 0L)
    val (subY, subAlpha)     = rememberSlideEntry(isCurrentPage, 20f, 80L)
    val (cardY, cardAlpha)   = rememberSlideEntry(isCurrentPage, 24f, 160L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.welcome_p4_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Text(
            stringResource(R.string.welcome_p4_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )

        // 功能选项卡片
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = cardY; alpha = cardAlpha },
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                FeatureToggleRow(
                    title = stringResource(R.string.welcome_p4_symptom_title),
                    description = stringResource(R.string.welcome_p4_symptom_desc),
                    icon = MedLogIcons.EditNote,
                    checked = uiState.enableSymptomDiary,
                    onCheckedChange = onToggleSymptomDiary,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title = stringResource(R.string.welcome_p4_drugs_title),
                    description = stringResource(R.string.welcome_p4_drugs_desc),
                    icon = MedLogIcons.MedicalServices,
                    checked = uiState.enableDrugDatabase,
                    onCheckedChange = onToggleDrugDatabase,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title = stringResource(R.string.welcome_p4_interaction_title),
                    description = stringResource(R.string.welcome_p4_interaction_desc),
                    icon = MedLogIcons.Warning,
                    checked = uiState.enableDrugInteractionCheck,
                    onCheckedChange = onToggleDrugInteractionCheck,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title = stringResource(R.string.welcome_p4_health_title),
                    description = stringResource(R.string.welcome_p4_health_desc),
                    icon = MedLogIcons.MonitorHeart,
                    checked = uiState.enableHealthModule,
                    onCheckedChange = onToggleHealthModule,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title = stringResource(R.string.welcome_p4_timeperiod_title),
                    description = stringResource(R.string.welcome_p4_timeperiod_desc),
                    icon = MedLogIcons.Schedule,
                    checked = uiState.enableTimePeriodMode,
                    onCheckedChange = onToggleTimePeriodMode,
                )
            }
        }

        Text(
            stringResource(R.string.welcome_p4_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        // 外观主题选择
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = cardY; alpha = cardAlpha },
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MedLogIcon(MedLogIcons.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.welcome_p4_theme_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
                val themeModes = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.welcome_p4_theme_system),
                    ThemeMode.LIGHT  to stringResource(R.string.welcome_p4_theme_light),
                    ThemeMode.DARK   to stringResource(R.string.welcome_p4_theme_dark),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    themeModes.forEachIndexed { index, (mode, label) ->
                        ToggleButton(
                            checked = uiState.themeMode == mode,
                            onCheckedChange = { onThemeModeChange(mode) },
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
        }
    }
}

@Composable
private fun FeatureToggleRow(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(description, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            MedLogIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}
