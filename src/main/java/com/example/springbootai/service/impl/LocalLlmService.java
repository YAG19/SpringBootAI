package com.example.springbootai.service.impl;

import com.example.springbootai.config.LlmProperties;
import com.example.springbootai.dto.ChatRequest;
import com.example.springbootai.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * LLM service for a locally-running model served by
 * <a href="https://lmstudio.ai">LM Studio</a>.
 *
 * <p>LM Studio exposes an <strong>OpenAI-compatible REST API</strong> on
 * {@code http://localhost:1234/v1} by default, so this class reuses all
 * logic from {@link OpenAiService} — the only differences are:
 * <ul>
 *   <li>Different {@link WebClient} bean (points to {@code localhost:1234})</li>
 *   <li>Different default model (whatever is loaded in LM Studio)</li>
 *   <li>Provider name reported as {@code LOCAL} in responses</li>
 * </ul>
 *
 * <p><strong>No API key is required</strong>; LM Studio accepts any bearer token.
 */
@Slf4j
@Service
public class LocalLlmService extends OpenAiService {

    private static final String PROVIDER_NAME = "LOCAL";

    public LocalLlmService(@Qualifier("localWebClient") WebClient webClient,
                           LlmProperties props,
                           ObjectMapper mapper) {
        super(webClient, props, mapper);
    }

    // -------------------------------------------------------------------------
    // Overrides
    // -------------------------------------------------------------------------

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        String model = resolveModel(request);
        ObjectNode body = buildRequestBody(request, model, false);

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .map(json -> {
                    String content = json.path("choices")
                                        .path(0)
                                        .path("message")
                                        .path("content")
                                        .asText(json.toString());
                    return ChatResponse.of(content, PROVIDER_NAME, model);
                })
                .doOnError(e -> log.error("Local LLM chat error: {}", e.getMessage()));
    }

    @Override
    public Flux<String> streamChat(ChatRequest request) {
        String model = resolveModel(request);
        ObjectNode body = buildRequestBody(request, model, true);

        return webClient.post()
                .uri("/v1/chat/completions")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamChunk)
                .doOnError(e -> log.error("Local LLM stream error: {}", e.getMessage()));
    }

    /** Resolves model from request, falling back to the {@code local} config section. */
    @Override
    protected String resolveModel(ChatRequest request) {
        return (request.model() != null && !request.model().isBlank())
                ? request.model()
                : props.getLocal().getDefaultModel();
    }
}
