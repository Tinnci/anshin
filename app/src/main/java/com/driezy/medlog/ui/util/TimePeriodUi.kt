package com.driezy.medlog.ui.util

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.annotation.StringRes
import com.driezy.medlog.R
import com.driezy.medlog.data.model.TimePeriod

/** TimePeriod 的展示用图标（Compose Material Icon） */
val TimePeriod.icon: Int
    get() = when (this) {
        TimePeriod.EXACT -> MedLogIcons.Schedule
        TimePeriod.MORNING -> MedLogIcons.WbSunny
        TimePeriod.AFTER_BREAKFAST -> MedLogIcons.Coffee
        TimePeriod.BEFORE_LUNCH -> MedLogIcons.LunchDining
        TimePeriod.AFTER_LUNCH -> MedLogIcons.LunchDining
        TimePeriod.BEFORE_DINNER -> MedLogIcons.DinnerDining
        TimePeriod.AFTER_DINNER -> MedLogIcons.DinnerDining
        TimePeriod.EVENING -> MedLogIcons.NightsStay
        TimePeriod.BEDTIME -> MedLogIcons.Bedtime
        TimePeriod.BEFORE_BREAKFAST -> MedLogIcons.Brightness5
        TimePeriod.AFTERNOON -> MedLogIcons.WbSunny
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
