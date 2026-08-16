package com.driezy.medlog.capability.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

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
            "amazon-bedrock" to
                ExpectedPreset(
                    "https://bedrock-runtime.us-east-1.amazonaws.com",
                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            "ant-ling" to ExpectedPreset("https://api.antling.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "anthropic" to ExpectedPreset("https://api.anthropic.com", CloudAiEndpointProtocol.ANTHROPIC),
            "azure-openai-responses" to
                ExpectedPreset(
                    "https://YOUR_RESOURCE.openai.azure.com/openai/v1",
                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            "cerebras" to ExpectedPreset("https://api.cerebras.ai/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "cloudflare-ai-gateway" to
                ExpectedPreset(
                    "https://gateway.ai.cloudflare.com/v1/\${CLOUDFLARE_ACCOUNT_ID}/\${CLOUDFLARE_GATEWAY_ID}",
                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            "cloudflare-workers-ai" to
                ExpectedPreset(
                    "https://api.cloudflare.com/client/v4/accounts/\${CLOUDFLARE_ACCOUNT_ID}/ai/v1",
                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            "deepseek" to ExpectedPreset("https://api.deepseek.com", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "fireworks" to
                ExpectedPreset("https://api.fireworks.ai/inference/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "google" to
                ExpectedPreset(
                    "https://generativelanguage.googleapis.com/v1beta/openai",
                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            "google-vertex" to
                ExpectedPreset("https://aiplatform.googleapis.com/v1beta1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "huggingface" to
                ExpectedPreset("https://router.huggingface.co/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "kimi-coding" to ExpectedPreset("https://api.kimi.com/coding/v1", CloudAiEndpointProtocol.ANTHROPIC),
            "minimax" to ExpectedPreset("https://api.minimax.io/anthropic/v1", CloudAiEndpointProtocol.ANTHROPIC),
            "minimax-cn" to ExpectedPreset("https://api.minimaxi.com/anthropic/v1", CloudAiEndpointProtocol.ANTHROPIC),
            "mistral" to ExpectedPreset("https://api.mistral.ai/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "moonshotai" to ExpectedPreset("https://api.moonshot.ai/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "moonshotai-cn" to ExpectedPreset("https://api.moonshot.cn/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "nvidia" to ExpectedPreset(
                "https://integrate.api.nvidia.com/v1",
                CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
            ),
            "qwen-token-plan" to
                ExpectedPreset(
                    "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            "qwen-token-plan-cn" to
                ExpectedPreset(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            "together" to ExpectedPreset("https://api.together.xyz/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "vercel-ai-gateway" to
                ExpectedPreset("https://ai-gateway.vercel.sh/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "xiaomi" to ExpectedPreset("https://api.xiaomimimo.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "xiaomi-token-plan-ams" to
                ExpectedPreset("https://token-plan-ams.xiaomimimo.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "xiaomi-token-plan-cn" to
                ExpectedPreset("https://token-plan-cn.xiaomimimo.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "xiaomi-token-plan-sgp" to
                ExpectedPreset("https://token-plan-sgp.xiaomimimo.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "zai" to ExpectedPreset("https://api.z.ai/api/paas/v4", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "zai-coding-cn" to
                ExpectedPreset("https://api.z.ai/api/paas/v4", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "github-copilot" to
                ExpectedPreset("https://api.githubcopilot.com", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "groq" to ExpectedPreset("https://api.groq.com/openai/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "lmstudio" to ExpectedPreset("http://127.0.0.1:1234/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
            "nvidia-nim" to
                ExpectedPreset("https://integrate.api.nvidia.com/v1", CloudAiEndpointProtocol.OPENAI_COMPATIBLE),
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

    private data class ExpectedPreset(val api: String, val protocol: CloudAiEndpointProtocol)
}
