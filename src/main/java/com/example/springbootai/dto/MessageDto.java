package com.example.springbootai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single message in a conversation (role + content).
 *
 * <p>Role is typically "user" or "assistant", matching the convention used by
 * both the OpenAI and Anthropic APIs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageDto(
        String role,
        String content
) {}
