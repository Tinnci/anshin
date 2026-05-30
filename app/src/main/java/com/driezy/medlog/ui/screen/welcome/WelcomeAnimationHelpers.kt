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
internal fun rememberSpringEntry(
    isCurrentPage: Boolean,
    initialScale: Float = 0.5f,
    delayMs: Long = 0L,
): Pair<Float, Float> {
    val scale = remember { Animatable(initialScale) }
    val alpha = remember { Animatable(0f) }
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            delay(delayMs)
            launch { scale.animateTo(1f, motionScheme.slowSpatialSpec()) }
            launch { alpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
        } else {
            scale.snapTo(initialScale)
            alpha.snapTo(0f)
        }
    }
    return scale.value to alpha.value
}

/** 弹簧上滑 + 淡入（用于标题/正文入场） */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberSlideEntry(
    isCurrentPage: Boolean,
    initialOffsetY: Float = 40f,
    delayMs: Long = 0L,
): Pair<Float, Float> {
    val offsetY = remember { Animatable(initialOffsetY) }
    val alpha   = remember { Animatable(0f) }
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            delay(delayMs)
            launch { offsetY.animateTo(0f, motionScheme.defaultSpatialSpec()) }
            launch { alpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
        } else {
            offsetY.snapTo(initialOffsetY)
            alpha.snapTo(0f)
        }
    }
    return offsetY.value to alpha.value
}

// ── 第0页：欢迎 ────────────────────────────────────────────────────────────
