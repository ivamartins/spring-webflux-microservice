package com.codesolutions;

import com.codesolutions.api.OrderController;
import com.codesolutions.api.OrderService;
import com.codesolutions.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit slice test for the REST controller.
 *
 * Uses @WebFluxTest (reactive slice, no full context) and mocks the
 * OrderService to keep the test fast and infra-free. This is the
 * "fast" tier of the test pyramid — runs in < 1s.
 */
@WebFluxTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    OrderService service;

    @Test
    void shouldReturnOkForHealth() {
        webTestClient.get()
                .uri("/api/orders/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    @Test
    void shouldCreateOrder() {
        Order created = new Order(
                UUID.randomUUID(), "c-1", new BigDecimal("99.90"),
                "USD", "CREATED", Instant.now()
        );
        when(service.create(eq("c-1"), any(BigDecimal.class), eq("USD")))
                .thenReturn(Mono.just(created));

        webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"customerId":"c-1","amount":99.90,"currency":"USD"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                    .jsonPath("$.customerId").isEqualTo("c-1")
                    .jsonPath("$.status").isEqualTo("CREATED");
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"customerId":"c-1","amount":0,"currency":"USD"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldRejectInvalidCurrencyLength() {
        webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"customerId":"c-1","amount":1.0,"currency":"US"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturnOrderWhenFound() {
        UUID id = UUID.randomUUID();
        Order o = new Order(id, "c-1", new BigDecimal("10"), "USD", "CREATED", Instant.now());
        when(service.get(id)).thenReturn(Mono.just(o));

        webTestClient.get()
                .uri("/api/orders/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                    .jsonPath("$.id").isEqualTo(id.toString());
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/orders/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldChangeStatus() {
        UUID id = UUID.randomUUID();
        Order o = new Order(id, "c-1", new BigDecimal("10"), "USD", "PAID", Instant.now());
        when(service.changeStatus(eq(id), eq("PAID"))).thenReturn(Mono.just(o));

        webTestClient.put()
                .uri("/api/orders/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"PAID"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                    .jsonPath("$.status").isEqualTo("PAID");
    }

    @Test
    void shouldListByCustomer() {
        Order o = new Order(UUID.randomUUID(), "c-1", new BigDecimal("10"), "USD", "CREATED", Instant.now());
        when(service.listByCustomer("c-1")).thenReturn(Flux.just(o));

        webTestClient.get()
                .uri("/api/orders?customerId=c-1")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Order.class).hasSize(1);
    }
}
