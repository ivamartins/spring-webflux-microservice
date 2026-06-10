# spring-webflux-microservice — Visão geral e fluxo

Microsserviço reativo de **gestão de pedidos** (Orders), construído em **Java 21 + Spring Boot 3.3 + WebFlux**. Java 21, Spring Boot/WebFlux, microsserviços, mensageria Kafka, integrações legadas (HTTP/SOAP/SFTP), CI/CD (GitLab + Jenkins).

## Stack (com versões)

- **Java 21** (records, pattern matching, virtual-thread-ready)
- **Spring Boot 3.3.4** + **Spring WebFlux** (reativo, não-bloqueante)
- **Spring Data R2DBC** (Postgres R2DBC driver `1.0.5.RELEASE`) → Postgres (system of record)
- **Spring Data MongoDB Reactive** → read model desnormalizado
- **Spring Data Redis Reactive** → cache de hot-path
- **Spring Kafka** → fan-out de eventos
- **Apache CXF 4.0.5** (`cxf-spring-boot-starter-jaxws`) → cliente SOAP
- **JSch 0.1.55** → cliente SFTP
- **WebClient** (Spring) → cliente HTTP legado
- **Micrometer + Prometheus + Spring Actuator** → observabilidade
- **springdoc-openapi 2.6.0** → OpenAPI/Swagger UI
- **Testcontainers 1.20.2** (junit-jupiter, postgresql, mongodb, kafka) → E2E
- **OkHttp MockWebServer 4.12.0** → mocks HTTP em testes
- **JVM tuning**: G1GC, `MaxGCPauseMillis=200`, string dedup, GC logs
- **CI**: `.gitlab-ci.yml` + `Jenkinsfile`

---

## Fluxo principal

### 1. Criar pedido — `POST /api/orders`
Entrada validada (Bean Validation) → `OrderService.create`:
1. Constrói o domínio `Order` (UUID, customerId, amount, currency ISO-3, status, createdAt).
2. Persiste em **Postgres** via `OrderRepository` (R2DBC).
3. Espelha em **MongoDB** (`mirrorToMongo`) — read model.
4. Aquece cache em **Redis** (`warmCache`).
5. Publica evento `OrderEventPublisher.publishCreated` no **Kafka**.
6. `onErrorResume` em qualquer falha de fan-out: a API continua respondendo mesmo se Mongo/Kafka estiverem degradados.

### 2. Buscar pedido — `GET /api/orders/{id}` (cache-aside)
- Tenta **Redis** primeiro.
- Em miss, busca no **Postgres** (R2DBC), grava no cache e devolve.

### 3. Listar pedidos — `GET /api/orders?customerId=X` ou `?status=X`
- Lê direto do **Postgres** (R2DBC), retorna `Flux<Order>`.

### 4. Mudar status — `PUT /api/orders/{id}/status`
1. Lê do Postgres.
2. Atualiza status, regrava no Postgres.
3. Re-espelha no Mongo.
4. Re-aquece cache.
5. Publica `OrderEventPublisher.publishStatusChanged` no Kafka.

### 5. Consumir eventos — `OrderEventConsumer`
- Listener Kafka para processar eventos `OrderCreated` / `OrderStatusChanged` publicados pelo próprio serviço ou por outros.

### 6. Integrações legadas
- `LegacyHttpClient` (WebClient) — chamadas HTTP a sistemas legados.
- `LegacyErpClient` / `LegacyErpService` / `LegacyErpServiceImpl` (Apache CXF) — SOAP/XML para ERP.
- `SftpClient` (JSch) — troca de arquivos via SFTP.

---

## Endpoints

| Método | Path                              | Descrição                                |
|--------|-----------------------------------|------------------------------------------|
| GET    | `/api/orders/health`              | Liveness                                 |
| POST   | `/api/orders`                     | Criar pedido                             |
| GET    | `/api/orders/{id}`                | Ler pedido (cache-aside)                 |
| GET    | `/api/orders?customerId=X`        | Listar por cliente                       |
| GET    | `/api/orders?status=X`            | Listar por status                        |
| PUT    | `/api/orders/{id}/status`         | Atualizar status                         |
| GET    | `/actuator/health`                | Health check (Spring Actuator)           |
| GET    | `/actuator/prometheus`            | Métricas Prometheus                      |
| GET    | `/v3/api-docs`                    | OpenAPI spec (springdoc)                 |

