package com.driezy.medlog.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.capability.ai.CloudAiEndpointPreset
import com.driezy.medlog.capability.ai.CloudAiEndpointProtocol
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EndpointPresetPicker(
    presets: List<CloudAiEndpointPreset>,
    currentBaseUrl: String,
    protocol: CloudAiEndpointProtocol,
    onSelect: (CloudAiEndpointPreset) -> Unit,
) {
    val protocolPresetCount = presets.count { it.protocol == protocol }
    if (protocolPresetCount == 0) return

    val motionScheme = MaterialTheme.motionScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val presentation = CloudAiEndpointPresetListPresentation.from(
        presets = presets,
        query = query,
        currentBaseUrl = currentBaseUrl,
        protocol = protocol,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_ai_endpoint_presets_title, protocolPresetCount),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = !expanded }) {
                MedLogIcon(
                    icon = if (expanded) MedLogIcons.ExpandLess else MedLogIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        if (expanded) R.string.common_collapse else R.string.common_expand,
                    ),
                    modifier = Modifier.padding(start = MedLogSpacing.Tiny),
                )
            }
        }
        if (presentation.featuredRows.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = MedLogSpacing.Small,
                        vertical = MedLogSpacing.Small,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                ) {
                    Text(
                        text = stringResource(R.string.settings_ai_endpoint_featured_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                    ) {
                        presentation.featuredRows.forEach { row ->
                            FilterChip(
                                selected = row.selected,
                                onClick = { onSelect(row.preset) },
                                label = {
                                    Text(
                                        text = row.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = if (row.selected) {
                                    {
                                        MedLogIcon(
                                            MedLogIcons.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
                fadeOut(motionScheme.fastEffectsSpec()),
            label = "endpoint_preset_picker_animated",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_ai_endpoint_search_label)) },
                    leadingIcon = {
                        MedLogIcon(
                            MedLogIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                MedLogIcon(
                                    MedLogIcons.Close,
                                    contentDescription = stringResource(
                                        R.string.settings_ai_endpoint_search_clear,
                                    ),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { }),
                )
                if (presentation.rows.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MedLogSpacing.Tiny),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ai_endpoint_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        if (query.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.settings_ai_endpoint_manual_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { query = "" }) {
                                MedLogIcon(
                                    icon = MedLogIcons.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.settings_ai_endpoint_search_clear),
                                    modifier = Modifier.padding(start = MedLogSpacing.Tiny),
                                )
                            }
                        }
                    }
                } else {
                    val featuredIds = presentation.featuredRows.map { it.id }.toSet()
                    val visibleRows = if (featuredIds.isNotEmpty()) {
                        presentation.rows.filterNot { it.id in featuredIds }
                    } else {
                        presentation.rows
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        visibleRows.forEach { row ->
                            EndpointPresetRow(
                                row = row,
                                onClick = { onSelect(row.preset) },
                            )
                        }
                        if (presentation.rows.size >= 80) {
                            Text(
                                text = stringResource(R.string.settings_ai_endpoint_more_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = MedLogSpacing.Tiny),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class CloudAiEndpointPresetRowPresentation(
    val id: String,
    val name: String,
    val api: String,
    val protocol: CloudAiEndpointProtocol,
    val selected: Boolean,
    val preset: CloudAiEndpointPreset,
)

internal data class CloudAiEndpointPresetListPresentation(
    val rows: List<CloudAiEndpointPresetRowPresentation>,
    val featuredRows: List<CloudAiEndpointPresetRowPresentation> = emptyList(),
) {
    companion object {
        fun from(
            presets: List<CloudAiEndpointPreset>,
            query: String,
            currentBaseUrl: String,
            protocol: CloudAiEndpointProtocol,
        ): CloudAiEndpointPresetListPresentation {
            val normalizedQuery = query.trim().lowercase(Locale.ROOT)
            val normalizedCurrentBaseUrl = currentBaseUrl.normalizedEndpointUrl()
            val filteredPresets = presets
                .asSequence()
                .filter { preset -> preset.protocol == protocol }
                .filter { preset ->
                    normalizedQuery.isBlank() ||
                        preset.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                        preset.api.normalizedEndpointUrl().contains(normalizedQuery) ||
                        preset.id.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                        preset.aliases.any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
                }
                .toList()
            val featuredRows = if (normalizedQuery.isBlank()) {
                filteredPresets
                    .filter { preset -> preset.featured }
                    .sortedBy { preset -> preset.name.lowercase(Locale.ROOT) }
                    .map { preset -> preset.toRow(normalizedCurrentBaseUrl) }
            } else {
                emptyList()
            }
            val rows = filteredPresets
                .sortedWith(
                    compareBy<CloudAiEndpointPreset> {
                        it.api.normalizedEndpointUrl() != normalizedCurrentBaseUrl
                    }.thenBy {
                        if (it.featured) 0 else 1
                    }.thenBy {
                        it.name.lowercase(Locale.ROOT)
                    },
                )
                .take(80)
                .map { preset -> preset.toRow(normalizedCurrentBaseUrl) }
                .toList()
            return CloudAiEndpointPresetListPresentation(
                rows = rows,
                featuredRows = featuredRows,
            )
        }
    }
}

private fun String.normalizedEndpointUrl(): String = trim()
    .removePrefix("https://")
    .removePrefix("http://")
    .trimEnd('/')
    .lowercase(Locale.ROOT)

private fun CloudAiEndpointPreset.toRow(normalizedCurrentBaseUrl: String): CloudAiEndpointPresetRowPresentation =
    CloudAiEndpointPresetRowPresentation(
        id = id,
        name = name,
        api = api,
        protocol = protocol,
        selected = api.normalizedEndpointUrl() == normalizedCurrentBaseUrl,
        preset = this,
    )

@Composable
private fun EndpointPresetRow(row: CloudAiEndpointPresetRowPresentation, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .semantics {
                role = Role.RadioButton
                selected = row.selected
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (row.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (row.selected) 2.dp else 1.dp,
            color = if (row.selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = MedLogSpacing.Small, vertical = MedLogSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.selected) {
                MedLogIcon(
                    MedLogIcons.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.api,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (row.selected) {
                    stringResource(R.string.settings_ai_endpoint_selected)
                } else {
                    stringResource(R.string.common_select)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (row.selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (row.selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
