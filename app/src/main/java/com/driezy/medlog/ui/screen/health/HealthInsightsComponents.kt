package com.driezy.medlog.ui.screen.health

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.domain.health.HealthInsight
import com.driezy.medlog.domain.health.HealthInsightSeverity
import com.driezy.medlog.ui.components.AiInteractionStatusPill
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography

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
