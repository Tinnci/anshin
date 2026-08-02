package com.driezy.medlog.feature.settings

/**
 * 设置首页的稳定阅读顺序。把顺序作为可测试的展示模型，避免新增大区块时
 * 意外把高频入口推到长列表底部。
 */
internal enum class SettingsHomeSection(val itemKey: String) {
    OVERVIEW("home-overview"),
    DESTINATIONS("home-destinations"),
}

internal val settingsHomeSectionOrder = listOf(
    SettingsHomeSection.OVERVIEW,
    SettingsHomeSection.DESTINATIONS,
)
