package com.driezy.medlog.data.model

import org.junit.Assert.assertTrue
import org.junit.Test

class DrugAliasSearchTest {
    @Test
    fun `drug search matches reviewed aliases through aliases`() {
        val drug = Drug(
            name = "阿司匹林",
            category = "血液和造血器官",
            aliases = listOf("拜阿司匹灵", "acetylsalicylic acid", "aspirin"),
        )

        assertTrue(drug.matches("拜阿司匹灵"))
        assertTrue(drug.matches("acetylsalicylic acid"))
        assertTrue(drug.relevanceScore("aspirin") > 0f)
    }

    @Test
    fun `alias relevance is separated from weaker semantic tags`() {
        val drug = Drug(
            name = "阿司匹林",
            category = "血液和造血器官",
            aliases = listOf("拜阿司匹灵"),
            semanticTags = listOf("止痛"),
        )

        assertTrue(drug.matches("拜阿司匹灵"))
        assertTrue(drug.matches("止痛"))
        assertTrue(drug.relevanceScore("拜阿司匹灵") > drug.relevanceScore("止痛"))
    }
}
