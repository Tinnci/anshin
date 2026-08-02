package com.driezy.medlog.feature.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
import com.driezy.medlog.ui.components.RoutineScheduleEditor

@Composable
internal fun WelcomePage2(
    uiState: WelcomeUiState,
    isCurrentPage: Boolean,
    onTimeChange: (RoutineTimeSlot, RoutineTime) -> Unit,
) {
    val profile = rememberWelcomeLayoutProfile()
    val (titleY, titleAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(0, profile.constrained, profile.motionEnabled),
    )
    val (subY, subAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(1, profile.constrained, profile.motionEnabled),
    )

    WelcomePageScaffold(modifier = Modifier.padding(welcomePagePadding(profile))) {
        Text(
            stringResource(R.string.welcome_p2_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                translationY = titleY
                alpha = titleAlpha
            },
        )
        if (profile.showSupportingText) {
            Text(
                stringResource(R.string.welcome_p2_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    translationY = subY
                    alpha = subAlpha
                },
            )
        }
        Spacer(Modifier.height(if (profile.constrained) 2.dp else 8.dp))

        val (editorY, editorAlpha) = rememberSlideEntry(
            isCurrentPage,
            16f,
            welcomeEntryDelayMs(2, profile.constrained, profile.motionEnabled),
        )
        RoutineScheduleEditor(
            schedule = uiState.routineSchedule,
            onTimeChange = onTimeChange,
            modifier = Modifier.graphicsLayer {
                translationY = editorY
                alpha = editorAlpha
            },
        )
    }
}

// ── 第4页：通知权限 ──────────────────────────────────────────────────────────
