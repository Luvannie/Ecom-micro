# Resilience4J Integration Design

**Date:** 2026-06-07
**Status:** Approved (brainstorming complete)
**Author:** Brainstorming session with user
**Scope:** Ecom microservices — `D:/project/Ecom/`

---

## 1. Context

Ecom hiện là một food-delivery microservices platform (12 module Maven, 8 business services + 3 infra + common) với các inter-service call qua OpenFeign, Kafka event-driven messaging, và API Gateway routing. Hiện tại:

- **Không có Resilience4J** trong bất kỳ pom.xml nào.
- **OpenFeign** dùng ở 3 service: `cart-service` → `product-service` (ProductClient), `order-service` → `cart-service` (CartClient) + `inventory-service` (InventoryClient). `payment-service` có `@EnableFeignClients` nhưng chưa có client nào.
- **API Gateway** có comment `# Rate limiting would go here with additional dependencies` (xem `BE/api-gateway/src/main/resources/application.yml:37`) — placeholder chưa implement.
- **Kafka consumer** dùng `KafkaErrorHandler` retry 3 lần / 1s back-off (xem `BE/common/src/main/java/com/ecom/common/config/KafkaErrorHandler.java:19-55`) — KHÔNG phải Resilience4J.
- **Plan gốc** (`ecommerce-food-delivery-microservices-plan.md:88-91`) liệt kê `Rate limiting`, `Circuit Breaker`, `Bulkhead` là các pattern cần implement.
- **Tech-debt liên quan** (từ `D:/project/Ecom/.claude/knowledge-map.md:285-305`):
  - `#5` — `GlobalExceptionHandler` ở `common/` không được scan, mỗi service tự viết handler riêng
  - `#6` — Prometheus chỉ scrape 3/11 service

### Goal

Áp dụng Resilience4J để bảo vệ hệ thống khỏi cascading failure và traffic burst, đồng thời cung cấp fail-fast có ý nghĩa thay vì treo request chờ response. Đồng thời fix 2 tech-debt liên quan (`#5`, `#6`) trong scope công việc.

### Non-goals

- KHÔNG thay thế `KafkaErrorHandler` retry bằng Resilience4J retry pattern (giữ `KafkaErrorHandler` nguyên trạng).
- KHÔNG thêm Circuit Breaker cho Kafka consumer (chỉ TimeLimiter).
- KHÔNG thêm load test / chaos test infra.
- KHÔNG thêm DLQ (Dead Letter Queue) cho Kafka (note tech-debt để sau).
- KHÔNG đo coverage (tech-debt `#15` — YAGNI).

---

## 2. Decisions (from brainstorming)

| # | Quyết định | Lựa chọn |
|---|---|---|
| 1 | Phạm vi R4J | Toàn diện: Feign + Gateway RateLimit + Kafka TimeLimiter |
| 2 | Feign fallback | Fail-fast — throw `ServiceUnavailableException` |
| 3 | Rate Limit granularity | 3 nhóm: STRICT (auth) / NORMAL (browse) / NONE (actuator, swagger) |
| 4 | Kafka TimeLimit | Mọi consumer, default 5s, timeout → retry qua `KafkaErrorHandler` |
| 5 | Implementation style | Spring Cloud Circuit Breaker abstraction (`@CircuitBreaker`, `@Retry`, `@Bulkhead`, `@TimeLimiter`) |
| 6 | Deps placement | Thêm vào `BE/common/pom.xml` (DRY) |
| 7 | Observability | Prometheus metrics + log warn/error; fix Prometheus scrape cho đủ service |
| 8 | Cấu hình aggressiveness | Phương án A (cân bằng): CB 50% / window 100 / min 10 / wait 30s; Retry 3 attempts / 200ms exp back-off; Bulkhead 20 concurrent; RateLimit STRICT 5/min, NORMAL 30/s; TimeLimiter 5s |

---

## 3. Architecture

### 3.1 Request/data flow với Resilience4J

