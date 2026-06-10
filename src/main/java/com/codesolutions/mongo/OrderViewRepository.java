package com.codesolutions.mongo;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderViewRepository extends ReactiveMongoRepository<OrderView, String> {
    Flux<OrderView> findByCustomerId(String customerId);
    Flux<OrderView> findByStatus(String status);
}
