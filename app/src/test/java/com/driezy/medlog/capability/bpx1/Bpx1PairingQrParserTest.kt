package com.driezy.medlog.capability.bpx1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Bpx1PairingQrParserTest {
    @Test
    fun `parses anshin deep-link URI`() {
        val result = Bpx1PairingQrParser.parse(
            "anshin://bpx1?mac=84%3A46%3A93%3A74%3A59%3A54&key=5842f45352878c8c17d23432f25389b6",
        )
        assertEquals(
            Bpx1PairingQrPayload(
                macAddress = "84:46:93:74:59:54",
                bindKeyHex = "5842f45352878c8c17d23432f25389b6",
            ),
            result,
        )
    }

    @Test
    fun `parses JSON payload`() {
        val result = Bpx1PairingQrParser.parse(
            """{"mac":"84-46-93-74-59-54","bindKey":"5842F45352878C8C17D23432F25389B6"}""",
        )
        assertEquals(
            Bpx1PairingQrPayload(
                macAddress = "84:46:93:74:59:54",
                bindKeyHex = "5842f45352878c8c17d23432f25389b6",
            ),
            result,
        )
    }

    @Test
    fun `parses raw Xiaomi token extractor text`() {
        val raw = """
            NAME:     Mijia Smart Blood Pressure Monitor
            ID:       blt.3.1j72kfsl04g00
            BLE KEY:  5842f45352878c8c17d23432f25389b6
            MAC:      84:46:93:74:59:54
            TOKEN:    4ba0b0cd211b0bb4f88e9f7e
            MODEL:    ihealth.bpm.bpx1
        """.trimIndent()
        val result = Bpx1PairingQrParser.parse(raw)
        assertEquals(
            Bpx1PairingQrPayload(
                macAddress = "84:46:93:74:59:54",
                bindKeyHex = "5842f45352878c8c17d23432f25389b6",
            ),
            result,
        )
    }

    @Test
    fun `returns null when no valid 32-char bindKey is present`() {
        assertNull(Bpx1PairingQrParser.parse("hello world"))
    }
}
