# Phase 5 Payment and Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Payment Service and Notification Service so orders can be paid, failed payments can cancel orders and release stock, and customers receive event-driven notifications.

**Architecture:** Payment Service owns payment attempts, refund records, webhook idempotency, and a transactional outbox table. A scheduler publishes outbox events to Kafka. Notification Service consumes order/payment events, renders templates, and records notification delivery attempts with mock email/SMS providers for local development.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Web MVC, Spring Data JPA, PostgreSQL, Flyway, Kafka, Spring Scheduler, Spring Mail mock adapter, Thymeleaf templates, Eureka Client, Springdoc OpenAPI, JUnit 5, Testcontainers PostgreSQL and Kafka.

---

## File Structure

- Modify `pom.xml`: add `payment-service` and `notification-service` modules.
- Modify `docker-compose.yml`: add `payment-db` and `notification-db`.
- Modify `config-repo/api-gateway.yml`: add `/api/payments/**` route.
- Create `config-repo/payment-service.yml`: Payment datasource, Kafka, scheduler config.
- Create `config-repo/notification-service.yml`: Notification datasource, Kafka, provider config.
- Create `payment-service/pom.xml`: Payment Service dependencies.
- Create `payment-service/src/main/java/com/ecom/payment/PaymentServiceApplication.java`: entrypoint.
- Create `payment-service/src/main/resources/db/migration/V1__create_payment_tables.sql`: payments, refunds, webhook, outbox schema.
- Create `payment-service/src/main/java/com/ecom/payment/domain/*.java`: payment aggregates and enums.
- Create `payment-service/src/main/java/com/ecom/payment/repository/*.java`: persistence.
- Create `payment-service/src/main/java/com/ecom/payment/service/PaymentService.java`: payment use cases.
- Create `payment-service/src/main/java/com/ecom/payment/service/MockPaymentProvider.java`: local provider simulation.
- Create `payment-service/src/main/java/com/ecom/payment/outbox/*.java`: transactional outbox writer, scheduler, publisher.
- Create `payment-service/src/main/java/com/ecom/payment/web/PaymentController.java`: payment APIs.
- Create `notification-service/pom.xml`: Notification Service dependencies.
- Create `notification-service/src/main/java/com/ecom/notification/NotificationServiceApplication.java`: entrypoint.
- Create `notification-service/src/main/resources/db/migration/V1__create_notification_tables.sql`: notification log schema.
- Create `notification-service/src/main/resources/templates/*.html`: email templates.
- Create `notification-service/src/main/java/com/ecom/notification/domain/*.java`: notification aggregates and enums.
- Create `notification-service/src/main/java/com/ecom/notification/messaging/*.java`: Kafka consumers.
- Create `notification-service/src/main/java/com/ecom/notification/service/*.java`: rendering and delivery services.

## API Contract

### Payment Service

- `POST /api/payments`: create payment for an order.
- `GET /api/payments/{paymentId}`: get payment detail for current user.
- `GET /api/payments`: list current user's payment history.
- `POST /api/payments/webhooks/mock`: mock provider callback.
- `POST /api/payments/{paymentId}/refund`: create refund, requires `ADMIN`.

### Notification Service

- No public customer API in this phase.
- Consumes Kafka events and records delivery logs.
- Actuator and OpenAPI are available for operational checks.

## Event Contract

- `payment.succeeded`: emitted by Payment Service via outbox.
- `payment.failed`: emitted by Payment Service via outbox.
- `payment.refunded`: emitted by Payment Service via outbox.
- `order.confirmed`: emitted by Order Service after `payment.succeeded`.
- `order.cancelled`: consumed by Inventory and Notification.
- `notification.sent`: emitted by Notification Service for observability.
- `notification.failed`: emitted by Notification Service for retry/DLQ follow-up.

## Phase Acceptance Criteria

- `mvn test` passes from repository root.
- Payment Service runs on `http://localhost:8087`.
- Notification Service runs on `http://localhost:8088`.
- Gateway routes Payment APIs through `http://localhost:8080`.
- Payment creation is idempotent per order.
- Mock webhook is idempotent by provider event ID.
- Payment success confirms order.
- Payment failure cancels order and releases stock through existing order/inventory flow.
- Outbox scheduler publishes pending events and marks them published.
- Notification Service records email/SMS delivery attempts for payment/order events.

### Task 1: Extend Build, Config, Compose, and Gateway Routes

**Files:**
- Modify: `pom.xml`
- Modify: `docker-compose.yml`
- Modify: `config-repo/api-gateway.yml`
- Create: `config-repo/payment-service.yml`
- Create: `config-repo/notification-service.yml`

