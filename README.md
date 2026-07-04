# spring-webflux-microservice

> Part of the **Code Solutions Java Modernization Framework** product line. Reactive Java 21 + Spring Boot 3 + WebFlux reference microservice, with Kafka and R2DBC.

Production-grade **Java 21 + Spring Boot 3 + WebFlux** reactive microservice reference.

## Why this base

- **Reactive end-to-end** (WebFlux + R2DBC) — high throughput, non-blocking I/O
- **Java 21 LTS** with virtual threads ready
- **Kafka integration** for event-driven patterns
- **Reference architecture** for greenfield reactive services and modernization of blocking Spring MVC apps

## Quick start

**Prerequisites:** Java 21+ and Maven (or use the wrapper `./mvnw`).

```bash
./mvnw compile spring-boot:run
```

The app will start on `http://localhost:8080`.

## Run the tests

```bash
./mvnw test
```

## API endpoints

- `GET    /api/info` — app info
- `GET    /api/products` — list products (reactive, R2DBC)
- `POST   /api/products` — create product
- `GET    /api/events` — Kafka event stream (SSE)

## Tech stack

- Java 21 (LTS)
- Spring Boot 3
- Spring WebFlux (reactive)
- Spring Data R2DBC
- Apache Kafka
- Maven build tool

> **Português?** Veja [`README.pt-BR.md`](./README.pt-BR.md).

## See also

- **Related base**: [quarkus-java-base](https://github.com/ivamartins/quarkus-java-base), [java-product-api](https://github.com/ivamartins/java-product-api)
- **Product line**: [Java Modernization Framework](https://ivamartins.github.io/code-solutions-site/#produtos)
- **Code Solutions on LinkedIn**: [linkedin.com/company/code-solutions-it](https://www.linkedin.com/company/code-solutions-it/)
- **All Code Solutions open source**: [github.com/ivamartins](https://github.com/ivamartins)

## License

MIT — see `LICENSE`.
