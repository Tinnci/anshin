package com.driezy.medlog.ui.components

enum class MainScreenWidthClass { Compact, Expanded }

enum class TopBarActionPriority { Primary, Secondary, Danger }

data class TopBarAction(
    val id: String,
    val label: String,
    val icon: Int,
    val priority: TopBarActionPriority,
    val enabled: Boolean = true,
)

data class TopBarActionPlacement(val visible: List<TopBarAction>, val overflow: List<TopBarAction>)

fun placeTopBarActions(actions: List<TopBarAction>, widthClass: MainScreenWidthClass): TopBarActionPlacement {
    val visible = actions.filter { action ->
        action.priority == TopBarActionPriority.Primary ||
            (widthClass == MainScreenWidthClass.Expanded && action.priority == TopBarActionPriority.Secondary)
    }
    val overflow = actions.filter { action ->
        action.priority == TopBarActionPriority.Danger ||
            (widthClass == MainScreenWidthClass.Compact && action.priority == TopBarActionPriority.Secondary)
    }
    return TopBarActionPlacement(visible = visible, overflow = overflow)
}

data class ScreenFab(val id: String, val label: String, val icon: Int, val enabled: Boolean = true)

data class ScreenEmptyState(
    val title: String,
    val body: String? = null,
    val icon: Int? = null,
    val actionLabel: String? = null,
    val actionId: String? = null,
)

enum class ScreenContentPaddingMode { Default, WithFab, None }

data class ScreenChromeState(
    val isLoading: Boolean = false,
    val emptyState: ScreenEmptyState? = null,
    val fab: ScreenFab? = null,
    val contentPaddingMode: ScreenContentPaddingMode = ScreenContentPaddingMode.Default,
)
