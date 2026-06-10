# System Design — spring-webflux-microservice

This document covers the **System Design** and architectural decisions for the microservice. The Java Sr (Híbrido) JD requires "atividades de System Design, definição de soluções técnicas e evolução da arquitetura".

## Bounded context

The service lives in the **Order Management** bounded context. It owns:

- The canonical `Order` aggregate
- Order lifecycle events (Created, Status Changed)
- Customer-by-id read models

It does **not** own:

- Customer identity (read-only, mirrored to Mongo)
- Payment (delegated to the legacy ERP via SOAP)
- Fulfillment (downstream consumers of Kafka events)

## Bounded context diagram

```
   ┌──────────────────┐
   │  Web/Mobile      │   (HTTP, JSON, reactive)
   └────────┬─────────┘
            │ WebFlux (HTTP/JSON, ~backpressure)
            ▼
   ┌──────────────────────────────────────────┐
   │  spring-webflux-microservice            │
   │                                          │
   │   Controller → Service → Repository      │
   │                │                         │
   │                ├─► Postgres (R2DBC) ─────┼──► system of record
   │                ├─► Redis (cache)   ──────┼──► hot reads, ~ms
   │                ├─► Kafka (events)  ──────┼──► downstream
   │                ├─► Mongo (view)    ──────┼──► denormalized read
   │                └─► SOAP / SFTP / HTTP ───┼──► legacy integration
   └──────────────────────────────────────────┘
            │                                  ▲
            ▼                                  │
   ┌──────────────────┐               ┌──────────────────┐
   │  Downstream      │               │  Legacy ERP      │
   │  consumers       │               │  (SOAP/SFTP/HTTP)│
   └──────────────────┘               └──────────────────┘
```

## Key decisions

| Topic | Decision | Rationale |
|---|---|---|
| Language / runtime | **Java 21** | JD requirement; allows virtual threads (`Loom`) + record patterns |
| HTTP stack | **Spring WebFlux** | JD requires WebFlux; reactive gives backpressure for free |
| Persistence | **R2DBC (Postgres)** for writes, **MongoDB** for denormalized views, **Redis** for hot reads | Polyglot persistence, each store plays to its strengths |
| Messaging | **Kafka** with idempotent producer (`acks=all`, `enable.idempotence=true`) | Exactly-once-style writes; downstream fan-out |
| Legacy | SOAP via CXF, SFTP via JSch, HTTP via WebClient | Each integration matches the protocol mandated by the legacy system |
| CI/CD | **GitLab CI** + **Jenkinsfile** | JD requires both; we ship both for the same pipeline |
| Observability | Micrometer + Prometheus + Spring Actuator | Standard for Spring Boot 3 |
| GC | **G1GC** with `MaxGCPauseMillis=200` | Latency-sensitive; G1 is the default for low-pause apps |

## Capacity planning (rough)

- Target: 1k req/s sustained, 5k req/s peak
- p99 latency: < 200ms (in-line with `MaxGCPauseMillis`)
- Per-instance: 4 vCPU, 4GB heap, 512MB metaspace
- Postgres: 8 vCPU, 32GB RAM, 500GB SSD, 3 replicas (RDS Multi-AZ)
- Redis: 2GB, 1 replica (ElastiCache)
- Kafka: 3 brokers, replication factor 3, 14-day retention

## Failure modes

| Failure | Mitigation |
|---|---|
| Postgres down | R2DBC pool exhaustion → 503; alerts via `/actuator/health` |
| Kafka down | Publish becomes best-effort (already wrapped in `onErrorResume`); events re-emitted on next read |
| Redis down | `switchIfEmpty` falls back to R2DBC; cache-aside means no data loss |
| Mongo down | Mirror becomes best-effort; system of record still in Postgres |
| SOAP down | SFTP fallback with retry; circuit-breaker (Resilience4j, not shown here) |

## See also

- ADR 0001: Postgres + R2DBC for system of record
- ADR 0002: Mongo as denormalized read model
- ADR 0003: Kafka for event-driven fan-out
- ADR 0004: G1GC tuning
