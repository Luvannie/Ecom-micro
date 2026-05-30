# Ecom Food Delivery Microservices

Hệ thống E-commerce / Food Delivery sử dụng kiến trúc Micro Services với Java Spring Boot (Backend) và React (Frontend).

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                      │
│                    React SPA (Port 5173)                            │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTP / WebSocket
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY (8080)                              │
│              Routing • CORS • JWT Validation • Correlation ID        │
└──────┬──────────────┬──────────────┬──────────────┬─────────────────┘
       │              │              │              │
       ▼              ▼              ▼              ▼
┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────────┐
│Auth (8081) │ │User (8082) │ │Product(8083)│ │   Cart (8084)      │
│            │ │            │ │  + Redis    │ │     + Redis        │
└────────────┘ └────────────┘ └────────────┘ └────────────────────┘
                           │                           │
                           ▼                           ▼
┌────────────────────────────────────┐  ┌────────────────────────────┐
│         INVENTORY (8085)           │  │      ORDER (8086)          │
│   Stock • Reservations • Commit     │  │ Checkout • State Machine   │
└────────────────────────────────────┘  └────────────┬───────────────┘
                                                   │
                        ┌──────────────────────────┼──────────────────┐
                        │                          │                  │
                        ▼                          ▼                  ▼
              ┌────────────────┐         ┌────────────┐    ┌─────────────────┐
              │ Payment (8087) │         │Notification│    │  PostgreSQL     │
              │ + Outbox Pattern│         │  (8088)    │    │  (shared DBs)   │
              └────────────────┘         └────────────┘    └─────────────────┘
                        │                                        ▲
                        │           ┌────────────────────────────┘
                        │           │
                        ▼           ▼
              ┌────────────────────────────┐
              │     KAFKA (9092)          │
              │  Event-Driven Messaging   │
              └────────────────────────────┘
```

## Tech Stack

### Backend (BE/)
| Component | Technology |
|-----------|------------|
| **Framework** | Spring Boot 3.3.6 |
| **Language** | Java 21 |
| **Cloud** | Spring Cloud 2023.0.4 |
| **Database** | PostgreSQL 16 |
| **Cache** | Redis 7 |
| **Messaging** | Apache Kafka 3.7.0 (KRaft) |
| **Service Discovery** | Netflix Eureka |
| **API Gateway** | Spring Cloud Gateway |
| **Config Server** | Spring Cloud Config |
| **Build** | Maven |
| **Observability** | Prometheus, Grafana, Zipkin |

### Frontend (FE/)
| Component | Technology |
|-----------|------------|
| **Framework** | React 18.3.1 |
| **Build Tool** | Vite 5.4.11 |
| **Language** | TypeScript 5.6.3 |
| **Routing** | React Router 6.30.3 |

## Services

| Service | Port | Description |
|---------|:----:|-------------|
| **API Gateway** | 8080 | Routing, CORS, JWT validation, correlation ID |
| **Discovery Server** | 8761 | Netflix Eureka service registry |
| **Config Server** | 8888 | Centralized configuration management |
| **Auth Service** | 8081 | Register, login, JWT token issuance |
| **User Service** | 8082 | User profile, addresses |
| **Product Service** | 8083 | Product catalog, categories, **Redis caching** |
| **Cart Service** | 8084 | Shopping cart, **Redis storage** (30-day TTL) |
| **Inventory Service** | 8085 | Stock management, reservations |
| **Order Service** | 8086 | Checkout orchestration, order state machine |
| **Payment Service** | 8087 | Payment processing, **Transactional Outbox** |
| **Notification Service** | 8088 | Email/SMS notifications (mock providers) |

## Quick Start

### 1. Start Infrastructure

```bash
cd BE
docker compose up -d
```

Đợi các container khởi động hoàn toàn (~30s).

### 2. Start Backend Services

```bash
# Terminal 1: Discovery Server
mvn spring-boot:run -pl discovery-server

# Terminal 2: Config Server
mvn spring-boot:run -pl config-server

# Terminal 3: API Gateway
mvn spring-boot:run -pl api-gateway

# Terminal 4: Auth Service
mvn spring-boot:run -pl auth-service

# Terminal 5: User Service
mvn spring-boot:run -pl user-service

# Terminal 6: Product Service
mvn spring-boot:run -pl product-service

# Terminal 7: Cart Service
mvn spring-boot:run -pl cart-service

# Terminal 8: Inventory Service
mvn spring-boot:run -pl inventory-service

# Terminal 9: Order Service
mvn spring-boot:run -pl order-service

# Terminal 10: Payment Service
mvn spring-boot:run -pl payment-service

