package com.driezy.medlog.debug

import androidx.room.withTransaction
import com.driezy.medlog.data.local.MedLogDatabase
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.model.TimePeriod
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

class SeedDemoDataUseCase @Inject constructor(
    private val database: MedLogDatabase,
) {

    suspend fun seed(
        reset: Boolean,
        profile: SeedDemoProfile,
        calendar: SeedDemoCalendar = SeedDemoCalendar(),
    ): SeedResult {
        val dataset = buildDataset(profile, calendar)
        return database.withTransaction {
            if (reset) {
                clearAllData()
            } else {
                clearPreviousSeedData()
            }

            val medicationIds = mutableMapOf<String, Long>()
            dataset.medications.forEach { seeded ->
                medicationIds[seeded.key] = database.medicationDao().insertMedication(seeded.medication)
            }

            dataset.logs.forEach { seeded ->
                val medicationId = checkNotNull(medicationIds[seeded.medicationKey]) {
                    "Missing medication for seed log key=${seeded.medicationKey}"
                }
                database.medicationLogDao().insertLog(seeded.log.copy(medicationId = medicationId))
            }

            dataset.healthRecords.forEach { seeded ->
                database.healthRecordDao().insert(seeded.record)
            }

            SeedResult(
                medicationCount = dataset.medications.size,
                logCount = dataset.logs.size,
                healthRecordCount = dataset.healthRecords.size,
            )
        }
    }

    private fun clearAllData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM medication_logs")
        db.execSQL("DELETE FROM symptom_logs")
        db.execSQL("DELETE FROM health_records")
        db.execSQL("DELETE FROM medications")
        db.execSQL(
            "DELETE FROM sqlite_sequence WHERE name IN " +
                "('medications', 'medication_logs', 'symptom_logs', 'health_records')",
        )
    }

    private fun clearPreviousSeedData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM medication_logs WHERE notes LIKE 'seed:%'")
        db.execSQL("DELETE FROM symptom_logs WHERE note LIKE 'seed:%'")
        db.execSQL("DELETE FROM health_records WHERE notes LIKE 'seed:%'")
        db.execSQL("DELETE FROM medications WHERE notes LIKE 'seed:%'")
    }

    data class SeedResult(
        val medicationCount: Int,
        val logCount: Int,
        val healthRecordCount: Int,
    )

    companion object {
        fun buildDataset(
            profile: SeedDemoProfile,
            calendar: SeedDemoCalendar,
        ): SeedDemoDataset {
            val tag = "seed:${profile.wireName}"
            val today = calendar.startOfTodayMs()
            val meds = listOf(
                SeedMedication(
                    key = "bp_lisinopril",
                    medication = Medication(
                        name = "Seed ${profile.label} Lisinopril",
                        dose = 10.0,
                        doseUnit = "mg",
                        category = "cardio",
                        form = "tablet",
                        isHighPriority = true,
                        timePeriod = TimePeriod.MORNING.key,
                        reminderTimes = "08:00",
                        reminderHour = 8,
                        reminderMinute = 0,
                        doseQuantity = 1.0,
                        startDate = calendar.daysAgoAtMs(14, 8, 0),
                        stock = 18.0,
                        refillThreshold = 10.0,
                        refillReminderDays = 5,
                        notes = "$tag:bp_lisinopril",
                    ),
                ),
                SeedMedication(
                    key = "glucose_metformin",
                    medication = Medication(
                        name = "Seed ${profile.label} Metformin",
                        dose = 500.0,
                        doseUnit = "mg",
                        category = "diabetes",
                        form = "tablet",
                        isHighPriority = true,
                        timePeriod = TimePeriod.EXACT.key,
                        reminderTimes = "07:30,19:30",
                        reminderHour = 7,
                        reminderMinute = 30,
                        doseQuantity = 1.0,
                        startDate = calendar.daysAgoAtMs(21, 7, 30),
                        stock = 6.0,
                        refillThreshold = 12.0,
                        refillReminderDays = 7,
                        notes = "$tag:glucose_metformin",
                    ),
                ),
                SeedMedication(
                    key = "vitamin_d",
                    medication = Medication(
                        name = "Seed ${profile.label} Vitamin D",
                        dose = 1000.0,
                        doseUnit = "IU",
                        category = "supplement",
                        form = "capsule",
                        timePeriod = TimePeriod.AFTER_LUNCH.key,
                        reminderTimes = "12:30",
                        reminderHour = 12,
                        reminderMinute = 30,
                        doseQuantity = 1.0,
                        startDate = calendar.daysAgoAtMs(30, 12, 30),
                        stock = 42.0,
                        refillThreshold = 14.0,
                        notes = "$tag:vitamin_d",
                    ),
                ),
                SeedMedication(
                    key = "antibiotic_amoxicillin",
                    medication = Medication(
                        name = "Seed ${profile.label} Amoxicillin",
                        dose = 250.0,
                        doseUnit = "mg",
                        category = "antibiotic",
                        form = "capsule",
                        isHighPriority = true,
                        timePeriod = TimePeriod.EXACT.key,
                        reminderTimes = "06:00,14:00,22:00",
                        reminderHour = 6,
                        reminderMinute = 0,
                        doseQuantity = 1.0,
                        startDate = calendar.daysAgoAtMs(2, 6, 0),
                        endDate = calendar.daysFromTodayAtMs(5, 22, 0),
                        stock = 4.0,
                        refillThreshold = 6.0,
                        notes = "$tag:antibiotic_amoxicillin",
                    ),
                ),
                SeedMedication(
                    key = "inhaler_prn",
                    medication = Medication(
                        name = "Seed ${profile.label} Rescue Inhaler",
                        dose = 90.0,
                        doseUnit = "mcg",
                        category = "respiratory",
                        form = "inhaler",
                        timePeriod = TimePeriod.EXACT.key,
                        reminderTimes = "09:00",
                        reminderHour = 9,
                        reminderMinute = 0,
                        doseQuantity = 1.0,
                        isPRN = true,
                        maxDailyDose = 8.0,
                        startDate = calendar.daysAgoAtMs(60, 9, 0),
                        stock = 26.0,
                        refillThreshold = 10.0,
                        notes = "$tag:inhaler_prn",
                    ),
                ),
            )

            val logs = listOf(
                log("bp_lisinopril", calendar.todayAtMs(8, 0), LogStatus.TAKEN, "$tag:bp:morning", actualOffsetMinutes = 3),
                log("glucose_metformin", calendar.todayAtMs(7, 30), LogStatus.TAKEN, "$tag:metformin:morning", actualOffsetMinutes = 6),
                log("glucose_metformin", calendar.todayAtMs(19, 30), LogStatus.PENDING, "$tag:metformin:evening"),
                log("vitamin_d", calendar.todayAtMs(12, 30), LogStatus.SKIPPED, "$tag:vitamin:lunch"),
                log("antibiotic_amoxicillin", calendar.todayAtMs(6, 0), LogStatus.TAKEN, "$tag:antibiotic:morning", actualOffsetMinutes = 2),
                log("antibiotic_amoxicillin", calendar.todayAtMs(14, 0), LogStatus.PARTIAL, "$tag:antibiotic:afternoon", actualOffsetMinutes = 12, actualDoseQuantity = 0.5),
                log("antibiotic_amoxicillin", calendar.todayAtMs(22, 0), LogStatus.PENDING, "$tag:antibiotic:night"),
                log("inhaler_prn", calendar.todayAtMs(10, 15), LogStatus.TAKEN, "$tag:inhaler:prn", actualOffsetMinutes = 0),
            )

            val health = listOf(
                health(HealthType.BLOOD_PRESSURE, 118.0, 76.0, calendar.todayAtMs(7, 10), "$tag:health:bp"),
                health(HealthType.BLOOD_GLUCOSE, 5.8, null, calendar.todayAtMs(7, 20), "$tag:health:glucose"),
                health(HealthType.WEIGHT, 68.4, null, calendar.daysAgoAtMs(1, 21, 0), "$tag:health:weight"),
                health(HealthType.BODY_FAT, 23.8, null, calendar.daysAgoAtMs(1, 21, 1), "$tag:health:bodyfat"),
                health(HealthType.HEART_RATE, 72.0, null, calendar.todayAtMs(7, 12), "$tag:health:heart"),
                health(HealthType.TEMPERATURE, 36.6, null, calendar.todayAtMs(7, 18), "$tag:health:temp"),
                health(HealthType.SPO2, 98.0, null, calendar.todayAtMs(7, 15), "$tag:health:spo2"),
            )

            return SeedDemoDataset(
                profile = profile,
                medications = meds,
                logs = logs,
                healthRecords = health,
                todayStartMs = today,
                tomorrowStartMs = calendar.tomorrowStartMs(),
            )
        }

        private fun log(
            medicationKey: String,
            scheduledTimeMs: Long,
            status: LogStatus,
            notes: String,
            actualOffsetMinutes: Long? = null,
            actualDoseQuantity: Double? = null,
        ): SeedMedicationLog = SeedMedicationLog(
            medicationKey = medicationKey,
            log = MedicationLog(
                medicationId = 0L,
                scheduledTimeMs = scheduledTimeMs,
                actualTakenTimeMs = actualOffsetMinutes?.let { scheduledTimeMs + it * 60_000L },
                status = status,
                notes = notes,
                actualDoseQuantity = actualDoseQuantity,
            ),
        )

        private fun health(
            type: HealthType,
            value: Double,
            secondaryValue: Double?,
            timestamp: Long,
            notes: String,
        ): SeedHealthRecord = SeedHealthRecord(
            record = HealthRecord(
                type = type.name,
                value = value,
                secondaryValue = secondaryValue,
                timestamp = timestamp,
                notes = notes,
            ),
        )
    }
}

