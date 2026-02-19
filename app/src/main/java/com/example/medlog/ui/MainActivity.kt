package com.example.medlog.ui

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import com.example.medlog.R
import com.example.medlog.ui.theme.MedLogTheme

/** 从快捷方式 / 通知触发"立即添加"流程时使用的 intent action */
const val ACTION_ADD_MEDICATION = "com.example.medlog.ADD_MEDICATION"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 处理 ADD_MEDICATION 快捷方式 intent
        val openAddMedication = intent?.action == ACTION_ADD_MEDICATION

        setContent {
            MedLogTheme {
                MedLogApp(openAddMedication = openAddMedication)
            }
        }

        // 更新动态快捷方式（API 25+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            registerDynamicShortcuts()
        }
    }

    /** 注册/刷新动态快捷方式 */
    private fun registerDynamicShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
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
