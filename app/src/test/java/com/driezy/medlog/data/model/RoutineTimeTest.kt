package com.driezy.medlog.data.model

import org.junit.Assert.assertThrows
import org.junit.Test

class RoutineTimeTest {
    @Test
    fun `rejects hours outside the wall clock range`() {
        listOf(-1, 24).forEach { hour ->
            assertThrows(IllegalArgumentException::class.java) {
                RoutineTime(hour, 0)
            }
        }
    }

    @Test
    fun `rejects minutes outside the wall clock range`() {
        listOf(-1, 60).forEach { minute ->
            assertThrows(IllegalArgumentException::class.java) {
                RoutineTime(12, minute)
            }
        }
    }
}
