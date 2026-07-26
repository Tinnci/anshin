package com.driezy.medlog.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.HomeHeroStyle
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.displayName
import com.driezy.medlog.ui.util.formatDose

private data class HomeHeroRenderTarget(val style: HomeHeroStyle, val presentation: HomeHeroPresentation)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeHero(
    presentation: HomeHeroPresentation,
    style: HomeHeroStyle,
    currentStreak: Int,
    onTakeNext: (MedicationWithStatus) -> Unit,
    onSkipNext: (MedicationWithStatus) -> Unit,
    onViewDetails: (MedicationWithStatus) -> Unit,
    onAddMedication: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = HomeHeroRenderTarget(style, presentation),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(motionScheme.defaultSpatialSpec())
            .testTag("homeHero"),
        transitionSpec = {
            (
                fadeIn(motionScheme.defaultEffectsSpec()) +
                    slideInVertically(motionScheme.defaultSpatialSpec()) { it / 10 }
                ) togetherWith
                (
                    fadeOut(motionScheme.defaultEffectsSpec()) +
                        slideOutVertically(motionScheme.defaultSpatialSpec()) { -it / 12 }
                    )
        },
        label = "homeHeroState",
    ) { target ->
        when (target.presentation.status) {
            HomeHeroStatus.NO_PLAN -> EmptyHomeHero(onAddMedication = onAddMedication)
            HomeHeroStatus.ALL_TAKEN,
            HomeHeroStatus.HANDLED_WITH_EXCEPTIONS,
            -> CompletedHomeHero(
                presentation = target.presentation,
                currentStreak = currentStreak,
            )
            HomeHeroStatus.ACTION_REQUIRED -> when (target.style) {
                HomeHeroStyle.ACTION -> ActionHomeHero(
                    presentation = target.presentation,
                    currentStreak = currentStreak,
                    onTakeNext = onTakeNext,
                    onSkipNext = onSkipNext,
                )
                HomeHeroStyle.PROGRESS -> ProgressHomeHero(
                    presentation = target.presentation,
                    onTakeNext = onTakeNext,
                    onViewDetails = onViewDetails,
                )
                HomeHeroStyle.TIMELINE -> TimelineHomeHero(
                    presentation = target.presentation,
                    onTakeNext = onTakeNext,
                    onSkipNext = onSkipNext,
                )
            }
        }
    }
}

