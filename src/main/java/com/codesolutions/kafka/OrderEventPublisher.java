package com.codesolutions.kafka;

import com.codesolutions.domain.Order;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event publisher. Emits OrderCreated and OrderStatusChanged
 * events to dedicated topics.
 *
 * Returns Mono so the calling service stays fully reactive.
 * KafkaTemplate is sync internally but we wrap so the type signature
 * aligns with WebFlux's reactive pipeline.
 */
@Component
public class OrderEventPublisher {

    public static final String TOPIC_CREATED  = "orders.events";
    public static final String TOPIC_STATUS   = "orders.status-changes";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public Mono<Order> publishCreated(Order order) {
        return publish(TOPIC_CREATED, order.id().toString(),
                new OrderEvent("OrderCreated", order.id(), order.customerId(),
                        order.amount(), order.status(), Instant.now()))
                .thenReturn(order);
    }

    public Mono<Order> publishStatusChanged(Order order) {
        return publish(TOPIC_STATUS, order.id().toString(),
                new OrderEvent("OrderStatusChanged", order.id(), order.customerId(),
                        order.amount(), order.status(), Instant.now()))
                .thenReturn(order);
    }

    private <T> Mono<T> publish(String topic, String key, OrderEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            kafka.send(topic, key, json);
            return Mono.empty();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public record OrderEvent(
            String type, UUID orderId, String customerId,
            java.math.BigDecimal amount, String status, Instant occurredAt
    ) {}
}
