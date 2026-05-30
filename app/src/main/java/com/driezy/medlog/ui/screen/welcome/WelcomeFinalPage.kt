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


private data class QuickStartStep(val icon: Int, val text: String)

@Composable
internal fun WelcomePage3(isCurrentPage: Boolean) {
    val (iconScale, iconAlpha) = rememberSpringEntry(isCurrentPage, 0.2f, 0L)
    val (titleY, titleAlpha)   = rememberSlideEntry(isCurrentPage, 24f, 180L)
    val (subY,   subAlpha)     = rememberSlideEntry(isCurrentPage, 24f, 260L)

    val steps = listOf(
        QuickStartStep(MedLogIcons.Add,         stringResource(R.string.welcome_p5_step1)),
        QuickStartStep(MedLogIcons.AccessTime,  stringResource(R.string.welcome_p5_step2)),
        QuickStartStep(MedLogIcons.CheckCircle, stringResource(R.string.welcome_p5_step3)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = iconAlpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                MedLogIcon(
                    MedLogIcons.CheckCircleDisplay48,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.welcome_p5_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_p5_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
        Spacer(Modifier.height(32.dp))
        // 快速开始步骤
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = subY; alpha = subAlpha },
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                steps.forEachIndexed { index, step ->
                    val (rowY, rowAlpha) = rememberSlideEntry(isCurrentPage, 20f, 300L + index * 80L)
                    ListItem(
                        headlineContent = {
                            Text(step.text, style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    MedLogIcon(
                                        step.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        modifier = Modifier.graphicsLayer { translationY = rowY; alpha = rowAlpha },
                    )
                    if (index < steps.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

// ── 第4页：功能选择 ────────────────────────────────────────────────────────────
