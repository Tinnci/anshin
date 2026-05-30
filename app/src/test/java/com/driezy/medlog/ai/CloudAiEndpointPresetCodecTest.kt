package com.driezy.medlog.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAiEndpointPresetCodecTest {
    @Test
    fun `codec decodes opencode endpoint presets with protocol`() {
        val presets = CloudAiEndpointPresetCodec.decode(
            """
                [
                  {
                    "id": "xiaomi-token-plan-sgp",
                    "name": "Xiaomi Token Plan (Singapore)",
                    "api": "https://token-plan-sgp.xiaomimimo.com/v1",
                    "protocol": "openai_compatible"
                  },
                  {
                    "id": "minimax",
                    "name": "MiniMax",
                    "api": "https://api.minimax.io/anthropic/v1/",
                    "protocol": "anthropic"
                  }
                ]
            """.trimIndent(),
        )

        assertEquals(2, presets.size)
        assertEquals("xiaomi-token-plan-sgp", presets[0].id)
        assertEquals("https://token-plan-sgp.xiaomimimo.com/v1", presets[0].api)
        assertEquals(CloudAiEndpointProtocol.OPENAI_COMPATIBLE, presets[0].protocol)
        assertEquals("https://api.minimax.io/anthropic/v1", presets[1].api)
        assertEquals(CloudAiEndpointProtocol.ANTHROPIC, presets[1].protocol)
    }
}
