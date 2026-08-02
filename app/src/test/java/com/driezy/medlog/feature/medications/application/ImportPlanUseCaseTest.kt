package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.data.repository.FakeMedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 单元测试：[ImportPlanUseCase]
 *
 * 覆盖：MERGE（新增、同名跳过、大小写不敏感去重）；REPLACE（清除旧数据、全量导入）；
 * id 归零（Room 重新分配主键）；导入后触发持久提醒投影对账。
 *
 * 使用 [FakeMedicationRepository] + Mockito mock。
 */
class ImportPlanUseCaseTest {

    private lateinit var medicationRepo: FakeMedicationRepository
    private lateinit var reconcileReminders: ReconcileRemindersUseCase
    private lateinit var useCase: ImportPlanUseCase
    private val clock = Clock.fixed(Instant.parse("2026-08-02T04:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Before
    fun setup() {
        medicationRepo = FakeMedicationRepository()
        reconcileReminders = mock()
        useCase = ImportPlanUseCase(medicationRepo, reconcileReminders, clock)
    }

    // ─── MERGE 模式 ───────────────────────────────────────────────────────────

    @Test
    fun `MERGE adds new medication not present in existing list`() = runTest {
        // 现有：药品A；计划：药品B → 合并后应有 2 种药品
        addExisting("药品A")
        val plan = planOf("药品B")

        useCase(plan, ImportMode.MERGE)

        val active = medicationRepo.getActiveOnce()
        assertEquals(2, active.size)
        assertTrue(active.any { it.name == "药品A" })
        assertTrue(active.any { it.name == "药品B" })
    }

    @Test
    fun `MERGE skips medication with same name as existing`() = runTest {
        addExisting("药品A")
        val plan = planOf("药品A")

        useCase(plan, ImportMode.MERGE)

        assertEquals(1, medicationRepo.getActiveOnce().size)
    }

    @Test
    fun `MERGE deduplication is case-insensitive`() = runTest {
        addExisting("阿司匹林片")
        // 大写变体也应被跳过
        val plan = planOf("阿司匹林片")

        useCase(plan, ImportMode.MERGE)

        assertEquals(1, medicationRepo.getActiveOnce().size)
    }

    @Test
    fun `MERGE with multiple plan meds adds only non-duplicates`() = runTest {
        addExisting("药品A")
        val plan = planOf("药品A", "药品B", "药品C")

        useCase(plan, ImportMode.MERGE)

        val active = medicationRepo.getActiveOnce()
        assertEquals(3, active.size)
    }

    @Test
    fun `MERGE deduplicates repeated names inside one import atomically`() = runTest {
        val plan = planOf("药品A", " 药品A ", "药品B")

        useCase(plan, ImportMode.MERGE)

        assertEquals(2, medicationRepo.getActiveOnce().size)
    }

    // ─── REPLACE 模式 ─────────────────────────────────────────────────────────

    @Test
    fun `REPLACE deletes all existing meds and imports plan`() = runTest {
        addExisting("旧药A")
        addExisting("旧药B")
        val plan = planOf("新药X")

        useCase(plan, ImportMode.REPLACE)

        val active = medicationRepo.getActiveOnce()
        assertEquals(1, active.size)
        assertEquals("新药X", active.first().name)
    }

    @Test
    fun `REPLACE imports all plan meds regardless of name overlap`() = runTest {
        addExisting("药品A")
        val plan = planOf("药品A", "药品B") // 同名也应被导入

        useCase(plan, ImportMode.REPLACE)

        val active = medicationRepo.getActiveOnce()
        assertEquals(2, active.size)
    }

    // ─── 药品 id 归零 ─────────────────────────────────────────────────────────

    @Test
    fun `imported medication receives a new id from Room (not 0)`() = runTest {
        val plan = planOf("药品Z")

        useCase(plan, ImportMode.MERGE)

        val med = medicationRepo.getActiveOnce().first()
        assertTrue("id should be assigned by Room (>0)", med.id > 0L)
    }

    // ─── Projection reconciliation ───────────────────────────────────────────

    @Test
    fun `MERGE reconciles reminders and widgets after committing truth`() = runTest {
        val plan = planOf("药品A")

        useCase(plan, ImportMode.MERGE)

        verify(reconcileReminders).all(ReminderReconcileReason.MEDICATION_CHANGED)
    }

    @Test
    fun `REPLACE reconciles reminders and widgets after committing truth`() = runTest {
        addExisting("旧药")
        val plan = planOf("新药")

        useCase(plan, ImportMode.REPLACE)

        verify(reconcileReminders).all(ReminderReconcileReason.MEDICATION_CHANGED)
    }

    @Test
    fun `REPLACE uses one repository transaction before projection reconciliation`() = runTest {
        val repository: com.driezy.medlog.data.repository.MedicationRepository = mock()
        val reconciler: ReconcileRemindersUseCase = mock()

        ImportPlanUseCase(repository, reconciler, clock)(planOf("新药A", "新药B"), ImportMode.REPLACE)

        inOrder(repository, reconciler) {
            verify(repository).replaceActiveMedications(any())
            verify(reconciler).all(ReminderReconcileReason.MEDICATION_CHANGED)
        }
        verify(repository, never()).deleteMedication(any())
        verify(repository, never()).addMedication(any())
    }

    // ─── 辅助方法 ─────────────────────────────────────────────────────────────

    private suspend fun addExisting(name: String) {
        medicationRepo.addMedication(
            com.driezy.medlog.data.model.Medication(name = name, dose = 1.0, doseUnit = "片"),
        )
    }

    private fun planOf(vararg names: String): PlanExport {
        val entries = names.map { name ->
            MedExportEntry(
                name = name,
                dose = 1.0,
                doseUnit = "片",
                timePeriod = "exact",
                reminderTimes = "08:00",
                reminderHour = 8,
                reminderMinute = 0,
            )
        }
        return PlanExport(meds = entries)
    }
}
