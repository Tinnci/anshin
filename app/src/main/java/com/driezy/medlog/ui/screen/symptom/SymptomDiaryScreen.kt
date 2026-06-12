package com.driezy.medlog.ui.screen.symptom

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.model.SymptomLog
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.ScreenChromeState
import com.driezy.medlog.ui.components.ScreenFab
import com.driezy.medlog.ui.components.ScreenOverlay
import com.driezy.medlog.ui.components.ScreenOverlayHost
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.voice.VoiceInputError
import com.driezy.medlog.voice.VoiceInputPhase
import com.driezy.medlog.voice.VoiceInputUiState
import java.text.SimpleDateFormat
import java.util.*

// ─── 评级 Emoji 映射 ─────────────────────────────────────────────────────────

internal fun ratingEmoji(rating: Int) = when (rating) {
    1 -> "😞"
    2 -> "😟"
    3 -> "😐"
    4 -> "🙂"
    else -> "😊"
}

@Composable
internal fun ratingLabel(rating: Int): String = when (rating) {
    1 -> stringResource(R.string.symptom_rating_1)
    2 -> stringResource(R.string.symptom_rating_2)
    3 -> stringResource(R.string.symptom_rating_3)
    4 -> stringResource(R.string.symptom_rating_4)
    else -> stringResource(R.string.symptom_rating_5)
}

// ─── Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SymptomDiaryScreen(
    onOpenSettings: () -> Unit,
    viewModel: SymptomDiaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    MedLogScreenScaffold(
        title = { Text(stringResource(R.string.symptom_screen_title)) },
        actions = listOf(
            TopBarAction(
                id = "settings",
                label = stringResource(R.string.settings_action_open),
                icon = MedLogIcons.Settings,
                priority = TopBarActionPriority.Secondary,
                onClick = onOpenSettings,
            ),
        ),
        chromeState = ScreenChromeState(
            isLoading = uiState.isLoading,
            fab = ScreenFab(
                label = stringResource(R.string.symptom_screen_fab_cd),
                icon = MedLogIcons.Add,
                onClick = viewModel::startAdd,
            ),
        ),
    ) { innerPadding ->
        if (uiState.logs.isEmpty()) {
            SymptomEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onCreate = viewModel::startAdd,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MedLogSpacing.Large,
                    end = MedLogSpacing.Large,
                    top = innerPadding.calculateTopPadding() + MedLogSpacing.Small,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            ) {
                items(uiState.logs, key = { it.id }) { log ->
                    SymptomLogCard(
                        log = log,
                        dateFormat = dateFormat,
                        onEdit = { viewModel.startEdit(log) },
                        onDelete = { viewModel.deleteLog(log.id) },
                    )
                }
            }
        }
    }

    val stateOverlay = if (uiState.showDialog) {
        ScreenOverlay.Custom(id = "diary:edit-sheet") {
            AddEditDiarySheet(
            draft = uiState.draft,
            onDismiss = viewModel::dismissDialog,
            onRatingChange = viewModel::onRatingChange,
            onToggleSymptom = viewModel::onToggleSymptom,
            onCustomSymptomChange = viewModel::onCustomSymptomChange,
            onAddCustomSymptom = viewModel::onAddCustomSymptom,
            onToggleSideEffect = viewModel::onToggleSideEffect,
            onCustomSideEffectChange = viewModel::onCustomSideEffectChange,
            onAddCustomSideEffect = viewModel::onAddCustomSideEffect,
            onNoteChange = viewModel::onNoteChange,
            voiceInput = uiState.voiceInput,
            onStartVoiceInput = viewModel::startVoiceInput,
            onStopVoiceInput = viewModel::stopVoiceInput,
            onSave = viewModel::saveLog,
        )
        }
    } else {
        null
    }
    ScreenOverlayHost(
        overlay = stateOverlay,
        onDismiss = viewModel::dismissDialog,
    )
}

@Composable
private fun SymptomEmptyState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = MedLogSpacing.Large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(24.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MedLogIcon(
                    MedLogIcons.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.symptom_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.symptom_empty_body),
                modifier = Modifier.widthIn(max = 300.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onCreate) {
                MedLogIcon(MedLogIcons.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.symptom_empty_action))
            }
        }
    }
}