```
CLIENT → API Gateway (R4J RateLimiter qua Redis)
             │
             ├─ /api/auth/login  → rateLimitStrict   (5 req/min per IP)
             ├─ /api/products/** → rateLimitNormal   (30 req/s per IP, burst 60)
             └─ /actuator/**     → no limit
                  │
                  ▼
        Downstream Service
                  │
   ┌──────────────┼──────────────┐
   ▼              ▼              ▼
cart-service  order-service   payment-service
(R4J CB+Retry  (R4J CB+Retry (chưa có Feign,
+Bulkhead      +Bulkhead       nhưng có sẵn R4J
wrap           wrap            khi cần)
ProductClient) CartClient
               InventoryClient)
   │              │              │
   └────── Kafka (R4J TimeLimiter wrap mọi @KafkaListener) ──────┘
                  (timeout 5s → AmqpRejectAndDontRequeueException
                                → KafkaErrorHandler retry 3 lần)
```

### 3.2 Integration points (4 chỗ)

| Layer | Service áp dụng | Pattern R4J | Cơ chế |
|---|---|---|---|
| **A. Feign wrapper** | `cart-service`, `order-service` (3 Feign client) | CircuitBreaker + Retry + Bulkhead | Annotation `@CircuitBreaker(name=..., fallbackMethod=...)` trên service method, fallback ném `ServiceUnavailableException` |
| **B. API Gateway filter** | `api-gateway` | RateLimiter | Spring Cloud Gateway filter `RequestRateLimiter` với `IpKeyResolver`, 2 instance: `rateLimitStrict` (5/min), `rateLimitNormal` (30/s, burst 60) |
| **C. Kafka listener wrapper** | Mọi service có `@KafkaListener` (5 service) | TimeLimiter | `KafkaListenerResilienceWrapper` bean dùng `Decorators.ofCompletionStage(...).withTimeLimiter(...).withFallback(...).decorate()` — vì `@TimeLimiter` annotation không áp dụng trực tiếp lên `@KafkaListener` method |
| **D. Shared configuration** | `BE/common/pom.xml` | — | Thêm `resilience4j-spring-boot3:2.2.0`, `spring-cloud-starter-circuitbreaker-resilience4j`, `spring-boot-starter-aop` |

### 3.3 Style

Dùng Spring Cloud Circuit Breaker abstraction → annotation-based, tương thích OpenFeign, dễ đổi implementation tương lai (vd: Istio sidecar) chỉ cần đổi starter.

### 3.4 Observability hook

- Resilience4J tự động expose metrics qua Spring Boot Actuator + Micrometer: `resilience4j_circuitbreaker_*`, `resilience4j_ratelimiter_*`, `resilience4j_timelimiter_*`
- Cần fix tech-debt `#6`: bổ sung Prometheus scrape jobs cho 8 business services + gateway (tổng cộng 9 service trong prometheus.yml)

---

## 4. Components

### 4.1 Dependency — `BE/common/pom.xml`

```xml
<dependencies>
    <!-- existing -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>2.2.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
</dependencies>
```

> `resilience4j-spring-boot3:2.2.0` tương thích Spring Boot 3.3.6 (đang dùng).

### 4.2 Named instances — `BE/config-repo/application.yml` (shared)

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 100
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 5
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.ResourceAccessException
          - feign.FeignException
    instances:
      productService:    { baseConfig: default }
      cartService:       { baseConfig: default }
      inventoryService:  { baseConfig: default }

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 200ms
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - feign.FeignException
    instances:
      productService:    { baseConfig: default }
      cartService:       { baseConfig: default }
      inventoryService:  { baseConfig: default }

  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 20
        maxWaitDuration: 0
    instances:
      productService:    { baseConfig: default }
      cartService:       { baseConfig: default }
      inventoryService:  { baseConfig: default }

  timelimiter:
    configs:
      default:
        timeoutDuration: 5s
        cancelRunningFuture: true
    instances:
      kafkaConsumer: { baseConfig: default }
