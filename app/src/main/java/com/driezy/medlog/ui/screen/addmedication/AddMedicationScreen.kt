package com.driezy.medlog.ui.screen.addmedication

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.ScreenOverlay
import com.driezy.medlog.ui.components.ScreenOverlayHost
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.util.icon
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
    var overlay by remember { mutableStateOf<ScreenOverlay?>(null) }

    val formOptions = listOf(
        FormOption("tablet", stringResource(R.string.add_form_tablet), MedLogIcons.Medication),
        FormOption("capsule", stringResource(R.string.add_form_capsule), MedLogIcons.Science),
        FormOption("liquid", stringResource(R.string.add_form_liquid), MedLogIcons.LocalDrink),
        FormOption("powder", stringResource(R.string.add_form_powder), MedLogIcons.WaterDrop),
        FormOption("patch", stringResource(R.string.add_form_patch), MedLogIcons.Healing),
        FormOption("other", stringResource(R.string.add_form_other), MedLogIcons.MoreHoriz),
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
    val customDoseTitle = stringResource(R.string.add_dose_custom_dialog_title)
    val customDoseLabel = stringResource(R.string.add_dose_custom_dialog_hint)
    val confirmLabel = stringResource(R.string.confirm)
    val cancelLabel = stringResource(R.string.cancel)
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

    MedLogScreenScaffold(
        title = {
            Text(
                if (medicationId ==
                    null
                ) {
                    stringResource(R.string.add_title_new)
                } else {
                    stringResource(R.string.add_title_edit)
                },
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                MedLogIcon(MedLogIcons.ArrowBack, contentDescription = stringResource(R.string.add_back_cd))
            }
        },
        actions = listOf(
            TopBarAction(
                id = "save",
                label = stringResource(R.string.add_save),
                icon = MedLogIcons.Check,
                priority = TopBarActionPriority.Primary,
                enabled = !uiState.isSaving,
                onClick = { viewModel.save(medicationId) },
            ),
        ),
    ) { paddingValues ->
        AddMedicationFormContent(
            paddingValues = paddingValues,
            uiState = uiState,
            enableTimePeriodMode = enableTimePeriodMode,
            formOptions = formOptions,
            doseUnits = doseUnits,
            viewModel = viewModel,
            onOpenOcrScanner = {
                overlay = ScreenOverlay.FullScreen(id = "add:ocr") {
                    com.driezy.medlog.ui.ocr.OcrScannerPage(
                        onResult = { text ->
                            overlay = null
                            viewModel.onNameChange(text)
                        },
                        onBack = { overlay = null },
                    )
                }
            },
            onEditCustomDose = { doseText ->
                overlay = ScreenOverlay.TextInput(
                    id = "add:custom-dose",
                    title = customDoseTitle,
                    label = customDoseLabel,
                    confirmLabel = confirmLabel,
                    dismissLabel = cancelLabel,
                    initialValue = doseText,
                    keyboardType = KeyboardType.Decimal,
                    onConfirm = { text ->
                        text.toDoubleOrNull()?.let { v ->
                            if (v > 0.0) viewModel.onDoseQuantityChange(v.coerceIn(0.125, 99.0))
                        }
                    },
                )
            },
        )
    }
    ScreenOverlayHost(overlay = overlay, onDismiss = { overlay = null })
}
