package com.driezy.medlog.ui.screen.symptom

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.model.SymptomLog
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.symptom_screen_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        MedLogIcon(
                            MedLogIcons.Settings,
                            contentDescription = stringResource(R.string.settings_action_open),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::startAdd,
                icon = { MedLogIcon(MedLogIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.symptom_screen_fab_cd)) },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }
        } else if (uiState.logs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✏️", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.symptom_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.symptom_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
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

    // ── 新增 / 编辑底部弹窗 ──────────────────────────────────────────────────
    if (uiState.showDialog) {
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
}
