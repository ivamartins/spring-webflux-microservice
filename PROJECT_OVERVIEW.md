# spring-webflux-microservice — Overview & flow

Reactive **Order management** microservice built with **Java 21 + Spring Boot 3.3 + WebFlux**. Java 21, Spring Boot/WebFlux, microservices, Kafka messaging, legacy integrations (HTTP/SOAP/SFTP), CI/CD (GitLab + Jenkins).

## Stack (with versions)

- **Java 21** (records, pattern matching, virtual-thread-ready)
- **Spring Boot 3.3.4** + **Spring WebFlux** (reactive, non-blocking)
- **Spring Data R2DBC** (Postgres R2DBC driver `1.0.5.RELEASE`) → Postgres (system of record)
- **Spring Data MongoDB Reactive** → denormalized read model
- **Spring Data Redis Reactive** → hot-path cache
- **Spring Kafka** → event fan-out
- **Apache CXF 4.0.5** (`cxf-spring-boot-starter-jaxws`) → SOAP client
- **JSch 0.1.55** → SFTP client
- **WebClient** (Spring) → legacy HTTP client
- **Micrometer + Prometheus + Spring Actuator** → observability
- **springdoc-openapi 2.6.0** → OpenAPI/Swagger UI
- **Testcontainers 1.20.2** (junit-jupiter, postgresql, mongodb, kafka) → E2E
- **OkHttp MockWebServer 4.12.0** → HTTP mocks in tests
- **JVM tuning**: G1GC, `MaxGCPauseMillis=200`, string dedup, GC logs
- **CI**: `.gitlab-ci.yml` + `Jenkinsfile`

---

## Main flow

### 1. Create order — `POST /api/orders`
Bean Validation on the request body → `OrderService.create`:
1. Builds the `Order` domain (UUID, customerId, amount, ISO-3 currency, status, createdAt).
2. Persists in **Postgres** via `OrderRepository` (R2DBC) — **system of record**.
3. Mirrors to **MongoDB** (`mirrorToMongo`) — denormalized read model.
4. Warms cache in **Redis** (`warmCache`).
5. Publishes `OrderEventPublisher.publishCreated` to **Kafka**.
6. `onErrorResume` on any fan-out failure: the API keeps responding even if Mongo/Kafka are degraded.

### 2. Get order — `GET /api/orders/{id}` (cache-aside)
- Tries **Redis** first.
- On miss, reads **Postgres** (R2DBC), writes to cache and returns.

### 3. List orders — `GET /api/orders?customerId=X&source=postgres|mongo` or `?status=X`
- **`source=postgres`** (default): reads from **Postgres** via `OrderRepository` (R2DBC) — `service.listByCustomer`.
- **`source=mongo`**: reads from **MongoDB** via `OrderViewRepository` — `service.listByCustomerFromMongo`. Demonstrates the **CQRS-lite** pattern: Postgres is the source of truth; Mongo is the denormalized projection optimized for read-heavy paths (e.g. customer dashboard aggregating all orders).
- `?status=X` reads from Postgres.

### 4. Change status — `PUT /api/orders/{id}/status`
1. Reads from Postgres.
2. Updates status, re-saves in Postgres.
3. Re-mirrors to Mongo.
4. Re-warms cache.
5. Publishes `OrderEventPublisher.publishStatusChanged` to Kafka.

### 5. Consume events — `OrderEventConsumer`
- Kafka listener that processes `OrderCreated` / `OrderStatusChanged` events published by the service itself or by other services.

### 6. Order enriched with legacy system — `GET /api/orders/{id}/legacy-data`
Composes the canonical order with data from a legacy HTTP system (`LegacyHttpClient` → `WebClient`):
1. Fetches the order via `service.get(id)` (cache-aside Redis → Postgres).
2. Calls `GET /customers/{customerId}/profile` on the legacy system.
3. Returns `{ order, legacyPayload, source }` in a single response.
4. `onErrorResume` on any legacy failure: the order is still returned with `source: "LEGACY_UNAVAILABLE"` — the API doesn't go down with the legacy.

### 7. Legacy integrations
- `LegacyHttpClient` (WebClient) — HTTP calls to legacy systems. **Used in production by the `/legacy-data` endpoint.**
- `LegacyErpClient` / `LegacyErpService` / `LegacyErpServiceImpl` (Apache CXF) — SOAP/XML to ERP (bean injected, available for use).
- `SftpClient` (JSch) — file exchange via SFTP (factory bean, lazy connection).

## Why each datastore?

- **Postgres (R2DBC)**: system of record. Rigid schema, transactions, JOINs. Where data is **born** (`POST /api/orders` → `INSERT`).
- **Redis**: hot-path cache. Sub-ms latency for `GET /api/orders/{id}`. Configurable TTL via `app.cache.orders.ttl` (ISO-8601, default `PT5M`). Inspected at runtime via `GET /api/orders/info`.
- **MongoDB**: **denormalized read model** (CQRS-lite). Each `OrderView` document is already display-ready (no JOIN). Consumed via `?source=mongo`.
- **Kafka**: event bus to propagate changes to other services (Notification, Analytics, Billing) without direct coupling.

## Cache TTL

