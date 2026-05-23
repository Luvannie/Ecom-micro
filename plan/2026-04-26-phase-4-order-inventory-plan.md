# Phase 4 Order and Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Inventory Service and Order Service so customers can create orders from cart snapshots, reserve stock, view order history, and cancel orders.

**Architecture:** Inventory Service owns stock and reservation state in PostgreSQL and publishes Kafka events for reservation outcomes. Order Service owns order lifecycle and orchestrates the create-order saga by reading cart items, requesting inventory reservation, and moving orders through `PENDING`, `RESERVED`, `CONFIRMED`, `CANCELLED`, and `FAILED` states.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Web MVC, Spring Data JPA, PostgreSQL, Flyway, Kafka, Spring Cloud OpenFeign, Eureka Client, Springdoc OpenAPI, JUnit 5, Testcontainers PostgreSQL and Kafka.

---

## File Structure

- Modify `pom.xml`: add `inventory-service` and `order-service` modules.
- Modify `docker-compose.yml`: add `inventory-db` and `order-db`.
- Modify `config-repo/api-gateway.yml`: add `/api/inventory/**`, `/api/admin/inventory/**`, and `/api/orders/**` routes.
- Create `config-repo/inventory-service.yml`: Inventory datasource and Kafka config.
- Create `config-repo/order-service.yml`: Order datasource, Kafka config, Cart/Product/Inventory client config.
- Create `inventory-service/pom.xml`: Inventory Service dependencies.
- Create `inventory-service/src/main/java/com/ecom/inventory/InventoryServiceApplication.java`: entrypoint.
- Create `inventory-service/src/main/resources/db/migration/V1__create_inventory_tables.sql`: stock, reservation, audit schema.
- Create `inventory-service/src/main/java/com/ecom/inventory/domain/*.java`: inventory aggregates and enums.
- Create `inventory-service/src/main/java/com/ecom/inventory/repository/*.java`: inventory persistence.
- Create `inventory-service/src/main/java/com/ecom/inventory/service/InventoryService.java`: stock and reservation use cases.
- Create `inventory-service/src/main/java/com/ecom/inventory/web/*.java`: customer/admin inventory APIs.
- Create `order-service/pom.xml`: Order Service dependencies.
- Create `order-service/src/main/java/com/ecom/order/OrderServiceApplication.java`: entrypoint.
- Create `order-service/src/main/resources/db/migration/V1__create_order_tables.sql`: order schema.
- Create `order-service/src/main/java/com/ecom/order/domain/*.java`: order aggregates and enums.
- Create `order-service/src/main/java/com/ecom/order/client/*.java`: Cart, Product, Inventory clients.
- Create `order-service/src/main/java/com/ecom/order/repository/*.java`: order persistence.
- Create `order-service/src/main/java/com/ecom/order/service/OrderService.java`: order lifecycle.
- Create `order-service/src/main/java/com/ecom/order/messaging/*.java`: Kafka event producer/consumer.
- Create `order-service/src/main/java/com/ecom/order/web/OrderController.java`: order APIs.

## API Contract

### Inventory Service

- `GET /api/inventory/products/{productId}`: public stock availability.
- `POST /api/inventory/reservations`: reserve stock for an order.
- `POST /api/inventory/reservations/{reservationId}/release`: release reserved stock.
- `POST /api/inventory/reservations/{reservationId}/commit`: commit reserved stock after payment.
- `PUT /api/admin/inventory/products/{productId}`: set stock, requires `ADMIN`.
- `GET /api/admin/inventory/audit`: stock audit list, requires `ADMIN`.

### Order Service

- `POST /api/orders`: create order from current cart.
- `GET /api/orders/{orderId}`: get current user's order detail.
- `GET /api/orders`: list current user's order history.
- `POST /api/orders/{orderId}/cancel`: cancel current user's order.

## Event Contract

