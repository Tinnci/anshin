package com.example.medlog.ui.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

fun View.performConfirmHapticFeedback() {
    val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(feedbackConstant)
}
