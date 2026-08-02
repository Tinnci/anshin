package com.driezy.medlog.capability.widgets

import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.feature.medications.application.ToggleMedicationDoseUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Hilt EntryPoint：供不支持构造函数注入的 Glance Widget 代码使用。
 *
 * 使用方式：
 * ```
 * val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
 * val useCase = ep.toggleMedicationDoseUseCase()
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun toggleMedicationDoseUseCase(): ToggleMedicationDoseUseCase
    fun medicationRepository(): MedicationRepository
    fun logRepository(): LogRepository
    fun clock(): Clock
}