@Composable
private fun ActionHomeHero(
    presentation: HomeHeroPresentation,
    currentStreak: Int,
    onTakeNext: (MedicationWithStatus) -> Unit,
    onSkipNext: (MedicationWithStatus) -> Unit,
) {
    val next = presentation.nextPendingItem ?: return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("homeHeroAction"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MedLogSpacing.XLarge, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_hero_next_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                HeroCountPill(presentation)
            }
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny)) {
                Text(
                    text = next.medication.displayName(),
                    style = MaterialTheme.emphasizedTypography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = doseScheduleLabel(next),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (currentStreak > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_streak_current, currentStreak, currentStreak),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Button(
                onClick = { onTakeNext(next) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .testTag("homeHeroPrimary"),
                shape = RoundedCornerShape(20.dp),
            ) {
                MedLogIcon(
                    icon = MedLogIcons.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(
                    text = stringResource(R.string.home_hero_take_named, next.medication.displayName()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(
                onClick = { onSkipNext(next) },
                modifier = Modifier.fillMaxWidth().testTag("homeHeroSecondary"),
            ) {
                Text(stringResource(R.string.home_hero_skip_dose))
            }
        }
    }
}

@Composable
private fun ProgressHomeHero(
    presentation: HomeHeroPresentation,
    onTakeNext: (MedicationWithStatus) -> Unit,
    onViewDetails: (MedicationWithStatus) -> Unit,
) {
    val next = presentation.nextPendingItem ?: return
    val progress by animateFloatAsState(
        targetValue = presentation.progressFraction,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "heroProgress",
    )
    Card(
        modifier = Modifier.fillMaxWidth().testTag("homeHeroProgress"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Text(
                text = stringResource(R.string.home_hero_today_progress),
                style = MaterialTheme.emphasizedTypography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            ProgressDial(
                presentation = presentation,
                progress = progress,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = pluralStringResource(
                    R.plurals.home_hero_remaining_doses,
                    presentation.pendingCount,
                    presentation.pendingCount,
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            ProgressNextDose(next)
            Button(
                onClick = { onTakeNext(next) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("homeHeroPrimary"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_take_next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(
                onClick = { onViewDetails(next) },
                modifier = Modifier.fillMaxWidth().testTag("homeHeroSecondary"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(stringResource(R.string.home_hero_view_details))
            }
        }
    }
}

@Composable
private fun ProgressDial(presentation: HomeHeroPresentation, progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(120.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
            strokeWidth = 10.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = presentation.handledCount.toString(),
                    style = MaterialTheme.emphasizedTypography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "/${presentation.totalCount}",
                    modifier = Modifier.padding(bottom = 7.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.home_hero_handled_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ProgressNextDose(next: MedicationWithStatus, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("homeHeroProgressNextDose"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        icon = MedLogIcons.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_next_dose),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = next.medication.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = doseScheduleLabel(next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimelineHomeHero(
    presentation: HomeHeroPresentation,
    onTakeNext: (MedicationWithStatus) -> Unit,
    onSkipNext: (MedicationWithStatus) -> Unit,
) {
    val next = presentation.nextPendingItem ?: return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("homeHeroTimeline"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_hero_next_dose),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                HeroCountPill(presentation)
            }
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny)) {
                Text(
                    text = next.displayTime(),
                    style = MaterialTheme.emphasizedTypography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.home_hero_name_and_dose,
                        next.medication.displayName(),
                        next.medication.doseQuantity.formatDose(),
                        next.medication.doseUnit,
                    ),
                    style = MaterialTheme.emphasizedTypography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DoseTimeline(presentation)
            Button(
                onClick = { onTakeNext(next) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("homeHeroPrimary"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_take_now),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = { onSkipNext(next) },
                modifier = Modifier.fillMaxWidth().testTag("homeHeroSecondary"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(stringResource(R.string.home_hero_skip_dose))
            }
        }
    }
}

@Composable
private fun DoseTimeline(presentation: HomeHeroPresentation) {
    val nextKey = presentation.nextPendingItem?.doseKey
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(54.dp)) {
        val markerTrackWidth = maxWidth - 12.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                thickness = 2.dp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HOME_TIMELINE_ANCHORS.forEach { anchor ->
                val scheduledItem = presentation.scheduledItems.firstOrNull {
                    it.displayTime() == anchor
                }
                val isNext = scheduledItem?.doseKey == nextKey
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = CircleShape,
                        color = when {
                            scheduledItem?.isHandled == true -> MaterialTheme.colorScheme.tertiary
                            isNext -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surface
                        },
                        border = if (scheduledItem?.isHandled != true && !isNext) {
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.32f),
                            )
                        } else {
                            null
                        },
                        content = {},
                    )
                    Text(
                        text = anchor,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNext) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
                        },
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        presentation.scheduledItems
            .filterNot { it.displayTime() in HOME_TIMELINE_ANCHORS }
            .forEach { item ->
                val isNext = item.doseKey == nextKey
                val fraction = timelineFraction(item.scheduledMinuteOfDay())
                Surface(
                    modifier = Modifier
                        .offset(x = markerOffset(markerTrackWidth, fraction))
                        .size(if (isNext) 14.dp else 12.dp),
                    shape = CircleShape,
                    color = when {
                        item.isHandled -> MaterialTheme.colorScheme.tertiary
                        isNext -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surface
                    },
                    border = if (!item.isHandled && !isNext) {
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.56f),
                        )
                    } else {
                        null
                    },
                    content = {},
                )
            }
    }
}

private val HOME_TIMELINE_ANCHORS = listOf("08:00", "12:00", "18:00", "22:00")
private const val HOME_TIMELINE_START_MINUTES = 8 * 60
private const val HOME_TIMELINE_END_MINUTES = 22 * 60

internal fun timelineFraction(minutes: Int): Float = (
    (minutes - HOME_TIMELINE_START_MINUTES).toFloat() /
        (HOME_TIMELINE_END_MINUTES - HOME_TIMELINE_START_MINUTES)
    )
    .coerceIn(0f, 1f)

private fun markerOffset(trackWidth: Dp, fraction: Float): Dp = trackWidth * fraction

@Composable
private fun CompletedHomeHero(presentation: HomeHeroPresentation, currentStreak: Int) {
    val allTaken = presentation.status == HomeHeroStatus.ALL_TAKEN
    Card(
        modifier = Modifier.fillMaxWidth().testTag("homeHeroCompleted"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.XLarge),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        icon = MedLogIcons.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Text(
                    text = stringResource(
                        if (allTaken) {
                            R.string.home_hero_completed_title
                        } else {
                            R.string.home_hero_exceptions_title
                        },
                    ),
                    style = MaterialTheme.emphasizedTypography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = if (allTaken) {
                        pluralStringResource(
                            R.plurals.home_hero_completed_doses,
                            presentation.totalCount,
                            presentation.totalCount,
                        )
                    } else {
                        stringResource(
                            R.string.home_hero_exceptions_body,
                            presentation.takenCount,
                            presentation.skippedCount,
                            presentation.partialCount,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f),
                )
                if (currentStreak > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_streak_current,
                            currentStreak,
                            currentStreak,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeHero(onAddMedication: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("homeHeroEmpty"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        icon = MedLogIcons.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.emphasizedTypography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddMedication,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("homeHeroPrimary"),
                shape = RoundedCornerShape(18.dp),
            ) {
                MedLogIcon(MedLogIcons.Add, contentDescription = null)
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(stringResource(R.string.home_empty_add_btn))
            }
        }
    }
}

@Composable
private fun HeroCountPill(presentation: HomeHeroPresentation) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = stringResource(
                R.string.home_hero_today_count,
                presentation.handledCount,
                presentation.totalCount,
            ),
            modifier = Modifier.padding(horizontal = MedLogSpacing.Medium, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun doseScheduleLabel(item: MedicationWithStatus): String = stringResource(
    R.string.home_hero_schedule_and_dose,
    item.displayTime(),
    item.medication.doseQuantity.formatDose(),
    item.medication.doseUnit,
)
