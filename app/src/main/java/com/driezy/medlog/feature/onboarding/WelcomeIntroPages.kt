package com.driezy.medlog.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.driezy.medlog.ui.components.AnshinBrandMark
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
        horizontalAlignment = Alignment.CenterHorizontally,
        centered = true,
    ) {
        if (profile.showBrandMark) {
            AnshinBrandMark(
                modifier = Modifier
                    .size(if (profile.constrained) 80.dp else 112.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    },
            )
            Spacer(Modifier.height(if (profile.constrained) 4.dp else 12.dp))
        }
        Text(
            stringResource(R.string.welcome_p0_title),
            style = welcomeTitleStyle(hero = true),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = titleY
                alpha = titleAlpha
            },
        )
        Text(
            stringResource(R.string.welcome_p0_body),
            style = welcomeBodyStyle(),
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

    WelcomePageScaffold {
        Text(
            stringResource(R.string.welcome_p1_title),
            style = welcomeTitleStyle(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = if (profile.constrained) 4.dp else 12.dp)
                .graphicsLayer {
                    translationY = titleY
                    alpha = titleAlpha
                },
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                features.forEachIndexed { index, feature ->
                    FeatureRow(
                        feature = feature,
                        isCurrentPage = isCurrentPage,
                        delayMs = welcomeEntryDelayMs(index + 1, profile.constrained, profile.motionEnabled),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature, isCurrentPage: Boolean, delayMs: Long) {
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
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                MedLogIcon(
                    feature.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
