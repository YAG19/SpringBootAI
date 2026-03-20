package com.example.springbootai.controller;

import com.example.springbootai.dto.ChatRequest;
import com.example.springbootai.dto.ChatResponse;
import com.example.springbootai.enums.ProviderType;
import com.example.springbootai.service.LlmService;
import com.example.springbootai.service.LlmServiceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing two chat endpoints:
 *
 * <ul>
 *   <li>{@code POST /api/chat}        – returns the full response at once (JSON)</li>
 *   <li>{@code POST /api/chat/stream} – streams tokens as Server-Sent Events</li>
 * </ul>
 *
 * <p>Both endpoints accept the same {@link ChatRequest} body. The {@code provider}
 * field selects which LLM backend to use ({@code CLAUDE}, {@code OPENAI}, or
 * {@code LOCAL}). When omitted, {@code LOCAL} is assumed.
 *
 * <h2>Example – full response (LOCAL / LM Studio)</h2>
 * <pre>{@code
 * curl -X POST http://localhost:8080/api/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message":"Hello!","provider":"LOCAL"}'
 * }</pre>
 *
 * <h2>Example – streaming response</h2>
 * <pre>{@code
 * curl -X POST http://localhost:8080/api/chat/stream \
 *   -H "Content-Type: application/json" \
 *   -H "Accept: text/event-stream" \
 *   -d '{"message":"Tell me a joke","provider":"LOCAL"}'
 * }</pre>
 *
 * <h2>Example – Claude</h2>
 * <pre>{@code
 * curl -X POST http://localhost:8080/api/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message":"Hello!","provider":"CLAUDE","systemPrompt":"Be concise."}'
 * }</pre>
 *
 * <h2>Example – multi-turn conversation</h2>
 * <pre>{@code
 * curl -X POST http://localhost:8080/api/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *         "message": "What did I just say?",
 *         "provider": "LOCAL",
 *         "history": [
 *           {"role":"user",      "content":"My name is Alice."},
 *           {"role":"assistant", "content":"Nice to meet you, Alice!"}
 *         ]
 *       }'
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final LlmServiceFactory serviceFactory;

    // -------------------------------------------------------------------------
    // Full (non-streaming) endpoint
    // -------------------------------------------------------------------------

    /**
     * Sends a chat message and returns the complete LLM response as JSON.
     *
     * @param request the chat payload
     * @return {@link ChatResponse} with the full assistant message
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        ProviderType provider = request.effectiveProvider();
        log.info("Chat request via provider={} model={}", provider,
                request.model() != null ? request.model() : "default");

        LlmService service = serviceFactory.getService(provider);
        return service.chat(request);
    }

    // -------------------------------------------------------------------------
    // Streaming SSE endpoint
    // -------------------------------------------------------------------------

    /**
     * Sends a chat message and streams back token chunks as Server-Sent Events.
     *
     * <p>Each SSE event carries a plain text token chunk. A terminal
     * {@code [DONE]} event is sent when streaming is complete.
     *
     * @param request the chat payload
     * @return a {@link Flux} of {@link ServerSentEvent} text chunks
     */
    @PostMapping(
            path = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        ProviderType provider = request.effectiveProvider();
        log.info("Stream request via provider={} model={}", provider,
                request.model() != null ? request.model() : "default");

        LlmService service = serviceFactory.getService(provider);
        System.out.println("Stream request via provider=" + provider + " model=" + request.model() + " message=" + request.message() + "request" + request);
        return service.streamChat(request)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build())
                .doOnNext(event -> log.info("Stream chunk: {}", event.data()))
                .doOnError(error -> log.error("Stream error: {}", error))
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("[DONE]")
                                .build()
                ));
    }
}