# Terminal 11: Notification Service
mvn spring-boot:run -pl notification-service
```

### 3. Start Frontend

```bash
cd FE
npm install
npm run dev
```

Frontend chạy tại **http://localhost:5173**

## Project Structure

```
Ecom-micro/
├── BE/                         # Backend (Spring Boot)
│   ├── api-gateway/            # Spring Cloud Gateway
│   ├── auth-service/           # Authentication & JWT
│   ├── cart-service/           # Shopping cart (Redis)
│   ├── common/                 # Shared utilities
│   ├── config-repo/            # Service configurations
│   ├── config-server/          # Config Server
│   ├── database/               # DB migration scripts
│   ├── discovery-server/       # Eureka Server
│   ├── inventory-service/      # Stock & reservations
│   ├── notification-service/   # Notifications
│   ├── order-service/          # Order management
│   ├── payment-service/        # Payment + Outbox
│   ├── product-service/        # Product catalog (Redis cache)
│   ├── user-service/           # User management
│   ├── docker-compose.yml      # Infrastructure containers
│   └── pom.xml                 # Parent POM
│
├── FE/                         # Frontend (React)
│   ├── src/
│   │   ├── api.ts              # API client
│   │   ├── context/            # React Context
│   │   ├── pages/              # Page components
│   │   ├── types.ts            # TypeScript interfaces
│   │   └── App.tsx             # Root component
│   ├── vite.config.ts          # Vite configuration
│   └── package.json
│
├── docs/                       # Documentation
│   ├── kafka-setup-documentation.html
│   └── redis-setup-documentation.html
│
└── plan/                       # Implementation plans
    ├── phase-1-foundation-plan.md
    ├── phase-2-identity-slice-plan.md
    ├── phase-3-catalog-cart-plan.md
    ├── phase-4-order-inventory-plan.md
    └── phase-5-payment-notification-plan.md
```

## Key Features

### Authentication & Authorization
- JWT-based stateless authentication
- Register, login, token refresh flows
- Role-based access control (customer, admin)

### Product Catalog
- Categories & products
- Search with pagination & filters
- **Redis caching** for product details & search results

### Shopping Cart
- Add/update/remove items
- Quantity validation (1-99)
- **Redis storage** with 30-day TTL
- Product snapshot at add-time

### Inventory Management
- Stock tracking per product
- Reservation with expiry
- Release on order cancellation

### Order Processing
- State machine: PENDING → RESERVED → CONFIRMED/FAILED
- Kafka events for async communication
- Rollback on payment failure

### Payment
- Mock payment integration
- **Transactional Outbox Pattern** for reliable events
- Automatic retry on failure

### Notifications
- Email & SMS (mock providers)
- Event-driven via Kafka

## API Documentation

### Authentication
```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!","displayName":"User"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

### Products
```bash
# List products
curl http://localhost:8080/api/products?page=0&size=10

# Search
curl http://localhost:8080/api/products?keyword=pizza&categoryId=xxx

# Get product details
curl http://localhost:8080/api/products/{productId}
```

### Cart
```bash
# Get cart
curl http://localhost:8080/api/cart \
  -H "Authorization: Bearer ${TOKEN}"

# Add item
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"productId":"xxx","quantity":2}'
```

### Orders
```bash
# Create order (checkout)
curl -X POST http://localhost:8080/api/orders/reserve \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"cartId":"xxx","addressId":"xxx"}'

# Get orders
curl http://localhost:8080/api/orders \
  -H "Authorization: Bearer ${TOKEN}"
```

## Design Patterns

### Event-Driven Architecture
- **Kafka** for async inter-service communication
- Topics: `inventory.reservation-requested`, `inventory.reserved`, `order.confirmed`, etc.

### Transactional Outbox
- Payment Service uses outbox pattern for reliable event publishing
- Scheduled publisher polls outbox table and sends to Kafka

### Repository Pattern
- Cart Repository abstracts Redis operations
- Clean separation of data access

### Declarative Caching
- Product Service uses `@Cacheable` / `@CacheEvict`
- Cache names: `product-detail`, `product-search`

## Observability

| Tool | URL | Purpose |
|------|-----|---------|
| **API Gateway** | http://localhost:8080 | Main entry point |
| **Eureka** | http://localhost:8761 | Service registry UI |
| **Zipkin** | http://localhost:9411 | Distributed tracing |
| **Prometheus** | http://localhost:9090 | Metrics collection |
| **Grafana** | http://localhost:3001 | Metrics dashboards |

## Testing

### Backend
```bash
cd BE
mvn test                    # Run all tests
mvn test -pl order-service  # Run specific service tests
```

### Frontend
```bash
cd FE
npm run build               # Build check
```

## Documentation

| Document | Description |
|----------|-------------|
| [Kafka Setup](docs/kafka-setup-documentation.html) | Chi tiết Kafka configuration và message flows |
| [Redis Setup](docs/redis-setup-documentation.html) | Chi tiết Redis caching và cart storage |
| [BE README](BE/README.md) | Backend architecture và API reference |
| [FE README](FE/README.md) | Frontend setup và features |

## License

[Xceedium](LICENSE)