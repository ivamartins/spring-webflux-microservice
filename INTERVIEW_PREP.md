# Interview Prep — Java SR (Híbrido) / IM-RH

Material de estudo baseado no `spring-webflux-microservice`. Para cada item da vaga, mostro **o que é, onde está no código, um exemplo real e como explicar em entrevista**.

> Vaga: **Desenvolvedor Java SR (Híbrido)** — IM-RH, Eldorado do Sul/RS
> Stack requerida: Java 21, JVM tuning, REST APIs, SQL/NoSQL, CI/CD (GitLab + Jenkins), Spring Boot + WebFlux, Kafka, microsserviços, integrações (HTTP/SOAP/SFTP), System Design, evolução de arquitetura.

---

## Índice

1. [Java 21](#1-java-21)
2. [JVM, GC, tuning, profiling](#2-jvm-gc-tuning-profiling)
3. [APIs REST](#3-apis-rest)
4. [SQL e NoSQL](#4-sql-e-nosql)
5. [CI/CD — GitLab CI + Jenkins](#5-cicd--gitlab-ci--jenkins)
6. [Spring Boot + WebFlux](#6-spring-boot--webflux)
7. [Microsserviços](#7-microsservicos)
8. [Mensageria Kafka](#8-mensageria-kafka)
9. [Integrações (HTTP/SOAP/SFTP)](#9-integracoes-httpsoapsftp)
10. [System Design + evolução de arquitetura](#10-system-design--evolucao-de-arquitetura)

---

## 1. Java 21

### O que a vaga pede
> "Domínio em Java 21, JVM, garbage collection, tuning e profiling"

### O que saber de cor
- **Records** (desde 14): DTOs imutáveis, menos boilerplate.
- **Pattern matching** (21): `switch` com tipos, `instanceof` direto.
- **Virtual threads** (21): substituem reactive ou threads pool para I/O bound. Não use junto com WebFlux (escolha um).
- **Sequenced collections** (21): `getFirst()`, `getLast()`, `reversed()` em List/Map/Set.
- **String templates** (preview em 21, stable em 22).

### Exemplo real no projeto

**Record para DTO de entrada** (em [`OrderController.java`](src/main/java/com/codesolutions/api/OrderController.java)):
```java
public record CreateOrderRequest(
    @NotBlank @Size(max = 64) String customerId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency
) {}
```

**Record para resposta composta** (em [`OrderService.java`](src/main/java/com/codesolutions/api/OrderService.java)):
```java
public record OrderWithLegacy(Order order, String legacyPayload, String source) {}
```

### Como explicar em entrevista
> "Records me dão imutabilidade, equals/hashCode/toString automáticos e zero boilerplate. Para DTOs de request/response ou value objects, são perfeitos. Para entidades JPA, ainda precisa de classe mutável (Hibernate exige)."
>
> "Virtual threads (Java 21) mudaram o jogo: agora dá pra escrever código bloqueante simples e escalar com milhões de threads. Mas **se misturar com WebFlux, você perde a reatividade** — escolha um caminho."

### Como **NÃO** usar
```java
// ERRADO: tentar usar virtual thread em WebFlux
Mono.fromCallable(() -> {
    Thread.startVirtualThread(() -> doBlockingCall());  // quebra o event loop
    return result;
});
```

### Material extra
- [JEP 440: Records](https://openjdk.org/jeps/440)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 441: Pattern Matching for switch](https://openjdk.org/jeps/441)

---

## 2. JVM, GC, tuning, profiling

### O que a vaga pede
> "JVM, garbage collection, tuning e profiling"

### O que saber de cor
- **Algoritmos de GC**: Serial, Parallel, G1 (default desde 9), ZGC (low-pause), Shenandoah.
- **G1GC tuning**: `MaxGCPauseMillis`, `InitiatingHeapOccupancyPercent`, regions.
- **ZGC**: pausas < 1ms mesmo com heaps grandes (>1TB).
- **Comandos**: `jstat -gc <pid>`, `jmap -heap <pid>`, `jstack`, `jcmd <pid> GC.heap_dump`.
- **Ferramentas de profiling**: async-profiler (recomendado), JFR (Java Flight Recorder), VisualVM.
- **JFR + JMC**: low-overhead, production-safe.

### Exemplo real no projeto

**GC tuning aplicado** (em [`application.yml`](src/main/resources/application.yml)):
```yaml
gc:
  jvm-options: |
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+UseStringDeduplication
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/tmp/heap.hprof
    -Xlog:gc*,gc+heap=info,safepoint:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=50m
```

**Explicação por flag:**
| Flag | Por quê |
|---|---|
| `UseG1GC` | Default Java 21; balanceia throughput e latência |
| `MaxGCPauseMillis=200` | SLA de 200ms — pausa típica de microsserviço |
| `UseStringDeduplication` | Reduz heap em apps com muitas strings (logs, JSON) |
| `HeapDumpOnOutOfMemoryError` | Em prod, captura automática antes de crashar |
| `Xlog:gc*` | Log estruturado de GC; rotacionado a 50MB×5 |

### Como explicar em entrevista
> "G1GC divide o heap em regiões (1-32MB cada) e faz coletas incrementais pra atingir a pausa alvo. Em microsserviço, alvo de 200ms é razoável. Se precisar de pausas sub-ms, migro pra ZGC."
>
> "Em prod, **nunca** afino GC no chute. Coleto com JFR por 24h, analiso com Java Mission Control, identifico padrões (allocation rate, pause distribution), e só então mudo flags."

### Como **NÃO** fazer
```bash
# ERRADO: tuning no escuro
java -Xms64g -Xmx64g -XX:+UseZGC -jar app.jar
# Resultado: metaspace cheio, swap, OOM, ou pior — sem ganho
```

### Material extra
- [G1GC tuning guide](https://www.oracle.com/technetwork/tutorials/tutorials-1876574.html)
- [async-profiler](https://github.com/async-profiler/async-profiler)

---

## 3. APIs REST

### O que a vaga pede
> "APIs REST"

### O que saber de cor
- **Verbos e status codes**: 200/201/204 (sucesso), 400/422 (validação), 401/403 (auth), 404, 409 (conflito), 500/503 (erro servidor).
- **Bean Validation** (jakarta.validation): `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Min`, `@DecimalMin`, `@Email`, custom validators.
- **OpenAPI / Swagger**: `springdoc-openapi` (padrão Spring Boot 3).
- **Versionamento**: URI (`/v1/orders`), header (`Accept: application/vnd.api.v2+json`), query param. URI é o mais comum.
- **HATEOAS**: hipermídia. Raro em microserviços; mais útil em APIs públicas.
- **RFC 7807 (Problem Details)**: padrão de erro com `type`, `title`, `status`, `detail`, `instance`.
- **Idempotência**: `Idempotency-Key` header em POST/PUT pra evitar duplicação.

### Exemplo real no projeto

**REST controller reativo** (em [`OrderController.java`](src/main/java/com/codesolutions/api/OrderController.java)):
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    public Mono<ResponseEntity<Order>> create(@Valid @RequestBody CreateOrderRequest req) {
        return service.create(req.customerId(), req.amount(), req.currency())
                .map(o -> ResponseEntity.status(HttpStatus.CREATED).body(o));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Order>> get(@PathVariable UUID id) {
        return service.get(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
```

**Bean Validation nos DTOs** (mesmo arquivo):
```java
public record CreateOrderRequest(
    @NotBlank @Size(max = 64) String customerId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency
) {}
```

**OpenAPI automático** (em [`application.yml`](src/main/resources/application.yml)):
```yaml
# springdoc já está nas deps; acessar /v3/api-docs e /swagger-ui.html
```

### Como explicar em entrevista
> "REST não é só JSON sobre HTTP. É contrato: status codes semânticos, versionamento explícito, validação no boundary, documentação viva (OpenAPI), erros estruturados (RFC 7807)."
>
> "Em microsserviços, **HATEOAS é overkill** — quem consome é outro time, não um browser explorando. OpenAPI + versionamento por URI é o sweet spot."

### Tabela de endpoints do projeto
| Método | Path | O que demonstra |
|---|---|---|
| `POST /api/orders` | Bean Validation + 201 Created |
| `GET /api/orders/{id}` | 200 OK / 404 Not Found |
| `GET /api/orders/{id}/legacy-data` | Composição com sistema externo |
| `GET /api/orders?source=mongo` | Leitura de read model (CQRS) |
| `PUT /api/orders/{id}/status` | 200 OK com estado atualizado |

---

## 4. SQL e NoSQL

### O que a vaga pede
> "Bancos de dados SQL e NoSQL"

### O que saber de cor
- **Postgres**: ACID, transações, JOINs, CTEs, window functions, índices parciais, EXPLAIN ANALYZE.
- **R2DBC vs JPA**: R2DBC é reativo (não-bloqueante); JPA é bloqueante mas ecossistema gigante.
- **MongoDB**: documentos, agregation framework, índices, transações multi-doc só em replica set.
- **Redis**: cache, pub/sub, streams, sorted sets, TTL, eviction policies.
- **Quando usar cada um**:
  - SQL: dados transacionais, fortes relações, integridade referencial.
  - NoSQL doc (Mongo): dados desnormalizados, schema flexível, read-heavy.
  - KV (Redis): cache, sessões, leaderboard, rate limit.

### Exemplo real no projeto

**Postgres via R2DBC** (em [`application.yml`](src/main/resources/application.yml)):
```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/webflux
    username: postgres
    password: postgres
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
```

**Repository reativo** (em [`OrderRepository.java`](src/main/java/com/codesolutions/persistence/OrderRepository.java)):
```java
public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, UUID> {
    Flux<OrderEntity> findByCustomerId(String customerId);
    Flux<OrderEntity> findByStatus(String status);
}
```

**MongoDB read model** (em [`OrderViewRepository.java`](src/main/java/com/codesolutions/mongo/OrderViewRepository.java)):
```java
public interface OrderViewRepository extends ReactiveMongoRepository<OrderView, String> {
    Flux<OrderView> findByCustomerId(String customerId);
    Flux<OrderView> findByStatus(String status);
}
```

### Como explicar em entrevista
> "Postgres para o **system of record** (transações, integridade). Mongo para o **read model** (dashboard, queries desnormalizadas). Redis para **cache** (latência sub-ms). Cada um com seu papel — não é over-engineering, é separação de concerns."

### Como **NÃO** fazer
```java
// ERRADO: usar Mongo para tudo "porque escala"
mongoRepo.save(new Order(...));  // sem transações, sem integridade
// Quando precisar de JOIN, você vai sofrer
```

---

## 5. CI/CD — GitLab CI + Jenkins

### O que a vaga pede
> "CI/CD, utilizando ferramentas GitLab CI e/ou Jenkins"

### O que saber de cor
- **Stages**: build → test → scan → package → deploy → smoke.
- **GitLab CI** (`.gitlab-ci.yml`): stages, jobs, artifacts, environments, manual gates, `only/except` de branches.
- **Jenkins** (`Jenkinsfile`): stages, steps, agents, parameters, post-actions, `when` conditionals.
- **Estratégias de deploy**: rolling, blue/green, canary.
- **Rollback**: reverter imagem anterior; GitOps (ArgoCD/Flux) é o state-of-the-art.
- **Segurança**: nunca credenciais em plaintext. Use Vault, GitLab CI variables masked, Jenkins Credentials.

### Exemplo real no projeto

**GitLab CI** (em [`.gitlab-ci.yml`](.gitlab-ci.yml)):
```yaml
stages:
  - build
  - test
  - package
  - deploy

build:
  stage: build
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn -B compile

test:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn -B test
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml

package:
  stage: package
  script:
    - mvn -B package -DskipTests
  artifacts:
    paths:
      - target/*.jar
```

**Jenkins** (em [`Jenkinsfile`](Jenkinsfile)):
```groovy
pipeline {
  agent any
  tools { jdk 'JDK21'; maven 'Maven 3.9' }
  stages {
    stage('Build') { steps { sh 'mvn -B compile' } }
    stage('Test')  { steps { sh 'mvn -B test' } }
    stage('Package') { steps { sh 'mvn -B package -DskipTests' } }
    stage('Deploy') {
      when { branch 'main' }
      steps { sh './deploy.sh' }
    }
  }
  post {
    always { junit 'target/surefire-reports/TEST-*.xml' }
  }
}
```

### Como explicar em entrevista
> "Pipeline tem **gates de qualidade**: build falha → ninguém avança. Test falha → bloqueia merge. Scan de segurança (Snyk, Trivy) → bloqueia deploy. Em prod, deploy é automatizado com **rollback automático** se health check falhar."

---

## 6. Spring Boot + WebFlux

### O que a vaga pede
> "Vivência com Spring Boot e WebFlux, aplicados em arquiteturas de microsserviços"

### O que saber de cor
- **WebFlux vs MVC**: MVC = thread-per-request, bloqueante. WebFlux = event-loop (Netty), não-bloqueante, backpressure.
- **Quando usar WebFlux**:
  - Muitas chamadas externas (HTTP/DB/messaging).
  - Latência baixa consistente.
  - Streaming de dados.
- **Quando NÃO usar**:
  - CPU-bound (WebFlux não ajuda).
  - Ecossistema bloqueante (JDBC, JPA, drivers legados).
- **Project Reactor**: `Mono<T>` (0..1), `Flux<T>` (0..N), operadores (`map`, `flatMap`, `zip`, `merge`, `switchIfEmpty`).
- **Schedulers**: `Schedulers.boundedElastic()` para blocking, `Schedulers.parallel()` para CPU.
- **Backpressure**: estratégia `BUFFER`, `DROP`, `LATEST`, `ERROR`.

### Exemplo real no projeto

**Cache-aside com Reactor** (em [`OrderService.java`](src/main/java/com/codesolutions/api/OrderService.java)):
```java
public Mono<Order> get(UUID id) {
    return cache.get(id)
        .switchIfEmpty(
            repo.findById(id)
                .map(OrderEntity::toDomain)
                .flatMap(o -> cache.put(o).thenReturn(o))
        );
}
```

**Tratamento de erro** (mesmo arquivo):
```java
return repo.save(...)
    .flatMap(this::mirrorToMongo)
    .flatMap(this::warmCache)
    .flatMap(kafkaPublisher::publishCreated)
    .onErrorResume(e -> Mono.just(order));  // API continua viva
```

### Como explicar em entrevista
> "WebFlux brilha em I/O-bound. O custo é complexidade: você não pode bloquear o event loop. Se a chamada externa é bloqueante, embrulha em `Mono.fromCallable` + `subscribeOn(Schedulers.boundedElastic())`."

### Como **NÃO** fazer
```java
// ERRADO: bloquear event loop
@GetMapping("/slow")
public Mono<String> slow() {
    return Mono.just(callBlockingHttp());  // bloqueia o event loop!
}

// CERTO: delegar para thread pool
@GetMapping("/slow")
public Mono<String> slow() {
    return Mono.fromCallable(this::callBlockingHttp)
        .subscribeOn(Schedulers.boundedElastic());
}
```

---

## 7. Microsserviços

### O que a vaga pede
> "Arquiteturas de microsserviços e mensageria Kafka"

### O que saber de cor
- **Bounded context**: cada MS é dono de um domínio.
- **Comunicação síncrona**: REST, gRPC, GraphQL.
- **Comunicação assíncrona**: Kafka, RabbitMQ, NATS.
- **Service discovery**: Eureka, Consul, K8s DNS.
- **API Gateway**: Kong, Spring Cloud Gateway, AWS API Gateway.
- **Circuit breaker**: Resilience4j, Hystrix (deprecated).
- **Saga pattern**: transação distribuída sem 2PC — coreografia (eventos) ou orquestração (coordenador).
- **Observability**: métricas (Micrometer/Prometheus), logs (ELK), traces (OpenTelemetry/Jaeger).
- **Outbox pattern**: garante atomicidade entre DB e mensagem.

### Exemplo real no projeto

**Comunicação assíncrona via Kafka** (em [`OrderEventPublisher.java`](src/main/java/com/codesolutions/kafka/OrderEventPublisher.java)):
```java
@Service
public class OrderEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private static final String TOPIC = "orders.events";

    public Mono<Order> publishCreated(Order order) {
        return Mono.fromFuture(
            kafka.send(TOPIC, order.id().toString(), toJson(order))
        ).thenReturn(order);
    }
}
```

**Consumer reativo** (em [`OrderEventConsumer.java`](src/main/java/com/codesolutions/kafka/OrderEventConsumer.java)):
```java
@KafkaListener(topics = "orders.events", groupId = "webflux-ms")
public void onMessage(String payload) {
    OrderEvent evt = parseEvent(payload);
    // processa evento (notificação, analytics, billing)
}
```

### Como explicar em entrevista
> "Microsserviço não é 'dividir monolito'. É ownership de bounded context, deploy independente, falha isolada, equipe autônoma. O custo é分布式 transação, observability e governança de contrato (API + schema)."
>
> "Em prod, o **outbox pattern** evita o problema clássico: 'salvei no DB mas o Kafka caiu'. A solução: salva o evento numa tabela `outbox` na mesma transação, e um worker envia pro Kafka depois."

---

## 8. Mensageria Kafka

### O que a vaga pede
> "Mensageria Kafka"

### O que saber de cor
- **Conceitos**: broker, topic, partition, consumer group, offset, replication factor.
- **Garantias**:
  - **At most once**: commit antes de processar. Rápido, pode perder mensagens.
  - **At least once**: commit depois de processar. Pode duplicar (idempotência no consumer).
  - **Exactly once**: Kafka Streams / transações Kafka. Complexo.
- **Partições**: ordenação garantida **dentro** de uma partição. Paralelização **entre** partições.
- **Chave de partição**: `key` decide partição. Mesma key = mesma partição = ordem preservada.
- **Schema evolution**: Schema Registry (Avro, Protobuf). Compatibilidade: backward, forward, full.
- **DLQ (Dead Letter Queue)**: tópico separado pra mensagens que falharam N vezes.

### Exemplo real no projeto

**Producer com KafkaTemplate** (em [`application.yml`](src/main/resources/application.yml)):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all  # espera todos os ISR confirmarem
      properties:
        enable.idempotence: true  # exatamente-um no producer
    consumer:
      group-id: webflux-ms
      auto-offset-reset: earliest
```

**Publicação reativa** (em [`OrderEventPublisher.java`](src/main/java/com/codesolutions/kafka/OrderEventPublisher.java)):
```java
public Mono<Order> publishCreated(Order order) {
    return Mono.fromFuture(
        kafka.send(TOPIC, order.id().toString(), toJson(order))
    ).thenReturn(order);
}
```

### Como explicar em entrevista
> "Kafka é **log distribuído**, não fila. Mensagens ficam lá por X dias (configurável), podem ser re-lidas por novos consumer groups. Isso é ótimo pra **event sourcing** e **change data capture**."

### Como **NÃO** fazer
```java
// ERRADO: usar Kafka como request/reply síncrono
Order order = kafka.send("orders.request", req).get(5, SECONDS);
// Latência alta, sem timeout decente, sem resposta clara
```

---

## 9. Integrações (HTTP/SOAP/SFTP)

### O que a vaga pede
> "Integrações com sistemas externos, via FTP, SFTP, HTTP, SOAP e soluções legadas"

### O que saber de cor
- **HTTP moderno**: `WebClient` (Spring), `HttpClient` (Java 11+), `OkHttp` (Kotlin/Android).
- **SOAP**: `Apache CXF`, `JAX-WS`, gerar stubs a partir do WSDL (`wsimport`).
- **SFTP**: `JSch` (legado mas funciona), `Apache Commons VFS`, `sshd-sftp` (Mina).
- **Resiliência**: timeout, retry com backoff exponencial, circuit breaker, fallback.
- **Idempotência**: client-generated ID + server-side dedup.
- **Async vs sync**: HTTP/SOAP podem ser reativos com WebClient; SFTP é sempre blocking (IO de arquivo).

### Exemplo real no projeto

**WebClient reativo** (em [`LegacyHttpClient.java`](src/main/java/com/codesolutions/integrations/http/LegacyHttpClient.java)):
```java
public class LegacyHttpClient {
    private final WebClient client;

    public LegacyHttpClient(String baseUrl) {
        this.client = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .build();
    }

    public Mono<String> get(String path) {
        return client.get().uri(path)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(5));  // timeout sempre!
    }
}
```

**SOAP/CXF** (em [`LegacyErpServiceImpl.java`](src/main/java/com/codesolutions/integrations/soap/LegacyErpServiceImpl.java)):
```java
@WebService(serviceName = "LegacyErpService", portName = "LegacyErpPort",
            targetNamespace = "http://legacy.codesolutions.com/")
public class LegacyErpServiceImpl implements LegacyErpService {
    @Override
    public CustomerStatus getCustomerStatus(String customerId) {
        // lógica de chamada ao ERP legado
    }
}
```

**SFTP/JSch** (em [`SftpClient.java`](src/main/java/com/codesolutions/integrations/sftp/SftpClient.java)):
```java
public class SftpClient implements AutoCloseable {
    private final ChannelSftp channel;

    public void upload(String remotePath, InputStream data) {
        channel.put(data, remotePath);
    }
    // ... download, list, etc.
}
```

**Resiliência com onErrorResume** (em [`OrderService.java`](src/main/java/com/codesolutions/api/OrderService.java)):
```java
public Mono<OrderWithLegacy> getWithLegacyData(UUID id) {
    return get(id).flatMap(order ->
        legacyHttp.get("/customers/" + order.customerId() + "/profile")
            .map(legacy -> new OrderWithLegacy(order, legacy, "OK"))
            .onErrorResume(e -> Mono.just(
                new OrderWithLegacy(order, "{}", "LEGACY_UNAVAILABLE: " + e.getMessage())
            ))
    );
}
```

### Como explicar em entrevista
> "Integração com legado é sobre **resiliência**, não sobre protocolo. O sistema legado vai cair, vai ficar lento, vai retornar dados sujos. Minha API precisa sobreviver a isso: timeout agressivo, retry com backoff, circuit breaker, fallback gracioso."
>
> "O `LegacyHttpClient` deste projeto tem timeout de 5s. Se o legado demora mais, eu corto e devolvo a API mesmo assim. **Não é o cliente que espera, é o usuário que desistiu.**"

### Como **NÃO** fazer
```java
// ERRADO: chamada bloqueante sem timeout
String response = restTemplate.getForObject(url, String.class);
// Se o legado travar, sua thread trava. 100 requests = 100 threads mortas.
```

---

## 10. System Design + evolução de arquitetura

### O que a vaga pede
> "Atuará em atividades de System Design, definição de soluções técnicas e evolução da arquitetura."

### O que saber de cor
- **Princípios SOLID**: Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion.
- **Design patterns**: Strategy, Factory, Builder, Observer, Decorator, Adapter, Repository.
- **Patterns de microserviço**: API Gateway, BFF (Backend for Frontend), Sidecar, Strangler Fig, Saga, Outbox, CQRS, Event Sourcing.
- **Trade-offs clássicos**:
  - **Latência vs consistência**: cache (rápido, stale) vs DB (lento, fresh).
  - **Acoplamento vs reuso**: shared library (reusa, acopla) vs duplicação (independe, duplica).
  - **Síncrono vs assíncrono**: REST (imediato, frágil) vs Kafka (eventual, resiliente).
  - **Strong consistency vs eventual consistency**: 2PC (lento, forte) vs event-driven (rápido, eventual).
- **CAP theorem**: Consistency, Availability, Partition tolerance — escolha 2.

### Exemplo real no projeto

**CQRS-lite no OrderService** (escrita no Postgres, leitura no Mongo):
```java
// WRITE: Postgres (system of record)
return repo.save(OrderEntity.fromDomain(order))
    .flatMap(this::mirrorToMongo)  // dual-write: também salva no Mongo
    .flatMap(this::warmCache)
    .flatMap(kafkaPublisher::publishCreated);

// READ: 2 fontes, consumidor escolhe
public Flux<Order> listByCustomer(String customerId) {
    return repo.findByCustomerId(customerId).map(OrderEntity::toDomain);  // Postgres
}

public Flux<Order> listByCustomerFromMongo(String customerId) {
    return mongoRepo.findByCustomerId(customerId).map(v -> /* ... */);  // Mongo
}
```

**Repository pattern + DI** (em [`OrderRepository.java`](src/main/java/com/codesolutions/persistence/OrderRepository.java)):
```java
public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, UUID> {
    Flux<OrderEntity> findByCustomerId(String customerId);
    Flux<OrderEntity> findByStatus(String status);
}
```

**Strategy pattern para storage** (em [`OrderService.java`](src/main/java/com/codesolutions/api/OrderService.java)):
```java
public Flux<Order> listByCustomer(String customerId) { /* Postgres */ }
public Flux<Order> listByCustomerFromMongo(String customerId) { /* Mongo */ }
```

### Como explicar em entrevista
> "System Design é sobre **trade-offs**, não soluções perfeitas. Em entrevista, eu mostro que considerei 2-3 alternativas, expliquei por que escolhi uma, e o que faria diferente se o requisito mudasse."
>
> "Exemplo: se o requisito fosse 'dashboard em tempo real com 1M de usuários', eu não escolheria Postgres + Mongo. Escolheria Redis Streams + projeção materializada, ou ClickHouse, ou Elastic. O importante é mostrar **raciocínio, não bala de prata**."

---

## 🧠 Simulado rápido de perguntas

### 1. "Por que WebFlux e não MVC?"
> "WebFlux brilha em I/O-bound. Este serviço faz muitas chamadas externas (Postgres, Mongo, Redis, Kafka, HTTP legado). MVC bloquearia uma thread por request. WebFlux mantém 1-2 threads (event loop) servindo milhares de requests. Trade-off: complexidade — qualquer chamada bloqueante quebra a vantagem."

### 2. "Como você lida com falhas em sistemas externos?"
> "Todas as chamadas externas passam por `onErrorResume`. Se o sistema legado cai, o endpoint `/legacy-data` devolve o pedido com `source: 'LEGACY_UNAVAILABLE'`. A API não cai junto. Timeout agressivo (5s) garante que não esperamos pra sempre."

### 3. "Por que você tem Postgres E Mongo?"
> "CQRS-lite. Postgres é o system of record (ACID, integridade). Mongo é o read model desnormalizado (queries rápidas para dashboard). O endpoint aceita `?source=postgres` (consistente) ou `?source=mongo` (eventualmente consistente, mais rápido). Trade-off explícito entre consistência e latência."

### 4. "Como você garante ordem de mensagens no Kafka?"
> "Kafka garante ordem **dentro de uma partição**. Uso a `key` igual ao `orderId` — todas as mensagens do mesmo pedido vão pra mesma partição, preservando ordem. Entre pedidos, ordem não é garantida (e não precisa ser)."

### 5. "Qual a diferença entre TTL e expiração natural?"
> "TTL é a janela de validade no cache. Expiração natural é quando o TTL expira e o Redis deleta. Mas se houver `cache.put` num write, o dado é atualizado **antes** do TTL expirar. Então TTL cobre o cenário de 'ninguém mexeu nesse dado por X tempo', não 'dado stale'."

### 6. "Como você testaria o sistema end-to-end?"
> "Três níveis: (1) **Unit** com Mockito+JUnit para services; (2) **Slice** com `@WebFluxTest` para controllers; (3) **E2E** com Testcontainers (`postgres`, `mongo`, `redis`, `kafka`) em `@SpringBootTest`. Para resiliência, injeto falhas com Chaos Monkey ou MockWebServer retornando 500."

### 7. "O que é outbox pattern?"
> "Quando você precisa salvar no DB E publicar no Kafka atomicamente. Solução: salva o evento numa tabela `outbox` na MESMA transação. Um worker separado lê o outbox e publica no Kafka. Se o worker cair, ele retenta. Sem outbox, você tem o problema 'DB comitou, Kafka não'."

### 8. "Como você dimensiona o pool de conexões?"
> "Depende. Para Postgres: `max-size: 20` é um começo. Em prod, eu dimensiono baseado em **Little's Law**: `concurrency = throughput × latency`. Se cada query leva 50ms e quero 1000 req/s, preciso de 50 conexões. Acima disso, gargalo no DB."

---

## 📚 Resumo de 1 frase por item

| Item | Resumo para entrevista |
|---|---|
| Java 21 | Records, pattern matching, virtual threads, sequenced collections |
| JVM/GC | G1GC default, tuning por SLA de pausa, JFR + JMC em prod |
| REST | Status codes semânticos, Bean Validation, OpenAPI, RFC 7807 |
| SQL/NoSQL | Postgres ACID, Mongo read model desnormalizado, Redis cache |
| CI/CD | GitLab CI + Jenkins com stages, gates de qualidade, deploy automatizado |
| Spring Boot + WebFlux | Reactor (Mono/Flux), backpressure, non-blocking I/O |
| Microsserviços | Bounded context, Kafka assíncrono, circuit breaker, outbox |
| Kafka | Log distribuído, partições, ordem por key, exactly-once via idempotência |
| Integrações | WebClient reativo, CXF/SOAP, JSch/SFTP, sempre com timeout + retry + fallback |
| System Design | Trade-offs explícitos, CQRS, event sourcing, saga, CAP |

**Boa entrevista!** 🎯
