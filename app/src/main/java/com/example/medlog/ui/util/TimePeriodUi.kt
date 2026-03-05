package com.example.medlog.ui.util

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
import com.example.medlog.data.model.TimePeriod

/** TimePeriod 的展示用图标（Compose Material Icon） */
val TimePeriod.icon: ImageVector
    get() = when (this) {
        TimePeriod.EXACT -> Icons.Rounded.Schedule
        TimePeriod.MORNING -> Icons.Rounded.WbSunny
        TimePeriod.AFTER_BREAKFAST -> Icons.Rounded.Coffee
        TimePeriod.BEFORE_LUNCH -> Icons.Rounded.LunchDining
        TimePeriod.AFTER_LUNCH -> Icons.Rounded.LunchDining
        TimePeriod.BEFORE_DINNER -> Icons.Rounded.DinnerDining
        TimePeriod.AFTER_DINNER -> Icons.Rounded.DinnerDining
        TimePeriod.EVENING -> Icons.Rounded.NightsStay
        TimePeriod.BEDTIME -> Icons.Rounded.Bedtime
        TimePeriod.BEFORE_BREAKFAST -> Icons.Rounded.Brightness5
        TimePeriod.AFTERNOON -> Icons.Rounded.WbSunny
    }

/** TimePeriod 的本地化标签字符串资源 ID */
val TimePeriod.labelRes: Int
    @StringRes get() = when (this) {
        TimePeriod.EXACT -> R.string.time_period_exact
        TimePeriod.MORNING -> R.string.time_period_morning
        TimePeriod.AFTER_BREAKFAST -> R.string.time_period_after_breakfast
        TimePeriod.BEFORE_LUNCH -> R.string.time_period_before_lunch
        TimePeriod.AFTER_LUNCH -> R.string.time_period_after_lunch
        TimePeriod.BEFORE_DINNER -> R.string.time_period_before_dinner
        TimePeriod.AFTER_DINNER -> R.string.time_period_after_dinner
        TimePeriod.EVENING -> R.string.time_period_evening
        TimePeriod.BEDTIME -> R.string.time_period_bedtime
        TimePeriod.BEFORE_BREAKFAST -> R.string.time_period_before_breakfast
        TimePeriod.AFTERNOON -> R.string.time_period_afternoon
    }
