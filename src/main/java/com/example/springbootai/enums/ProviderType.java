package com.example.springbootai.enums;

/**
 * Supported LLM providers.
 *
 * <ul>
 *   <li>CLAUDE – Anthropic Claude (cloud)</li>
 *   <li>OPENAI – OpenAI GPT models (cloud)</li>
 *   <li>LOCAL  – Local model served by LM Studio (http://localhost:1234/v1)</li>
 * </ul>
 */
public enum ProviderType {
    CLAUDE,
    OPENAI,
    LOCAL
}