Redis is a **cache-aside** layer (the consumer doesn't choose — `OrderService` reads Redis first, falls back to Postgres on miss and re-populates the cache). The TTL is configurable per environment:

```yaml
app:
  cache:
    orders:
      ttl: PT5M  # ISO-8601: PT30S=30s, PT5M=5m, PT1H=1h
```

Override at runtime: `APP_CACHE_ORDERS_TTL=PT10M java -jar app.jar`.

**Trade-off:**
- **Short TTL (e.g. 30s)**: more consistent (less stale data), more Postgres reads, lower cache hit ratio.
- **Long TTL (e.g. 1h)**: less Postgres load, higher cache hit ratio, more stale data possible.
- The cache is also **explicitly updated on every write** (`changeStatus` calls `cache.put`), so TTL mostly matters for natural expiration between writes — not for stale state after mutations.

Inspect the active TTL at runtime:
```bash
curl http://localhost:8080/api/orders/info
# → {"status":"ok","cache.orders.ttl":"PT5M"}
```

---

## Endpoints

| Method | Path                              | Description                                |
|--------|-----------------------------------|--------------------------------------------|
| GET    | `/api/orders/health`              | Liveness                                   |
| GET    | `/api/orders/info`                | Service info + current cache TTL           |
| POST   | `/api/orders`                     | Create order                               |
| GET    | `/api/orders/{id}`                | Read order (cache-aside)                   |
| GET    | `/api/orders/{id}/legacy-data`    | Read order enriched with legacy HTTP data |
| GET    | `/api/orders?customerId=X`        | List by customer (default: Postgres)       |
| GET    | `/api/orders?customerId=X&source=mongo` | List by customer from Mongo read model |
| GET    | `/api/orders?status=X`            | List by status                             |
| PUT    | `/api/orders/{id}/status`         | Update status                              |
| GET    | `/actuator/health`                | Spring Actuator health check               |
| GET    | `/actuator/prometheus`            | Prometheus metrics                         |
| GET    | `/v3/api-docs`                    | OpenAPI spec (springdoc)                   |

---

## What's in each subfolder

### Root
- `pom.xml` — Maven (Java 21, Spring Boot 3.3.4, all dependencies above).
- `Dockerfile` — app image.
- `Jenkinsfile` — Jenkins pipeline.
- `.gitlab-ci.yml` — GitLab CI pipeline.
- `.gitignore` — IDEs, build, classes.
- `README.md` — quickstart + endpoints.
- `docs/adr/0001-postgres-r2dbc.md` — ADR: Postgres as system of record via R2DBC.
- `docs/adr/0002-mongo-projection.md` — ADR: Mongo as projection/read model.
- `docs/adr/0003-kafka-fanout.md` — ADR: async fan-out via Kafka.
- `docs/adr/0004-gc-tuning.md` — ADR: GC tuning (G1GC, 200ms pause target).
- `docs/system-design/README.md` — architectural overview.
- `.idea/` — IntelliJ config.
- `target/` — Maven build (compiled classes, surefire reports).

### `src/main/java/com/codesolutions/`
- `Application.java` — entry point `@SpringBootApplication`.

### `src/main/java/com/codesolutions/api/`
- `OrderController.java` — reactive REST controller (Mono/Flux), Bean Validation, record-based DTOs.
- `OrderService.java` — service: orchestrates R2DBC + Mongo + Redis + Kafka, cache-aside and write fan-out patterns, `onErrorResume` for resilience.
- `ApiExceptionHandler.java` — global error handler.

### `src/main/java/com/codesolutions/config/`
- `IntegrationConfig.java` — legacy integration beans (SFTP, SOAP, HTTP).
- `RedisConfig.java` — reactive Redis configuration.

### `src/main/java/com/codesolutions/domain/`
- `Order.java` — domain model (Java 21 record).

### `src/main/java/com/codesolutions/persistence/`
- `OrderEntity.java` — R2DBC entity mapped to the `orders` table.
- `OrderRepository.java` — `ReactiveCrudRepository` with queries by `customerId` and `status`.

### `src/main/java/com/codesolutions/mongo/`
- `OrderView.java` — denormalized Mongo document (read model).
- `OrderViewRepository.java` — `ReactiveMongoRepository`.

### `src/main/java/com/codesolutions/redis/`
- `OrderCache.java` — reactive cache wrapper (manual cache-aside).

### `src/main/java/com/codesolutions/kafka/`
- `OrderEventPublisher.java` — publishes `OrderCreated` / `OrderStatusChanged` to Kafka.
- `OrderEventConsumer.java` — consumes order events.

### `src/main/java/com/codesolutions/integrations/`
- `http/LegacyHttpClient.java` — `WebClient` for legacy REST APIs.
- `soap/LegacyErpClient.java` — SOAP interface.
- `soap/LegacyErpService.java` — legacy ERP service contract.
- `soap/LegacyErpServiceImpl.java` — CXF implementation.
- `sftp/SftpClient.java` — SFTP client (JSch).

### `src/main/resources/`
- `application.yml` — configs for Postgres, Mongo, Redis, Kafka, Actuator, JVM.
- `schema.sql` — DDL for the `orders` table.

### `src/test/java/com/codesolutions/`
- `api/OrderControllerTest.java` — WebFlux slice tests.
- `api/OrderServiceTest.java` — service unit tests.
- `kafka/OrderEventPublisherTest.java` — Kafka publisher tests.
- `kafka/KafkaContractTestConfig.java` — Testcontainers test config.
- `integrations/soap/LegacyErpServiceTest.java` — SOAP client tests.

---

## How to run locally

```bash
docker run -d --name pg     -p 5432:5432  -e POSTGRES_PASSWORD=postgres postgres:15
docker run -d --name mongo  -p 27017:27017 mongo:7
docker run -d --name redis  -p 6379:6379  redis:7
docker run -d --name kafka  -p 9092:9092  apache/kafka:3.7.0   # see README for envs

mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"c-1","amount":99.9,"currency":"USD"}'

curl http://localhost:8080/api/orders/<id>
```

## How to test

```bash
mvn test      # unit + slice (no infra)
mvn verify    # E2E with Testcontainers (needs Docker)
```
