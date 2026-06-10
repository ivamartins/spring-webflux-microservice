package com.codesolutions.api;

import com.codesolutions.domain.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller — fully reactive, returning Mono / Flux.
 *
 * Validation: Bean Validation (jakarta.validation) on the request body.
 * Status codes: 201 on create, 200 on read/update, 404 on missing.
 *
 * Maps to the JD's "APIs REST" requirement.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("ok"));
    }

    /**
     * Liveness + cache TTL inspection.
     * Useful to confirm at runtime that the TTL config is being read correctly.
     */
    @GetMapping("/info")
    public Mono<ResponseEntity<java.util.Map<String, Object>>> info() {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", "ok");
        body.put("cache.orders.ttl", service.cacheTtlDescription());
        return Mono.just(ResponseEntity.ok(body));
    }

    @PostMapping
    public Mono<ResponseEntity<Order>> create(@Valid @RequestBody CreateOrderRequest req) {
        return service.create(req.customerId(), req.amount(), req.currency())
                .map(o -> ResponseEntity.status(HttpStatus.CREATED).body(o));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Order>> get(@PathVariable UUID id) {
        return service.get(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<Order> list(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "postgres") String source
    ) {
        if (customerId != null) {
            // "mongo" reads from the denormalized read model (CQRS-lite)
            if ("mongo".equalsIgnoreCase(source)) {
                return service.listByCustomerFromMongo(customerId);
            }
            return service.listByCustomer(customerId);
        }
        if (status != null)     return service.listByStatus(status);
        return Flux.empty();
    }

    /**
     * Returns the order enriched with data from a legacy HTTP system.
     * If the legacy is down, the order is still returned with a flag
     * indicating the legacy data is unavailable.
     */
    @GetMapping("/{id}/legacy-data")
    public Mono<ResponseEntity<OrderService.OrderWithLegacy>> getWithLegacyData(@PathVariable UUID id) {
        return service.getWithLegacyData(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Order>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req
    ) {
        return service.changeStatus(id, req.status())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 64) String customerId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency
    ) {}

    public record UpdateStatusRequest(
            @NotBlank String status
    ) {}
}
