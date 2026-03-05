package com.example.medlog.data.model

/**
 * 服药时段，对应用户作息的相对时间。
 *
 * 纯数据层 enum，不依赖 Android 框架。
 * UI 相关属性（icon / labelRes）请使用 `ui.util.TimePeriodUi` 扩展。
 */
enum class TimePeriod(val key: String) {
    EXACT("exact"),
    MORNING("morning"),
    AFTER_BREAKFAST("afterBreakfast"),
    BEFORE_LUNCH("beforeLunch"),
    AFTER_LUNCH("afterLunch"),
    BEFORE_DINNER("beforeDinner"),
    AFTER_DINNER("afterDinner"),
    EVENING("evening"),
    BEDTIME("bedtime"),
    BEFORE_BREAKFAST("beforeBreakfast"),
    AFTERNOON("afternoon"),
    ;

    companion object {
        fun fromKey(key: String): TimePeriod = entries.find { it.key == key } ?: EXACT
    }
}
