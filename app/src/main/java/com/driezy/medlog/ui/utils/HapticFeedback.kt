package com.driezy.medlog.ui.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

enum class MedLogHapticEffect {
    CONFIRM,
    REJECT,
    TOGGLE,
    SEGMENT_TICK,
}

/**
 * 统一的应用内触觉入口。
 *
 * 使用 action-oriented constants，而不是 legacy one-shot/waveform vibration。
 * 平台会在低端设备或用户关闭触觉时提供合适的降级。
 */
fun View.performMedLogHaptic(effect: MedLogHapticEffect) {
    val constant = when (effect) {
        MedLogHapticEffect.CONFIRM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
        }
        MedLogHapticEffect.REJECT -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
        }
        MedLogHapticEffect.TOGGLE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        }
        MedLogHapticEffect.SEGMENT_TICK -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        }
    }
    performHapticFeedback(constant)
}

fun View.performConfirmHapticFeedback() = performMedLogHaptic(MedLogHapticEffect.CONFIRM)

fun View.performRejectHapticFeedback() = performMedLogHaptic(MedLogHapticEffect.REJECT)
