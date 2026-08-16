package com.driezy.medlog.feature.settings.application

import com.driezy.medlog.data.local.DatabaseSchema
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCompatibilityPolicyTest {
    @Test
    fun `current schema backup is restorable`() {
        assertTrue(BackupCompatibilityPolicy.canRestore(DatabaseSchema.VERSION))
    }

    @Test
    fun `older schema backup is restorable through Room migrations`() {
        assertTrue(BackupCompatibilityPolicy.canRestore(5))
    }

    @Test
    fun `future and invalid schema backups are rejected`() {
        assertFalse(BackupCompatibilityPolicy.canRestore(DatabaseSchema.VERSION + 1))
        assertFalse(BackupCompatibilityPolicy.canRestore(0))
        assertFalse(BackupCompatibilityPolicy.canRestore(4))
    }
}
