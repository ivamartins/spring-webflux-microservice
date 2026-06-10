# Fluxo de interação entre classes — spring-webflux-microservice

Visualização rápida de como uma requisição atravessa o sistema, do input do usuário até os bancos.

## 1. Criar pedido — `POST /api/orders`

```
Usuário (HTTP POST)
  └─> OrderController.create()              [api/OrderController]
        └─> OrderService.create()           [api/OrderService]
              ├─> OrderRepository.save()    [persistence/OrderRepository]       ──> Postgres (R2DBC)
              ├─> mirrorToMongo()           [OrderService]                       ──> MongoDB
              ├─> warmCache()               [OrderService]                       ──> Redis
              └─> OrderEventPublisher.publishCreated()  [kafka/]              ──> Kafka
```

**Caminho resumido:**
`OrderController → OrderService → (OrderRepository + MongoRepository + RedisCache + KafkaPublisher)`

## 2. Buscar pedido — `GET /api/orders/{id}` (cache-aside)

```
Usuário (HTTP GET)
  └─> OrderController.get()                [api/OrderController]
        └─> OrderService.get()             [api/OrderService]
              ├─> OrderCache.get()         [redis/OrderCache]                  ──> Redis
              │     └─ (se cache HIT) retorna
              └─ (se cache MISS)
                    └─> OrderRepository.findById()  [persistence/OrderRepository] ──> Postgres
                          └─> OrderCache.put()     [redis/OrderCache]            ──> Redis
```

**Caminho resumido:**
`OrderController → OrderService → (Redis → Postgres → Redis)`

## 3. Listar pedidos — `GET /api/orders?customerId=X` ou `?status=X`

```
Usuário (HTTP GET)
  └─> OrderController.list()               [api/OrderController]
        └─> OrderService.listByCustomer()  [api/OrderService]
              └─> OrderRepository.findByCustomerId()  [persistence/OrderRepository] ──> Postgres
```

## 4. Mudar status — `PUT /api/orders/{id}/status`

```
Usuário (HTTP PUT)
  └─> OrderController.changeStatus()       [api/OrderController]
        └─> OrderService.changeStatus()    [api/OrderService]
              ├─> OrderRepository.findById()  [persistence/OrderRepository]     ──> Postgres
              ├─> OrderRepository.save()      [persistence/OrderRepository]     ──> Postgres
              ├─> mirrorToMongo()           [OrderService]                       ──> MongoDB
              ├─> warmCache()               [OrderService]                       ──> Redis
              └─> OrderEventPublisher.publishStatusChanged()  [kafka/]        ──> Kafka
```

**Caminho resumido:**
`OrderController → OrderService → (Postgres → Postgres → Mongo → Redis → Kafka)`

## 5. Consumir evento Kafka — `OrderEventConsumer`

```
Kafka (OrderCreated / OrderStatusChanged)
  └─> OrderEventConsumer.onMessage()       [kafka/OrderEventConsumer]
        └─> ... (handler customizado, atualmente placeholder)
```

## 6. Integrações legadas

São beans injetados via `IntegrationConfig` e expostos para uso em outros serviços:

```
OrderService (ou outro consumidor)
  ├─> LegacyHttpClient (WebClient)         [integrations/http/]
  ├─> LegacyErpService (CXF/SOAP)          [integrations/soap/]
  └─> SftpClient (JSch)                    [integrations/sftp/]
```

## Mapa de pacotes

```
com.codesolutions
├── Application              ← entry point
├── api/                     ← camada HTTP (Controller, Service, ExceptionHandler)
├── config/                  ← beans (Integration, Redis)
├── domain/                  ← modelo de domínio (Order)
├── persistence/             ← R2DBC Postgres (Entity, Repository)
├── mongo/                   ← read model Mongo (View, Repository)
├── redis/                   ← cache reativo
├── kafka/                   ← Publisher + Consumer
└── integrations/            ← Clientes legados (http, soap, sftp)
```

## Erros

`ApiExceptionHandler` (em `api/`) intercepta exceções lançadas em qualquer camada e devolve JSON padronizado com status HTTP apropriado.