- [ ] **Step 1: Add Maven modules**

Add to root `pom.xml`:

```xml
<module>payment-service</module>
<module>notification-service</module>
```

- [ ] **Step 2: Add databases**

Add `payment-db` on port `5438` with database `payment_service`, user `payment_user`, password `payment_password`.

Add `notification-db` on port `5439` with database `notification_service`, user `notification_user`, password `notification_password`.

- [ ] **Step 3: Add service configs**

`config-repo/payment-service.yml`:

```yaml
server:
  port: 8087

spring:
  application:
    name: payment-service
  datasource:
    url: jdbc:postgresql://localhost:5438/payment_service
    username: payment_user
    password: payment_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: localhost:9092

payment:
  outbox:
    publish-fixed-delay-ms: 5000
```

`config-repo/notification-service.yml`:

```yaml
server:
  port: 8088

spring:
  application:
    name: notification-service
  datasource:
    url: jdbc:postgresql://localhost:5439/notification_service
    username: notification_user
    password: notification_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: localhost:9092

notification:
  providers:
    email: mock
    sms: mock
```

- [ ] **Step 4: Add Gateway route**

Add:

```yaml
- id: payment-service
  uri: lb://payment-service
  predicates:
    - Path=/api/payments/**
```

- [ ] **Step 5: Run validation**

Run: `mvn -q validate`

Expected: Maven fails until modules are created.

- [ ] **Step 6: Commit**

```bash
git add pom.xml docker-compose.yml config-repo
git commit -m "chore: prepare payment and notification modules"
```

### Task 2: Create Payment Service Skeleton and Schema

**Files:**
- Create: `payment-service/pom.xml`
- Create: `payment-service/src/main/java/com/ecom/payment/PaymentServiceApplication.java`
- Create: `payment-service/src/main/resources/application.yml`
- Create: `payment-service/src/main/resources/db/migration/V1__create_payment_tables.sql`
- Create: `payment-service/src/test/java/com/ecom/payment/PaymentServiceApplicationTest.java`

