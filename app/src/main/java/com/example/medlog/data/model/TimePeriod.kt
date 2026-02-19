package com.example.medlog.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brightness5
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** 服药时段，对应用户作息的相对时间 */
enum class TimePeriod(
    val key: String,
    val label: String,
    val icon: ImageVector = Icons.Rounded.Schedule,
) {
    EXACT("exact", "精确时间", Icons.Rounded.Schedule),
    MORNING("morning", "早晨", Icons.Rounded.WbSunny),
    AFTER_BREAKFAST("afterBreakfast", "早餐后", Icons.Rounded.Coffee),
    BEFORE_LUNCH("beforeLunch", "午餐前", Icons.Rounded.LunchDining),
    AFTER_LUNCH("afterLunch", "午餐后", Icons.Rounded.LunchDining),
    BEFORE_DINNER("beforeDinner", "晚餐前", Icons.Rounded.DinnerDining),
    AFTER_DINNER("afterDinner", "晚餐后", Icons.Rounded.DinnerDining),
    EVENING("evening", "晚间", Icons.Rounded.NightsStay),
    BEDTIME("bedtime", "睡前", Icons.Rounded.Bedtime),
    BEFORE_BREAKFAST("beforeBreakfast", "早餐前", Icons.Rounded.Brightness5),
    AFTERNOON("afternoon", "下午", Icons.Rounded.WbSunny),
    ;

    companion object {
        fun fromKey(key: String): TimePeriod = entries.find { it.key == key } ?: EXACT
    }
}
