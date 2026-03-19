package com.example.springbootai.dto;

import com.example.springbootai.enums.ProviderType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Incoming chat request body.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "message": "Hello!",
 *   "provider": "LOCAL",
 *   "model": "optional-model-override",
 *   "systemPrompt": "You are a helpful assistant.",
 *   "history": [
 *     { "role": "user",      "content": "Hi" },
 *     { "role": "assistant", "content": "Hello! How can I help?" }
 *   ]
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code provider} – which LLM to use; defaults to {@code LOCAL} if omitted</li>
 *   <li>{@code model}    – optional override; falls back to the configured default</li>
 *   <li>{@code history}  – optional prior turns for multi-turn conversations</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String message,
        ProviderType provider,
        String model,
        String systemPrompt,
        List<MessageDto> history
) {
    /** Returns {@code LOCAL} when no provider is specified. */
    public ProviderType effectiveProvider() {
        return provider != null ? provider : ProviderType.LOCAL;
    }
}
