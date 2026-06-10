package com.codesolutions.api;

import com.codesolutions.domain.Order;
import com.codesolutions.mongo.OrderView;
import com.codesolutions.mongo.OrderViewRepository;
import com.codesolutions.persistence.OrderEntity;
import com.codesolutions.persistence.OrderRepository;
import com.codesolutions.redis.OrderCache;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service — orchestrates the writes (R2DBC, Kafka, Mongo, Redis).
 *
 * Reactive composition patterns:
 *  - Cache-aside: read goes to Redis first, falls back to R2DBC
 *  - Write fan-out: 1 R2DBC write + 1 Kafka publish + 1 Mongo mirror + 1 cache set
 *  - Error handling with onErrorResume to keep the API available even
 *    if downstream (Kafka, Mongo) is degraded
 */
@Service
public class OrderService {

    private final OrderRepository repo;
    private final OrderViewRepository mongoRepo;
    private final OrderCache cache;
    private final com.codesolutions.kafka.OrderEventPublisher kafkaPublisher;

    public OrderService(
            OrderRepository repo,
            OrderViewRepository mongoRepo,
            OrderCache cache,
            com.codesolutions.kafka.OrderEventPublisher kafkaPublisher
    ) {
        this.repo = repo;
        this.mongoRepo = mongoRepo;
        this.cache = cache;
        this.kafkaPublisher = kafkaPublisher;
    }

    public Mono<Order> create(String customerId, BigDecimal amount, String currency) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");

        if (amount.signum() <= 0) {
            return Mono.error(new IllegalArgumentException("amount must be > 0"));
        }
        if (currency.length() != 3) {
            return Mono.error(new IllegalArgumentException("currency must be 3-letter ISO"));
        }

        Order order = new Order(
                UUID.randomUUID(),
                customerId,
                amount,
                currency.toUpperCase(),
                Order.STATUS_CREATED,
                Instant.now()
        );

        return repo.save(OrderEntity.fromDomain(order))
                .map(OrderEntity::toDomain)
                .flatMap(this::mirrorToMongo)
                .flatMap(this::warmCache)
                .flatMap(kafkaPublisher::publishCreated)
                .onErrorResume(e -> Mono.just(order));
    }

    public Mono<Order> get(UUID id) {
        return cache.get(id)
                .switchIfEmpty(
                    repo.findById(id)
                        .map(OrderEntity::toDomain)
                        .flatMap(o -> cache.put(o).thenReturn(o))
                );
    }

    public Flux<Order> listByCustomer(String customerId) {
        return repo.findByCustomerId(customerId).map(OrderEntity::toDomain);
    }

    public Flux<Order> listByStatus(String status) {
        return repo.findByStatus(status).map(OrderEntity::toDomain);
    }

    public Mono<Order> changeStatus(UUID id, String newStatus) {
        return repo.findById(id)
                .map(OrderEntity::toDomain)
                .map(o -> o.withStatus(newStatus))
                .flatMap(updated -> repo.save(OrderEntity.fromDomain(updated)).map(OrderEntity::toDomain))
                .flatMap(this::mirrorToMongo)
                .flatMap(this::warmCache)
                .flatMap(o -> kafkaPublisher.publishStatusChanged(o).thenReturn(o));
    }

    private Mono<Order> mirrorToMongo(Order order) {
        OrderView view = OrderView.fromDomain(order);
        return mongoRepo.save(view).thenReturn(order);
    }

    private Mono<Order> warmCache(Order order) {
        return cache.put(order).thenReturn(order);
    }
}
