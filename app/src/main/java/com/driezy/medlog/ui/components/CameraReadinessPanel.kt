package com.driezy.medlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraReadinessPanel(
    fillFrameText: String,
    holdSteadyText: String,
    avoidGlareText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadinessChip(MedLogIcons.CenterFocusStrong, fillFrameText)
            ReadinessChip(MedLogIcons.PanTool, holdSteadyText)
            ReadinessChip(MedLogIcons.LightMode, avoidGlareText)
        }
    }
}

@Composable
private fun ReadinessChip(icon: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedLogIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
