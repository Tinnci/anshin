package com.driezy.medlog.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.voice.VoiceInputError
import com.driezy.medlog.voice.VoiceInputPhase
import com.driezy.medlog.voice.VoiceInputUiState

@Composable
internal fun VoiceInputTrailingIcon(
    voiceInput: VoiceInputUiState,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
) {
    val context = LocalContext.current
    var showVoicePrivacy by remember { mutableStateOf(false) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onStartVoiceInput()
    }
    val isVoiceInputActive = voiceInput.phase == VoiceInputPhase.LISTENING ||
        voiceInput.phase == VoiceInputPhase.CONNECTING

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

    IconButton(
        onClick = {
            if (isVoiceInputActive) {
                onStopVoiceInput()
            } else {
                showVoicePrivacy = true
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

@Composable
internal fun VoiceInputUiState.messageText(): String? = when (phase) {
    VoiceInputPhase.IDLE -> null
    VoiceInputPhase.CONNECTING -> stringResource(R.string.voice_input_status_connecting)
    VoiceInputPhase.LISTENING -> stringResource(R.string.voice_input_status_listening)
    VoiceInputPhase.ERROR -> {
        val baseMessage = when (error) {
            VoiceInputError.MISSING_PERMISSION -> stringResource(R.string.voice_input_error_permission)
            VoiceInputError.NETWORK_UNAVAILABLE -> stringResource(R.string.voice_input_error_network)
            VoiceInputError.DEVICE_REGISTRATION_FAILED -> stringResource(R.string.voice_input_error_registration)
            VoiceInputError.TOKEN_UNAVAILABLE -> stringResource(R.string.voice_input_error_token)
            VoiceInputError.WEBSOCKET_FAILED -> stringResource(R.string.voice_input_error_websocket)
            VoiceInputError.ENCODER_UNAVAILABLE -> stringResource(R.string.voice_input_error_encoder)
            VoiceInputError.RECORDER_UNAVAILABLE -> stringResource(R.string.voice_input_error_recorder)
            VoiceInputError.PROTOCOL_FAILED -> stringResource(R.string.voice_input_error_protocol)
            VoiceInputError.UNKNOWN, null -> stringResource(R.string.voice_input_error_unknown)
        }
        if (detail.isNotBlank()) "$baseMessage\n$detail" else baseMessage
    }
}
