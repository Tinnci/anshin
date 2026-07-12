package com.driezy.medlog.ui.screen.home

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.editorialTypography
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.icon
import com.driezy.medlog.ui.util.labelRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 低库存警告 banner ─────────────────────────────────────────────────────────

private const val LOW_STOCK_VISIBLE_LIMIT = 3

internal data class LowStockItemPresentation(
    val name: String,
    val stock: Double,
    val unit: String,
)

internal data class LowStockPresentation(
    val visibleItems: List<LowStockItemPresentation>,
    val hiddenCount: Int,
) {
    companion object {
        fun from(
            medications: List<Pair<String, Pair<Double, String>>>,
            visibleLimit: Int = LOW_STOCK_VISIBLE_LIMIT,
        ): LowStockPresentation {
            val collapsed = medications
                .groupBy { (name, stockPair) -> name.trim() to stockPair.second }
                .map { (key, entries) ->
                    val lowestStock = entries.minOf { it.second.first }
                    LowStockItemPresentation(
                        name = key.first,
                        stock = lowestStock,
                        unit = key.second,
                    )
                }
                .sortedWith(compareBy<LowStockItemPresentation> { it.stock }.thenBy { it.name })
            return LowStockPresentation(
                visibleItems = collapsed.take(visibleLimit),
                hiddenCount = (collapsed.size - visibleLimit).coerceAtLeast(0),
            )
        }
    }
}

@Composable
internal fun LowStockBanner(
    medications: List<Pair<String, Pair<Double, String>>>,
    modifier: Modifier = Modifier,
) {
    val presentation = remember(medications) { LowStockPresentation.from(medications) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                MedLogIcon(
                    icon = MedLogIcons.Warning,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_low_stock_title),
                    style = MaterialTheme.emphasizedTypography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(4.dp))
                presentation.visibleItems.forEach { item ->
                    Text(
                        text = stringResource(
                            R.string.home_low_stock_item,
                            item.name,
                            item.stock.toString(),
                            item.unit,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.90f),
                    )
                }
                if (presentation.hiddenCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_low_stock_more,
                            presentation.hiddenCount,
                            presentation.hiddenCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }
    }
}

// ── 进度卡片（弹性动画进度条）────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun AnimatedProgressCard(
    taken: Int,
    total: Int,
    currentStreak: Int,
    longestStreak: Int,
    nextUp: Pair<TimePeriod, String>?,
    pendingCount: Int,
    onTakeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else taken.toFloat() / total,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "progress",
    )
    val allDone = total > 0 && taken == total
    val containerColor by animateColorAsState(
        targetValue = if (allDone)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.primaryContainer,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "progressBg",
    )

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.XMedium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stackProgress = maxWidth < 340.dp
                if (stackProgress || total == 0) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        ProgressHeroHeading(taken = taken, total = total, allDone = allDone)
                        if (total > 0) {
                            EditorialProgressMoment(
                                taken = taken,
                                total = total,
                                allDone = allDone,
                                motionScheme = motionScheme,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressHeroHeading(
                            taken = taken,
                            total = total,
                            allDone = allDone,
                            modifier = Modifier.weight(1f),
                        )
                        EditorialProgressMoment(
                            taken = taken,
                            total = total,
                            allDone = allDone,
                            motionScheme = motionScheme,
                        )
                    }
                }
            }
            if (total > 0) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (currentStreak > 0 || nextUp != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                ) {
                    if (currentStreak > 0) {
                        HeroMetaPill(
                            text = pluralStringResource(R.plurals.home_streak_current, currentStreak, currentStreak),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (longestStreak > currentStreak) {
                        HeroMetaPill(
                            text = pluralStringResource(R.plurals.home_streak_longest, longestStreak, longestStreak),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    nextUp?.let { (period, time) ->
                        HeroMetaPill(
                            text = stringResource(R.string.home_next_up, stringResource(period.labelRes), time),
                            icon = period.icon,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (pendingCount > 1) {
                Button(
                    onClick = onTakeAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (allDone) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    MedLogIcon(MedLogIcons.DoneAll, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(MedLogSpacing.Small))
                    Text(
                        stringResource(R.string.home_take_all_btn, pendingCount),
                        style = MaterialTheme.emphasizedTypography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressHeroHeading(
    taken: Int,
    total: Int,
    allDone: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (allDone) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
    ) {
        Text(
            if (total == 0) stringResource(R.string.home_progress_no_plan) else stringResource(R.string.home_progress_title),
            style = MaterialTheme.emphasizedTypography.titleLarge,
            color = contentColor,
        )
        if (total > 0) {
            Text(
                if (allDone) stringResource(R.string.home_progress_all_done)
                else pluralStringResource(R.plurals.home_progress_remaining, total - taken, total - taken),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun HeroMetaPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: Int? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                MedLogIcon(
                    icon = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.emphasizedTypography.labelMedium,
            )
        }
    }
}

@Composable
private fun EditorialProgressMoment(
    taken: Int,
    total: Int,
    allDone: Boolean,
    motionScheme: androidx.compose.material3.MotionScheme,
) {
    val color = if (allDone) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.Bottom,
    ) {
        AnimatedContent(
            targetState = if (allDone) stringResource(R.string.home_editorial_complete) else taken.toString(),
            transitionSpec = {
                (
                    slideInVertically(motionScheme.defaultSpatialSpec()) { -it / 2 } +
                        fadeIn(motionScheme.defaultEffectsSpec())
                ) togetherWith (
                    slideOutVertically(motionScheme.fastSpatialSpec()) { it / 2 } +
                        fadeOut(motionScheme.fastEffectsSpec())
                )
            },
            label = "editorialProgress",
        ) { label ->
            Text(
                text = label,
                style = if (allDone)
                    MaterialTheme.editorialTypography.celebrationWord
                else
                    MaterialTheme.editorialTypography.progressNumeral,
                color = color,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(MedLogSpacing.Small))
        Text(
            text = if (allDone) "$taken / $total" else "/ $total",
            style = MaterialTheme.editorialTypography.progressTotal,
            color = color.copy(alpha = 0.84f),
            maxLines = 1,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

// ── 空状态组件 ────────────────────────────────────────────────────────────────

@Composable
internal fun EmptyMedicationState(onAddMedication: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MedLogIcon(
            MedLogIcons.Medication,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onAddMedication) {
            MedLogIcon(MedLogIcons.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_empty_add_btn))
        }
    }
}

@Composable
internal fun todayDateString(): String {
    val pattern = stringResource(R.string.date_format_day_label)
    return remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date()) }
}
