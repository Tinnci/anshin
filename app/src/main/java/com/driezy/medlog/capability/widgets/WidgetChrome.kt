package com.driezy.medlog.capability.widgets

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

@Composable
internal fun WidgetContainer(
    prominent: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    sizing: WidgetSizing = WidgetSizing(densityFactor = 1f, textFactor = 1f),
    verticalAlignment: Alignment.Vertical = Alignment.Vertical.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Horizontal.Start,
    content: @Composable () -> Unit,
) {
    val outerRadius = systemWidgetCornerRadius()
    Column(
        modifier = modifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(
                if (prominent) {
                    GlanceTheme.colors.tertiaryContainer
                } else {
                    GlanceTheme.colors.widgetBackground
                },
            )
            .cornerRadius(outerRadius)
            .padding(sizing.dp(14)),
        verticalAlignment = verticalAlignment,
        horizontalAlignment = horizontalAlignment,
    ) {
        content()
    }
}

@Composable
internal fun WidgetHeader(
    @DrawableRes icon: Int,
    title: String,
    trailing: String,
    prominent: Boolean = false,
    sizing: WidgetSizing = WidgetSizing(densityFactor = 1f, textFactor = 1f),
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        WidgetIconBadge(
            icon = icon,
            prominent = prominent,
            size = sizing.dp(28),
            iconSize = sizing.dp(16),
        )
        Spacer(GlanceModifier.size(sizing.dp(8)))
        Text(
            title,
            style = TextStyle(
                fontSize = sizing.sp(12),
                fontWeight = FontWeight.Medium,
                color = if (prominent) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            trailing,
            style = TextStyle(
                fontSize = sizing.sp(13),
                fontWeight = FontWeight.Bold,
                color = if (prominent) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
            ),
        )
    }
}

@Composable
internal fun WidgetIconBadge(
    @DrawableRes icon: Int,
    prominent: Boolean = false,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(if (prominent) GlanceTheme.colors.tertiary else GlanceTheme.colors.primaryContainer)
            .cornerRadius(size / 2),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(
                if (prominent) GlanceTheme.colors.onTertiary else GlanceTheme.colors.primary,
            ),
        )
    }
}

@Composable
private fun systemWidgetCornerRadius(): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 28.dp
    val resources = LocalContext.current.resources
    val radiusPx = resources.getDimension(android.R.dimen.system_app_widget_background_radius)
    return (radiusPx / resources.displayMetrics.density).dp
}

@Composable
internal fun WidgetActionButton(
    label: String,
    action: Action,
    modifier: GlanceModifier? = null,
    sizing: WidgetSizing = WidgetSizing(densityFactor = 1f, textFactor = 1f),
) {
    Box(
        modifier = (modifier ?: GlanceModifier.size(sizing.dp(48)))
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(sizing.dp(16))
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = sizing.sp(12),
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onPrimaryContainer,
            ),
        )
    }
}

@Composable
internal fun WidgetEmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String? = null,
    compact: Boolean = false,
    sizing: WidgetSizing = WidgetSizing(densityFactor = 1f, textFactor = 1f),
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetIconBadge(
            icon = icon,
            size = if (compact) sizing.dp(36) else sizing.dp(40),
            iconSize = if (compact) sizing.dp(20) else sizing.dp(22),
        )
        Spacer(GlanceModifier.height(if (compact) sizing.dp(6) else sizing.dp(10)))
        Text(
            title,
            style = TextStyle(
                fontSize = if (compact) sizing.sp(11) else sizing.sp(12),
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurface,
            ),
        )
        if (body != null && !compact) {
            Spacer(GlanceModifier.height(sizing.dp(3)))
            Text(
                body,
                style = TextStyle(
                    fontSize = sizing.sp(11),
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.primary,
                ),
            )
        }
    }
}
