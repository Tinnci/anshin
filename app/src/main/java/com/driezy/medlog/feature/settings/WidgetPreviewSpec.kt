package com.driezy.medlog.feature.settings

internal enum class WidgetPreviewType {
    TODAY,
    NEXT_DOSE,
    STREAK,
}

internal data class WidgetPreviewSpec(
    val type: WidgetPreviewType,
    val primaryText: String,
    val progress: Float? = null,
    val minutesUntilNext: Int? = null,
    val showActionButton: Boolean = false,
    val completedDays: List<Boolean> = emptyList(),
) {
    companion object {
        fun forType(type: WidgetPreviewType, showActions: Boolean): WidgetPreviewSpec = when (type) {
            WidgetPreviewType.TODAY -> WidgetPreviewSpec(
                type = type,
                primaryText = "2 / 4",
                progress = 0.5f,
                showActionButton = showActions,
            )
            WidgetPreviewType.NEXT_DOSE -> WidgetPreviewSpec(
                type = type,
                primaryText = "45 min",
                minutesUntilNext = 45,
                showActionButton = showActions,
            )
            WidgetPreviewType.STREAK -> WidgetPreviewSpec(
                type = type,
                primaryText = "7",
                completedDays = listOf(true, true, true, true, true, true, true),
            )
        }
    }
}
