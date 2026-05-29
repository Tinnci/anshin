# AI Health Intelligence Review And Follow-Up Plan

Last reviewed: 2026-05-30

## Confirmed Product Decisions

- Cloud-first for health insights when the user enables intelligent analysis, has a configured API key, and the network policy allows it.
- Local analysis remains the fallback for disabled cloud AI, missing keys, unsupported capabilities, network policy blocks, provider failures, invalid response formats, empty cloud insights, and safety violations.
- Text health insights may run in the background with cache protection. Image-based health OCR stays user-triggered because it sends sensitive image data and may cost more.
- The main UI does not expose cache mechanics. Cache hits, model identity, provider status, and failure categories stay in internal usage/cache records and debug-oriented surfaces.
- Health suggestions are limited to trend explanation, record-quality guidance, and safety escalation. Diagnosis, treatment plans, medication recommendations, and dose changes are not allowed.
- Prompt customization is not exposed as a free-form user input. Prompt profile fields stay versioned and code-managed, with only low-risk preference switches reserved for future work.
- Failed cloud calls do not automatically retry through a different provider. Cross-provider fallback requires a future explicit user setting.
- All supported health metrics enter the analysis context, but only actionable insights are displayed.
- Health device reading capture keeps a dedicated product entry while sharing camera and OCR infrastructure underneath.

## Current Implementation Review

### AI Provider Layer

- `AiChatClient` provides one internal request/response shape for text and image-capable models.
- Mimo uses the OpenAI-compatible chat-completions adapter.
- Gemini uses `generateContent` with system instructions, inline image data, finish reason, and token usage mapping.
- Anthropic uses `/v1/messages` with `x-api-key`, `anthropic-version`, system field, text blocks, image source blocks, stop reason, and token usage mapping.
- OpenAI-compatible supports bearer auth, API-key header auth, no-auth local endpoints, string content, text content parts, refusal parts, and image URL parts.
- Provider tests cover normal text responses, image request encoding, HTTP error propagation, tool/function-only non-text responses, prompt-block/no-candidate responses, custom Anthropic-compatible endpoints, and usage parsing.

### Health Insight Layer

- `HealthIntelligenceEngine` aggregates every supported metric type into a compact prompt context without exposing free-form notes.
- Local insight rules cover blood pressure, blood glucose, weight, body fat, heart rate, temperature, SpO2, data cadence, and low-confidence OCR review.
- Cloud insight cache keys include provider, model, prompt version, locale, prompt profile, metric values, timestamps, trends, source mix, and low-confidence counts.
- Cloud insight TTL is 12 hours.
- Empty, invalid, malformed, or non-JSON cloud responses fall back to local insights.
- New safety guard: cloud insight batches containing restricted medical advice are rejected with `POLICY_VIOLATION` and fall back to local insights.
- Provider-wrapped health insight JSON can be unwrapped from common metadata envelopes and array wrappers before schema validation.

### Image OCR Layer

- Health image analysis sends text plus image content to providers that support image input.
- OCR response parsing prefers structured JSON, then falls back to text-number parsing when the model returns usable visible text.
- OCR result caching is image-hash based with a 24-hour TTL.
- OCR records retain provenance fields: source, provider, model, confidence, cache key, and confirmation time.

### UI And Interaction Layer

- Health insights show a compact AI status pill and pending state without exposing prompt complexity.
- OCR cloud analysis is an explicit secondary enhancement action under the primary capture flow.
- Settings expose intelligent analysis status as off, needs key, ready, or text-only.
- Settings expose a compact internal 7-day AI usage summary without raw prompts, responses, API keys, or input hashes.
- AI motion uses Material motion scheme tokens through shared `AiInteractionStatusPill`.
- Demo seed prefixes are hidden in user-facing medication names while raw data remains unchanged.

## Acceptance Checklist

- Provider HTTP status and body are preserved in `AiProviderException`.
- Provider non-text responses fail closed instead of producing empty insights.
- OCR can tolerate partial model output if it still contains extractable readings.
- Health insights fail closed on malformed JSON, invalid schema, empty parsed results, and restricted advice.
- Cloud insight failure always keeps local insight output available when local context exists.
- No API key or raw provider response is stored in health records.
- Cache payloads are versioned and ignored when schema versions mismatch.
- Main user flows do not require a chat prompt or free-form AI configuration.

## Follow-Up Plan

### Phase 1: Safety And Response Robustness

- Completed: add a dedicated `POLICY_VIOLATION` structured error kind for restricted health advice.
- Completed: expand restricted-advice tests with English, Japanese, and Korean examples.
- Completed: add a small allowlist test to ensure safe phrases like "记录用药情况" and "按医嘱处理" are not blocked.
- Completed: add parser tests for provider responses wrapped in arrays or extra metadata.

### Phase 2: Observability And Debuggability

- Completed: add repository-level 7-day AI usage summaries grouped by feature, including total, success, fallback, error, cache hits, latest use, and latest error category.
- Completed: add a compact internal settings summary showing total runs, successes, errors, cache hits, and latest error category.
- Add an advanced export/debug section showing feature, provider, model, cache hit, result, error category, and timestamp.
- Add explicit per-feature counters for cloud success, unavailable fallback, failed fallback, and local-only execution.
- Keep this out of the main health page unless the user opens advanced details.

### Phase 3: Health Capture UX

- Design a dedicated health device capture surface around a display-shaped frame, glare guidance, confidence review, and metric confirmation.
- Add visual verification screenshots on the physical device for the health OCR flow.
- Consider a one-tap retake/confirm layout for blood pressure, glucose, weight/body-fat scales, pulse oximeters, and thermometers.

### Phase 4: Provider Compatibility

- Validate the default Mimo endpoint and model names against real API docs or sandbox calls before release.
- Add compatibility fixtures for OpenAI-compatible providers that return content arrays, refusals, and provider-specific usage fields.
- Add a user-visible "text-only model" state when image input is unsupported, already reflected in settings status.

### Phase 5: Preference System

- Keep the current default profile: conservative, concise, non-alarming, max 4 insights, system locale.
- Later add one lightweight setting if needed: "fewer reminders" versus "more detailed suggestions".
- Do not expose raw prompt editing in normal settings.

### Phase 6: Release Hardening

- Run full `ktlintCheck`, unit tests, debug assemble, install on physical device, and a short UI smoke pass before merging.
- Add migration verification for every schema bump and keep generated Room schema JSON checked in.
- Re-check Android lint separately if lint analysis stalls in the current toolchain.
