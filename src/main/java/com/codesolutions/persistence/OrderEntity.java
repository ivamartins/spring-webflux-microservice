package com.codesolutions.persistence;

import com.codesolutions.domain.Order;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity — the Postgres representation of an Order.
 *
 * Kept separate from the domain class so the domain stays pure
 * (no persistence annotations leak into business logic).
 */
@Table("orders")
public record OrderEntity(
        @Id @Column("id") UUID id,
        @Column("customer_id") String customerId,
        @Column("amount") BigDecimal amount,
        @Column("currency") String currency,
        @Column("status") String status,
        @Column("created_at") Instant createdAt
) {
    public static OrderEntity fromDomain(Order o) {
        return new OrderEntity(o.id(), o.customerId(), o.amount(), o.currency(), o.status(), o.createdAt());
    }

    public Order toDomain() {
        return new Order(id, customerId, amount, currency, status, createdAt);
    }
}
