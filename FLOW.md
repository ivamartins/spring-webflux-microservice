# Class interaction flow — spring-webflux-microservice

Quick visualization of how a request travels through the system, from user input down to the databases.

## 1. Create order — `POST /api/orders`

```
User (HTTP POST)
  └─> OrderController.create()              [api/OrderController]
        └─> OrderService.create()           [api/OrderService]
              ├─> OrderRepository.save()    [persistence/OrderRepository]       ──> Postgres (R2DBC)
              ├─> mirrorToMongo()           [OrderService]                       ──> MongoDB
              ├─> warmCache()               [OrderService]                       ──> Redis
              └─> OrderEventPublisher.publishCreated()  [kafka/]              ──> Kafka
```

**Summary path:**
`OrderController → OrderService → (OrderRepository + MongoRepository + RedisCache + KafkaPublisher)`

## 2. Get order — `GET /api/orders/{id}` (cache-aside)

```
User (HTTP GET)
  └─> OrderController.get()                [api/OrderController]
        └─> OrderService.get()             [api/OrderService]
              ├─> OrderCache.get()         [redis/OrderCache]                  ──> Redis
              │     └─ (cache HIT) return
              └─ (cache MISS)
                    └─> OrderRepository.findById()  [persistence/OrderRepository] ──> Postgres
                          └─> OrderCache.put()     [redis/OrderCache]            ──> Redis
```

**Summary path:**
`OrderController → OrderService → (Redis → Postgres → Redis)`

## 3. List orders — `GET /api/orders?customerId=X&source=postgres|mongo` or `?status=X`

```
User (HTTP GET)
  └─> OrderController.list()               [api/OrderController]
        ├─> source=postgres (default)
        │     └─> OrderService.listByCustomer()  [api/OrderService]
        │           └─> OrderRepository.findByCustomerId()  [persistence/OrderRepository] ──> Postgres
        └─> source=mongo
              └─> OrderService.listByCustomerFromMongo()  [api/OrderService]
                    └─> OrderViewRepository.findByCustomerId()  [mongo/OrderViewRepository] ──> MongoDB
```

## 4. Change status — `PUT /api/orders/{id}/status`

```
User (HTTP PUT)
  └─> OrderController.changeStatus()       [api/OrderController]
        └─> OrderService.changeStatus()    [api/OrderService]
              ├─> OrderRepository.findById()  [persistence/OrderRepository]     ──> Postgres
              ├─> OrderRepository.save()      [persistence/OrderRepository]     ──> Postgres
              ├─> mirrorToMongo()           [OrderService]                       ──> MongoDB
              ├─> warmCache()               [OrderService]                       ──> Redis
              └─> OrderEventPublisher.publishStatusChanged()  [kafka/]        ──> Kafka
```

**Summary path:**
`OrderController → OrderService → (Postgres → Postgres → Mongo → Redis → Kafka)`

## 5. Consume Kafka event — `OrderEventConsumer`

```
Kafka (OrderCreated / OrderStatusChanged)
  └─> OrderEventConsumer.onMessage()       [kafka/OrderEventConsumer]
        └─> ... (custom handler, currently a placeholder)
```

## 6. Order enriched with legacy system — `GET /api/orders/{id}/legacy-data`

```
User (HTTP GET)
  └─> OrderController.getWithLegacyData()  [api/OrderController]
        └─> OrderService.getWithLegacyData()  [api/OrderService]
              ├─> OrderService.get(id)     [api/OrderService]
              │     ├─> OrderCache.get()         [redis/OrderCache]            ──> Redis
              │     └─ (miss) OrderRepository.findById()  [persistence/OrderRepository] ──> Postgres
              └─> LegacyHttpClient.get("/customers/{id}/profile")  [integrations/http/] ──> Legacy HTTP
                    └─ (on error) onErrorResume → returns OrderWithLegacy(order, "{}", "LEGACY_UNAVAILABLE")
```

**Summary path:**
`OrderController → OrderService → (Redis → Postgres) + LegacyHttpClient → Legacy System`

## 7. Legacy integrations

Beans injected via `IntegrationConfig` and exposed for use in other services:

```
OrderService (or other consumer)
  ├─> LegacyHttpClient (WebClient)         [integrations/http/]   ← used in /legacy-data
  ├─> LegacyErpService (CXF/SOAP)          [integrations/soap/]
  └─> SftpClient (JSch)                    [integrations/sftp/]
```

## Package map

```
com.codesolutions
├── Application              ← entry point
├── api/                     ← HTTP layer (Controller, Service, ExceptionHandler)
├── config/                  ← beans (Integration, Redis)
├── domain/                  ← domain model (Order)
├── persistence/             ← R2DBC Postgres (Entity, Repository)
├── mongo/                   ← read model Mongo (View, Repository)
├── redis/                   ← reactive cache
├── kafka/                   ← Publisher + Consumer
└── integrations/            ← Legacy clients (http, soap, sftp)
```

## Errors

`ApiExceptionHandler` (in `api/`) intercepts exceptions thrown in any layer and returns standardized JSON with the appropriate HTTP status.
