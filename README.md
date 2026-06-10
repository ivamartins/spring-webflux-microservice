# spring-webflux-microservice

Production-grade **Java 21 + Spring Boot 3 + WebFlux** reference microservice. Covers the **Java Sr (Híbrido)** JD (Java 21, JVM tuning, REST APIs, SQL/NoSQL, CI/CD with GitLab + Jenkins, Spring Boot + WebFlux, Kafka, legacy integrations).

## Stack

- **Java 21** (records, pattern matching, virtual-thread-ready)
- **Spring Boot 3.3.4** + **Spring WebFlux** (reactive)
- **Spring Data R2DBC** (Postgres) — system of record
- **Spring Data MongoDB Reactive** — denormalized read model
- **Spring Data Redis Reactive** — hot-path cache
- **Spring Kafka** — event-driven fan-out
- **Apache CXF** — SOAP client
- **JSch** — SFTP client
- **WebClient** — HTTP legacy client
- **Micrometer + Prometheus + Actuator** — observability
- **JVM tuning**: G1GC, `MaxGCPauseMillis=200`, string dedup, GC logs
- **CI**: GitLab CI + Jenkinsfile

## How to run (local)

```bash
docker run -d --name pg -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:15
docker run -d --name mongo -p 27017:27017 mongo:7
docker run -d --name redis -p 6379:6379 redis:7
docker run -d --name kafka -p 9092:9092 \
    -e KAFKA_NODE_ID=1 \
    -e KAFKA_PROCESS_ROLES=broker,controller \
    -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
    -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
    -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
    -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
    apache/kafka:3.7.0

mvn spring-boot:run
```

Then:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"c-1","amount":99.9,"currency":"USD"}'

curl http://localhost:8080/api/orders/<id>
```

## How to test

```bash
# Unit + slice tests (no infra needed)
mvn test

# Full E2E with Testcontainers (requires Docker)
mvn verify
```

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET    | /api/orders/health       | liveness |
| POST   | /api/orders              | create |
| GET    | /api/orders/{id}         | read (cache-aside) |
| GET    | /api/orders?customerId=X | list by customer |
| GET    | /api/orders?status=X     | list by status |
| PUT    | /api/orders/{id}/status  | update status |
| GET    | /actuator/health         | Spring Actuator health |
| GET    | /actuator/prometheus     | Prometheus metrics |
| GET    | /v3/api-docs             | OpenAPI spec (springdoc) |

## Architecture

See `docs/system-design/README.md` and `docs/adr/*`.

## See also

- `quarkus-java-base` (Java portfolio)
- `akka-scala-base` (Scala/Akka — Senior SWE role)
- `scala-akka-aws-microservice` (Scala on AWS)
