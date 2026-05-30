package com.driezy.medlog.data.model

import org.junit.Assert.assertTrue
import org.junit.Test

class DrugAliasSearchTest {
    @Test
    fun `drug search matches reviewed aliases through tags`() {
        val drug = Drug(
            name = "阿司匹林",
            category = "血液和造血器官",
            tags = listOf("拜阿司匹灵", "acetylsalicylic acid", "aspirin"),
        )

        assertTrue(drug.matches("拜阿司匹灵"))
        assertTrue(drug.matches("acetylsalicylic acid"))
        assertTrue(drug.relevanceScore("aspirin") > 0f)
    }
}
