package com.driezy.medlog.ui.screen.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

private data class QuickStartStep(val icon: Int, val text: String)

@Composable
internal fun WelcomePage3(isCurrentPage: Boolean) {
    val profile = rememberWelcomeLayoutProfile()
    val (iconScale, iconAlpha) = rememberSpringEntry(
        isCurrentPage,
        0.88f,
        welcomeEntryDelayMs(0, profile.constrained, profile.motionEnabled),
    )
    val (contentY, contentAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(1, profile.constrained, profile.motionEnabled),
    )
    val steps = listOf(
        QuickStartStep(MedLogIcons.Add, stringResource(R.string.welcome_p5_step1)),
        QuickStartStep(MedLogIcons.AccessTime, stringResource(R.string.welcome_p5_step2)),
        QuickStartStep(MedLogIcons.CheckCircle, stringResource(R.string.welcome_p5_step3)),
    )

    WelcomePageScaffold(
        modifier = Modifier.padding(welcomePagePadding(profile)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (profile.showIllustration) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        MedLogIcons.CheckCircleDisplay48,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.welcome_p5_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = contentY
                alpha = contentAlpha
            },
        )
        Text(
            stringResource(R.string.welcome_p5_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = contentY
                alpha = contentAlpha
            },
        )
        Spacer(Modifier.height(if (profile.constrained) 2.dp else 8.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = contentY
                    alpha = contentAlpha
                },
        ) {
            Column {
                steps.forEachIndexed { index, step ->
                    ListItem(
                        headlineContent = {
                            Text(step.text, style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    MedLogIcon(
                                        step.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (index < steps.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
