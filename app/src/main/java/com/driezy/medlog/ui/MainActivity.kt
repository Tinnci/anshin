package com.driezy.medlog.ui

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.ui.theme.applyMedLogSystemBars
import com.driezy.medlog.ui.theme.MedLogTheme
import com.driezy.medlog.ui.theme.ThemePalette

/** 从快捷方式 / 通知触发"立即添加"流程时使用的 intent action */
const val ACTION_ADD_MEDICATION = "com.driezy.medlog.ADD_MEDICATION"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 处理 ADD_MEDICATION 快捷方式 intent
        val openAddMedication = intent?.action == ACTION_ADD_MEDICATION

        // 报告快捷方式使用（提升排名），当从 history 快捷方式启动时
        if (intent?.extras?.getString("navigate_to") == "history") {
            getSystemService(ShortcutManager::class.java)
                ?.reportShortcutUsed("history_shortcut")
        }

        setContent {
            // 观察主题设置（与 MedLogApp 共享同一个 ViewModel 实例）
            val appViewModel: MedLogAppViewModel = hiltViewModel()
            val prefs by appViewModel.featureFlags.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM -> systemDark
            }

            SideEffect {
                applyMedLogSystemBars(darkTheme)
            }

            MedLogTheme(
                darkTheme = darkTheme,
                dynamicColor = prefs.useDynamicColor,
                palette = ThemePalette.fromStoredName(prefs.themePaletteName),
                fontMode = prefs.fontMode,
                appTextScale = prefs.appTextScale,
                uiDensityScale = prefs.uiDensityScale,
            ) {
                MedLogApp(openAddMedication = openAddMedication)
            }
        }

        // 更新动态快捷方式
        registerDynamicShortcuts()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // 当 Activity 已在前台时，从快捷方式再次启动也需要报告使用
        if (intent.extras?.getString("navigate_to") == "history") {
            getSystemService(ShortcutManager::class.java)
                ?.reportShortcutUsed("history_shortcut")
        }
    }

    /** 注册/刷新动态快捷方式 */
    private fun registerDynamicShortcuts() {
        val sm = getSystemService(ShortcutManager::class.java) ?: return

        val historyShortcut = ShortcutInfo.Builder(this, "history_shortcut")
            .setShortLabel(getString(R.string.shortcut_history_short))
            .setLongLabel(getString(R.string.shortcut_history_long))
            .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_today))
            .setIntent(
                android.content.Intent(this, MainActivity::class.java).apply {
                    action = android.content.Intent.ACTION_VIEW
                    putExtra("navigate_to", "history")
                }
            )
            .build()

        sm.dynamicShortcuts = listOf(historyShortcut)
    }
}
