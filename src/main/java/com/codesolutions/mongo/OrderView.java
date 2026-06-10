package com.codesolutions.mongo;

import com.codesolutions.domain.Order;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB projection of an Order — denormalized for read-heavy
 * access patterns (e.g. customer dashboard aggregating all orders).
 *
 * Why Mongo here? See ADR 0002 (docs/adr/0002-mongo-projection.md).
 */
@Document(collection = "orders_view")
public record OrderView(
        @Id String id,
        @Indexed String customerId,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt
) {
    public static OrderView fromDomain(Order o) {
        return new OrderView(
                o.id().toString(),
                o.customerId(),
                o.amount(),
                o.currency(),
                o.status(),
                o.createdAt()
        );
    }
}
