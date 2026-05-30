package com.driezy.medlog.ui.screen.addmedication

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.driezy.medlog.ui.theme.emphasizedTypography
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.ui.util.icon
import com.driezy.medlog.ui.util.labelRes
import com.driezy.medlog.ui.util.formatDosePrecise
import java.text.SimpleDateFormat
import java.util.*

internal data class FormOption(val key: String, val label: String, val icon: Int)

// FORM_OPTIONS and DOSE_UNITS moved inside AddMedicationScreen composable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddMedicationScreen(
    medicationId: Long?,
    drugName: String? = null,
    drugCategory: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddMedicationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val enableTimePeriodMode by viewModel.enableTimePeriodMode.collectAsStateWithLifecycle()
    var showCustomDoseDialog by remember { mutableStateOf(false) }
    var customDoseText     by remember { mutableStateOf("") }
    var showOcrScanner by remember { mutableStateOf(false) }

    val formOptions = listOf(
        FormOption("tablet",  stringResource(R.string.add_form_tablet), MedLogIcons.Medication),
        FormOption("capsule", stringResource(R.string.add_form_capsule), MedLogIcons.Science),
        FormOption("liquid",  stringResource(R.string.add_form_liquid), MedLogIcons.LocalDrink),
        FormOption("powder",  stringResource(R.string.add_form_powder), MedLogIcons.WaterDrop),
        FormOption("patch",   stringResource(R.string.add_form_patch), MedLogIcons.Healing),
        FormOption("other",   stringResource(R.string.add_form_other), MedLogIcons.MoreHoriz),
    )
    val doseUnits = listOf(
        stringResource(R.string.add_unit_tablet),
        stringResource(R.string.add_unit_capsule),
        "ml",
        "mg",
        stringResource(R.string.add_unit_drop),
        stringResource(R.string.add_unit_bag),
        stringResource(R.string.add_unit_tube),
        stringResource(R.string.add_unit_patch_unit),
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(medicationId) {
        if (medicationId != null) viewModel.loadExisting(medicationId)
    }
    LaunchedEffect(drugName) {
        if (!drugName.isNullOrEmpty() && medicationId == null) {
            viewModel.prefillFromDrug(drugName, drugCategory.orEmpty())
        }
    }
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (medicationId == null) stringResource(R.string.add_title_new) else stringResource(R.string.add_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MedLogIcon(MedLogIcons.ArrowBack, contentDescription = stringResource(R.string.add_back_cd))
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.save(medicationId) },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.padding(end = MedLogSpacing.Medium),
                    ) {
                        if (uiState.isSaving) {
                            LoadingIndicator(
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(MedLogSpacing.Small))
                        }
                        Text(stringResource(R.string.add_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        AddMedicationFormContent(
            paddingValues = paddingValues,
            uiState = uiState,
            enableTimePeriodMode = enableTimePeriodMode,
            formOptions = formOptions,
            doseUnits = doseUnits,
            viewModel = viewModel,
            onOpenOcrScanner = { showOcrScanner = true },
            onEditCustomDose = { doseText ->
                customDoseText = doseText
                showCustomDoseDialog = true
            },
        )
    }

    // ── 自定义剂量输入对话框 ─────────────────────────────────────────────
    if (showCustomDoseDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDoseDialog = false },
            title = { Text(stringResource(R.string.add_dose_custom_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = customDoseText,
                    onValueChange = { customDoseText = it },
                    label = { Text(stringResource(R.string.add_dose_custom_dialog_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customDoseText.toDoubleOrNull()?.let { v ->
                        if (v > 0.0) viewModel.onDoseQuantityChange(v.coerceIn(0.125, 99.0))
                    }
                    showCustomDoseDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDoseDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // ── OCR 扫描器全屏覆盖层 ─────────────────────────────────────────────────
    if (showOcrScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOcrScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
            ),
        ) {
            com.driezy.medlog.ui.ocr.OcrScannerPage(
                onResult = { text ->
                    showOcrScanner = false
                    viewModel.onNameChange(text)
                },
                onBack = { showOcrScanner = false },
            )
        }
    }
}
