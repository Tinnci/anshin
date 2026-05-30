package com.driezy.medlog.ui.screen.welcome

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
internal fun WelcomeNotificationPage(
    isCurrentPage: Boolean,
    notifGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.3f, 0L)
    val (titleY, titleAlpha)   = rememberSlideEntry(isCurrentPage, 24f, 150L)
    val (subY,   subAlpha)     = rememberSlideEntry(isCurrentPage, 24f, 240L)
    val (btnY,   btnAlpha)     = rememberSlideEntry(isCurrentPage, 24f, 320L)

    // 未授权时按钮脐冲动画，吸引老年用户注意
    val pulseScale = remember { Animatable(1f) }
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(isCurrentPage, notifGranted) {
        if (isCurrentPage && !notifGranted) {
            while (true) {
                pulseScale.animateTo(1.06f, motionScheme.fastSpatialSpec())
                pulseScale.animateTo(1.0f, motionScheme.defaultSpatialSpec())
                delay(1200)
            }
        } else {
            pulseScale.snapTo(1f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (notifGranted) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                MedLogIcon(
                    if (notifGranted) MedLogIcons.NotificationsActive else MedLogIcons.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = if (notifGranted) MaterialTheme.colorScheme.onTertiaryContainer
                           else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            if (notifGranted) stringResource(R.string.welcome_notif_granted_title) else stringResource(R.string.welcome_notif_request_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (notifGranted) stringResource(R.string.welcome_notif_granted_body)
            else stringResource(R.string.welcome_notif_request_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
        if (!notifGranted) {
            Spacer(Modifier.height(28.dp))
            // 引导文字：明显的操作指引，尤其适合老年用户
            Text(
                stringResource(R.string.welcome_notif_guide),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = btnAlpha },
            )
            Spacer(Modifier.height(12.dp))
            // 升级为充填按鈕 + 脐冲缩放动画
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = pulseScale.value
                        scaleY = pulseScale.value
                        translationY = btnY
                        alpha = btnAlpha
                    },
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                MedLogIcon(MedLogIcons.NotificationsActive, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.welcome_notif_grant_btn), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_notif_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = btnAlpha },
            )
        }
    }
}

// ── 第5页：准备完毕 + 快速开始 ──────────────────────────────────────────────
