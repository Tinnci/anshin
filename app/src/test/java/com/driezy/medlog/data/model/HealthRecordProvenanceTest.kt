package com.driezy.medlog.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthRecordProvenanceTest {

    @Test
    fun `manual health records keep manual provenance by default`() {
        val record = HealthRecord(
            type = HealthType.WEIGHT.name,
            value = 72.4,
        )

        assertEquals(HealthRecordSource.MANUAL, record.source)
        assertNull(record.sourceFeature)
        assertNull(record.sourceProvider)
        assertNull(record.sourceModel)
        assertNull(record.sourceConfidence)
        assertNull(record.sourceCacheKey)
        assertNull(record.confirmedAt)
    }

    @Test
    fun `ocr metrics can carry cloud provenance into health records`() {
        val metric = ParsedHealthMetric(
            type = HealthType.SPO2,
            value = 97.0,
            rawText = "SpO2 97%",
            confidence = 0.91f,
            source = HealthRecordSource.CLOUD_OCR,
            sourceFeature = AiUsageFeature.IMAGE_OCR,
            sourceProvider = "Gemini",
            sourceModel = "gemini-2.5-flash",
            sourceCacheKey = "cache-key",
        )

        val record = HealthRecord(
            type = metric.type.name,
            value = metric.value,
            source = metric.source,
            sourceFeature = metric.sourceFeature,
            sourceProvider = metric.sourceProvider,
            sourceModel = metric.sourceModel,
            sourceConfidence = metric.confidence,
            sourceCacheKey = metric.sourceCacheKey,
            confirmedAt = 1_700_000_000_000L,
        )

        assertEquals(HealthRecordSource.CLOUD_OCR, record.source)
        assertEquals(AiUsageFeature.IMAGE_OCR, record.sourceFeature)
        assertEquals("Gemini", record.sourceProvider)
        assertEquals("gemini-2.5-flash", record.sourceModel)
        assertEquals(0.91f, record.sourceConfidence)
        assertEquals("cache-key", record.sourceCacheKey)
        assertEquals(1_700_000_000_000L, record.confirmedAt)
    }
}
