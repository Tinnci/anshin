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
import com.driezy.medlog.ui.components.messageText
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.voice.VoiceInputPhase
import com.driezy.medlog.voice.VoiceInputUiState
import java.text.SimpleDateFormat
import java.util.*


@Composable
internal fun SymptomLogCard(
    log: SymptomLog,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(MedLogSpacing.Large)) {
            // 头部：日期 + 评级 + 操作按钮
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateFormat.format(Date(log.recordedAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${ratingEmoji(log.overallRating)} ${ratingLabel(log.overallRating)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onEdit) {
                    MedLogIcon(
                        MedLogIcons.Edit,
                        contentDescription = stringResource(R.string.common_action_edit),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                ) {
                    MedLogIcon(
                        MedLogIcons.Delete,
                        contentDescription = stringResource(R.string.common_action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 关联药品
            if (log.medicationName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "💊 ${log.medicationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 症状 Chips
            if (log.symptomList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.symptom_card_symptoms_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    log.symptomList.forEach { s ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // 副作用 Chips
            if (log.sideEffectList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.symptom_card_sideeff_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    log.sideEffectList.forEach { se ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(se, style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        )
                    }
                }
            }

            // 备注
            if (log.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    log.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.symptom_delete_title)) },
            text = { Text(stringResource(R.string.symptom_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.common_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_action_cancel)) }
            },
        )
    }
}

// ─── 新增 / 编辑 ModalBottomSheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEditDiarySheet(
    draft: DiaryDraftState,
    onDismiss: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onCustomSymptomChange: (String) -> Unit,
    onAddCustomSymptom: () -> Unit,
    onToggleSideEffect: (String) -> Unit,
    onCustomSideEffectChange: (String) -> Unit,
    onAddCustomSideEffect: () -> Unit,
    onNoteChange: (String) -> Unit,
    voiceInput: VoiceInputUiState,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val context = LocalContext.current
    var showVoicePrivacy by remember { mutableStateOf(false) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onStartVoiceInput()
    }

    fun startVoiceInputWithPrivacy() {
        showVoicePrivacy = true
    }

    fun confirmVoicePrivacy() {
        showVoicePrivacy = false
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            onStartVoiceInput()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (draft.editingId == null) stringResource(R.string.symptom_dialog_add_title) else stringResource(R.string.symptom_dialog_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.symptom_dialog_supporting_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── 评级选择 ──────────────────────────────────────────────────
            Text(stringResource(R.string.symptom_rating_label), style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                (1..5).forEach { r ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        FilledIconToggleButton(
                            checked = draft.rating == r,
                            onCheckedChange = { onRatingChange(r) },
                        ) {
                            Text(ratingEmoji(r), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            ratingLabel(r),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (draft.rating == r)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── 症状快选 ──────────────────────────────────────────────────
            Text(stringResource(R.string.symptom_section_symptoms), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.preset_symptoms).forEach { s ->
                    FilterChip(
                        selected = s in draft.symptoms,
                        onClick = { onToggleSymptom(s) },
                        label = { Text(s) },
                    )
                }
            }
            // 自定义症状输入
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.customSymptom,
                    onValueChange = onCustomSymptomChange,
                    label = { Text(stringResource(R.string.symptom_custom_input_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onAddCustomSymptom,
                    enabled = draft.customSymptom.isNotBlank(),
                ) { Text(stringResource(R.string.common_action_add)) }
            }

            HorizontalDivider()

            // ── 副作用快选 ─────────────────────────────────────────────────
            Text(stringResource(R.string.symptom_section_side_effects), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.preset_side_effects).forEach { se ->
                    FilterChip(
                        selected = se in draft.sideEffects,
                        onClick = { onToggleSideEffect(se) },
                        label = { Text(se) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }
            // 自定义副作用输入
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.customSideEffect,
                    onValueChange = onCustomSideEffectChange,
                    label = { Text(stringResource(R.string.symptom_custom_se_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onAddCustomSideEffect,
                    enabled = draft.customSideEffect.isNotBlank(),
                ) { Text(stringResource(R.string.common_action_add)) }
            }

            HorizontalDivider()

            // ── 备注 ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.common_notes_hint)) },
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    val isVoiceInputActive = voiceInput.phase == VoiceInputPhase.LISTENING ||
                        voiceInput.phase == VoiceInputPhase.CONNECTING
                    IconButton(
                        onClick = {
                            if (isVoiceInputActive) {
                                onStopVoiceInput()
                            } else {
                                startVoiceInputWithPrivacy()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isVoiceInputActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                    ) {
                        MedLogIcon(
                            MedLogIcons.Mic,
                            contentDescription = stringResource(
                                if (isVoiceInputActive) {
                                    R.string.voice_input_stop_cd
                                } else {
                                    R.string.voice_input_start_cd
                                },
                            ),
                        )
                    }
                },
                supportingText = {
                    val message = voiceInput.messageText()
                    if (message != null) {
                        Text(message)
                    }
                },
                isError = voiceInput.phase == VoiceInputPhase.ERROR,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 操作按钮 ─────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_action_cancel)) }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(2f),
                ) { Text(stringResource(R.string.symptom_save_btn)) }
            }
        }
    }

    if (showVoicePrivacy) {
        AlertDialog(
            onDismissRequest = { showVoicePrivacy = false },
            title = { Text(stringResource(R.string.voice_input_privacy_title)) },
            text = { Text(stringResource(R.string.voice_input_privacy_body)) },
            confirmButton = {
                Button(onClick = ::confirmVoicePrivacy) {
                    Text(stringResource(R.string.voice_input_privacy_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoicePrivacy = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }
}
