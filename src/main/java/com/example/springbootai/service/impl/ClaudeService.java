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
 * LLM service backed by the Anthropic Messages API.
 *
 * <p>Endpoint: {@code POST /v1/messages}
 *
 * <p><strong>Streaming</strong> parses Anthropic SSE events of type
 * {@code content_block_delta} and emits the {@code delta.text} field.
 *
 * <p>API reference:
 * <a href="https://docs.anthropic.com/en/api/messages">Anthropic Messages API</a>
 */
@Slf4j
@Service
public class ClaudeService implements LlmService {

    private static final String PROVIDER_NAME = "CLAUDE";
    private static final int    MAX_TOKENS    = 4096;

    private final WebClient    webClient;
    private final LlmProperties props;
    private final ObjectMapper  mapper;

    public ClaudeService(@Qualifier("claudeWebClient") WebClient webClient,
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
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String content = extractContent(json);
                    return ChatResponse.of(content, PROVIDER_NAME, model);
                })
                .doOnError(e -> log.error("Claude chat error: {}", e.getMessage()));
    }

    @Override
    public Flux<String> streamChat(ChatRequest request) {
        String model = resolveModel(request);
        ObjectNode body = buildRequestBody(request, model, true);

        return webClient.post()
                .uri("/v1/messages")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamChunk)
                .doOnError(e -> log.error("Claude stream error: {}", e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveModel(ChatRequest request) {
        return (request.model() != null && !request.model().isBlank())
                ? request.model()
                : props.getClaude().getDefaultModel();
    }

    /**
     * Builds the Anthropic Messages request body.
     *
     * <p>The Anthropic API separates the system prompt from the messages array.
     * History is flattened into the messages array before the current user turn.
     */
    private ObjectNode buildRequestBody(ChatRequest request, String model, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", stream);

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.put("system", request.systemPrompt());
        }

        ArrayNode messages = mapper.createArrayNode();
        List<MessageDto> history = request.history() != null ? request.history() : new ArrayList<>();
        for (MessageDto msg : history) {
            ObjectNode msgNode = mapper.createObjectNode();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
            messages.add(msgNode);
        }

        ObjectNode currentMsg = mapper.createObjectNode();
        currentMsg.put("role", "user");
        currentMsg.put("content", request.message());
        messages.add(currentMsg);

        body.set("messages", messages);
        return body;
    }

    /**
     * Extracts the assistant text from a non-streaming Anthropic response.
     *
     * <pre>
     * { "content": [ { "type": "text", "text": "Hello!" } ] }
     * </pre>
     */
    private String extractContent(JsonNode json) {
        JsonNode content = json.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText();
                }
            }
        }
        return json.toString();
    }

    /**
     * Parses a raw SSE line from the Anthropic streaming response.
     *
     * <p>Anthropic sends interleaved {@code event:} and {@code data:} lines.
     * We only care about {@code content_block_delta} data lines, specifically
     * the {@code delta.text} field.
     *
     * <pre>
     * event: content_block_delta
     * data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
     * </pre>
     */
    private Flux<String> parseStreamChunk(String line) {
        if (line == null || line.isBlank()) {
            return Flux.empty();
        }

        // SSE data lines start with "data: "
        String data = null;
        if (line.startsWith("data: ")) {
            data = line.substring(6).trim();
        } else if (!line.startsWith("event:") && !line.startsWith(":")) {
            // Fallback: treat the whole line as data (some proxies strip the prefix)
            data = line.trim();
        }

        if (data == null || data.isBlank() || "[DONE]".equals(data)) {
            return Flux.empty();
        }

        try {
            JsonNode node = mapper.readTree(data);
            String type = node.path("type").asText();
            if ("content_block_delta".equals(type)) {
                String text = node.path("delta").path("text").asText();
                if (!text.isBlank()) {
                    return Flux.just(text);
                }
            }
        } catch (Exception e) {
            log.debug("Skipping unparseable Claude SSE line: {}", line);
        }
        return Flux.empty();
    }
}