```

### 4.3 Spring Cloud Gateway — `BE/api-gateway/src/main/resources/application.yml`

Bổ sung routes với `RequestRateLimiter` filter (ghi đè block `default-filters` ở dòng 32-37 hiện tại):

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-strict
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/register,/api/auth/login
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 5
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@ipKeyResolver}"
        - id: auth-refresh-normal
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/refresh
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 30
                redis-rate-limiter.burstCapacity: 60
                key-resolver: "#{@ipKeyResolver}"
        - id: products-normal
          uri: lb://product-service
          predicates:
            - Path=/api/products/**,/api/categories/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 30
                redis-rate-limiter.burstCapacity: 60
                key-resolver: "#{@ipKeyResolver}"
        # actuator, swagger, v3/api-docs: route mặc định qua discovery locator
        # → KHÔNG gắn filter → unlimited
```

> Spring Cloud Gateway `RequestRateLimiter` mặc định dùng Redis. `redis:6379` đã có sẵn trong `BE/docker-compose.yml:186-204` → zero new infra.

### 4.4 Bean mới

| Bean | File | Mục đích |
|---|---|---|
| `IpKeyResolver` | `BE/api-gateway/src/main/java/com/ecom/gateway/config/IpKeyResolver.java` | Trích IP từ `X-Forwarded-For` (header đầu tiên) hoặc `RemoteAddr` |
| `ServiceUnavailableException` | `BE/common/src/main/java/com/ecom/common/web/ServiceUnavailableException.java` | Exception dùng cho Feign fallback |
| `KafkaListenerResilienceWrapper` | `BE/common/src/main/java/com/ecom/common/messaging/KafkaListenerResilienceWrapper.java` | Bean utility wrap handler với TimeLimiter + fallback ném `AmqpRejectAndDontRequeueException` |

### 4.5 File cần sửa (existing)

| File | Sửa |
|---|---|
| `BE/cart-service/.../service/CartService.java` | Thêm `@CircuitBreaker(name="productService", fallbackMethod="...") @Retry(name="productService") @Bulkhead(name="productService")` trên `addItem`, `updateItem`. Thêm fallback method throw `ServiceUnavailableException`. |
| `BE/order-service/.../service/OrderService.java` | Thêm annotation trên `createOrder` (cartService), `cancelOrder` (inventoryService), `cancelAfterPaymentFailure` (inventoryService). |
| `BE/api-gateway/.../application.yml` | Thêm 3 routes với `RequestRateLimiter` filter. |
| `BE/api-gateway/.../config/RateLimitConfig.java` (mới) | Đăng ký `IpKeyResolver` bean. |
| `BE/common/.../web/GlobalExceptionHandler.java` | Thêm 2 handler mới: `ServiceUnavailableException`/`CallNotPermittedException`/`BulkheadFullException`/`IOException` → 503; `RequestNotPermitted` → 429. **Đồng thời fix tech-debt #5** bằng cách `@ComponentScan` hoặc `@Import` từ Application class của từng service. |
| 8 service `*Application.java` (auth, user, product, cart, inventory, order, payment, notification) | Thêm `@ComponentScan(basePackages = {"com.ecom.common", "com.ecom.<service>"})` hoặc `@Import(GlobalExceptionHandler.class)`. |
| Mỗi Kafka consumer class | Inject `KafkaListenerResilienceWrapper` thay vì gọi trực tiếp handler. |
| `BE/infra/prometheus/prometheus.yml` | Bổ sung 8 job cho 8 business services + gateway. **Fix tech-debt #6.** |
| `BE/config-repo/<service>.yml` | Verify `management.endpoints.web.exposure.include` đã có `prometheus,metrics` (đa số có qua `config-repo/application.yml`). |

### 4.6 File cần tạo mới

