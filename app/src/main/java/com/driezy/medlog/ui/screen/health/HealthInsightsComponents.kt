package com.driezy.medlog.ui.screen.health

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.theme.MedLogSpacing
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.domain.health.HealthInsight
import com.driezy.medlog.domain.health.HealthInsightSeverity
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.ui.components.AiInteractionStatusPill
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HealthInsightsSection(
    insights: List<HealthInsight>,
    executionStatus: AiExecutionStatus,
    isRefreshing: Boolean,
) {
    val presentation = HealthInsightsPresentation.from(
        insightCount = insights.size,
        isRefreshing = isRefreshing,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SectionHeader(
                    title = stringResource(R.string.health_insights_section_title),
                    subtitle = stringResource(R.string.health_insights_section_subtitle),
                )
            }
            AiInteractionStatusPill(
                status = executionStatus,
                isRunning = isRefreshing,
            )
        }
        if (presentation.showPendingBody) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(MedLogSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(presentation.pendingBodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        insights.forEach { insight ->
            HealthInsightCard(insight = insight)
        }
    }
}

@Composable
private fun HealthInsightCard(insight: HealthInsight) {
    val colors = when (insight.severity) {
        HealthInsightSeverity.URGENT -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        HealthInsightSeverity.WARNING -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        HealthInsightSeverity.INFO -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    val icon = when (insight.severity) {
        HealthInsightSeverity.URGENT,
        HealthInsightSeverity.WARNING,
        -> MedLogIcons.Warning
        HealthInsightSeverity.INFO -> MedLogIcons.AutoAwesome
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = colors,
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            MedLogIcon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Text(
                    insight.title,
                    style = MaterialTheme.emphasizedTypography.titleSmall,
                )
                Text(
                    insight.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                )
            }
        }
    }
}
