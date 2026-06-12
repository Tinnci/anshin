package com.driezy.medlog.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.ai.CloudAiEndpointProtocol
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.domain.UnifiedImportPayload
import com.driezy.medlog.domain.UnifiedImportPayloadCodec
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.ThemePalette


@Composable
internal fun ThemePaletteChip(
    palette: ThemePalette,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
) {
    val scheme = palette.colorScheme(darkTheme)
    Surface(
        modifier = Modifier
            .widthIn(min = 148.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) scheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Small, vertical = MedLogSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { swatchColor ->
                    Surface(
                        modifier = Modifier.size(width = 8.dp, height = 34.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = swatchColor,
                        content = {},
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(palette.descriptionRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.78f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun <T> DisplayOptionGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, (value, label) ->
                ToggleButton(
                    checked = selected == value,
                    onCheckedChange = { onSelected(value) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

internal enum class CloudAiSettingsVisualState {
    OFF,
    NEEDS_KEY,
    READY,
    TEXT_ONLY,
}

internal enum class SettingsHomeStatusTone {
    OK,
    WARNING,
    INFO,
}

internal data class SettingsHomeOverviewPresentation(
    val attentionCount: Int,
    val enabledModuleCount: Int,
    val reminderTone: SettingsHomeStatusTone,
    val intelligenceTone: SettingsHomeStatusTone,
) {
    companion object {
        fun from(
            uiState: SettingsUiState,
            canScheduleExactAlarms: Boolean,
            canPostNotifications: Boolean,
        ): SettingsHomeOverviewPresentation {
            val reminderWarning = !canScheduleExactAlarms || !canPostNotifications
            val intelligenceWarning = uiState.cloudAiEnabled && !uiState.cloudAiProviderHasApiKey
            val enabledModuleCount = listOf(
                uiState.enableSymptomDiary,
                uiState.enableDrugInteractionCheck,
                uiState.enableDrugDatabase,
                uiState.enableHealthModule,
            ).count { it }

            return SettingsHomeOverviewPresentation(
                attentionCount = listOf(
                    !canScheduleExactAlarms,
                    !canPostNotifications,
                    intelligenceWarning,
                ).count { it },
                enabledModuleCount = enabledModuleCount,
                reminderTone = if (reminderWarning) SettingsHomeStatusTone.WARNING else SettingsHomeStatusTone.OK,
                intelligenceTone = when {
                    intelligenceWarning -> SettingsHomeStatusTone.WARNING
                    uiState.cloudAiEnabled -> SettingsHomeStatusTone.OK
                    else -> SettingsHomeStatusTone.INFO
                },
            )
        }
    }
}

internal data class CloudAiSettingsPresentation(
    val visualState: CloudAiSettingsVisualState,
    @param:StringRes val labelRes: Int,
    @param:StringRes val bodyRes: Int,
) {
    companion object {
        fun from(
            enabled: Boolean,
            hasApiKey: Boolean,
            supportsImageInput: Boolean,
        ): CloudAiSettingsPresentation = when {
            !enabled -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.OFF,
                labelRes = R.string.settings_ai_status_off,
                bodyRes = R.string.settings_ai_status_off_body,
            )
            !hasApiKey -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.NEEDS_KEY,
                labelRes = R.string.settings_ai_status_needs_key,
                bodyRes = R.string.settings_ai_status_needs_key_body,
            )
            supportsImageInput -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.READY,
                labelRes = R.string.settings_ai_status_ready,
                bodyRes = R.string.settings_ai_status_ready_body,
            )
            else -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.TEXT_ONLY,
                labelRes = R.string.settings_ai_status_text_only,
                bodyRes = R.string.settings_ai_status_text_only_body,
            )
        }
    }
}

internal enum class CloudAiApiKeyImportVisualState {
    EMPTY,
    UNSUPPORTED,
    READY,
}

internal data class CloudAiApiKeyImportPresentation(
    val visualState: CloudAiApiKeyImportVisualState,
    val canImport: Boolean,
    val providerName: String?,
    val model: String?,
) {
    companion object {
        fun from(raw: String): CloudAiApiKeyImportPresentation {
            if (raw.isBlank()) {
                return CloudAiApiKeyImportPresentation(
                    visualState = CloudAiApiKeyImportVisualState.EMPTY,
                    canImport = false,
                    providerName = null,
                    model = null,
                )
            }
            val payload = UnifiedImportPayloadCodec.decode(raw)
            val key = (payload as? UnifiedImportPayload.CloudAiApiKey)?.key
            return CloudAiApiKeyImportPresentation(
                visualState = if (key == null) {
                    CloudAiApiKeyImportVisualState.UNSUPPORTED
                } else {
                    CloudAiApiKeyImportVisualState.READY
                },
                canImport = key != null,
                providerName = key?.providerName ?: key?.provider?.providerName,
                model = key?.model,
            )
        }
    }
}
