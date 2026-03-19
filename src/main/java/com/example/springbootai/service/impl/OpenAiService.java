package com.example.springbootai.service.impl;

import com.example.springbootai.config.LlmProperties;
import com.example.springbootai.dto.ChatRequest;
import com.example.springbootai.dto.ChatResponse;
import com.example.springbootai.dto.MessageDto;
import com.example.springbootai.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM service backed by the OpenAI Chat Completions API.
 *
 * <p>Endpoint: {@code POST /v1/chat/completions}
 *
 * <p><strong>Streaming</strong> parses OpenAI SSE lines and emits
 * {@code choices[0].delta.content} from each data chunk.
 *
 * <p>API reference:
 * <a href="https://platform.openai.com/docs/api-reference/chat">OpenAI Chat API</a>
 */
@Slf4j
@Service
public class OpenAiService implements LlmService {

    private static final String PROVIDER_NAME = "OPENAI";

    protected final WebClient    webClient;
    protected final LlmProperties props;
    protected final ObjectMapper  mapper;

    public OpenAiService(@Qualifier("openAiWebClient") WebClient webClient,
                         LlmProperties props,
                         ObjectMapper mapper) {
        this.webClient = webClient;
        this.props     = props;
        this.mapper    = mapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        String model = resolveModel(request);
        ObjectNode body = buildRequestBody(request, model, false);

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String content = extractContent(json);
                    return ChatResponse.of(content, PROVIDER_NAME, model);
                })
                .doOnError(e -> log.error("OpenAI chat error: {}", e.getMessage()));
    }

    @Override
    public Flux<String> streamChat(ChatRequest request) {
        String model = resolveModel(request);
        ObjectNode body = buildRequestBody(request, model, true);

        return webClient.post()
                .uri("/v1/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamChunk)
                .doOnError(e -> log.error("OpenAI stream error: {}", e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Helpers (protected so LocalLlmService can override behaviour if needed)
    // -------------------------------------------------------------------------

    protected String resolveModel(ChatRequest request) {
        return (request.model() != null && !request.model().isBlank())
                ? request.model()
                : props.getOpenai().getDefaultModel();
    }

    /**
     * Builds the OpenAI Chat Completions request body.
     *
     * <p>The system prompt, if present, is inserted as the first message with
     * role {@code "system"}.
     */
    protected ObjectNode buildRequestBody(ChatRequest request, String model, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);

        ArrayNode messages = mapper.createArrayNode();

        // Optional system message
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            ObjectNode systemMsg = mapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", request.systemPrompt());
            messages.add(systemMsg);
        }

        // Conversation history
        List<MessageDto> history = request.history() != null ? request.history() : new ArrayList<>();
        for (MessageDto msg : history) {
            ObjectNode msgNode = mapper.createObjectNode();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
            messages.add(msgNode);
        }

        // Current user turn
        ObjectNode currentMsg = mapper.createObjectNode();
        currentMsg.put("role", "user");
        currentMsg.put("content", request.message());
        messages.add(currentMsg);

        body.set("messages", messages);
        return body;
    }

    /**
     * Extracts the assistant message from a non-streaming OpenAI response.
     *
     * <pre>
     * { "choices": [ { "message": { "content": "Hello!" } } ] }
     * </pre>
     */
    private String extractContent(JsonNode json) {
        return json.path("choices")
                   .path(0)
                   .path("message")
                   .path("content")
                   .asText(json.toString());
    }

    /**
     * Parses a single SSE line from an OpenAI streaming response.
     *
     * <p>OpenAI sends lines like:
     * <pre>
     * data: {"id":"...","choices":[{"delta":{"content":"Hello"},...}],...}
     * data: [DONE]
     * </pre>
     */
    protected Flux<String> parseStreamChunk(String line) {
        if (line == null || line.isBlank()) {
            return Flux.empty();
        }

        String data = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();

        if (data.isBlank() || "[DONE]".equals(data)) {
            return Flux.empty();
        }

        try {
            JsonNode node = mapper.readTree(data);
            String text = node.path("choices")
                              .path(0)
                              .path("delta")
                              .path("content")
                              .asText("");
            if (!text.isEmpty()) {
                return Flux.just(text);
            }
        } catch (Exception e) {
            log.debug("Skipping unparseable OpenAI SSE line: {}", line);
        }
        return Flux.empty();
    }
}
