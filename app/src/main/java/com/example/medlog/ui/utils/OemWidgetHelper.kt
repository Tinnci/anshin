package com.example.medlog.ui.utils

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.medlog.R

/**
 * OEM 桌面小组件固定（Widget Pinning）兼容辅助工具。
 *
 * 背景：
 * - Android 原生（Pixel / Samsung One UI）：调用 requestPinAppWidget() 会直接弹出系统对话框。
 * - 小米 MIUI / HyperOS：即使 isRequestPinAppWidgetSupported 返回 true，若未开启「桌面快捷方式」
 *   权限，调用后系统会静默拦截，既无对话框也无任何错误回调。
 * - OPPO ColorOS / vivo OriginOS：行为与 MIUI 类似，默认禁止第三方应用请求固定，
 *   需在权限管理中手动开启。
 *
 * 使用建议：
 *   1. 在小组件固定按钮下方展示 [permissionNote]（若非空）。
 *   2. 提供「前往授权」按钮，点击后调用 [openPermissionSettings(context)]。
 *   3. 实际调用 requestPinAppWidget() 之前无需额外检查——即使权限未授予，
 *      最坏情况是静默失败，不会崩溃。
 */
object OemWidgetHelper {

    /** 已知 OEM 类型枚举 */
    enum class OemType { XIAOMI, OPPO, VIVO, SAMSUNG, GOOGLE, OTHER }

    /** 当前设备的 OEM 类型（通过 Build.MANUFACTURER 识别） */
    val oemType: OemType = when {
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)   -> OemType.XIAOMI
        Build.MANUFACTURER.equals("OPPO",   ignoreCase = true)   -> OemType.OPPO
        Build.MANUFACTURER.equals("vivo",   ignoreCase = true)   -> OemType.VIVO
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)  -> OemType.SAMSUNG
        Build.MANUFACTURER.equals("Google",  ignoreCase = true)  -> OemType.GOOGLE
        else -> OemType.OTHER
    }

    /**
     * 当前 OEM 是否可能因额外权限限制导致 Widget Pinning 静默失败。
     * 对于这类设备需额外提示用户前往授权。
     */
    val requiresExtraPermission: Boolean
        get() = oemType == OemType.XIAOMI || oemType == OemType.OPPO || oemType == OemType.VIVO

    /**
     * OEM 专属的权限说明文字，为空字符串时表示无需特殊权限说明。
     *
     * 显示位置：每个 WidgetPickerCard 的下方，或统一显示在小组件 SettingsCard 顶部。
     */
    fun permissionNote(context: Context): String = when (oemType) {
        OemType.XIAOMI -> context.getString(R.string.oem_miui_pin_guide)
        OemType.OPPO   -> context.getString(R.string.oem_coloros_pin_guide)
        OemType.VIVO   -> context.getString(R.string.oem_originos_pin_guide)
        else           -> ""
    }

    /**
     * 尝试跳转到 OEM 的应用权限详情页，以便用户开启「桌面快捷方式」权限。
     *
     * @return `true` 表示 Intent 已成功启动；`false` 表示跳转失败，调用方可降级到系统 App 详情。
     */
    fun openPermissionSettings(context: Context): Boolean {
        // 小米：直接跳转到 MIUI 权限编辑器
        if (oemType == OemType.XIAOMI) {
            val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                putExtra("extra_pkgname", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, miuiIntent)) return true
            // fallback：MIUI 新版本路径
            val miuiFallback = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
                )
                putExtra("extra_pkgname", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, miuiFallback)) return true
        }
        // 通用降级：系统应用详情页
        val appDetail = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStart(context, appDetail)
    }

    /**
     * 针对 Samsung / Pixel 等不需要额外权限的设备，在无法使用 requestPinAppWidget 时
     * 返回一段通用的手动添加引导文字。
     */
    fun manualAddGuidance(context: Context): String =
        context.getString(R.string.oem_manual_add_guide)

    // ── 私有工具 ───────────────────────────────────────────────────────────────

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
