package com.example.medlog.data.model

import androidx.annotation.StringRes
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
import com.example.medlog.R

/** 服药时段，对应用户作息的相对时间 */
enum class TimePeriod(
    val key: String,
    @get:StringRes val labelRes: Int,
    val icon: ImageVector = Icons.Rounded.Schedule,
) {
    EXACT("exact", R.string.time_period_exact, Icons.Rounded.Schedule),
    MORNING("morning", R.string.time_period_morning, Icons.Rounded.WbSunny),
    AFTER_BREAKFAST("afterBreakfast", R.string.time_period_after_breakfast, Icons.Rounded.Coffee),
    BEFORE_LUNCH("beforeLunch", R.string.time_period_before_lunch, Icons.Rounded.LunchDining),
    AFTER_LUNCH("afterLunch", R.string.time_period_after_lunch, Icons.Rounded.LunchDining),
    BEFORE_DINNER("beforeDinner", R.string.time_period_before_dinner, Icons.Rounded.DinnerDining),
    AFTER_DINNER("afterDinner", R.string.time_period_after_dinner, Icons.Rounded.DinnerDining),
    EVENING("evening", R.string.time_period_evening, Icons.Rounded.NightsStay),
    BEDTIME("bedtime", R.string.time_period_bedtime, Icons.Rounded.Bedtime),
    BEFORE_BREAKFAST("beforeBreakfast", R.string.time_period_before_breakfast, Icons.Rounded.Brightness5),
    AFTERNOON("afternoon", R.string.time_period_afternoon, Icons.Rounded.WbSunny),
    ;

    companion object {
        fun fromKey(key: String): TimePeriod = entries.find { it.key == key } ?: EXACT
    }
}