- [ ] **Step 1: Create Payment Service module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-kafka`
- `spring-cloud-starter-openfeign`
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
package com.ecom.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableFeignClients
@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create schema**

```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(120),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payment_webhook_events (
    provider_event_id VARCHAR(160) PRIMARY KEY,
    payment_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status_created_at ON outbox_events(status, created_at);
```

- [ ] **Step 4: Add context test**

```java
package com.ecom.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn -q -pl payment-service test`

Expected: context test passes after datasource test config is added with integration tests.

- [ ] **Step 6: Commit**

```bash
git add payment-service pom.xml
git commit -m "feat: add payment service skeleton"
```

### Task 3: Implement Payment Domain, Provider, and Outbox

**Files:**
- Create: `payment-service/src/main/java/com/ecom/payment/domain/Payment.java`
- Create: `payment-service/src/main/java/com/ecom/payment/domain/PaymentStatus.java`
- Create: `payment-service/src/main/java/com/ecom/payment/domain/Refund.java`
- Create: `payment-service/src/main/java/com/ecom/payment/domain/OutboxEvent.java`
- Create: `payment-service/src/main/java/com/ecom/payment/repository/PaymentRepository.java`
- Create: `payment-service/src/main/java/com/ecom/payment/repository/RefundRepository.java`
- Create: `payment-service/src/main/java/com/ecom/payment/repository/OutboxEventRepository.java`
- Create: `payment-service/src/main/java/com/ecom/payment/service/MockPaymentProvider.java`
- Create: `payment-service/src/main/java/com/ecom/payment/outbox/OutboxService.java`
- Create: `payment-service/src/main/java/com/ecom/payment/outbox/OutboxPublisher.java`
- Create: `payment-service/src/test/java/com/ecom/payment/service/PaymentServiceTest.java`
- Create: `payment-service/src/test/java/com/ecom/payment/outbox/OutboxPublisherTest.java`

- [ ] **Step 1: Define payment statuses**

Payment statuses: `PENDING`, `SUCCEEDED`, `FAILED`, `REFUNDED`.

Outbox statuses: `PENDING`, `PUBLISHED`, `FAILED`.

- [ ] **Step 2: Implement repositories**

`PaymentRepository` methods:
- `Optional<Payment> findByOrderId(UUID orderId)`
- `Optional<Payment> findByIdAndUserId(UUID id, UUID userId)`
- `Optional<Payment> findByIdempotencyKey(String idempotencyKey)`
- `Page<Payment> findByUserId(UUID userId, Pageable pageable)`

`OutboxEventRepository` methods:
- `List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status)`

- [ ] **Step 3: Implement mock provider**

Methods:
- `ProviderPayment createPayment(UUID paymentId, BigDecimal amount, String currency)`
- `ProviderPayment markSucceeded(String providerPaymentId)`
- `ProviderPayment markFailed(String providerPaymentId, String reason)`

Provider result fields: `providerPaymentId`, `status`, `redirectUrl`.

- [ ] **Step 4: Implement outbox service**

Methods:
- `void append(String aggregateType, UUID aggregateId, String eventType, Object payload)`
- `void markPublished(UUID eventId)`
- `void markFailed(UUID eventId)`

- [ ] **Step 5: Implement outbox publisher**

Scheduled method:
- loads up to 100 `PENDING` events.
- publishes to Kafka topic matching `eventType`.
- marks event `PUBLISHED` on success.
- marks event `FAILED` on publish exception.

- [ ] **Step 6: Add tests**

Test cases:
- outbox event is inserted in same transaction as payment status change.
- publisher sends event to Kafka template with expected topic.
- publisher marks event published after send succeeds.
- publisher marks failed after send exception.

- [ ] **Step 7: Run tests**

Run: `mvn -q -pl payment-service test -Dtest=PaymentServiceTest,OutboxPublisherTest`

Expected: tests pass.

- [ ] **Step 8: Commit**

```bash
git add payment-service
git commit -m "feat: add payment outbox foundation"
```

### Task 4: Implement Payment Use Cases and API

**Files:**
- Create: `payment-service/src/main/java/com/ecom/payment/client/OrderClient.java`
- Create: `payment-service/src/main/java/com/ecom/payment/service/PaymentService.java`
- Create: `payment-service/src/main/java/com/ecom/payment/web/PaymentController.java`
- Create: `payment-service/src/main/java/com/ecom/payment/web/dto/*.java`
- Create: `payment-service/src/main/java/com/ecom/payment/security/GatewayUserContextFilter.java`
- Create: `payment-service/src/test/java/com/ecom/payment/web/PaymentControllerIT.java`

- [ ] **Step 1: Implement Order client**

Methods:
- `GET /api/orders/{orderId}` to verify ownership and amount.
- `POST /api/orders/{orderId}/cancel` for payment failure flow.

- [ ] **Step 2: Implement PaymentService**

Methods:
- `PaymentResponse createPayment(UUID userId, CreatePaymentRequest request, String idempotencyKey)`
- `PaymentResponse getPayment(UUID userId, UUID paymentId)`
- `Page<PaymentResponse> listPayments(UUID userId, Pageable pageable)`
- `PaymentResponse refund(UUID paymentId, RefundRequest request)`

Rules:
- create payment requires `Idempotency-Key` header.
- same idempotency key returns the same payment.
- one order can have only one active payment.
- refund requires succeeded payment.

- [ ] **Step 3: Expose endpoints**

Create:
- `POST /api/payments`
- `GET /api/payments/{paymentId}`
- `GET /api/payments`
- `POST /api/payments/{paymentId}/refund`

- [ ] **Step 4: Add API tests**

Test cases:
- missing `Idempotency-Key` returns `400`.
- duplicate create payment with same key returns same payment ID.
- user cannot read another user's payment.
- refund without `ADMIN` role returns `403`.
- refund succeeded payment emits `payment.refunded` outbox event.

- [ ] **Step 5: Run tests**

Run: `mvn -q -pl payment-service test -Dtest=PaymentControllerIT`

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add payment-service
git commit -m "feat: expose payment api"
```

### Task 5: Implement Mock Webhook and Order Payment Events

**Files:**
- Create: `payment-service/src/main/java/com/ecom/payment/web/MockPaymentWebhookController.java`
- Create: `payment-service/src/main/java/com/ecom/payment/repository/PaymentWebhookEventRepository.java`
- Create: `payment-service/src/main/java/com/ecom/payment/service/PaymentWebhookService.java`
- Create: `payment-service/src/test/java/com/ecom/payment/web/MockPaymentWebhookControllerIT.java`
- Modify: `order-service/src/main/java/com/ecom/order/messaging/PaymentEventConsumer.java`
- Modify: `order-service/src/main/java/com/ecom/order/service/OrderService.java`
- Create: `order-service/src/test/java/com/ecom/order/messaging/PaymentEventConsumerIT.java`

- [ ] **Step 1: Implement webhook idempotency**

Store `providerEventId` in `payment_webhook_events`.

If the same `providerEventId` arrives again, return `200 OK` without changing payment state again.

- [ ] **Step 2: Expose mock webhook**

Endpoint:
- `POST /api/payments/webhooks/mock`

Request fields:
- `providerEventId`
- `providerPaymentId`
- `eventType`: `PAYMENT_SUCCEEDED` or `PAYMENT_FAILED`
- `reason`

- [ ] **Step 3: Emit payment events**

On webhook success:
- mark payment `SUCCEEDED`.
- append outbox event `payment.succeeded`.

On webhook failure:
- mark payment `FAILED`.
- append outbox event `payment.failed`.

- [ ] **Step 4: Update Order Service payment event consumer**

On `payment.succeeded`:
- mark order `CONFIRMED`.
- emit `order.confirmed`.

On `payment.failed`:
- cancel order.
- emit `order.cancelled`.

- [ ] **Step 5: Add tests**

Test cases:
- duplicate webhook event is idempotent.
- success webhook emits `payment.succeeded`.
- failure webhook emits `payment.failed`.
- Order Service confirms order on payment success.
- Order Service cancels order on payment failure.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -q -pl payment-service test -Dtest=MockPaymentWebhookControllerIT
mvn -q -pl order-service test -Dtest=PaymentEventConsumerIT
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add payment-service order-service
git commit -m "feat: handle payment webhook outcomes"
```

### Task 6: Create Notification Service Skeleton and Schema

**Files:**
- Create: `notification-service/pom.xml`
- Create: `notification-service/src/main/java/com/ecom/notification/NotificationServiceApplication.java`
- Create: `notification-service/src/main/resources/application.yml`
- Create: `notification-service/src/main/resources/db/migration/V1__create_notification_tables.sql`
- Create: `notification-service/src/test/java/com/ecom/notification/NotificationServiceApplicationTest.java`

- [ ] **Step 1: Create Notification Service module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-kafka`
- `spring-boot-starter-thymeleaf`
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
package com.ecom.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create schema**

```sql
CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    channel VARCHAR(40) NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_logs_user_id_created_at ON notification_logs(user_id, created_at DESC);
```

- [ ] **Step 4: Add context test**

```java
package com.ecom.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NotificationServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn -q -pl notification-service test`

Expected: context test passes after datasource test config is added with integration tests.

- [ ] **Step 6: Commit**

```bash
git add notification-service pom.xml
git commit -m "feat: add notification service skeleton"
```

### Task 7: Implement Notification Rendering, Delivery, and Consumers

**Files:**
- Create: `notification-service/src/main/java/com/ecom/notification/domain/NotificationLog.java`
- Create: `notification-service/src/main/java/com/ecom/notification/domain/NotificationStatus.java`
- Create: `notification-service/src/main/java/com/ecom/notification/domain/NotificationChannel.java`
- Create: `notification-service/src/main/java/com/ecom/notification/repository/NotificationLogRepository.java`
- Create: `notification-service/src/main/java/com/ecom/notification/service/TemplateRenderer.java`
- Create: `notification-service/src/main/java/com/ecom/notification/service/MockEmailSender.java`
- Create: `notification-service/src/main/java/com/ecom/notification/service/MockSmsSender.java`
- Create: `notification-service/src/main/java/com/ecom/notification/service/NotificationService.java`
- Create: `notification-service/src/main/java/com/ecom/notification/messaging/PaymentEventConsumer.java`
- Create: `notification-service/src/main/java/com/ecom/notification/messaging/OrderEventConsumer.java`
- Create: `notification-service/src/main/resources/templates/payment-succeeded.html`
- Create: `notification-service/src/main/resources/templates/payment-failed.html`
- Create: `notification-service/src/main/resources/templates/order-confirmed.html`
- Create: `notification-service/src/main/resources/templates/order-cancelled.html`
- Create: `notification-service/src/test/java/com/ecom/notification/service/NotificationServiceTest.java`
- Create: `notification-service/src/test/java/com/ecom/notification/messaging/NotificationConsumerIT.java`

- [ ] **Step 1: Define notification statuses**

Statuses: `PENDING`, `SENT`, `FAILED`.

Channels: `EMAIL`, `SMS`, `PUSH`.

- [ ] **Step 2: Create templates**

Templates must render:
- customer email.
- order ID.
- payment ID when payment event exists.
- total amount.

- [ ] **Step 3: Implement delivery service**

Methods:
- `NotificationLog sendEmail(UUID userId, String recipient, String templateName, Map<String, Object> model)`
- `NotificationLog sendSms(UUID userId, String recipient, String message)`

Rules:
- mock senders log payload to application log.
- successful send marks log `SENT`.
- sender exception marks log `FAILED` with reason.

- [ ] **Step 4: Implement event consumers**

Consume:
- `payment.succeeded`: send payment success email.
- `payment.failed`: send payment failure email.
- `order.confirmed`: send order confirmation email.
- `order.cancelled`: send cancellation email and SMS.

- [ ] **Step 5: Add tests**

Test cases:
- template renderer injects order ID and total amount.
- successful email creates `SENT` notification log.
- sender failure creates `FAILED` notification log.
- payment success event creates notification log.
- order cancelled event creates email and SMS logs.

- [ ] **Step 6: Run tests**

Run: `mvn -q -pl notification-service test -Dtest=NotificationServiceTest,NotificationConsumerIT`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add notification-service
git commit -m "feat: send notifications from domain events"
```

### Task 8: End-to-End Payment and Notification Verification

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
mvn spring-boot:run -pl payment-service
mvn spring-boot:run -pl notification-service
```

Expected: Payment and Notification register in Eureka.

- [ ] **Step 3: Create payment**

Run:

```bash
ACCESS_TOKEN="<customer access token>"
ORDER_ID="<reserved order id>"
curl -sS -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Idempotency-Key: pay-${ORDER_ID}" \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":\"${ORDER_ID}\",\"currency\":\"USD\"}"
```

Expected: response contains `paymentId`, `status: PENDING`, and mock redirect URL.

- [ ] **Step 4: Send mock success webhook**

Run:

```bash
PAYMENT_ID="<payment id>"
PROVIDER_PAYMENT_ID="<provider payment id>"
curl -sS -X POST http://localhost:8080/api/payments/webhooks/mock \
  -H "Content-Type: application/json" \
  -d "{\"providerEventId\":\"evt-success-${PAYMENT_ID}\",\"providerPaymentId\":\"${PROVIDER_PAYMENT_ID}\",\"eventType\":\"PAYMENT_SUCCEEDED\"}"
```

Expected:
- Payment changes to `SUCCEEDED`.
- Order changes to `CONFIRMED`.
- Notification log contains payment success and order confirmation email entries.

- [ ] **Step 5: Verify failure flow**

Create another reserved order, create payment, then send:

```bash
curl -sS -X POST http://localhost:8080/api/payments/webhooks/mock \
  -H "Content-Type: application/json" \
  -d "{\"providerEventId\":\"evt-fail-${PAYMENT_ID}\",\"providerPaymentId\":\"${PROVIDER_PAYMENT_ID}\",\"eventType\":\"PAYMENT_FAILED\",\"reason\":\"mock failure\"}"
```

Expected:
- Payment changes to `FAILED`.
- Order changes to `CANCELLED`.
- Inventory reservation is released.
- Notification log contains failure notification.

- [ ] **Step 6: Document verification commands**

Add payment webhook and notification verification commands to `README.md`.

- [ ] **Step 7: Commit**

```bash
git add README.md
git commit -m "docs: document payment and notification verification"
```

## Self-Review

- Spec coverage: Phase 5 covers payment creation, webhook callback, refund, payment history, transactional outbox, notification templates, email/SMS mock delivery, and success/failure business flows.
- Placeholder scan: No unresolved placeholders remain; every task has concrete files, commands, and expected behavior.
- Type consistency: Payment statuses, Kafka topics, service ports, route paths, outbox statuses, and notification channels are consistent across Payment, Order, Inventory, and Notification.

## Implementation Status - 2026-05-03

- Completed Task 1 through Task 7 in the local workspace.
- Added Payment Service and Notification Service modules, config-repo entries, Docker Compose databases, and API Gateway payment route.
- Implemented payment creation, idempotency, mock webhook handling, refund support, payment history/detail APIs, transactional outbox publishing, and gateway-header based role checks.
- Implemented order integration for `payment.succeeded` and `payment.failed`; success confirms the order and failure cancels through the existing order/inventory cancellation path.
- Implemented notification templates, mock email/SMS delivery, notification logs, and payment/order event consumers.
- Updated `README.md` with Payment/Notification services and verification curl commands.
- Verification passed:
  - `rtk mvn -q -pl payment-service test`
  - `rtk mvn -q -pl notification-service test`
  - `rtk mvn -q -pl order-service test`
  - `rtk mvn -q test`
- Runtime Docker/service startup and live curl E2E were not run in this pass.
- Git commit steps could not be executed because `/mnt/d/project/Ecom` is not currently detected as a Git repository (`fatal: not a git repository`).
