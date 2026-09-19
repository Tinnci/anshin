package com.driezy.medlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Re-evaluate date-dependent state on resume and as the clock advances, without background polling. */
@Composable
fun RefreshWhileVisible(onRefresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(60_000)
            }
        }
    }
}
