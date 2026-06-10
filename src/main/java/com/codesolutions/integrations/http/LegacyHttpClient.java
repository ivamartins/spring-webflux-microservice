package com.codesolutions.integrations.http;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * HTTP client for legacy REST endpoints using WebClient (reactive).
 *
 * The JD requires familiarity with "integrações com sistemas externos
 * via HTTP". WebClient is the Spring WebFlux native HTTP client and
 * integrates seamlessly with the reactive pipeline.
 */
public class LegacyHttpClient {

    private final WebClient client;

    public LegacyHttpClient(String baseUrl) {
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public Mono<String> get(String path) {
        return client.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5));
    }

    public <T> Mono<T> post(String path, Object body, Class<T> responseType) {
        return client.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(5));
    }
}
