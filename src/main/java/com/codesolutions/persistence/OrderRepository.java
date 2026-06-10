package com.codesolutions.persistence;

import com.codesolutions.domain.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive R2DBC repository for orders.
 *
 * Demonstrates:
 *  - Mono / Flux return types (Project Reactor)
 *  - Custom query methods that return reactive types
 *  - SQL via method-name conventions
 */
public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, UUID> {

    Flux<OrderEntity> findByCustomerId(String customerId);

    Flux<OrderEntity> findByStatus(String status);

    Mono<Order> findOrderById(UUID id);

    default Mono<Order> toDomainOrder(UUID id) {
        return findById(id).map(OrderEntity::toDomain);
    }
}