- `inventory.reservation-requested`: emitted by Order Service when an order needs stock reservation.
- `inventory.reserved`: emitted by Inventory Service when stock reservation succeeds.
- `inventory.reservation-failed`: emitted by Inventory Service when stock is insufficient.
- `order.cancelled`: emitted by Order Service when cancellation requires stock release.

## Implementation Status

Updated: 2026-05-03

- Tasks 1-7 completed in workspace: build/config/compose/gateway, Inventory service, Inventory APIs/events, Order service, Order lifecycle, and Order APIs/events.
- Verification completed with `rtk mvn -q test` from repository root; command exited 0.
- Kafka behavior is covered with Spring integration tests that call listener handlers directly and mock event producers, so no Kafka broker/container is required for the test run.
- Plan commit steps were not executed because the current workspace is not a valid git repository; `rtk git status --short --branch` fails with `not a git repository`.

## Phase Acceptance Criteria

- `mvn test` passes from repository root.
- Inventory Service runs on `http://localhost:8085`.
- Order Service runs on `http://localhost:8086`.
- Gateway routes Inventory and Order APIs through `http://localhost:8080`.
- Admin can set stock for a product.
- Customer can create an order from cart.
- Order creation reserves stock and stores an immutable product snapshot.
- Insufficient stock moves order to `FAILED`.
- Cancel order releases reserved stock.

### Task 1: Extend Build, Config, Compose, and Gateway Routes

**Files:**
- Modify: `pom.xml`
- Modify: `docker-compose.yml`
- Modify: `config-repo/api-gateway.yml`
- Create: `config-repo/inventory-service.yml`
- Create: `config-repo/order-service.yml`

- [ ] **Step 1: Add Maven modules**

Add to root `pom.xml`:

```xml
<module>inventory-service</module>
<module>order-service</module>
```

- [ ] **Step 2: Add databases**

Add `inventory-db` on port `5436` with database `inventory_service`, user `inventory_user`, password `inventory_password`.

Add `order-db` on port `5437` with database `order_service`, user `order_user`, password `order_password`.

- [ ] **Step 3: Add service configs**

`config-repo/inventory-service.yml`:

```yaml
server:
  port: 8085

spring:
  application:
    name: inventory-service
  datasource:
    url: jdbc:postgresql://localhost:5436/inventory_service
    username: inventory_user
    password: inventory_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: localhost:9092
```

`config-repo/order-service.yml`:

```yaml
server:
  port: 8086

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://localhost:5437/order_service
    username: order_user
    password: order_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: localhost:9092
```

- [ ] **Step 4: Add Gateway routes**

Add:

```yaml
- id: inventory-service
  uri: lb://inventory-service
  predicates:
    - Path=/api/inventory/**,/api/admin/inventory/**
- id: order-service
  uri: lb://order-service
  predicates:
    - Path=/api/orders/**
```

- [ ] **Step 5: Run validation**

Run: `mvn -q validate`

Expected: Maven fails until the two modules are created.

- [ ] **Step 6: Commit**

```bash
git add pom.xml docker-compose.yml config-repo
git commit -m "chore: prepare order and inventory modules"
```

### Task 2: Create Inventory Service Skeleton and Schema

**Files:**
- Create: `inventory-service/pom.xml`
- Create: `inventory-service/src/main/java/com/ecom/inventory/InventoryServiceApplication.java`
- Create: `inventory-service/src/main/resources/application.yml`
- Create: `inventory-service/src/main/resources/db/migration/V1__create_inventory_tables.sql`
- Create: `inventory-service/src/test/java/com/ecom/inventory/InventoryServiceApplicationTest.java`

- [ ] **Step 1: Create Inventory Service module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-kafka`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql`
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test`
- `spring-kafka-test`
- `testcontainers-postgresql`
- `testcontainers-kafka`

- [ ] **Step 2: Create application class**

```java
package com.ecom.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create schema**

```sql
CREATE TABLE product_stock (
    product_id UUID PRIMARY KEY,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_reservation_items (
    reservation_id UUID NOT NULL REFERENCES stock_reservations(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (reservation_id, product_id)
);

CREATE TABLE stock_audit_logs (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    change_type VARCHAR(60) NOT NULL,
    quantity_delta INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
```

