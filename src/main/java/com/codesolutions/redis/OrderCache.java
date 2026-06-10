package com.codesolutions.redis;

import com.codesolutions.domain.Order;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Thin Redis cache over Order. Demonstrates:
 *  - Reactive Redis access (ReactiveRedisTemplate)
 *  - Cache-aside pattern (read-through fallback to upstream)
 *  - JSON serialization (Jackson with JavaTimeModule for Instant)
 */
@Component
public class OrderCache {

    private final ReactiveRedisTemplate<String, String> redis;
    private final ObjectMapper mapper;

    public OrderCache(ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private String key(UUID id) { return "order:" + id; }

    public Mono<Order> get(UUID id) {
        return redis.opsForValue()
                .get(key(id))
                .flatMap(json -> Mono.fromCallable(() -> mapper.readValue(json, CachedOrder.class))
                        .onErrorResume(e -> Mono.empty())
                        .map(CachedOrder::toDomain));
    }

    public Mono<Void> put(Order order) {
        try {
            String json = mapper.writeValueAsString(CachedOrder.fromDomain(order));
            return redis.opsForValue()
                    .set(key(order.id()), json, Duration.ofMinutes(5))
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public Mono<Long> evict(UUID id) {
        return redis.delete(key(id));
    }

    // Flat record for serialization (avoids Jackson issues with BigDecimal in records)
    public record CachedOrder(
            UUID id, String customerId, BigDecimal amount, String currency,
            String status, Instant createdAt
    ) {
        public static CachedOrder fromDomain(Order o) {
            return new CachedOrder(o.id(), o.customerId(), o.amount(), o.currency(), o.status(), o.createdAt());
        }
        public Order toDomain() {
            return new Order(id, customerId, amount, currency, status, createdAt);
        }
    }
}
