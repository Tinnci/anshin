package com.example.medlog.widget

import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.domain.ToggleMedicationDoseUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
}