| File | Mục đích |
|---|---|
| `BE/common/src/main/java/com/ecom/common/web/ServiceUnavailableException.java` | Exception mới |
| `BE/common/src/main/java/com/ecom/common/messaging/KafkaListenerResilienceWrapper.java` | Bean wrapper |
| `BE/api-gateway/src/main/java/com/ecom/gateway/config/RateLimitConfig.java` | `IpKeyResolver` bean |
| `BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceTest.java` | Unit test |
| `BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceIT.java` | Integration test với WireMock |
| `BE/order-service/src/test/java/com/ecom/order/service/OrderServiceResilienceTest.java` | Unit test |
| `BE/order-service/src/test/java/com/ecom/order/service/OrderServiceResilienceIT.java` | Integration test với WireMock |
| `BE/common/src/test/java/com/ecom/common/messaging/KafkaListenerResilienceWrapperTest.java` | Unit test |
| `BE/api-gateway/src/test/java/com/ecom/gateway/config/IpKeyResolverTest.java` | Unit test |
| `BE/api-gateway/src/test/java/com/ecom/gateway/GatewayRateLimitTest.java` | Integration test với embedded Redis |

---

## 5. Fallback Semantics

### 5.1 Feign (CircuitBreaker + Retry + Bulkhead) — Fail-fast

Khi CB mở, retry hết, hoặc bulkhead full → fallback method ném `ServiceUnavailableException`. Controller catch qua `GlobalExceptionHandler` → HTTP 503.

**Lý do fail-fast** (đã chọn ở brainstorming): khi `product-service` chết, cố snapshot giả sẽ tạo order sai giá → refund storm. Throw 503 + retry hint → user nhận thông báo "service temporarily unavailable, please retry" → đúng nghĩa.

### 5.2 API Gateway RateLimiter — 429 Too Many Requests

| Nhóm | Endpoints | Limit | Response |
|---|---|---|---|
| STRICT | `/api/auth/register`, `/api/auth/login` | 5 req/min per IP | 429 + body `{"code":"RATE_LIMITED",...}` + `Retry-After: 60` |
| NORMAL | `/api/auth/refresh`, `/api/products/**`, `/api/categories/**` | 30 req/s per IP, burst 60 | 429 + body tương tự + `Retry-After: 1` |
| NONE | `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**` | Không filter | — |

**Key resolution:** `IpKeyResolver` ưu tiên `X-Forwarded-For` (split `,` lấy phần tử đầu) → fallback `RemoteAddr`. Tương thích khi đặt behind load balancer.

**Atomic guarantee:** Redis `INCR` + `EXPIRE` (Lua script) → atomic, không cần distributed lock.

### 5.3 Kafka Consumer TimeLimiter — Timeout → retry

`KafkaListenerResilienceWrapper.execute(handler, fallbackLogger)`:
- Wrap handler trong `CompletableFuture.supplyAsync(...)`
- Decorate với `TimeLimiter.of("kafkaConsumer", Duration.ofSeconds(5))`
- Fallback cho `TimeoutException` + `CallNotPermittedException` → ném `AmqpRejectAndDontRequeueException("timeout", e)` (KHÔNG return null)
- Spring Kafka `DefaultErrorHandler` (đã có sẵn ở `BE/common/.../config/KafkaErrorHandler.java`) bắt exception → retry 3 lần với 1s back-off → nếu vẫn fail thì drop + log error

**Lý do `AmqpRejectAndDontRequeueException` thay vì return null:** nếu return null thì Spring Kafka sẽ ack message ngay → mất data. Ném exception để trigger retry.

### 5.4 Response body tổng hợp

| Failure | HTTP Status | Body | Header |
|---|---|---|---|
| Feign downstream chết / CB mở | 503 | `{code:"SERVICE_UNAVAILABLE", message, correlationId, timestamp}` | `Retry-After: 5` |
| Rate limit vượt (STRICT) | 429 | `{code:"RATE_LIMITED", message:"Too many auth attempts. Try again later.",...}` | `Retry-After: 60` |
| Rate limit vượt (NORMAL) | 429 | `{code:"RATE_LIMITED", message:"Rate limit exceeded.",...}` | `Retry-After: 1` |
| Kafka handler timeout | (nội bộ) | — | retry 3 lần qua KafkaErrorHandler rồi drop |

---

## 6. Error Handling

### 6.1 `ServiceUnavailableException` (mới, common)

