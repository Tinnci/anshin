package com.driezy.medlog.capability.bpx1.application

import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.Bpx1Protocol
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.repository.HealthRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class Bpx1ImportResult(val inserted: Boolean, val importedCount: Int)

/**
 * Writes BPX1 measurement packets into the shared health repository.
 *
 * A single BPX1 MiBeacon packet contains both blood pressure and heart rate.
 * The two records are deduplicated with deterministic [HealthRecord.sourceCacheKey]
 * values so repeated advertisements for the same packet do not create duplicates.
 */
@Singleton
class Bpx1MeasurementImporter @Inject constructor(
    private val healthRepository: HealthRepository,
    private val clock: Clock,
) {
    suspend fun import(macAddress: String, measurement: Bpx1Measurement): Bpx1ImportResult {
        val baseKey = buildString {
            append("bpx1:")
            append(Bpx1Protocol.normalizeMac(macAddress).replace(":", "").lowercase())
            append(':')
            append(measurement.timestampMillis / 1_000L)
            append(':')
            append(measurement.systolic)
            append(':')
            append(measurement.diastolic)
            append(':')
            append(measurement.heartRate)
        }
        var inserted = false
        val bloodPressureKey = "$baseKey:bp"
        if (!healthRepository.hasSourceCacheKey(bloodPressureKey)) {
            healthRepository.addRecord(
                HealthRecord(
                    type = HealthType.BLOOD_PRESSURE.name,
                    value = measurement.systolic.toDouble(),
                    secondaryValue = measurement.diastolic.toDouble(),
                    timestamp = measurement.timestampMillis,
                    source = HealthRecordSource.IMPORT,
                    sourceProvider = Bpx1Protocol.MODEL,
                    sourceCacheKey = bloodPressureKey,
                    confirmedAt = clock.millis(),
                ),
            )
            inserted = true
        }
        val heartRateKey = "$baseKey:hr"
        if (!healthRepository.hasSourceCacheKey(heartRateKey)) {
            healthRepository.addRecord(
                HealthRecord(
                    type = HealthType.HEART_RATE.name,
                    value = measurement.heartRate.toDouble(),
                    timestamp = measurement.timestampMillis,
                    source = HealthRecordSource.IMPORT,
                    sourceProvider = Bpx1Protocol.MODEL,
                    sourceCacheKey = heartRateKey,
                    confirmedAt = clock.millis(),
                ),
            )
            inserted = true
        }
        return Bpx1ImportResult(
            inserted = inserted,
            importedCount = if (inserted) 1 else 0,
        )
    }
}
