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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomePage2(
    uiState: WelcomeUiState,
    isCurrentPage: Boolean,
    onTimeChange: (String, Int, Int) -> Unit,
) {
    val (titleY, titleAlpha) = rememberSlideEntry(isCurrentPage, 20f, 0L)
    val (subY,   subAlpha)   = rememberSlideEntry(isCurrentPage, 20f, 80L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            stringResource(R.string.welcome_p2_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { translationY = titleY; alpha = titleAlpha },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_p2_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { translationY = subY; alpha = subAlpha },
        )
        Spacer(Modifier.height(24.dp))

        listOf(
            Triple("wake",      stringResource(R.string.wake_time),      MedLogIcons.WbSunny),
            Triple("breakfast", stringResource(R.string.breakfast_time), MedLogIcons.BreakfastDining),
            Triple("lunch",     stringResource(R.string.lunch_time),     MedLogIcons.LunchDining),
            Triple("dinner",    stringResource(R.string.dinner_time),    MedLogIcons.DinnerDining),
            Triple("bed",       stringResource(R.string.bed_time),       MedLogIcons.Bedtime),
        ).forEachIndexed { index, (field, label, icon) ->
            val delay = 120L + index * 60L
            val (cardY, cardAlpha) = rememberSlideEntry(isCurrentPage, 24f, delay)
            Box(modifier = Modifier.graphicsLayer { translationY = cardY; alpha = cardAlpha }) {
                val (h, m) = when (field) {
                    "wake"      -> uiState.wakeHour      to uiState.wakeMinute
                    "breakfast" -> uiState.breakfastHour to uiState.breakfastMinute
                    "lunch"     -> uiState.lunchHour     to uiState.lunchMinute
                    "dinner"    -> uiState.dinnerHour    to uiState.dinnerMinute
                    else        -> uiState.bedHour       to uiState.bedMinute
                }
                RoutineTimeField(label, icon, h, m) { nh, nm -> onTimeChange(field, nh, nm) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineTimeField(
    label: String,
    icon: Int,
    hour: Int,
    minute: Int,
    onChanged: (Int, Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MedLogIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            MedLogIcon(MedLogIcons.Edit, contentDescription = stringResource(R.string.welcome_time_edit_cd), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text  = { TimeInput(state = timePickerState) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    onChanged(timePickerState.hour, timePickerState.minute)
                    showPicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

// ── 第4页：通知权限 ──────────────────────────────────────────────────────────
