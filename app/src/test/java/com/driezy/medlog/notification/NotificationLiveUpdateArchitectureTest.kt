package com.driezy.medlog.notification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLiveUpdateArchitectureTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `medication reminder window requests promoted ongoing notifications`() {
        val helper = source("app/src/main/java/com/driezy/medlog/notification/NotificationHelper.kt")

        assertTrue(
            "Medication reminder notifications should request promoted ongoing presentation.",
            helper.contains("setRequestPromotedOngoing(true)"),
        )
        assertTrue(
            "Promoted medication notifications should provide short critical text for compact surfaces.",
            helper.contains("setShortCriticalText("),
        )
        assertTrue(
            "Medication reminder notifications should be ongoing during the action window.",
            helper.contains("setOngoing(true)"),
        )
    }

    @Test
    fun `manifest declares promoted notification and exact alarm recovery hooks`() {
        val manifest = source("app/src/main/AndroidManifest.xml")

        assertTrue(manifest.contains("android.permission.POST_PROMOTED_NOTIFICATIONS"))
        assertTrue(manifest.contains("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
    }
}
