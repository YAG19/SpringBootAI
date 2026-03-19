package com.example.springbootai.service;

import com.example.springbootai.dto.ChatRequest;
import com.example.springbootai.dto.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Contract that every LLM provider implementation must fulfil.
 *
 * <ul>
 *   <li>{@link #chat(ChatRequest)} – returns the full response as a single {@link Mono}</li>
 *   <li>{@link #streamChat(ChatRequest)} – streams token chunks as a {@link Flux}</li>
 * </ul>
 */
public interface LlmService {

    /**
     * Sends a chat request and waits for the complete response.
     *
     * @param request the incoming chat payload
     * @return a {@link Mono} emitting the complete {@link ChatResponse}
     */
    Mono<ChatResponse> chat(ChatRequest request);

    /**
     * Sends a chat request and streams back individual text chunks as they arrive.
     *
     * <p>Each element emitted is a raw text fragment (token or partial sentence).
     * The controller wraps these in Server-Sent Events before sending to the client.
     *
     * @param request the incoming chat payload
     * @return a {@link Flux} of text chunks
     */
    Flux<String> streamChat(ChatRequest request);
}
