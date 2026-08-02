package com.driezy.medlog.feature.medications.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.ui.components.MedicationCard
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.displayName
import com.driezy.medlog.ui.util.formatDose
import com.driezy.medlog.ui.util.labelRes
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MedicationTaskGroupCard(
    title: String,
    subtitle: String,
    icon: Int,
    items: List<MedicationWithStatus>,
    onToggleTaken: (MedicationWithStatus) -> Unit,
    onSkip: (MedicationWithStatus) -> Unit,
    onTakeAll: () -> Unit,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    autoCollapse: Boolean = true,
    onPartialTake: ((MedicationWithStatus, Double) -> Unit)? = null,
) {
    val pendingCount = items.count { !it.isHandled }
    val allDone = pendingCount == 0
    val motionScheme = MaterialTheme.motionScheme
    var isExpanded by remember(allDone, autoCollapse) {
        mutableStateOf(!allDone || !autoCollapse)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pendingCount > 0) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                icon = icon,
                contentDescription = null,
                tint = if (pendingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pendingCount > 1) {
                Button(
                    onClick = onTakeAll,
                    contentPadding = PaddingValues(horizontal = MedLogSpacing.Large, vertical = 0.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    MedLogIcon(MedLogIcons.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(MedLogSpacing.Small))
                    Text(
                        stringResource(R.string.home_period_take_all_btn),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = { isExpanded = !isExpanded }) {
                MedLogIcon(
                    icon = if (isExpanded) MedLogIcons.ExpandLess else MedLogIcons.ExpandMore,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.home_period_collapse else R.string.home_period_expand,
                    ),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(motionScheme.defaultSpatialSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()),
        ) {
            Column {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MedLogSpacing.Medium),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                items.forEachIndexed { idx, item ->
                    var visible by remember(item.doseKey) { mutableStateOf(false) }
                    LaunchedEffect(item.doseKey) {
                        delay(idx * STAGGER_DELAY_MS)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                            slideInVertically(motionScheme.defaultSpatialSpec()) { it / 3 },
                    ) {
                        Column {
                            MedicationCard(
                                item = item,
                                onToggleTaken = { onToggleTaken(item) },
                                onSkip = { onSkip(item) },
                                onClick = { onClick(item.medication.id) },
                                modifier = Modifier,
                                flatStyle = true,
                                onPartialTake = if (onPartialTake != null) {
                                    { qty -> onPartialTake(item, qty) }
                                } else {
                                    null
                                },
                            )
                            if (idx < items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = MedLogSpacing.Large),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(MedLogSpacing.Tiny))
            }
        }
    }
}

@Composable
internal fun CompactMedicationPlanRow(
    item: MedicationWithStatus,
    onToggleTaken: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val medication = item.medication
    val period = TimePeriod.fromKey(medication.timePeriod)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .testTag("homeCompactPlanRow")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (item.isHandled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Medium, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        icon = MedLogIcons.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.width(54.dp),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
            ) {
                Text(
                    text = item.displayTime(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(period.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            VerticalDivider(
                modifier = Modifier.height(42.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
            ) {
                Text(
                    text = medication.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${medication.doseQuantity.formatDose()} ${medication.doseUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            FilledTonalIconButton(
                onClick = onToggleTaken,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("homeCompactPlanToggle"),
            ) {
                MedLogIcon(
                    icon = if (item.isHandled) MedLogIcons.Undo else MedLogIcons.Check,
                    contentDescription = stringResource(
                        if (item.isHandled) R.string.home_snackbar_undo else R.string.med_card_btn_take,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