---

## O que tem em cada subpasta

### Raiz
- `pom.xml` — Maven (Java 21, Spring Boot 3.3.4, todas as deps acima).
- `Dockerfile` — imagem do app.
- `Jenkinsfile` — pipeline Jenkins.
- `.gitlab-ci.yml` — pipeline GitLab CI.
- `.gitignore` — IDEs, build, classes.
- `README.md` — quickstart + endpoints.
- `docs/adr/0001-postgres-r2dbc.md` — ADR: Postgres como system of record via R2DBC.
- `docs/adr/0002-mongo-projection.md` — ADR: Mongo como projeção/read model.
- `docs/adr/0003-kafka-fanout.md` — ADR: fan-out assíncrono via Kafka.
- `docs/adr/0004-gc-tuning.md` — ADR: tuning de GC (G1GC, 200ms pause target).
- `docs/system-design/README.md` — visão arquitetural.
- `.idea/` — config do IntelliJ.
- `target/` — build Maven (classes compiladas, surefire reports).

### `src/main/java/com/codesolutions/`
- `Application.java` — entry point `@SpringBootApplication`.

### `src/main/java/com/codesolutions/api/`
- `OrderController.java` — controller REST reativo (Mono/Flux), validação Bean Validation, record-based DTOs.
- `OrderService.java` — service: orquestra R2DBC + Mongo + Redis + Kafka, padrões cache-aside e write fan-out, `onErrorResume` para resiliência.
- `ApiExceptionHandler.java` — handler global de erros.

### `src/main/java/com/codesolutions/config/`
- `IntegrationConfig.java` — beans das integrações legadas (SFTP, SOAP, HTTP).
- `RedisConfig.java` — configuração do Redis reativo.

### `src/main/java/com/codesolutions/domain/`
- `Order.java` — modelo de domínio (record Java 21).

### `src/main/java/com/codesolutions/persistence/`
- `OrderEntity.java` — entidade R2DBC mapeada na tabela `orders`.
- `OrderRepository.java` — `ReactiveCrudRepository` com queries por `customerId` e `status`.

### `src/main/java/com/codesolutions/mongo/`
- `OrderView.java` — documento Mongo desnormalizado (read model).
- `OrderViewRepository.java` — `ReactiveMongoRepository`.

### `src/main/java/com/codesolutions/redis/`
- `OrderCache.java` — wrapper de cache reativo (cache-aside manual).

### `src/main/java/com/codesolutions/kafka/`
- `OrderEventPublisher.java` — publica `OrderCreated` / `OrderStatusChanged` no Kafka.
- `OrderEventConsumer.java` — consome eventos de pedidos.

### `src/main/java/com/codesolutions/integrations/`
- `http/LegacyHttpClient.java` — `WebClient` para APIs REST legadas.
- `soap/LegacyErpClient.java` — interface SOAP.
- `soap/LegacyErpService.java` — contrato do serviço ERP legado.
- `soap/LegacyErpServiceImpl.java` — implementação CXF.
- `sftp/SftpClient.java` — cliente SFTP (JSch).

### `src/main/resources/`
- `application.yml` — configs de Postgres, Mongo, Redis, Kafka, Actuator, JVM.
- `schema.sql` — DDL da tabela `orders`.

### `src/test/java/com/codesolutions/`
- `api/OrderControllerTest.java` — testes WebFlux slice.
- `api/OrderServiceTest.java` — testes unitários do service.
- `kafka/OrderEventPublisherTest.java` — testes do publisher Kafka.
- `kafka/KafkaContractTestConfig.java` — config de testes com Testcontainers.
- `integrations/soap/LegacyErpServiceTest.java` — testes do cliente SOAP.

---

## Como rodar localmente

```bash
docker run -d --name pg     -p 5432:5432  -e POSTGRES_PASSWORD=postgres postgres:15
docker run -d --name mongo  -p 27017:27017 mongo:7
docker run -d --name redis  -p 6379:6379  redis:7
docker run -d --name kafka  -p 9092:9092  apache/kafka:3.7.0   # ver README p/ envs

mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"c-1","amount":99.9,"currency":"USD"}'

curl http://localhost:8080/api/orders/<id>
```

## Como testar

```bash
mvn test      # unit + slice (sem infra)
mvn verify    # E2E com Testcontainers (precisa Docker)
```