```java
package com.ecom.common.web;

public class ServiceUnavailableException extends RuntimeException {
    private final String downstreamService;
    public ServiceUnavailableException(String downstreamService, Throwable cause) {
        super("Downstream service '" + downstreamService + "' is unavailable", cause);
        this.downstreamService = downstreamService;
    }
    public String getDownstreamService() { return downstreamService; }
}
```

### 6.2 Sửa `GlobalExceptionHandler` (fix tech-debt #5 đồng thời)

Thêm 2 handler mới vào file hiện có (`BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java`):

```java
@ExceptionHandler({ServiceUnavailableException.class, CallNotPermittedException.class,
                   BulkheadFullException.class, IOException.class})
public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(Exception ex, HttpServletRequest request) {
    String downstream = ex instanceof ServiceUnavailableException sue
        ? sue.getDownstreamService() : "unknown";
    log.warn("Downstream unavailable: {} | path={} | reason={}",
             downstream, request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                         .header("Retry-After", "5")
                         .body(ErrorResponse.of("SERVICE_UNAVAILABLE",
                             "Downstream service temporarily unavailable. Please retry.",
                             List.of(), getCorrelationId(request), Instant.now()));
}

@ExceptionHandler(RequestNotPermitted.class)
public ResponseEntity<ErrorResponse> handleRateLimit(RequestNotPermitted ex, HttpServletRequest request) {
    log.warn("Rate limit exceeded: {} | path={}", ex.getMessage(), request.getRequestURI());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                         .header("Retry-After", "60")
                         .body(ErrorResponse.of("RATE_LIMITED",
                             "Rate limit exceeded. Please slow down.",
                             List.of(), getCorrelationId(request), Instant.now()));
}
```

