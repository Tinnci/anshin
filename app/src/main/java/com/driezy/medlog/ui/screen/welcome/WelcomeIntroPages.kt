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
internal fun WelcomePage0(isCurrentPage: Boolean) {
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.3f, 0L)
    val (titleY, titleAlpha)   = rememberSlideEntry(isCurrentPage,  24f, 150L)
    val (subY,   subAlpha)     = rememberSlideEntry(isCurrentPage,  24f, 250L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                MedLogIcon(
                    MedLogIcons.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.welcome_p0_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.welcome_p0_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
    }
}

// ── 第1页：核心功能（错落进场） ────────────────────────────────────────────

private data class Feature(val icon: Int, val title: String, val desc: String)

@Composable
internal fun WelcomePage1(isCurrentPage: Boolean) {
    val features = listOf(
        Feature(MedLogIcons.NotificationsActive, stringResource(R.string.welcome_p1_feat1_title), stringResource(R.string.welcome_p1_feat1_desc)),
        Feature(MedLogIcons.Inventory2,          stringResource(R.string.welcome_p1_feat2_title), stringResource(R.string.welcome_p1_feat2_desc)),
        Feature(MedLogIcons.History,             stringResource(R.string.welcome_p1_feat3_title), stringResource(R.string.welcome_p1_feat3_desc)),
    )
    val (titleY, titleAlpha) = rememberSlideEntry(isCurrentPage, 20f, 0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.welcome_p1_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        features.forEachIndexed { index, feature ->
            AnimatedFeatureRow(feature, isCurrentPage, delayMs = 100L + index * 90L)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AnimatedFeatureRow(feature: Feature, isCurrentPage: Boolean, delayMs: Long) {
    val (offsetY, rowAlpha)    = rememberSlideEntry(isCurrentPage, 36f, delayMs)
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.6f, delayMs)
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.graphicsLayer { translationY = offsetY; this.alpha = rowAlpha },
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; this.alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                MedLogIcon(
                    feature.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column {
            Text(feature.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(feature.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 第2页：作息时间设置（错落进场） ────────────────────────────────────────
