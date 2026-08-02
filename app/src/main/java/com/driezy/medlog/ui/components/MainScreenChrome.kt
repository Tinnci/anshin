package com.driezy.medlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

sealed interface ScreenOverlay {
    val id: String

    data class Confirm(
        override val id: String,
        val title: String,
        val body: String,
        val confirmLabel: String,
        val dismissLabel: String,
        val targetKey: String? = null,
        val isDanger: Boolean = false,
    ) : ScreenOverlay

    data class TextInput(
        override val id: String,
        val title: String,
        val label: String,
        val confirmLabel: String,
        val dismissLabel: String,
        val initialValue: String = "",
        val keyboardType: KeyboardType = KeyboardType.Text,
    ) : ScreenOverlay

    class FullScreen(
        override val id: String,
        val dismissOnClickOutside: Boolean = false,
        val content: @Composable () -> Unit,
    ) : ScreenOverlay

    class BottomSheet(override val id: String, val content: @Composable () -> Unit) : ScreenOverlay

    class Custom(override val id: String, val content: @Composable () -> Unit) : ScreenOverlay
}

enum class ScreenTopBarSize {
    Compact,
    Large,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedLogScreenScaffold(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    topBarSize: ScreenTopBarSize = ScreenTopBarSize.Large,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: List<TopBarAction> = emptyList(),
    chromeState: ScreenChromeState = ScreenChromeState(),
    snackbarHostState: SnackbarHostState? = null,
    onChromeAction: (String) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = when (topBarSize) {
        ScreenTopBarSize.Compact -> TopAppBarDefaults.pinnedScrollBehavior()
        ScreenTopBarSize.Large -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val resolvedSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (showTopBar) {
                when (topBarSize) {
                    ScreenTopBarSize.Compact -> TopAppBar(
                        title = title,
                        navigationIcon = { navigationIcon?.invoke() },
                        actions = { PriorityTopBarActions(actions = actions, onAction = onChromeAction) },
                        scrollBehavior = scrollBehavior,
                    )
                    ScreenTopBarSize.Large -> LargeTopAppBar(
                        title = title,
                        navigationIcon = { navigationIcon?.invoke() },
                        actions = { PriorityTopBarActions(actions = actions, onAction = onChromeAction) },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(resolvedSnackbarHostState) },
        floatingActionButton = {
            chromeState.fab?.let { fab ->
                ExtendedFloatingActionButton(
                    onClick = { onChromeAction(fab.id) },
                    icon = { MedLogIcon(fab.icon, contentDescription = null) },
                    text = { Text(fab.label) },
                    expanded = true,
                )
            }
        },
    ) { innerPadding ->
        when {
            chromeState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
            chromeState.emptyState != null -> {
                val emptyState = requireNotNull(chromeState.emptyState)
                ScreenEmptyStateContent(
                    state = emptyState,
                    onAction = onChromeAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            else -> content(innerPadding)
        }
    }
}

@Composable
fun PriorityTopBarActions(
    actions: List<TopBarAction>,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier) {
        val widthClass = if (maxWidth < 600.dp) MainScreenWidthClass.Compact else MainScreenWidthClass.Expanded
        val placement = placeTopBarActions(actions, widthClass)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            placement.visible.forEach { action ->
                IconButton(
                    onClick = { onAction(action.id) },
                    enabled = action.enabled,
                ) {
                    MedLogIcon(action.icon, contentDescription = action.label)
                }
            }
            if (placement.overflow.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        MedLogIcon(
                            MedLogIcons.MoreVert,
                            contentDescription = stringResource(R.string.common_more_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        placement.overflow.forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = action.label,
                                        color = if (action.priority == TopBarActionPriority.Danger) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                leadingIcon = {
                                    MedLogIcon(
                                        action.icon,
                                        contentDescription = null,
                                        tint = if (action.priority == TopBarActionPriority.Danger) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                enabled = action.enabled,
                                onClick = {
                                    expanded = false
                                    onAction(action.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenOverlayHost(
    overlay: ScreenOverlay?,
    onDismiss: () -> Unit,
    onConfirm: (ScreenOverlay, String?) -> Unit = { _, _ -> },
) {
    when (overlay) {
        null -> Unit
        is ScreenOverlay.Confirm -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(overlay.title) },
                text = { Text(overlay.body) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onConfirm(overlay, null)
                            onDismiss()
                        },
                        colors = if (overlay.isDanger) {
                            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                    ) {
                        Text(overlay.confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(overlay.dismissLabel) }
                },
            )
        }
        is ScreenOverlay.TextInput -> {
            var value by remember(overlay.id) { mutableStateOf(overlay.initialValue) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(overlay.title) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(overlay.label) },
                        keyboardOptions = KeyboardOptions(keyboardType = overlay.keyboardType),
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onConfirm(overlay, value)
                            onDismiss()
                        },
                    ) {
                        Text(overlay.confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(overlay.dismissLabel) }
                },
            )
        }
        is ScreenOverlay.FullScreen -> {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = overlay.dismissOnClickOutside,
                ),
            ) {
                overlay.content()
            }
        }
        is ScreenOverlay.BottomSheet -> {
            ModalBottomSheet(onDismissRequest = onDismiss) {
                overlay.content()
            }
        }
        is ScreenOverlay.Custom -> overlay.content()
    }
}

@Composable
private fun ScreenEmptyStateContent(
    state: ScreenEmptyState,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = MedLogSpacing.Large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            state.icon?.let { icon ->
                MedLogIcon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.body?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val actionLabel = state.actionLabel
            val actionId = state.actionId
            if (actionLabel != null && actionId != null) {
                Spacer(Modifier.size(MedLogSpacing.Small))
                Button(onClick = { onAction(actionId) }) {
                    Text(actionLabel)
                }
            }
        }
    }
}
