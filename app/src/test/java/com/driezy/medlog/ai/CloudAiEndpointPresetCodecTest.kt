package com.driezy.medlog.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAiEndpointPresetCodecTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

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

    @Test
    fun `asset includes opencode provider api presets supported by this app`() {
        val presets = CloudAiEndpointPresetCodec.decode(
            File(projectRoot, "app/src/main/assets/json/opencode_ai_endpoints.json").readText(),
        ).associateBy { it.id }

        mapOf(
            "anthropic" to ExpectedPreset("https://api.anthropic.com", CloudAiEndpointProtocol.ANTHROPIC),
            "github-copilot" to ExpectedPreset("https://api.githubcopilot.com", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "groq" to ExpectedPreset("https://api.groq.com/openai/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "lmstudio" to ExpectedPreset("http://127.0.0.1:1234/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "nvidia-nim" to ExpectedPreset("https://integrate.api.nvidia.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "ollama-local" to ExpectedPreset("http://127.0.0.1:11434/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "opencode" to ExpectedPreset("https://opencode.ai/zen/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "opencode-go" to ExpectedPreset("https://opencode.ai/zen/go/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "openai" to ExpectedPreset("https://api.openai.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "openrouter" to ExpectedPreset("https://openrouter.ai/api/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "xai" to ExpectedPreset("https://api.x.ai/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
        ).forEach { (id, expected) ->
            assertEquals(expected.api, presets[id]?.api)
            assertEquals(expected.protocol, presets[id]?.protocol)
        }
    }

    private data class ExpectedPreset(
        val api: String,
        val protocol: CloudAiEndpointProtocol,
    )
}
