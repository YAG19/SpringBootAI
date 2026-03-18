package com.example.springbootai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Creates a dedicated {@link WebClient} bean for each LLM provider.
 *
 * <p>Each client is pre-configured with the provider's base URL and default
 * headers. The exchange buffer is increased to 10 MB to handle large streamed
 * responses gracefully.
 */
@Configuration
public class WebClientConfig {

    /** 10 MB in-memory buffer for streamed responses. */
    private static final int BUFFER_SIZE = 10 * 1024 * 1024;

    private ExchangeStrategies exchangeStrategies() {
        return ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(BUFFER_SIZE))
                .build();
    }

    @Bean
    @Qualifier("claudeWebClient")
    public WebClient claudeWebClient(LlmProperties props) {
        LlmProperties.ProviderConfig cfg = props.getClaude();
        return WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", cfg.getApiKey())
                .defaultHeader("anthropic-version", cfg.getAnthropicVersion())
                .exchangeStrategies(exchangeStrategies())
                .build();
    }

    @Bean
    @Qualifier("openAiWebClient")
    public WebClient openAiWebClient(LlmProperties props) {
        LlmProperties.ProviderConfig cfg = props.getOpenai();
        return WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .exchangeStrategies(exchangeStrategies())
                .build();
    }

    @Bean
    @Qualifier("localWebClient")
    public WebClient localWebClient(LlmProperties props) {
        LlmProperties.ProviderConfig cfg = props.getLocal();
        return WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // LM Studio accepts any bearer token
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .exchangeStrategies(exchangeStrategies())
                .build();
    }
}
