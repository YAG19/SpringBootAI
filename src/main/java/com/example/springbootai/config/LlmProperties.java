package com.example.springbootai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration for all LLM providers, bound from {@code application.yml}
 * under the {@code llm} prefix.
 *
 * <p>API keys are resolved from environment variables:
 * <ul>
 *   <li>{@code ANTHROPIC_API_KEY} – for Claude</li>
 *   <li>{@code OPENAI_API_KEY}    – for OpenAI</li>
 * </ul>
 * LM Studio does not require a real API key.
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private ProviderConfig claude = new ProviderConfig();
    private ProviderConfig openai = new ProviderConfig();
    private ProviderConfig local  = new ProviderConfig();

    @Data
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        /** Anthropic-specific header value (only used by Claude). */
        private String anthropicVersion;
    }
}
