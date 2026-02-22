package com.example.medlog.interaction

import com.example.medlog.data.model.InteractionSeverity
import com.example.medlog.data.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 纯 JVM 单元测试：[InteractionRuleEngine]
 *
 * 覆盖：空列表、单药、已知 HIGH/MODERATE/LOW 配伍禁忌、顺序无关性、
 * 多对药品的多结果排序，以及无交叉时空结果。
 */
class InteractionRuleEngineTest {

    private lateinit var engine: InteractionRuleEngine

    @Before
    fun setup() {
        engine = InteractionRuleEngine()
    }

    // ─── 边界情况 ────────────────────────────────────────────────────────────

    @Test
    fun `empty list returns no interactions`() {
        assertTrue(engine.check(emptyList()).isEmpty())
    }

    @Test
    fun `single medication returns no interactions`() {
        val meds = listOf(med("华法林片"))
        assertTrue(engine.check(meds).isEmpty())
    }

    @Test
    fun `two completely unrelated meds return no interactions`() {
        // 维生素C + 钙片 — 两者均无规则匹配
        val meds = listOf(med("维生素C"), med("碳酸钙D3片"))
        assertTrue(engine.check(meds).isEmpty())
    }

    // ─── HIGH 相互作用 ───────────────────────────────────────────────────────

    @Test
    fun `warfarin + aspirin produces HIGH interaction`() {
        val meds = listOf(med("华法林"), med("阿司匹林"))
        val results = engine.check(meds)
        assertTrue(results.isNotEmpty())
        assertEquals(InteractionSeverity.HIGH, results.first().severity)
    }

    @Test
    fun `interaction is order-independent for warfarin and aspirin`() {
        val forward = engine.check(listOf(med("华法林"), med("阿司匹林")))
        val reversed = engine.check(listOf(med("阿司匹林"), med("华法林")))
        assertEquals(forward.size, reversed.size)
        assertEquals(forward.first().severity, reversed.first().severity)
    }

    @Test
    fun `MAOI + SSRI produces HIGH interaction`() {
        // 司来吉兰是 MAOI; 氟西汀 (fluoxetine) 是 SSRI
        val meds = listOf(med("司来吉兰"), med("氟西汀"))
        val results = engine.check(meds)
        assertTrue(results.isNotEmpty())
        assertEquals(InteractionSeverity.HIGH, results.first().severity)
    }

    @Test
    fun `metronidazole + alcohol produces HIGH interaction`() {
        val meds = listOf(med("甲硝唑"), med("酒精"))
        val results = engine.check(meds)
        assertTrue(results.isNotEmpty())
        assertEquals(InteractionSeverity.HIGH, results.first().severity)
    }

    @Test
    fun `warfarin + azithromycin produces HIGH interaction`() {
        // 阿奇霉素 (azithromycin) 抑制华法林代谢 → HIGH
        val meds = listOf(med("华法林"), med("阿奇霉素"))
        val results = engine.check(meds)
        assertTrue(results.isNotEmpty())
        assertEquals(InteractionSeverity.HIGH, results.first().severity)
    }

    // ─── MODERATE / LOW 相互作用 ─────────────────────────────────────────────

    @Test
    fun `clopidogrel + omeprazole produces MODERATE interaction`() {
        // 氯吡格雷 + 奥美拉唑 — PPI 降低氯吡格雷活化 → MODERATE
        val meds = listOf(med("氯吡格雷"), med("奥美拉唑"))
        val results = engine.check(meds)
        assertTrue(results.isNotEmpty())
        assertEquals(InteractionSeverity.MODERATE, results.first().severity)
    }

    @Test
    fun `aspirin + ibuprofen produces LOW interaction`() {
        val meds = listOf(med("阿司匹林"), med("布洛芬"))
        val results = engine.check(meds)
        assertTrue(results.isNotEmpty())
        assertEquals(InteractionSeverity.LOW, results.first().severity)
    }

    // ─── 多药品、排序 ────────────────────────────────────────────────────────

    @Test
    fun `three meds with two interactions return two results`() {
        // 华法林+阿司匹林 → HIGH ; 氯吡格雷+奥美拉唑 → MODERATE
        val meds = listOf(med("华法林"), med("阿司匹林"), med("氯吡格雷"), med("奥美拉唑"))
        val results = engine.check(meds)
        // 至少 2 条（华法林 + 阿司匹林 / 氯吡格雷 + 奥美拉唑）
        assertTrue("Expected at least 2 results, got ${results.size}", results.size >= 2)
    }

    @Test
    fun `results are sorted HIGH severity first`() {
        // 混入一个 HIGH (warfarin+aspirin) 和一个 MODERATE (clopidogrel+omeprazole)
        val meds = listOf(med("华法林"), med("阿司匹林"), med("氯吡格雷"), med("奥美拉唑"))
        val results = engine.check(meds)
        assertTrue(results.size >= 2)
        // 第一个应为 HIGH
        assertEquals(InteractionSeverity.HIGH, results.first().severity)
    }

    @Test
    fun `duplicate pairs are deduplicated`() {
        // 同样的药方被检测两次不应产生重复结果
        val meds = listOf(med("华法林"), med("阿司匹林"))
        val results = engine.check(meds)
        assertEquals(1, results.size)
    }

    @Test
    fun `drugA and drugB names are set correctly in result`() {
        val meds = listOf(med("华法林"), med("阿司匹林"))
        val result = engine.check(meds).first()
        // 华法林 匹配 groupA, 阿司匹林 匹配 groupB
        assertEquals("华法林", result.drugA)
        assertEquals("阿司匹林", result.drugB)
    }

    // ─── 辅助函数 ────────────────────────────────────────────────────────────

    /** 创建仅含 name 字段的最简 [Medication]（其余字段使用默认值）。 */
    private fun med(name: String, fullPath: String = "", category: String = "") =
        Medication(name = name, dose = 1.0, doseUnit = "片", fullPath = fullPath, category = category)
}
