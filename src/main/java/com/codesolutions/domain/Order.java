package com.codesolutions.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order — the bounded-context aggregate.
 *
 * Stored in Postgres (R2DBC) as the system of record.
 * Cached in Redis for read-heavy access.
 * Mirrored in MongoDB for denormalized projections.
 * Emitted as events on Kafka for downstream consumers.
 *
 * The "system of record" choice (Postgres) follows the
 * System Design rationale recorded in docs/adr/0001-postgres-r2dbc.md.
 */
public record Order(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt
) {
    public static final String STATUS_CREATED   = "CREATED";
    public static final String STATUS_PAID      = "PAID";
    public static final String STATUS_SHIPPED   = "SHIPPED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public Order withStatus(String newStatus) {
        return new Order(id, customerId, amount, currency, newStatus, createdAt);
    }
}
