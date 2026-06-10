# ADR 0003: Kafka for event-driven fan-out

- **Status:** Accepted
- **Date:** 2024-01

## Decision

Use **Apache Kafka** for downstream fan-out, with two topics: `orders.events` (created) and `orders.status-changes`.

## Rationale

- Decouples the API service from downstream consumers (notification, billing, analytics).
- Idempotent producer (`acks=all`, `enable.idempotence=true`) gives at-least-once with deduplication downstream.
- Replayable — useful for rebuilding the Mongo view from scratch.

## Alternatives considered

- RabbitMQ: simpler, but no log-based replay; would require a separate change-data-capture story.
- Direct HTTP fan-out: tightly couples services; we explicitly avoid it.

## Consequences

- Kafka is now part of the deploy — adds operational complexity.
- Events must be versioned (`type` field) to allow schema evolution.
