package com.example.springbootai.service.impl;

import com.example.springbootai.config.LlmProperties;
import com.example.springbootai.dto.ChatRequest;
import com.example.springbootai.dto.ChatResponse;
import com.example.springbootai.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * LLM service for LM Studio using its native SDK API ({@code /api/v1/chat}).
 *
 * <p>This uses the <strong>LM Studio SDK API</strong> (not the OpenAI-compatible endpoint).
 * Request format: {@code { model, input, system_prompt?, history?, stream? }}
 * Response format: {@code { content, model, stats }}
 */
@Slf4j
@Service
public class LocalLlmService implements LlmService {

    private static final String PROVIDER_NAME = "LOCAL";
    private static final String CHAT_PATH = "/api/v1/chat";

    private final WebClient webClient;
    private final LlmProperties props;
    private final ObjectMapper mapper;

    public LocalLlmService(@Qualifier("localWebClient") WebClient webClient,
                           LlmProperties props,
                           ObjectMapper mapper) {
        this.webClient = webClient;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        String model = resolveModel(request);
        ObjectNode body = buildBody(request, model, false);

        return webClient.post()
                .uri(CHAT_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String content = json.path("content").asText(json.toString());
                    return ChatResponse.of(content, PROVIDER_NAME, model);
                })
                .doOnError(e -> log.error("Local LLM chat error: {}", e.getMessage()));
    }

    @Override
    public Flux<String> streamChat(ChatRequest request) {
        String model = resolveModel(request);
        ObjectNode body = buildBody(request, model, true);

        return webClient.post()
                .uri(CHAT_PATH)
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamChunk)
                .doOnError(e -> log.error("Local LLM stream error: {}", e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveModel(ChatRequest request) {
        return (request.model() != null && !request.model().isBlank())
                ? request.model()
                : props.getLocal().getDefaultModel();
    }

    /**
     * Builds the request body for the LM Studio SDK API.
     *
     * <pre>{@code
     * {
     *   "model": "...",
     *   "input": "user message",
     *   "system_prompt": "...",          // optional
     *   "history": [{role, content}...], // optional
     *   "stream": true                   // only when streaming
     * }
     * }</pre>
     */
    private ObjectNode buildBody(ChatRequest request, String model, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("input", request.message());

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.put("system_prompt", request.systemPrompt());
        }

        if (request.history() != null && !request.history().isEmpty()) {
            ArrayNode history = body.putArray("history");
            request.history().forEach(msg -> {
                ObjectNode entry = history.addObject();
                entry.put("role", msg.role());
                entry.put("content", msg.content());
            });
        }

        if (stream) {
            body.put("stream", true);
        }

        return body;
    }

    /**
     * Parses a single SSE line from the LM Studio streaming response.
     * LM Studio emits: {@code data: {"content":"chunk"}} or {@code data: [DONE]}
     */
    private Flux<String> parseStreamChunk(String line) {
        if (line == null || line.isBlank()) return Flux.empty();

        String data = null;
        if (line.startsWith("data:")) {
            data = line.substring(5).trim();
        } else if (!line.startsWith("event:") && !line.startsWith(":")) {
            // Some LM Studio versions emit raw JSON without the "data:" prefix
            data = line.trim();
        }

        if (data == null || data.isEmpty() || "[DONE]".equals(data)) return Flux.empty();

        try {
            JsonNode node = mapper.readTree(data);
            // LM Studio SDK format: { "content": "chunk" }
            if (node.has("content")) {
                String text = node.path("content").asText();
                return text.isEmpty() ? Flux.empty() : Flux.just(text);
            }
            // Fallback: OpenAI-style delta in case server switches formats
            String delta = node.path("choices").path(0).path("delta").path("content").asText();
            return delta.isEmpty() ? Flux.empty() : Flux.just(delta);
        } catch (Exception e) {
            log.debug("Could not parse stream chunk: {}", data);
            return Flux.empty();
        }
    }
}
