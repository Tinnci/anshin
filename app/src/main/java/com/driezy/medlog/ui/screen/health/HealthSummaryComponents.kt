package com.driezy.medlog.ui.screen.health

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HealthOcrHeroCard(onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.XMedium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Large),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    MedLogIcon(
                        MedLogIcons.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(MedLogSpacing.Medium).size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.health_ocr_hero_title),
                        style = MaterialTheme.emphasizedTypography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.health_ocr_hero_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
            }
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                MedLogIcon(MedLogIcons.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(
                    stringResource(R.string.health_ocr_hero_scan),
                    style = MaterialTheme.emphasizedTypography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HealthMetricsSection(stats: List<HealthTypeStat>) {
    val carouselState = rememberCarouselState { stats.size }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        SectionHeader(
            title = stringResource(R.string.health_metrics_section_title),
            subtitle = stringResource(R.string.health_metrics_section_subtitle),
        )
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 168.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            itemSpacing = MedLogSpacing.Small,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            HealthStatCard(
                stat = stats[it],
                modifier = Modifier
                    .fillMaxHeight()
                    .maskClip(MaterialTheme.shapes.large),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline)) {
        Text(
            title,
            style = MaterialTheme.emphasizedTypography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun HealthStatCard(stat: HealthTypeStat, modifier: Modifier = Modifier) {
    val dateFormat = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }
    val containerColor = if (stat.isAbnormal) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        modifier = modifier.width(168.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedLogIcon(
                    healthTypeIcon(stat.type),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (stat.isAbnormal) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(stat.type.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stat.isAbnormal) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Text(
                stat.type.formatMetricValue(stat.latestValue, stat.latestSecondary),
                style = MaterialTheme.emphasizedTypography.headlineSmall,
                color = if (stat.isAbnormal) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            Text(
                stat.type.unit,
                style = MaterialTheme.typography.labelMedium,
                color = if (stat.isAbnormal) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                },
            )

            // ── 血压分类标签 ──────────────────────────────────────
            if (stat.bpClassRes != null) {
                val bpColor = when (stat.bpClassRes) {
                    R.string.health_bp_class_normal -> MaterialTheme.colorScheme.primary
                    R.string.health_bp_class_low -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = bpColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        stringResource(stat.bpClassRes),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = bpColor,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (stat.avg7d != null) {
                    Text(
                        stringResource(R.string.health_7day_avg, "%.1f".format(stat.avg7d)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val trendArrow = when (stat.trend) {
                        1 -> "↑"
                        -1 -> "↓"
                        0 -> "→"
                        else -> ""
                    }
                    if (trendArrow.isNotEmpty()) {
                        Text(
                            trendArrow,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (stat.trend) {
                                1 -> MaterialTheme.colorScheme.error
                                -1 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                } else {
                    Text(
                        dateFormat.format(Date(stat.latestTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 智能解读文案 ──────────────────────────────────────
            val interpText = buildInterpretation(stat)
            if (interpText != null) {
                Text(
                    interpText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** 根据统计数据生成智能解读文案 */
@Composable
private fun buildInterpretation(stat: HealthTypeStat): String? {
    val parts = mutableListOf<String>()
    // 异常判断
    if (stat.isAbnormal) {
        if (stat.latestValue > stat.type.normalMax) {
            parts += stringResource(R.string.health_interp_high)
        } else if (stat.latestValue < stat.type.normalMin) {
            parts += stringResource(R.string.health_interp_low)
        }
    }
    // 趋势
    when (stat.trend) {
        1 -> parts += stringResource(R.string.health_interp_trend_rising)
        -1 -> parts += stringResource(R.string.health_interp_trend_falling)
        0 -> if (!stat.isAbnormal) parts += stringResource(R.string.health_interp_normal)
    }
    return parts.joinToString("；").ifEmpty { null }
}

// ─── 单条记录 ListItem ────────────────────────────────────────────────────────