enum class SeedDemoProfile(
    val wireName: String,
    val label: String,
) {
    STANDARD("standard", "Standard"),
    OCR("ocr", "OCR"),
    ;

    companion object {
        fun from(value: String?): SeedDemoProfile = entries.firstOrNull {
            it.wireName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
        } ?: STANDARD
    }
}

class SeedDemoCalendar(
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
) {
    private val zone: ZoneId = clock.zone

    fun startOfTodayMs(): Long = LocalDate.now(clock).atStartOfDay(zone).toInstant().toEpochMilli()

    fun tomorrowStartMs(): Long = LocalDate.now(clock).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun todayAtMs(hour: Int, minute: Int): Long = atMs(LocalDate.now(clock), hour, minute)

    fun daysAgoAtMs(days: Long, hour: Int, minute: Int): Long =
        atMs(LocalDate.now(clock).minusDays(days), hour, minute)

    fun daysFromTodayAtMs(days: Long, hour: Int, minute: Int): Long =
        atMs(LocalDate.now(clock).plusDays(days), hour, minute)

    private fun atMs(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

data class SeedDemoDataset(
    val profile: SeedDemoProfile,
    val medications: List<SeedMedication>,
    val logs: List<SeedMedicationLog>,
    val healthRecords: List<SeedHealthRecord>,
    val todayStartMs: Long,
    val tomorrowStartMs: Long,
)

data class SeedMedication(
    val key: String,
    val medication: Medication,
)

data class SeedMedicationLog(
    val medicationKey: String,
    val log: MedicationLog,
)

data class SeedHealthRecord(
    val record: HealthRecord,
)
