package com.driezy.medlog.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A previous prescription schedule, valid only during its recorded effective interval. */
@Entity(
    tableName = "medication_plan_revisions",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId")],
)
data class MedicationPlanRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val effectiveFromMs: Long,
    val effectiveUntilMs: Long,
    val startDate: Long,
    val endDate: Long?,
    val frequencyType: String,
    val frequencyInterval: Int,
    val frequencyDays: String,
    val timePeriod: String,
    val reminderTimes: String,
    val reminderHour: Int,
    val reminderMinute: Int,
    val intervalHours: Int,
    val isPRN: Boolean,
    val isArchived: Boolean,
    val doseQuantity: Double,
    val doseUnit: String,
)

fun Medication.planRevision(until: Long) = MedicationPlanRevision(
    medicationId = id,
    effectiveFromMs = planEffectiveFromMs,
    effectiveUntilMs = until,
    startDate = startDate,
    endDate = endDate,
    frequencyType = frequencyType,
    frequencyInterval = frequencyInterval,
    frequencyDays = frequencyDays,
    timePeriod = timePeriod,
    reminderTimes = reminderTimes,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    intervalHours = intervalHours,
    isPRN = isPRN,
    isArchived = isArchived,
    doseQuantity = doseQuantity,
    doseUnit = doseUnit,
)

fun MedicationPlanRevision.applyTo(medication: Medication) = medication.copy(
    startDate = startDate,
    endDate = endDate,
    frequencyType = frequencyType,
    frequencyInterval = frequencyInterval,
    frequencyDays = frequencyDays,
    timePeriod = timePeriod,
    reminderTimes = reminderTimes,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    intervalHours = intervalHours,
    isPRN = isPRN,
    isArchived = isArchived,
    doseQuantity = doseQuantity,
    doseUnit = doseUnit,
    planEffectiveFromMs = effectiveFromMs,
)