- [ ] **Step 4: Add context test**

```java
package com.ecom.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InventoryServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn -q -pl inventory-service test`

Expected: context test passes after datasource test config is added with integration tests.

- [ ] **Step 6: Commit**

```bash
git add inventory-service pom.xml
git commit -m "feat: add inventory service skeleton"
```

### Task 3: Implement Inventory Stock and Reservation Use Cases

**Files:**
- Create: `inventory-service/src/main/java/com/ecom/inventory/domain/ProductStock.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/domain/StockReservation.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/domain/StockReservationItem.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/domain/ReservationStatus.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/domain/StockAuditLog.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/repository/ProductStockRepository.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/repository/StockReservationRepository.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/repository/StockAuditLogRepository.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/service/InventoryService.java`
- Create: `inventory-service/src/test/java/com/ecom/inventory/service/InventoryServiceTest.java`

- [ ] **Step 1: Implement domain rules**

Reservation statuses: `RESERVED`, `RELEASED`, `COMMITTED`, `FAILED`.

Stock rules:
- available quantity cannot be negative.
- reservation moves quantity from available to reserved.
- release moves quantity from reserved to available.
- commit subtracts from reserved permanently.

- [ ] **Step 2: Implement repository locking**

`ProductStockRepository` must expose:
- `Optional<ProductStock> findByProductId(UUID productId)`
- `@Lock(PESSIMISTIC_WRITE) Optional<ProductStock> findLockedByProductId(UUID productId)`

- [ ] **Step 3: Implement service methods**

Methods:
- `ProductStock setStock(UUID productId, int quantity, String reason)`
- `ReservationResult reserve(UUID orderId, List<ReservationItemRequest> items)`
- `void release(UUID reservationId)`
- `void commit(UUID reservationId)`
- `StockAvailability getAvailability(UUID productId)`
- `List<StockAuditLog> audit(UUID productId)`

- [ ] **Step 4: Add service tests**

Test cases:
- set stock creates audit log.
- reserve succeeds when available stock is enough.
- reserve fails and leaves stock unchanged when one item is insufficient.
- release restores available quantity.
- commit decreases reserved quantity.
- duplicate reservation for the same order is idempotent.

- [ ] **Step 5: Run tests**