**Để handler có hiệu lực** (fix tech-debt #5): thêm `@ComponentScan(basePackages = {"com.ecom.common", "com.ecom.<service>"})` vào Application class của 8 business service. Trước đây mỗi service tự có `*ExceptionHandler` riêng (vd: `OrderExceptionHandler` ở `BE/order-service/.../web/OrderExceptionHandler.java`) — KHÔNG xóa trong scope này, chỉ thêm common handler ưu tiên cao hơn. Note tech-debt để dọn sau.

### 6.3 Log strategy

| Event | Level | Format (kèm correlationId qua MDC) |
|---|---|---|
| CB mở (state OPEN) | WARN | `circuitbreaker: name=X state=OPEN failureRate=Y%` |
| CB đóng lại (HALF_OPEN → CLOSED) | INFO | `circuitbreaker: name=X state=CLOSED` |
| Retry attempt | DEBUG | `retry: name=X attempt=N/3 ex=IOException` |
| Rate limit reject | WARN | `ratelimit: name=X path=/api/auth/login ip=1.2.3.4` |
| TimeLimiter timeout | WARN | `timelimiter: name=kafkaConsumer topic=order.confirmed duration=5.001s` |
| ServiceUnavailableException thrown | WARN | `Downstream unavailable: inventory-service | path=/api/orders` |

Log format có correlationId nhờ MDC pattern (`BE/config-repo/application.yml:23-27`).

### 6.4 KafkaErrorHandler KHÔNG thay đổi

Giữ nguyên `BE/common/.../config/KafkaErrorHandler.java`. Tương thích với `AmqpRejectAndDontRequeueException` từ wrapper. **Optional future work**: thêm `DeadLetterPublishingRecoverer` để chuyển message timeout sang DLQ topic — note tech-debt, không làm trong scope này.

---

## 7. Testing Strategy

### 7.1 Test layers

| Layer | Type | Tool | File naming |
|---|---|---|---|
| Feign + CB + Retry + Bulkhead | Unit | JUnit 5 + Mockito + R4J `CircuitBreakerRegistry` | `*ServiceResilienceTest.java` |
| API Gateway RateLimiter | Unit | JUnit 5 + `WebTestClient` mock | `GatewayRateLimitTest.java` |
| Kafka TimeLimiter wrapper | Unit | JUnit 5 + assert `TimeoutException` | `KafkaListenerResilienceWrapperTest.java` |
| GlobalExceptionHandler | Unit | `@WebMvcTest` + MockMvc | `GlobalExceptionHandlerResilienceTest.java` |
| End-to-end Feign → CB mở → 503 | Integration | Spring Boot Test + WireMock | `*ServiceResilienceIT.java` |

### 7.2 Test cases cho mỗi Feign client (3 client × ~5 test = 15 test)

| # | Test | Expected |
|---|---|---|
| 1 | Downstream returns 200 | Success, no fallback |
| 2 | Downstream returns 503 | Retry tối đa 2 lần, cuối cùng ServiceUnavailableException |
| 3 | Downstream timeout (WireMock delay 6s với CB timeout 2s) | Retry → ServiceUnavailableException |
| 4 | 10 failures liên tiếp | CB → OPEN, call tiếp theo ném ngay |
| 5 | Sau waitDurationInOpenState (30s) | CB → HALF_OPEN, 5 lần test pass → CLOSED |
| 6 | 20 concurrent calls | Call thứ 21 ném BulkheadFullException |
| 7 | Exception không nằm trong recordExceptions (vd: IllegalArgumentException) | KHÔNG tính failure, CB không đếm |

### 7.3 Test cho KafkaListenerResilienceWrapper

```java
@Test void timeout_throwsAmqpRejectAndDontRequeue() {
    Callable<String> slow = () -> { Thread.sleep(6000); return "ok"; };
    // Fallback ném AmqpRejectAndDontRequeueException → wrapper.execute cũng ném
    // (Spring Kafka KafkaErrorHandler sẽ bắt và retry 3 lần)
    assertThatThrownBy(() -> wrapper.execute(slow, t -> log.warn("timeout: {}", t.getMessage())))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasMessageContaining("timeout");
}
@Test void normalExecution_returnsResult() {
    assertThat(wrapper.execute(() -> "hello", t -> {})).isEqualTo("hello");
}
```

### 7.4 Test cho RateLimiter Gateway

Dùng `WebTestClient` với 6 request liên tiếp đến `/api/auth/login` → request thứ 6 trả 429. Verify response header `Retry-After`.

### 7.5 Observability verification

Sau integration test, kiểm tra:
- `GET /actuator/prometheus` có `resilience4j_circuitbreaker_state{name="productService",state="open"} 1.0`
- Có `resilience4j_ratelimiter_available_permissions{name="rateLimitStrict"}`
- Log output có correlationId (format: `WARN [cart-service,abc-123] Downstream unavailable: ...`)

### 7.6 Không test trong scope

- Load test (Gatling/JMeter) — YAGNI
- Chaos test (Chaos Monkey) — YAGNI
- Contract test (Spring Cloud Contract) — có thể thêm sau
- Coverage measurement (jacoco) — tech-debt `#15`, YAGNI

---

## 8. Tech-debt được fix trong scope này

| Tech-debt | Mô tả | Cách fix |
|---|---|---|
| `#5` | `GlobalExceptionHandler` ở `common/` không được scan | Thêm handler mới (R4J) + `@ComponentScan` từ 8 Application class |
| `#6` | Prometheus chỉ scrape 3/11 service | Bổ sung 8 jobs cho business services + gateway (tổng 9) trong `BE/infra/prometheus/prometheus.yml` |

## 9. Tech-debt note (KHÔNG fix)

| # | Mô tả | Lý do không fix |
|---|---|---|
| `#15` | Chưa đo coverage | YAGNI — chưa có test coverage gate |
| (mới) | `GlobalExceptionHandler` mới sẽ duplicate với `*ExceptionHandler` riêng của từng service | Dọn riêng (xóa handler cũ) trong plan sau |
| (mới) | Kafka timeout → retry 3 lần rồi drop, không có DLQ | Cần thêm `DeadLetterPublishingRecoverer` + DLQ topic infra — out of scope |
| (mới) | `request body` cho 503/429 chưa bao gồm `details` field | Service-specific errors (vd: từng productId) không kèm; có thể nâng cấp sau |

---

## 10. Out of scope (explicit)

- **KHÔNG** thêm Circuit Breaker cho Kafka consumer (chỉ TimeLimiter)
- **KHÔNG** thay thế `KafkaErrorHandler` retry bằng R4J retry
- **KHÔNG** thêm DLQ cho Kafka
- **KHÔNG** đo coverage (jacoco)
- **KHÔNG** load test / chaos test
- **KHÔNG** rate limit trên internal service-to-service call (chỉ public-facing qua gateway)
- **KHÔNG** thay đổi `*ExceptionHandler` riêng của từng service (vd: `OrderExceptionHandler`) — chỉ thêm common handler
- **KHÔNG** thêm `permitAll()` cho `actuator/prometheus` ở từng service (chỉ gateway)

---

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Resilience4J version conflict với Spring Boot 3.3.6 | Pin version `resilience4j-spring-boot3:2.2.0` (đã verify tương thích) |
| Global handler duplicate với handler cũ của từng service | Specific handler vẫn ưu tiên (Spring chọn handler cụ thể trước generic). Nếu conflict, thêm `@Order(Ordered.HIGHEST_PRECEDENCE)` lên GlobalExceptionHandler. |
| Test WireMock không start được khi chạy Maven test parallel | Mỗi IT dùng port ngẫu nhiên (`wireMockConfig().dynamicPort()`) |
| Redis rate limiter chết → toàn bộ gateway down | Spring Cloud Gateway fallback trả 500 (acceptable — Redis là infra critical). Note để monitor. |
| Retry có thể amplify load khi downstream vừa recover | `maxAttempts: 3` (1 + 2 retries) là giới hạn an toàn. Có thể giảm xuống 2 nếu thấy amplify. |

---

## 12. Definition of Done

- [ ] Tất cả file mục 4.4, 4.6 đã tạo
- [ ] Tất cả file mục 4.5 đã sửa đúng theo thiết kế
- [ ] `BE/config-repo/application.yml` có section `resilience4j:` đầy đủ
- [ ] `BE/api-gateway/.../application.yml` có 3 routes mới với `RequestRateLimiter`
- [ ] 8 Application class có `@ComponentScan` bao gồm `com.ecom.common`
- [ ] `BE/infra/prometheus/prometheus.yml` có đủ 9+ jobs
- [ ] Tất cả test mục 7 pass: 3 × 5 = 15 Feign unit test, 1 wrapper test, 1 IP resolver test, 1 rate limit test, 2 IT test
- [ ] Không test hiện có nào bị broken
- [ ] `mvn clean install` pass
- [ ] Manual smoke: start infrastructure + 1 service (cart), gọi `POST /api/cart/items` khi product-service chết → nhận 503 với `code:"SERVICE_UNAVAILABLE"`
- [ ] Manual smoke: gọi 6 lần `POST /api/auth/login` trong 1 phút → request thứ 6 nhận 429 với `code:"RATE_LIMITED"`
- [ ] Manual smoke: `GET /actuator/prometheus` chứa metric `resilience4j_circuitbreaker_state`

---

## 13. References

- Resilience4J docs: https://resilience4j.readme.io/docs/getting-started-3
- Spring Cloud Circuit Breaker: https://docs.spring.io/spring-cloud-circuitbreaker/reference/
- Spring Cloud Gateway RequestRateLimiter: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/gateway-filter-factories.html#requestratelimiter
- File nguồn quan trọng:
  - `D:/project/Ecom/.claude/knowledge-map.md` (knowledge map tổng)
  - `BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java`
  - `BE/common/src/main/java/com/ecom/common/config/KafkaErrorHandler.java`
  - `BE/cart-service/src/main/java/com/ecom/cart/client/ProductClient.java`
  - `BE/order-service/src/main/java/com/ecom/order/client/CartClient.java`
  - `BE/order-service/src/main/java/com/ecom/order/client/InventoryClient.java`
  - `BE/api-gateway/src/main/resources/application.yml`
  - `BE/config-repo/application.yml`
  - `BE/infra/prometheus/prometheus.yml`
