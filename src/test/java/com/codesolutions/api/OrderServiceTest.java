package com.codesolutions;

import com.codesolutions.api.OrderService;
import com.codesolutions.domain.Order;
import com.codesolutions.kafka.OrderEventPublisher;
import com.codesolutions.mongo.OrderView;
import com.codesolutions.mongo.OrderViewRepository;
import com.codesolutions.persistence.OrderEntity;
import com.codesolutions.persistence.OrderRepository;
import com.codesolutions.redis.OrderCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the OrderService.
 *
 * Demonstrates:
 *  - Mockito + Reactor: chaining Mono/Flux mocks
 *  - StepVerifier: reactive test assertions
 *  - Validation: non-positive amount, invalid currency length
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository repo;
    @Mock OrderViewRepository mongoRepo;
    @Mock OrderCache cache;
    @Mock OrderEventPublisher kafka;

    @InjectMocks OrderService service;

    @Test
    void shouldCreateOrderAndFanOut() {
        Order saved = new Order(
                UUID.randomUUID(), "c-1", new BigDecimal("10"), "USD",
                Order.STATUS_CREATED, Instant.now()
        );
        when(repo.save(any(OrderEntity.class))).thenReturn(Mono.just(OrderEntity.fromDomain(saved)));
        when(mongoRepo.save(any(OrderView.class))).thenReturn(Mono.just(OrderView.fromDomain(saved)));
        when(cache.put(any(Order.class))).thenReturn(Mono.empty());
        when(kafka.publishCreated(any(Order.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.create("c-1", new BigDecimal("10"), "usd"))
                .assertNext(o -> {
                    assertThat(o.customerId()).isEqualTo("c-1");
                    assertThat(o.currency()).isEqualTo("USD");
                    assertThat(o.status()).isEqualTo("CREATED");
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        StepVerifier.create(service.create("c-1", BigDecimal.ZERO, "USD"))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                        && e.getMessage().contains("> 0"))
                .verify();
    }

    @Test
    void shouldRejectBadCurrencyLength() {
        StepVerifier.create(service.create("c-1", new BigDecimal("1"), "US"))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                        && e.getMessage().contains("3-letter"))
                .verify();
    }

    @Test
    void shouldReadThroughCache() {
        UUID id = UUID.randomUUID();
        Order o = new Order(id, "c-1", new BigDecimal("1"), "USD", "CREATED", Instant.now());
        when(cache.get(id)).thenReturn(Mono.just(o));
        when(repo.findById(id)).thenReturn(Mono.just(OrderEntity.fromDomain(o)));

        StepVerifier.create(service.get(id))
                .assertNext(got -> assertThat(got).isEqualTo(o))
                .verifyComplete();
    }

    @Test
    void shouldFallbackToRepoWhenCacheEmpty() {
        UUID id = UUID.randomUUID();
        Order o = new Order(id, "c-1", new BigDecimal("1"), "USD", "CREATED", Instant.now());
        when(cache.get(id)).thenReturn(Mono.empty());
        when(repo.findById(id)).thenReturn(Mono.just(OrderEntity.fromDomain(o)));
        when(cache.put(o)).thenReturn(Mono.empty());

        StepVerifier.create(service.get(id))
                .assertNext(got -> assertThat(got.id()).isEqualTo(id))
                .verifyComplete();
    }

    @Test
    void shouldChangeStatus() {
        UUID id = UUID.randomUUID();
        Order existing = new Order(id, "c-1", new BigDecimal("1"), "USD", "CREATED", Instant.now());
        Order updated = existing.withStatus("PAID");
        when(repo.findById(id)).thenReturn(Mono.just(OrderEntity.fromDomain(existing)));
        when(repo.save(any(OrderEntity.class))).thenReturn(Mono.just(OrderEntity.fromDomain(updated)));
        when(mongoRepo.save(any(OrderView.class))).thenReturn(Mono.just(OrderView.fromDomain(updated)));
        when(cache.put(any(Order.class))).thenReturn(Mono.empty());
        when(kafka.publishStatusChanged(any(Order.class))).thenReturn(Mono.just(updated));

        StepVerifier.create(service.changeStatus(id, "PAID"))
                .assertNext(o -> assertThat(o.status()).isEqualTo("PAID"))
                .verifyComplete();
    }
}
