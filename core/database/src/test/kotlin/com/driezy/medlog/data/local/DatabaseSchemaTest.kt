package com.driezy.medlog.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSchemaTest {
    @Test
    fun `schema identity is valid and versioned`() {
        assertEquals("medlog.db", DatabaseSchema.NAME)
        assertTrue(DatabaseSchema.VERSION > 0)
    }
}
