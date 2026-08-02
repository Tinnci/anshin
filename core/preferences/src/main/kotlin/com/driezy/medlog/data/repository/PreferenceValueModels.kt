package com.driezy.medlog.data.repository

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FontMode {
    SYSTEM,
    ANSHIN,
    ;

    companion object {
        fun fromStoredName(name: String?): FontMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class AppTextScale(val factor: Float) {
    SMALL(0.90f),
    STANDARD(1.00f),
    LARGE(1.15f),
    EXTRA_LARGE(1.30f),
    ;

    companion object {
        fun fromStoredName(name: String?): AppTextScale = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class UiDensityScale(val factor: Float) {
    COMPACT(0.94f),
    STANDARD(1.00f),
    COMFORTABLE(1.08f),
    ;

    companion object {
        fun fromStoredName(name: String?): UiDensityScale = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class HomeHeroStyle {
    ACTION,
    PROGRESS,
    TIMELINE,
    ;

    companion object {
        fun fromStoredName(name: String?): HomeHeroStyle = entries.firstOrNull { it.name == name } ?: ACTION
    }
}

enum class WidgetThemeMode {
    SYSTEM,
    APP,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class WidgetColorSource {
    SYSTEM_DYNAMIC,
    APP_THEME,
    CUSTOM_PALETTE,
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetColorSource = entries.firstOrNull { it.name == name } ?: SYSTEM_DYNAMIC
    }
}

enum class WidgetDensityScale(val factor: Float) {
    COMPACT(0.90f),
    STANDARD(1.00f),
    COMFORTABLE(1.10f),
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetDensityScale = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class WidgetTextScale(val factor: Float) {
    STANDARD(1.00f),
    LARGE(1.10f),
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetTextScale = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class OcrModelType { LIGHT_SVTR, FASTVIT_T8 }

enum class CloudAiProvider(val providerName: String, val defaultModel: String) {
    MIMO("MiMo", "mimo-v2.5-pro"),
    GEMINI("Gemini", "gemini-2.5-flash"),
    ANTHROPIC("Anthropic", "claude-sonnet-4-20250514"),
    OPENAI_COMPATIBLE("OpenAI-compatible", "gpt-4.1"),
}

enum class OpenAiCompatibleCloudAuthMode { API_KEY_HEADER, BEARER }
