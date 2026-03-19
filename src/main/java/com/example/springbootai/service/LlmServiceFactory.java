package com.example.springbootai.service;

import com.example.springbootai.enums.ProviderType;
import com.example.springbootai.service.impl.ClaudeService;
import com.example.springbootai.service.impl.LocalLlmService;
import com.example.springbootai.service.impl.OpenAiService;
import org.springframework.stereotype.Component;

/**
 * Resolves the correct {@link LlmService} implementation for a given {@link ProviderType}.
 */
@Component
public class LlmServiceFactory {

    private final ClaudeService claudeService;
    private final OpenAiService openAiService;
    private final LocalLlmService localLlmService;

    public LlmServiceFactory(ClaudeService claudeService,
                             OpenAiService openAiService,
                             LocalLlmService localLlmService) {
        this.claudeService   = claudeService;
        this.openAiService   = openAiService;
        this.localLlmService = localLlmService;
    }

    /**
     * Returns the {@link LlmService} for the requested provider.
     *
     * @param type the desired provider
     * @return the matching service implementation
     * @throws IllegalArgumentException for unknown provider types
     */
    public LlmService getService(ProviderType type) {
        return switch (type) {
            case CLAUDE -> claudeService;
            case OPENAI -> openAiService;
            case LOCAL  -> localLlmService;
        };
    }
}
