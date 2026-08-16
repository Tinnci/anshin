package com.driezy.medlog.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberMedLogHaptics(): (MedLogHapticEffect) -> Unit {
    val view = LocalView.current
    return remember(view) { { effect -> view.performMedLogHaptic(effect) } }
}
