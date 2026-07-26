package com.driezy.medlog.ui.screen.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

@Composable
internal fun WelcomePage0(isCurrentPage: Boolean) {
    val profile = rememberWelcomeLayoutProfile()
    val iconDelay = welcomeEntryDelayMs(0, profile.constrained, profile.motionEnabled)
    val titleDelay = welcomeEntryDelayMs(1, profile.constrained, profile.motionEnabled)
    val bodyDelay = welcomeEntryDelayMs(2, profile.constrained, profile.motionEnabled)
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.88f, iconDelay)
    val (titleY, titleAlpha) = rememberSlideEntry(isCurrentPage, 16f, titleDelay)
    val (bodyY, bodyAlpha) = rememberSlideEntry(isCurrentPage, 16f, bodyDelay)

    WelcomePageScaffold(
        modifier = Modifier.padding(welcomePagePadding(profile)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (profile.constrained) 8.dp else 56.dp))
        if (profile.showIllustration) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        MedLogIcons.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            stringResource(R.string.welcome_p0_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = titleY
                alpha = titleAlpha
            },
        )
        Text(
            stringResource(R.string.welcome_p0_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = bodyY
                alpha = bodyAlpha
            },
        )
    }
}

private data class Feature(val icon: Int, val title: String, val description: String)

@Composable
internal fun WelcomePage1(isCurrentPage: Boolean) {
    val profile = rememberWelcomeLayoutProfile()
    val features = listOf(
        Feature(
            MedLogIcons.NotificationsActive,
            stringResource(R.string.welcome_p1_feat1_title),
            stringResource(R.string.welcome_p1_feat1_desc),
        ),
        Feature(
            MedLogIcons.Inventory2,
            stringResource(R.string.welcome_p1_feat2_title),
            stringResource(R.string.welcome_p1_feat2_desc),
        ),
        Feature(
            MedLogIcons.History,
            stringResource(R.string.welcome_p1_feat3_title),
            stringResource(R.string.welcome_p1_feat3_desc),
        ),
    )
    val (titleY, titleAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(0, profile.constrained, profile.motionEnabled),
    )

    WelcomePageScaffold(modifier = Modifier.padding(welcomePagePadding(profile))) {
        Text(
            stringResource(R.string.welcome_p1_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = if (profile.constrained) 4.dp else 12.dp)
                .graphicsLayer {
                    translationY = titleY
                    alpha = titleAlpha
                },
        )
        features.forEachIndexed { index, feature ->
            FeatureRow(
                feature = feature,
                isCurrentPage = isCurrentPage,
                delayMs = welcomeEntryDelayMs(index + 1, profile.constrained, profile.motionEnabled),
                showDescription = profile.showSupportingText,
            )
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature, isCurrentPage: Boolean, delayMs: Long, showDescription: Boolean) {
    val (offsetY, alpha) = rememberSlideEntry(isCurrentPage, 20f, delayMs)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetY
                this.alpha = alpha
            },
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                MedLogIcon(
                    feature.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (showDescription) {
                Spacer(Modifier.height(2.dp))
                Text(
                    feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
