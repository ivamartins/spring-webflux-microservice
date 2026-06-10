# ADR 0002: Mongo as denormalized read model

- **Status:** Accepted
- **Date:** 2024-01

## Decision

Mirror the order aggregate into **MongoDB** (collection `orders_view`) for read-heavy access patterns (customer dashboards, BI queries).

## Rationale

- Mongo's document model is a natural fit for denormalized read views.
- Spring Data MongoDB reactive is first-class in WebFlux.
- Indexes on `customerId` and `status` are cheap to maintain.

## Alternatives considered

- Read replicas in Postgres: would work, but doesn't help with schema flexibility or with downstream systems that want JSON-shaped data.
- Elasticsearch: heavier, would be the next step if we needed free-text search.

## Consequences

- Two sources of truth — Postgres (system) and Mongo (read model). The Kafka topic is the eventual source of truth for downstream replay.
- Mongo writes are best-effort; we `onErrorResume` so a Mongo outage doesn't block the API.