Run: `mvn -q -pl inventory-service test -Dtest=InventoryServiceTest`

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add inventory-service
git commit -m "feat: implement inventory reservation logic"
```

### Task 4: Expose Inventory APIs and Events

**Files:**
- Create: `inventory-service/src/main/java/com/ecom/inventory/messaging/InventoryEventProducer.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/messaging/ReservationEventConsumer.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/web/InventoryController.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/web/AdminInventoryController.java`
- Create: `inventory-service/src/main/java/com/ecom/inventory/web/dto/*.java`
- Create: `inventory-service/src/test/java/com/ecom/inventory/web/InventoryControllerIT.java`
- Create: `inventory-service/src/test/java/com/ecom/inventory/messaging/ReservationEventConsumerIT.java`

- [ ] **Step 1: Add Kafka topics as constants**

Topics:
- `inventory.reservation-requested`
- `inventory.reserved`
- `inventory.reservation-failed`
- `order.cancelled`

- [ ] **Step 2: Expose REST endpoints**

Create:
- `GET /api/inventory/products/{productId}`
- `POST /api/inventory/reservations`
- `POST /api/inventory/reservations/{reservationId}/release`
- `POST /api/inventory/reservations/{reservationId}/commit`
- `PUT /api/admin/inventory/products/{productId}`
- `GET /api/admin/inventory/audit`

- [ ] **Step 3: Implement event consumer**

Consume `inventory.reservation-requested`, call `InventoryService.reserve`, publish `inventory.reserved` or `inventory.reservation-failed`.

- [ ] **Step 4: Add tests**

Test cases:
- admin set stock requires `ADMIN` role.
- reservation endpoint returns reservation ID.
- Kafka reservation request emits success event.
- Kafka reservation request emits failure event on insufficient stock.

- [ ] **Step 5: Run tests**

Run: `mvn -q -pl inventory-service test -Dtest=InventoryControllerIT,ReservationEventConsumerIT`

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add inventory-service
git commit -m "feat: expose inventory api and events"
```

### Task 5: Create Order Service Skeleton and Schema

**Files:**
- Create: `order-service/pom.xml`
- Create: `order-service/src/main/java/com/ecom/order/OrderServiceApplication.java`
- Create: `order-service/src/main/resources/application.yml`
- Create: `order-service/src/main/resources/db/migration/V1__create_order_tables.sql`
- Create: `order-service/src/test/java/com/ecom/order/OrderServiceApplicationTest.java`

- [ ] **Step 1: Create Order Service module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-cloud-starter-openfeign`
- `spring-kafka`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql`
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test`
- `spring-kafka-test`
- `testcontainers-postgresql`
- `testcontainers-kafka`

- [ ] **Step 2: Create application class**

```java
package com.ecom.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create order schema**

```sql
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL,
    shipping_fee NUMERIC(12, 2) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    reservation_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_orders_user_id_created_at ON orders(user_id, created_at DESC);
```

- [ ] **Step 4: Add context test**

```java
package com.ecom.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn -q -pl order-service test`

Expected: context test passes after datasource test config is added with integration tests.

- [ ] **Step 6: Commit**

```bash
git add order-service pom.xml
git commit -m "feat: add order service skeleton"
```

### Task 6: Implement Order Lifecycle and Clients

**Files:**
- Create: `order-service/src/main/java/com/ecom/order/domain/Order.java`
- Create: `order-service/src/main/java/com/ecom/order/domain/OrderItem.java`
- Create: `order-service/src/main/java/com/ecom/order/domain/OrderStatus.java`
- Create: `order-service/src/main/java/com/ecom/order/client/CartClient.java`
- Create: `order-service/src/main/java/com/ecom/order/client/InventoryClient.java`
- Create: `order-service/src/main/java/com/ecom/order/repository/OrderRepository.java`
- Create: `order-service/src/main/java/com/ecom/order/service/OrderService.java`
- Create: `order-service/src/test/java/com/ecom/order/service/OrderServiceTest.java`

- [ ] **Step 1: Define statuses**

Order statuses: `PENDING`, `RESERVED`, `CONFIRMED`, `CANCELLED`, `FAILED`.

- [ ] **Step 2: Implement clients**

`CartClient`:
- `GET /api/cart`
- `DELETE /api/cart`

`InventoryClient`:
- `POST /api/inventory/reservations`
- `POST /api/inventory/reservations/{reservationId}/release`

- [ ] **Step 3: Implement OrderService methods**

Methods:
- `OrderResponse createOrder(UUID userId, String email)`
- `OrderResponse getOrder(UUID userId, UUID orderId)`
- `Page<OrderSummaryResponse> listOrders(UUID userId, Pageable pageable)`
- `OrderResponse cancelOrder(UUID userId, UUID orderId)`
- `void markReserved(UUID orderId, UUID reservationId)`
- `void markReservationFailed(UUID orderId, String reason)`

Rules:
- empty cart rejects create order.
- order stores immutable product name, price, and quantity snapshot.
- create order starts as `PENDING`.
- successful reservation changes order to `RESERVED`.
- failed reservation changes order to `FAILED`.
- cancellation allowed for `PENDING` and `RESERVED`.

- [ ] **Step 4: Add service tests**

Test cases:
- create order from cart stores snapshots.
- empty cart returns business error.
- get order rejects access by another user.
- cancel reserved order calls inventory release.
- reservation failed changes status to `FAILED`.

- [ ] **Step 5: Run tests**

Run: `mvn -q -pl order-service test -Dtest=OrderServiceTest`

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add order-service
git commit -m "feat: implement order lifecycle"
```

### Task 7: Expose Order API and Saga Events

**Files:**
- Create: `order-service/src/main/java/com/ecom/order/messaging/OrderEventProducer.java`
- Create: `order-service/src/main/java/com/ecom/order/messaging/InventoryEventConsumer.java`
- Create: `order-service/src/main/java/com/ecom/order/web/OrderController.java`
- Create: `order-service/src/main/java/com/ecom/order/web/dto/*.java`
- Create: `order-service/src/main/java/com/ecom/order/security/GatewayUserContextFilter.java`
- Create: `order-service/src/test/java/com/ecom/order/web/OrderControllerIT.java`
- Create: `order-service/src/test/java/com/ecom/order/messaging/InventoryEventConsumerIT.java`

- [ ] **Step 1: Add user context filter**

Require `X-User-Id` and `X-User-Email` for all `/api/orders/**` endpoints.

- [ ] **Step 2: Expose endpoints**

Create:
- `POST /api/orders`
- `GET /api/orders/{orderId}`
- `GET /api/orders`
- `POST /api/orders/{orderId}/cancel`

- [ ] **Step 3: Emit reservation request**

After creating a `PENDING` order, publish `inventory.reservation-requested` with order ID and product quantities.

- [ ] **Step 4: Consume inventory events**

On `inventory.reserved`, mark order `RESERVED`.

On `inventory.reservation-failed`, mark order `FAILED`.

- [ ] **Step 5: Add tests**

Test cases:
- create order endpoint requires authentication.
- create order emits reservation request.
- list orders returns only current user's orders.
- cancel order emits `order.cancelled`.
- inventory success event updates order status.
- inventory failure event updates order status.

- [ ] **Step 6: Run tests**

Run: `mvn -q -pl order-service test -Dtest=OrderControllerIT,InventoryEventConsumerIT`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add order-service
git commit -m "feat: expose order api and inventory saga"
```

### Task 8: End-to-End Order and Inventory Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run full tests**

Run: `mvn -B test`

Expected: all modules pass.

- [ ] **Step 2: Start dependencies and services**

Run dependencies:

```bash
docker compose up -d
```

Start services:

```bash
mvn spring-boot:run -pl discovery-server
mvn spring-boot:run -pl config-server
mvn spring-boot:run -pl api-gateway
mvn spring-boot:run -pl auth-service
mvn spring-boot:run -pl product-service
mvn spring-boot:run -pl cart-service
mvn spring-boot:run -pl inventory-service
mvn spring-boot:run -pl order-service
```

Expected: Inventory and Order register in Eureka.

- [ ] **Step 3: Set stock**

Run:

```bash
ADMIN_TOKEN="<admin access token>"
PRODUCT_ID="<product id>"
curl -sS -X PUT "http://localhost:8080/api/admin/inventory/products/${PRODUCT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"quantity":20,"reason":"initial stock"}'
```

Expected: availability shows `availableQuantity: 20`.

- [ ] **Step 4: Create order**

Run:

```bash
ACCESS_TOKEN="<customer access token>"
curl -sS -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: order response status starts as `PENDING`, then becomes `RESERVED` after reservation event is processed.

- [ ] **Step 5: Cancel order**

Run:

```bash
ORDER_ID="<order id>"
curl -sS -X POST "http://localhost:8080/api/orders/${ORDER_ID}/cancel" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: order status is `CANCELLED` and stock is released.

- [ ] **Step 6: Document verification commands**

Add Inventory and Order curl commands to `README.md`.

- [ ] **Step 7: Commit**

```bash
git add README.md
git commit -m "docs: document order and inventory verification"
```

## Self-Review

- Spec coverage: Phase 4 covers stock, reserve, release, audit, create order, order detail/history, cancellation, and Kafka-backed reservation saga.
- Placeholder scan: No unresolved placeholders remain; every task has concrete files, commands, and expected behavior.
- Type consistency: Status names, Kafka topics, service ports, database names, and Gateway routes are consistent across Order and Inventory.
