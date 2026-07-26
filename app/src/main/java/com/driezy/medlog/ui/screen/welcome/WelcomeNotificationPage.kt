package com.driezy.medlog.ui.screen.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
internal fun WelcomeNotificationPage(isCurrentPage: Boolean, notifGranted: Boolean, onRequestPermission: () -> Unit) {
    val profile = rememberWelcomeLayoutProfile()
    val (iconScale, iconAlpha) = rememberSpringEntry(
        isCurrentPage,
        0.88f,
        welcomeEntryDelayMs(0, profile.constrained, profile.motionEnabled),
    )
    val (titleY, titleAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(1, profile.constrained, profile.motionEnabled),
    )
    val (bodyY, bodyAlpha) = rememberSlideEntry(
        isCurrentPage,
        16f,
        welcomeEntryDelayMs(2, profile.constrained, profile.motionEnabled),
    )

    WelcomePageScaffold(
        modifier = Modifier.padding(welcomePagePadding(profile)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (profile.showIllustration) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (notifGranted) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MedLogIcon(
                        if (notifGranted) MedLogIcons.NotificationsActive else MedLogIcons.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = if (notifGranted) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        }
        Text(
            if (notifGranted) {
                stringResource(R.string.welcome_notif_granted_title)
            } else {
                stringResource(R.string.welcome_notif_request_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = titleY
                alpha = titleAlpha
            },
        )
        Text(
            if (notifGranted) {
                stringResource(R.string.welcome_notif_granted_body)
            } else {
                stringResource(R.string.welcome_notif_request_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = bodyY
                alpha = bodyAlpha
            },
        )
        if (!notifGranted) {
            Spacer(Modifier.height(if (profile.constrained) 4.dp else 12.dp))
            if (profile.showSupportingText) {
                Text(
                    stringResource(R.string.welcome_notif_guide),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                MedLogIcon(
                    MedLogIcons.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.welcome_notif_grant_btn))
            }
            Text(
                stringResource(R.string.welcome_notif_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
