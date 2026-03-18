package com.example.springbootai.dto;

import java.time.Instant;

/**
 * Response body for the non-streaming {@code POST /api/chat} endpoint.
 */
public record ChatResponse(
        String content,
        String provider,
        String model,
        Instant timestamp
) {
    public static ChatResponse of(String content, String provider, String model) {
        return new ChatResponse(content, provider, model, Instant.now());
    }
}
