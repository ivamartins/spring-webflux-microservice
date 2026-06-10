# ADR 0001: Postgres + R2DBC for system of record

- **Status:** Accepted
- **Date:** 2024-01
- **Context:** We need a system of record for Orders in a Java 21 + WebFlux microservice.

## Decision

Use **Postgres 15** accessed via **Spring Data R2DBC** as the system of record.

## Rationale

- **Strong consistency** for orders (no eventual-consistency surprises on amount/status).
- **Mature tooling** (DBA expertise, monitoring, backups) — typical in Brazilian cooperatives.
- **Reactive driver** keeps the WebFlux pipeline non-blocking (no need to wrap JDBC in `Mono.fromCallable`).
- **R2DBC is officially supported** by Spring Boot 3.

## Alternatives considered

- **JDBC + virtual threads (Java 21)**: viable, but virtual threads alone don't give us backpressure, and they tie up threads on slow I/O.
- **Mongo as system of record**: rejected — strong consistency requirements; also a separate stack to operate.
- **Event sourcing from day 1**: rejected — premature complexity for a junior model. Easy to layer later via the outbox pattern.

## Consequences

- R2DBC has a smaller feature set than JPA (no lazy loading, no dirty checking). We keep the domain layer free of R2DBC annotations.
- Migrations are owned by Flyway (added in roadmap).
