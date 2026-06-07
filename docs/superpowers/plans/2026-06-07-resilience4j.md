# Resilience4J Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tích hợp Resilience4J vào Ecom microservices với 3 pattern (Circuit Breaker + Retry + Bulkhead cho Feign, RateLimiter cho Gateway, TimeLimiter cho Kafka) để bảo vệ khỏi cascading failure và traffic burst, đồng thời fix 2 tech-debt liên quan (`#5` GlobalExceptionHandler scan, `#6` Prometheus scrape).

**Architecture:** Spring Cloud Circuit Breaker abstraction với annotation `@CircuitBreaker/@Retry/@Bulkhead/@TimeLimiter` wrap quanh service method. Deps centralized ở `BE/common/pom.xml`. Config ở `BE/config-repo/application.yml`. ServiceUnavailableException mới cho Feign fallback fail-fast. Gateway dùng Spring Cloud Gateway `RequestRateLimiter` với Redis backend (đã có sẵn). Kafka dùng custom wrapper bean vì `@TimeLimiter` annotation không áp dụng trực tiếp lên `@KafkaListener` method.

**Tech Stack:** Resilience4J 2.2.0 (resilience4j-spring-boot3), Spring Cloud Circuit Breaker abstraction, Spring Boot 3.3.6, Java 21, Redis 7 (sẵn), WireMock (test), JUnit 5, Mockito.

**Reference:** Spec tại `D:/project/Ecom/docs/superpowers/specs/2026-06-07-resilience4j-design.md`. Knowledge map tại `D:/project/Ecom/.claude/knowledge-map.md`.

---

## Task 1: Add Resilience4J dependencies to common/pom.xml

**Files:**
- Modify: `BE/common/pom.xml:1-25`

- [ ] **Step 1: Modify common/pom.xml**

Mở `BE/common/pom.xml`. Thêm 3 dependencies SAU dependency `spring-kafka` hiện tại (dòng 16-19) và TRƯỚC `spring-boot-starter-test`:

```xml
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
```

Toàn bộ block `<dependencies>` của common/pom.xml sau khi sửa sẽ trông như:

```xml
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
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

- [ ] **Step 2: Verify compilation**

Run: `cd D:/project/Ecom/BE && mvn -pl common -am compile -q`
Expected: `BUILD SUCCESS` (no output if `-q` quiet)

- [ ] **Step 3: Commit**

```bash
cd D:/project/Ecom && git add BE/common/pom.xml && git commit -m "feat(common): add Resilience4J, Spring Cloud Circuit Breaker, AOP starter"
```

---

## Task 2: Add resilience4j shared config to config-repo/application.yml

**Files:**
- Modify: `BE/config-repo/application.yml:1-29` (thêm section `resilience4j` ở cuối)

- [ ] **Step 1: Append resilience4j config**

Mở `BE/config-repo/application.yml`. Nội dung hiện tại:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  health:
    db:
      enabled: true
    redis:
      enabled: true
    kafka:
      enabled: true

logging:
  pattern:
    level: "%5p [${spring.application.name:unknown},%X{correlationId:-}]"
  level:
    root: INFO
    com.ecom: DEBUG
    org.springframework.web: INFO
    org.apache.kafka: WARN
```

THÊM section `resilience4j` ở cuối file (sau `org.apache.kafka: WARN`):

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

- [ ] **Step 2: Verify YAML syntax**

Run: `cd D:/project/Ecom/BE && python -c "import yaml; yaml.safe_load(open('config-repo/application.yml'))" && echo OK`
Expected: `OK` (Python available trên Windows; nếu không có Python, skip và dựa vào mvn test ở task sau)

- [ ] **Step 3: Commit**

```bash
cd D:/project/Ecom && git add BE/config-repo/application.yml && git commit -m "feat(config): add resilience4j shared config (CB/Retry/Bulkhead/TimeLimiter instances)"
```

---

## Task 3: Create ServiceUnavailableException (TDD)

**Files:**
- Create: `BE/common/src/main/java/com/ecom/common/web/ServiceUnavailableException.java`
- Test: `BE/common/src/test/java/com/ecom/common/web/ServiceUnavailableExceptionTest.java`

- [ ] **Step 1: Write the failing test**

Tạo file `BE/common/src/test/java/com/ecom/common/web/ServiceUnavailableExceptionTest.java`:

```java
package com.ecom.common.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceUnavailableExceptionTest {

    @Test
    void constructor_setsDownstreamServiceAndCause() {
        IOException cause = new IOException("connection refused");
        ServiceUnavailableException ex = new ServiceUnavailableException("product-service", cause);

        assertThat(ex.getDownstreamService()).isEqualTo("product-service");
        assertThat(ex.getCauseException()).isSameAs(cause);
        assertThat(ex.getMessage()).contains("product-service");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void exceptionIsRuntimeException() {
        ServiceUnavailableException ex = new ServiceUnavailableException("x", new IOException());
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:/project/Ecom/BE && mvn -pl common test -Dtest=ServiceUnavailableExceptionTest`
Expected: FAIL with `error: cannot find symbol: class ServiceUnavailableException`

- [ ] **Step 3: Create the exception class**

Tạo file `BE/common/src/main/java/com/ecom/common/web/ServiceUnavailableException.java`:

```java
package com.ecom.common.web;

public class ServiceUnavailableException extends RuntimeException {
    private final String downstreamService;

    public ServiceUnavailableException(String downstreamService, Throwable cause) {
        super("Downstream service '" + downstreamService + "' is unavailable", cause);
        this.downstreamService = downstreamService;
    }

    public String getDownstreamService() {
        return downstreamService;
    }

    public Throwable getCauseException() {
        return getCause();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd D:/project/Ecom/BE && mvn -pl common test -Dtest=ServiceUnavailableExceptionTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
cd D:/project/Ecom && git add BE/common/src/main/java/com/ecom/common/web/ServiceUnavailableException.java BE/common/src/test/java/com/ecom/common/web/ServiceUnavailableExceptionTest.java && git commit -m "feat(common): add ServiceUnavailableException for Feign fallback"
```

---

## Task 4: Add Resilience4J handlers to GlobalExceptionHandler (TDD)

**Files:**
- Modify: `BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java`
- Test: `BE/common/src/test/java/com/ecom/common/web/GlobalExceptionHandlerResilienceTest.java`

- [ ] **Step 1: Add test deps to common/pom.xml (test scope)**

Mở `BE/common/pom.xml`. Thêm vào cuối block `<dependencies>` (sau `spring-boot-starter-aop`):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
```

Lưu ý: `spring-boot-starter-test` có thể đã có sẵn ở dòng 12-15. Nếu đã có, bỏ qua. Verify: file common/pom.xml hiện tại dòng 11-15 có `<artifactId>spring-boot-starter-test</artifactId>` — CÓ. Chỉ thêm h2.

- [ ] **Step 2: Write the failing test**

Tạo file `BE/common/src/test/java/com/ecom/common/web/GlobalExceptionHandlerResilienceTest.java`:

```java
package com.ecom.common.web;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerResilienceTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(request.getHeader("X-Correlation-Id")).thenReturn("corr-123");
        when(request.getRequestURI()).thenReturn("/api/cart/items");
    }

    @Test
    void serviceUnavailable_returns503WithCode() {
        ServiceUnavailableException ex = new ServiceUnavailableException("product-service", new IOException());

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-123");
        assertThat(response.getBody().message()).contains("temporarily unavailable");
    }

    @Test
    void callNotPermitted_returns503() {
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50).build());
        // Force OPEN
        cb.transitionToOpenState();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void bulkheadFull_returns503() {
        BulkheadFullException ex = BulkheadFullException.createBulkheadFullException(
            io.github.resilience4j.bulkhead.Bulkhead.ofDefaults("test"));

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void ioException_returns503() {
        IOException ex = new IOException("connection timeout");

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void requestNotPermitted_returns429() {
        RateLimiter limiter = RateLimiter.of("test", RateLimiterConfig.custom()
            .limitForPeriod(5).limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build());
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(limiter);

        ResponseEntity<ErrorResponse> response = handler.handleRateLimit(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd D:/project/Ecom/BE && mvn -pl common test -Dtest=GlobalExceptionHandlerResilienceTest`
Expected: FAIL với compile error (chưa có method `handleDownstreamUnavailable` và `handleRateLimit`)

- [ ] **Step 4: Add the 2 handlers to GlobalExceptionHandler**

Mở `BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java`. Sửa imports ở đầu file từ:

```java
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
```

thành:

```java
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
```

Sau method `handleAll` (cuối file), thêm trước method `getCorrelationId`:

```java
    @ExceptionHandler({ServiceUnavailableException.class, CallNotPermittedException.class,
                       BulkheadFullException.class, java.io.IOException.class})
    public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(Exception ex, HttpServletRequest request) {
        String downstream = ex instanceof ServiceUnavailableException sue
            ? sue.getDownstreamService() : "unknown";
        log.warn("Downstream unavailable: {} | path={} | reason={}",
                 downstream, request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            "SERVICE_UNAVAILABLE",
            "Downstream service temporarily unavailable. Please retry.",
            List.of(),
            getCorrelationId(request),
            java.time.Instant.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                             .header("Retry-After", "5")
                             .body(body);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RequestNotPermitted ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded: {} | path={}", ex.getMessage(), request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(
            "RATE_LIMITED",
            "Rate limit exceeded. Please slow down.",
            List.of(),
            getCorrelationId(request),
            java.time.Instant.now()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                             .header("Retry-After", "60")
                             .body(body);
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd D:/project/Ecom/BE && mvn -pl common test -Dtest=GlobalExceptionHandlerResilienceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd D:/project/Ecom && git add BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java BE/common/src/test/java/com/ecom/common/web/GlobalExceptionHandlerResilienceTest.java BE/common/pom.xml && git commit -m "feat(common): add Resilience4J exception handlers to GlobalExceptionHandler"
```

---

## Task 5: Add @ComponentScan to 8 Application classes

**Files:**
- Modify: `BE/auth-service/src/main/java/com/ecom/auth/AuthServiceApplication.java`
- Modify: `BE/user-service/src/main/java/com/ecom/user/UserServiceApplication.java`
- Modify: `BE/product-service/src/main/java/com/ecom/product/ProductServiceApplication.java`
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/CartServiceApplication.java`
- Modify: `BE/inventory-service/src/main/java/com/ecom/inventory/InventoryServiceApplication.java`
- Modify: `BE/order-service/src/main/java/com/ecom/order/OrderServiceApplication.java`
- Modify: `BE/payment-service/src/main/java/com/ecom/payment/PaymentServiceApplication.java`
- Modify: `BE/notification-service/src/main/java/com/ecom/notification/NotificationServiceApplication.java`

- [ ] **Step 1: Add @ComponentScan to auth-service**

Mở `BE/auth-service/src/main/java/com/ecom/auth/AuthServiceApplication.java`. Thêm import `org.springframework.context.annotation.ComponentScan` và annotation `@ComponentScan(basePackages = {"com.ecom.common", "com.ecom.auth"})` ngay trước `@SpringBootApplication`. File sau sửa:

```java
package com.ecom.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {"com.ecom.common", "com.ecom.auth"})
@SpringBootApplication
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

- [ ] **Step 2: Repeat for 7 remaining services**

Với mỗi service trong {user, product, cart, inventory, order, payment, notification}, mở file `*Application.java` của service đó và thêm `@ComponentScan(basePackages = {"com.ecom.common", "com.ecom.<service>"})` với `<service>` tương ứng (`user`, `product`, `cart`, `inventory`, `order`, `payment`, `notification`).

Ví dụ cho `order-service` (`BE/order-service/src/main/java/com/ecom/order/OrderServiceApplication.java`):

```java
package com.ecom.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {"com.ecom.common", "com.ecom.order"})
@EnableFeignClients
@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Verify all 8 services compile**

Run: `cd D:/project/Ecom/BE && mvn -pl auth-service,user-service,product-service,cart-service,inventory-service,order-service,payment-service,notification-service -am compile -q`
Expected: `BUILD SUCCESS` (no errors)

- [ ] **Step 4: Verify smoke test still passes for 1 service**

Run: `cd D:/project/Ecom/BE && mvn -pl cart-service test -Dtest=CartServiceApplicationTest -q`
Expected: `BUILD SUCCESS` (smoke test pass)

- [ ] **Step 5: Commit**

```bash
cd D:/project/Ecom && git add BE/auth-service/src/main/java/com/ecom/auth/AuthServiceApplication.java BE/user-service/src/main/java/com/ecom/user/UserServiceApplication.java BE/product-service/src/main/java/com/ecom/product/ProductServiceApplication.java BE/cart-service/src/main/java/com/ecom/cart/CartServiceApplication.java BE/inventory-service/src/main/java/com/ecom/inventory/InventoryServiceApplication.java BE/order-service/src/main/java/com/ecom/order/OrderServiceApplication.java BE/payment-service/src/main/java/com/ecom/payment/PaymentServiceApplication.java BE/notification-service/src/main/java/com/ecom/notification/NotificationServiceApplication.java && git commit -m "fix: scan com.ecom.common package in all 8 service applications

Fixes tech-debt #5: GlobalExceptionHandler in common/ was never scanned by services."
```

---

## Task 6: Create KafkaListenerResilienceWrapper (TDD)

**Files:**
- Create: `BE/common/src/main/java/com/ecom/common/messaging/KafkaListenerResilienceWrapper.java`
- Test: `BE/common/src/test/java/com/ecom/common/messaging/KafkaListenerResilienceWrapperTest.java`

- [ ] **Step 1: Write the failing test**

Tạo file `BE/common/src/test/java/com/ecom/common/messaging/KafkaListenerResilienceWrapperTest.java`:

```java
package com.ecom.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaListenerResilienceWrapperTest {

    private final KafkaListenerResilienceWrapper wrapper = new KafkaListenerResilienceWrapper();

    @Test
    void normalExecution_returnsResult() throws Exception {
        Callable<String> handler = () -> "hello";

        String result = wrapper.execute(handler, t -> {});

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void timeout_throwsAmqpRejectAndDontRequeue() {
        Callable<String> slowHandler = () -> {
            Thread.sleep(6000);
            return "ok";
        };
        AtomicReference<Throwable> captured = new AtomicReference<>();

        assertThatThrownBy(() -> wrapper.execute(slowHandler, captured::set))
            .isInstanceOf(AmqpRejectAndDontRequeueException.class)
            .hasMessageContaining("timeout");

        // captured chỉ nhận Throwable từ supplier, không phải từ AmqpReject
        // (AmqpReject được ném ra bên ngoài, không qua fallback lambda)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:/project/Ecom/BE && mvn -pl common test -Dtest=KafkaListenerResilienceWrapperTest`
Expected: FAIL với compile error (chưa có class `KafkaListenerResilienceWrapper`)

- [ ] **Step 3: Create the wrapper**

Tạo file `BE/common/src/main/java/com/ecom/common/messaging/KafkaListenerResilienceWrapper.java`:

```java
package com.ecom.common.messaging;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Component
public class KafkaListenerResilienceWrapper {
    private static final Logger log = LoggerFactory.getLogger(KafkaListenerResilienceWrapper.class);

    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduler;

    public KafkaListenerResilienceWrapper() {
        this(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .cancelRunningFuture(true)
                .build());
    }

    KafkaListenerResilienceWrapper(TimeLimiterConfig config) {
        this.timeLimiter = TimeLimiter.of("kafkaConsumer", config);
        this.scheduler = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors());
    }

    public <T> T execute(Callable<T> handler, Consumer<Throwable> onTimeout) {
        var supplier = java.util.function.Supplier.<CompletionStage<T>>of(() ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    return handler.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
        );
        var decorated = io.github.resilience4j.decorators.Decorators
            .ofCompletionStage(supplier)
            .withTimeLimiter(timeLimiter, scheduler)
            .withFallback(java.util.List.of(TimeoutException.class),
                e -> {
                    onTimeout.accept(e);
                    throw new AmqpRejectAndDontRequeueException(
                        "kafka handler timeout (5s) — will be retried by KafkaErrorHandler", e);
                })
            .decorate();
        return decorated.get().toCompletableFuture().join();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd D:/project/Ecom/BE && mvn -pl common test -Dtest=KafkaListenerResilienceWrapperTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
cd D:/project/Ecom && git add BE/common/src/main/java/com/ecom/common/messaging/KafkaListenerResilienceWrapper.java BE/common/src/test/java/com/ecom/common/messaging/KafkaListenerResilienceWrapperTest.java && git commit -m "feat(common): add KafkaListenerResilienceWrapper with TimeLimiter (5s timeout)"
```

---

## Task 7: Wrap cart-service ProductClient with CB+Retry+Bulkhead (TDD)

**Files:**
- Modify: `BE/cart-service/pom.xml:1-50` (add WireMock test deps)
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/service/CartService.java`
- Test: `BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceTest.java`
- Test: `BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceIT.java`

- [ ] **Step 1: Add WireMock dep to cart-service pom.xml**

Mở `BE/cart-service/pom.xml`. Thêm dependency ngay sau block `spring-cloud-starter-openfeign`:

```xml
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>3.9.1</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the failing test (unit)**

Tạo file `BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceTest.java`:

```java
package com.ecom.cart.service;

import com.ecom.cart.client.ProductClient;
import com.ecom.cart.client.ProductSnapshot;
import com.ecom.cart.config.CartProperties;
import com.ecom.cart.domain.Cart;
import com.ecom.cart.repository.CartRepository;
import com.ecom.cart.web.dto.AddItemRequest;
import com.ecom.cart.web.dto.CartResponse;
import com.ecom.common.web.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceResilienceTest {

    private ProductClient productClient;
    private CartRepository cartRepository;
    private CartService cartService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        productClient = mock(ProductClient.class);
        cartRepository = mock(CartRepository.class);
        Cart cart = new Cart(userId = UUID.randomUUID());
        when(cartRepository.findByUserIdOrCreate(any())).thenReturn(cart);
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Build a CircuitBreakerRegistry with a fast-fail config for tests
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(10)
                .failureRateThreshold(50).build());
        cartService = new CartService(cartRepository, productClient, mock(CartProperties.class));
        ReflectionTestUtils.setField(cartService, "productServiceCircuitBreaker",
            registry.circuitBreaker("productService"));
    }

    @Test
    void circuitBreaker_opensAfterFailuresAndRejects() {
        when(productClient.getProduct(any()))
            .thenThrow(new RuntimeException(new IOException("connection refused")));

        // 10 failures
        for (int i = 0; i < 10; i++) {
            try {
                cartService.addItem(userId, new AddItemRequest(UUID.randomUUID(), 1));
            } catch (Exception ignored) {}
        }

        // 11th call should fail-fast with ServiceUnavailableException
        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(UUID.randomUUID(), 1)))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("product-service");
    }

    @Test
    void retry_succeedsOnSecondAttempt() {
        UUID productId = UUID.randomUUID();
        ProductSnapshot snap = new ProductSnapshot(productId, "Pizza", new BigDecimal("10.00"), "url", true);
        when(productClient.getProduct(productId))
            .thenThrow(new RuntimeException(new IOException("transient")))
            .thenReturn(snap);

        CartResponse result = cartService.addItem(userId, new AddItemRequest(productId, 2));

        verify(productClient, times(2)).getProduct(productId);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).productName()).isEqualTo("Pizza");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd D:/project/Ecom/BE && mvn -pl cart-service test -Dtest=CartServiceResilienceTest`
Expected: FAIL — có thể compile fail (chưa có `productServiceCircuitBreaker` field) hoặc test fail vì retry chưa apply. Dù sao cũng fail.

- [ ] **Step 4: Modify CartService to add R4J annotations**

Mở `BE/cart-service/src/main/java/com/ecom/cart/service/CartService.java`. Thêm imports:

```java
import com.ecom.common.web.ServiceUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
```

Đọc nội dung file hiện tại (cần xem constructor và method signatures) trước khi sửa. Nếu `addItem` hiện có signature `public CartResponse addItem(UUID userId, AddItemRequest request)`, thêm annotation:

```java
    @CircuitBreaker(name = "productService", fallbackMethod = "addItemFallback")
    @Retry(name = "productService")
    @Bulkhead(name = "productService")
    public CartResponse addItem(UUID userId, AddItemRequest request) {
        // existing body
    }

    // Add fallback method - same signature + Throwable param
    private CartResponse addItemFallback(UUID userId, AddItemRequest request, Throwable t) {
        log.warn("productService unavailable for addItem: {}", t.getMessage());
        throw new ServiceUnavailableException("product-service", t);
    }
```

Tương tự cho `updateItem` nếu có method đó (cần đọc file trước). Nếu `CartService` chỉ có 1 method public dùng ProductClient, chỉ thêm ở đó.

Quan trọng: Class `CartService` cần có field `private static final Logger log = LoggerFactory.getLogger(CartService.class);` — kiểm tra file hiện tại, thêm nếu thiếu.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd D:/project/Ecom/BE && mvn -pl cart-service test -Dtest=CartServiceResilienceTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 6: Write the failing integration test (WireMock)**

Tạo file `BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceIT.java`:

```java
package com.ecom.cart.service;

import com.ecom.cart.CartServiceApplication;
import com.ecom.cart.config.CartProperties;
import com.ecom.cart.repository.CartRepository;
import com.ecom.cart.web.dto.AddItemRequest;
import com.ecom.common.web.ServiceUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CartServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.config.enabled=false",
    "clients.product-service.name=localhost",
    "clients.product-service.port=${wiremock.server.port}"
})
class CartServiceResilienceIT {

    static com.github.tomakehurst.wiremock.junit.WireMockRule wireMockRule =
        new com.github.tomakehurst.wiremock.junit.WireMockRule(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort());

    @org.junit.jupiter.api.BeforeAll
    static void startServer() { wireMockRule.start(); }
    @org.junit.jupiter.api.AfterAll
    static void stopServer() { wireMockRule.stop(); }

    @MockBean private CartRepository cartRepository;
    @Autowired private CartService cartService;
    @Autowired private CartProperties cartProperties;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(cartRepository.findByUserIdOrCreate(any())).thenReturn(
            new com.ecom.cart.domain.Cart(userId));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void productServiceDown_throwsServiceUnavailableException() {
        wireMockRule.stubFor(get(urlMatching("/api/products/.*"))
            .willReturn(aResponse().withStatus(503)));

        UUID productId = UUID.randomUUID();
        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(productId, 1)))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("product-service");
    }
}
```

- [ ] **Step 7: Run integration test**

Run: `cd D:/project/Ecom/BE && mvn -pl cart-service verify -Dtest=CartServiceResilienceIT -DfailIfNoTests=false`
Expected: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
cd D:/project/Ecom && git add BE/cart-service/pom.xml BE/cart-service/src/main/java/com/ecom/cart/service/CartService.java BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceTest.java BE/cart-service/src/test/java/com/ecom/cart/service/CartServiceResilienceIT.java && git commit -m "feat(cart-service): wrap ProductClient with CB+Retry+Bulkhead, fail-fast via ServiceUnavailableException"
```

---

## Task 8: Wrap order-service CartClient with CB+Retry+Bulkhead (TDD)

**Files:**
- Modify: `BE/order-service/pom.xml:1-94` (add WireMock test deps)
- Modify: `BE/order-service/src/main/java/com/ecom/order/service/OrderService.java`
- Test: `BE/order-service/src/test/java/com/ecom/order/service/OrderServiceResilienceTest.java`

- [ ] **Step 1: Add WireMock dep to order-service pom.xml**

Mở `BE/order-service/pom.xml`. Thêm dependency ngay sau `spring-cloud-starter-openfeign`:

```xml
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>3.9.1</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Read OrderService to find methods using CartClient**

Mở `BE/order-service/src/main/java/com/ecom/order/service/OrderService.java`. Tìm methods gọi `cartClient.getCart()` hoặc `cartClient.clearCart()`. Theo code review: `createOrder(UUID userId, String email)` (dòng ~30) gọi cả hai.

- [ ] **Step 3: Write the failing test**

Tạo file `BE/order-service/src/test/java/com/ecom/order/service/OrderServiceResilienceTest.java`:

```java
package com.ecom.order.service;

import com.ecom.common.web.ServiceUnavailableException;
import com.ecom.order.client.CartClient;
import com.ecom.order.client.CartResponse;
import com.ecom.order.client.InventoryClient;
import com.ecom.order.domain.Order;
import com.ecom.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceResilienceTest {

    private CartClient cartClient;
    private InventoryClient inventoryClient;
    private OrderRepository orderRepository;
    private OrderService orderService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        cartClient = mock(CartClient.class);
        inventoryClient = mock(InventoryClient.class);
        orderRepository = mock(OrderRepository.class);
        userId = UUID.randomUUID();
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(10)
                .failureRateThreshold(50).build());
        orderService = new OrderService(orderRepository, cartClient, inventoryClient);
        ReflectionTestUtils.setField(orderService, "cartServiceCircuitBreaker",
            registry.circuitBreaker("cartService"));
        ReflectionTestUtils.setField(orderService, "inventoryServiceCircuitBreaker",
            registry.circuitBreaker("inventoryService"));
    }

    @Test
    void cartServiceDown_throwsServiceUnavailable() {
        when(cartClient.getCart(any(), any()))
            .thenThrow(new RuntimeException(new IOException("connection refused")));

        assertThatThrownBy(() -> orderService.createOrder(userId, "test@example.com"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("cart-service");
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd D:/project/Ecom/BE && mvn -pl order-service test -Dtest=OrderServiceResilienceTest`
Expected: FAIL (compile error do thiếu field `cartServiceCircuitBreaker`)

- [ ] **Step 5: Modify OrderService to add R4J annotations**

Mở `BE/order-service/src/main/java/com/ecom/order/service/OrderService.java`. Thêm imports:

```java
import com.ecom.common.web.ServiceUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

Thêm field `log` nếu chưa có:

```java
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
```

Trên method `createOrder(UUID userId, String email)` (dòng ~30), thêm annotation:

```java
    @CircuitBreaker(name = "cartService", fallbackMethod = "createOrderCartFallback")
    @Retry(name = "cartService")
    @Bulkhead(name = "cartService")
    public OrderResponse createOrder(UUID userId, String email) {
        // existing body
    }

    private OrderResponse createOrderCartFallback(UUID userId, String email, Throwable t) {
        log.warn("cartService unavailable for createOrder: {}", t.getMessage());
        throw new ServiceUnavailableException("cart-service", t);
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd D:/project/Ecom/BE && mvn -pl order-service test -Dtest=OrderServiceResilienceTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
cd D:/project/Ecom && git add BE/order-service/pom.xml BE/order-service/src/main/java/com/ecom/order/service/OrderService.java BE/order-service/src/test/java/com/ecom/order/service/OrderServiceResilienceTest.java && git commit -m "feat(order-service): wrap CartClient with CB+Retry+Bulkhead, fail-fast"
```

---

## Task 9: Wrap order-service InventoryClient with CB+Retry+Bulkhead

**Files:**
- Modify: `BE/order-service/src/main/java/com/ecom/order/service/OrderService.java` (continue từ Task 8)
- Test: bổ sung test case trong `OrderServiceResilienceTest.java`

- [ ] **Step 1: Add the second test case**

Mở `BE/order-service/src/test/java/com/ecom/order/service/OrderServiceResilienceTest.java`. Thêm test method mới (đặt trước closing brace):

```java
    @Test
    void inventoryReserveDown_throwsServiceUnavailable() {
        // Cart ok
        when(cartClient.getCart(any(), any())).thenReturn(
            new CartResponse(userId, List.of()));
        org.mockito.Mockito.doNothing().when(cartClient).clearCart(any(), any());

        // Inventory reserve fails
        when(inventoryClient.reserve(any()))
            .thenThrow(new RuntimeException(new IOException("timeout")));

        assertThatThrownBy(() -> orderService.createOrder(userId, "test@example.com"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("inventory-service");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:/project/Ecom/BE && mvn -pl order-service test -Dtest=OrderServiceResilienceTest`
Expected: FAIL vì `createOrder` chưa có R4J wrap cho `inventoryClient.reserve`

- [ ] **Step 3: Modify createOrder to add inventory wrap**

Trong `OrderService.java`, `createOrder` chỉ wrap cart ở method level. Cần tách logic: cartClient.getCart() wrap riêng, inventoryClient.reserve() wrap riêng. Cách tốt nhất là tách thành 2 private method hoặc dùng programmatic API trong method body.

Cách đơn giản nhất: wrap lời gọi `inventoryClient.reserve()` bằng `Decorators` programmatic:

```java
@Transactional
public OrderResponse createOrder(UUID userId, String email) {
    var cart = cartClient.getCart(userId, email);  // vẫn được wrap bởi @CircuitBreaker(name="cartService")
    if (cart.items().isEmpty()) {
        throw new EmptyCartException();
    }
    Order order = new Order(userId);
    cart.items().forEach(item -> order.addItem(item.productId(), item.productName(), item.unitPrice(), item.quantity()));
    Order saved = orderRepository.save(order);

    // Wrap inventory reserve with CB programmatically
    try {
        ReservationResult result = inventoryReserveWithCB(() -> inventoryClient.reserve(
            new InventoryClient.ReservationRequest(saved.getId(), saved.getItems().stream()
                .map(i -> new InventoryClient.ReservationItemRequest(i.getProductId(), i.getQuantity()))
                .toList())));
        if ("FAILED".equals(result.status())) {
            saved.markFailed();
        }
    } catch (ServiceUnavailableException e) {
        throw e;
    }

    cartClient.clearCart(userId, email);
    return OrderResponse.from(saved);
}

private <T> T inventoryReserveWithCB(java.util.function.Supplier<T> supplier) {
    // Use programmatic CircuitBreaker — R4J "inventoryService" instance từ config
    var cb = circuitBreakerRegistry.circuitBreaker("inventoryService");
    return io.github.resilience4j.decorators.Decorators.ofSupplier(supplier)
        .withCircuitBreaker(cb)
        .withRetry(retryRegistry.retry("inventoryService"))
        .withBulkhead(bulkheadRegistry.bulkhead("inventoryService"))
        .withFallback(java.util.List.of(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
            java.io.IOException.class),
            e -> { throw new ServiceUnavailableException("inventory-service", e); })
        .decorate()
        .get();
}
```

Inject `CircuitBreakerRegistry`, `RetryRegistry`, `BulkheadRegistry` qua constructor (Spring auto-wire).

**Lưu ý**: Cách này phức tạp hơn annotation. Nếu user muốn đơn giản hơn, có thể bỏ qua inventory wrap trong scope này và note tech-debt. **Chọn cách này** vì spec yêu cầu wrap cả inventory.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd D:/project/Ecom/BE && mvn -pl order-service test -Dtest=OrderServiceResilienceTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
cd D:/project/Ecom && git add BE/order-service/src/main/java/com/ecom/order/service/OrderService.java BE/order-service/src/test/java/com/ecom/order/service/OrderServiceResilienceTest.java && git commit -m "feat(order-service): wrap InventoryClient with CB+Retry+Bulkhead (programmatic API)"
```

---

## Task 10: Create IpKeyResolver bean in api-gateway (TDD)

**Files:**
- Create: `BE/api-gateway/src/main/java/com/ecom/gateway/config/RateLimitConfig.java`
- Test: `BE/api-gateway/src/test/java/com/ecom/gateway/config/IpKeyResolverTest.java`

- [ ] **Step 1: Write the failing test**

Tạo file `BE/api-gateway/src/test/java/com/ecom/gateway/config/IpKeyResolverTest.java`:

```java
package com.ecom.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class IpKeyResolverTest {

    private final IpKeyResolver resolver = new IpKeyResolver();

    @Test
    void usesFirstIpFromXForwardedFor() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.1")
                .remoteAddress(new java.net.InetSocketAddress("5.6.7.8", 1234))
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("1.2.3.4");
    }

    @Test
    void fallsBackToRemoteAddrWhenNoHeader() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("5.6.7.8", 1234))
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("5.6.7.8");
    }

    @Test
    void usesXForwardedForSingleValue() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "9.9.9.9")
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("9.9.9.9");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:/project/Ecom/BE && mvn -pl api-gateway test -Dtest=IpKeyResolverTest`
Expected: FAIL (compile error — class `IpKeyResolver` chưa tồn tại)

- [ ] **Step 3: Create RateLimitConfig with IpKeyResolver bean**

Tạo file `BE/api-gateway/src/main/java/com/ecom/gateway/config/RateLimitConfig.java`:

```java
package com.ecom.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    public IpKeyResolver ipKeyResolver() {
        return new IpKeyResolver();
    }
}

class IpKeyResolver implements KeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Lấy IP đầu tiên (client gốc)
            int commaIdx = xff.indexOf(',');
            String firstIp = commaIdx > 0 ? xff.substring(0, commaIdx).trim() : xff.trim();
            return Mono.just(firstIp);
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return Mono.just(request.getRemoteAddress().getAddress().getHostAddress());
        }
        return Mono.just("unknown");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd D:/project/Ecom/BE && mvn -pl api-gateway test -Dtest=IpKeyResolverTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
cd D:/project/Ecom && git add BE/api-gateway/src/main/java/com/ecom/gateway/config/RateLimitConfig.java BE/api-gateway/src/test/java/com/ecom/gateway/config/IpKeyResolverTest.java && git commit -m "feat(api-gateway): add IpKeyResolver for Spring Cloud Gateway RateLimiter"
```

---

## Task 11: Add 3 Gateway routes with RequestRateLimiter

**Files:**
- Modify: `BE/api-gateway/src/main/resources/application.yml:1-77`

- [ ] **Step 1: Modify api-gateway application.yml**

Mở `BE/api-gateway/src/main/resources/application.yml`. Sửa block `spring.cloud.gateway.default-filters` hiện tại (dòng 31-37) — giữ `RequestSize` nhưng bỏ comment placeholder. Sau đó, SAU block `default-filters`, thêm `routes:` section (Spring Cloud Gateway merge `default-filters` với route-specific filters).

Toàn bộ file sau khi sửa:

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  config:
    import: optional:configserver:http://localhost:8888
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      # CORS configuration
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "${CORS_ALLOWED_ORIGINS:*}"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - PATCH
              - OPTIONS
            allowedHeaders:
              - "*"
            allowCredentials: true
            maxAge: 3600
      # Default filters for all routes
      default-filters:
        - name: RequestSize
          args:
            maxSize: 10485760  # 10MB max request size
      # Per-route rate limiting
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
                redis-rate-limiter.requestedTokens: 1
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
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@ipKeyResolver}"

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when_authorized

# Global headers for security
security:
  jwt:
    issuer: ecom-auth-service
    secret: ${JWT_SECRET:?JWT_SECRET environment variable is required}
  public-paths:
    - /api/auth/register
    - /api/auth/login
    - /api/auth/refresh
    - /api/products
    - /api/categories
    - /api/inventory/products
    - /actuator/health
    - /actuator/info
    - /actuator/prometheus
    - /v3/api-docs
    - /swagger-ui
    - /swagger-ui/**
  headers:
    X-Frame-Options: DENY
    X-Content-Type-Options: nosniff
    X-XSS-Protection: "1; mode=block"
    Strict-Transport-Security: "max-age=31536000; includeSubDomains"
    Content-Security-Policy: "default-src 'self'; frame-ancestors 'none'"
```

- [ ] **Step 2: Verify gateway compiles**

Run: `cd D:/project/Ecom/BE && mvn -pl api-gateway compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd D:/project/Ecom && git add BE/api-gateway/src/main/resources/application.yml && git commit -m "feat(api-gateway): add 3 routes with RequestRateLimiter (STRICT for login/register, NORMAL for refresh/products)"
```

---

## Task 12: Update 5 Kafka consumer classes to use wrapper

**Files:**
- Modify: `BE/inventory-service/src/main/java/com/ecom/inventory/messaging/ReservationEventConsumer.java`
- Modify: `BE/order-service/src/main/java/com/ecom/order/messaging/InventoryEventConsumer.java`
- Modify: `BE/order-service/src/main/java/com/ecom/order/messaging/PaymentEventConsumer.java`
- Modify: `BE/notification-service/src/main/java/com/ecom/notification/messaging/OrderEventConsumer.java`
- Modify: `BE/notification-service/src/main/java/com/ecom/notification/messaging/PaymentEventConsumer.java`

- [ ] **Step 1: Read one consumer to understand pattern**

Mở `BE/inventory-service/src/main/java/com/ecom/inventory/messaging/ReservationEventConsumer.java`. Tìm method `@KafkaListener` và gọi service. Theo pattern chung, method sẽ có dạng:

```java
@KafkaListener(topics = "...", groupId = "...")
public void handleEvent(SomeEvent event) {
    inventoryService.reserve(...);
}
```

- [ ] **Step 2: Modify ReservationEventConsumer**

Inject `KafkaListenerResilienceWrapper` qua constructor (cần sửa constructor). Sau đó wrap lời gọi service:

```java
@KafkaListener(topics = "inventory.reservation-requested", groupId = "inventory-service")
public void handleReservationRequested(ReservationRequestEvent event) {
    wrapper.execute(
        () -> inventoryService.reserve(event),
        ex -> log.warn("Timeout/breaker open for inventory.reservation-requested: {}", ex.getMessage())
    );
}
```

Lưu ý: Nếu listener hiện throw exception thì Spring Kafka sẽ retry. Khi wrap bằng wrapper, exception bên trong được nuốt (fallback log). Cần kiểm tra behavior: nếu wrapper ném `AmqpRejectAndDontRequeueException` (như code Task 6 đã viết) thì Spring Kafka sẽ retry. Đây là behavior mong muốn.

- [ ] **Step 3: Repeat for 4 remaining consumers**

Với mỗi file trong:
- `BE/order-service/src/main/java/com/ecom/order/messaging/InventoryEventConsumer.java`
- `BE/order-service/src/main/java/com/ecom/order/messaging/PaymentEventConsumer.java`
- `BE/notification-service/src/main/java/com/ecom/notification/messaging/OrderEventConsumer.java`
- `BE/notification-service/src/main/java/com/ecom/notification/messaging/PaymentEventConsumer.java`

Sửa tương tự: inject `KafkaListenerResilienceWrapper`, wrap từng `@KafkaListener` method body.

- [ ] **Step 4: Verify all 5 services compile**

Run: `cd D:/project/Ecom/BE && mvn -pl inventory-service,order-service,notification-service -am compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Verify smoke tests pass**

Run: `cd D:/project/Ecom/BE && mvn -pl inventory-service test -Dtest=InventoryServiceApplicationTest -q && mvn -pl order-service test -Dtest=OrderServiceApplicationTest -q && mvn -pl notification-service test -Dtest=NotificationServiceApplicationTest -q`
Expected: 3 lần `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd D:/project/Ecom && git add BE/inventory-service/src/main/java/com/ecom/inventory/messaging/ReservationEventConsumer.java BE/order-service/src/main/java/com/ecom/order/messaging/InventoryEventConsumer.java BE/order-service/src/main/java/com/ecom/order/messaging/PaymentEventConsumer.java BE/notification-service/src/main/java/com/ecom/notification/messaging/OrderEventConsumer.java BE/notification-service/src/main/java/com/ecom/notification/messaging/PaymentEventConsumer.java && git commit -m "feat(messaging): wrap 5 Kafka @KafkaListener handlers with KafkaListenerResilienceWrapper (5s timeout)"
```

---

## Task 13: Add Prometheus scrape jobs for 9 services (fix tech-debt #6)

**Files:**
- Modify: `BE/infra/prometheus/prometheus.yml:1-17`

- [ ] **Step 1: Read current prometheus.yml**

Đọc file `BE/infra/prometheus/prometheus.yml` để xem cấu trúc hiện tại. Thường sẽ là 1 `scrape_configs:` với 3 jobs (api-gateway, discovery-server, config-server).

- [ ] **Step 2: Add 9 jobs (gateway + 8 business services)**

Thêm 9 jobs MỚI vào block `scrape_configs:`. Mỗi service cần expose `actuator/prometheus` qua docker network. Theo `BE/docker-compose.yml`, các service chưa có trong prometheus, nhưng đều đã có `management.endpoints.web.exposure.include: health,info,prometheus,metrics` (qua `config-repo/application.yml`).

Mỗi job cấu hình:

```yaml
  - job_name: 'auth-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
  - job_name: 'user-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8082']
  - job_name: 'product-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8083']
  - job_name: 'cart-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8084']
  - job_name: 'inventory-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8085']
  - job_name: 'order-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8086']
  - job_name: 'payment-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8087']
  - job_name: 'notification-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8088']
```

Lưu ý: 3 jobs hiện tại (api-gateway, discovery-server, config-server) ĐÃ dùng `host.docker.internal` pattern → giữ nguyên. Chỉ thêm 8 jobs mới (auth/user/product/cart/inventory/order/payment/notification).

- [ ] **Step 3: Verify YAML syntax**

Run: `cd D:/project/Ecom/BE && python -c "import yaml; yaml.safe_load(open('infra/prometheus/prometheus.yml'))" && echo OK`
Expected: `OK`

- [ ] **Step 4: Commit**

```bash
cd D:/project/Ecom && git add BE/infra/prometheus/prometheus.yml && git commit -m "fix(observability): add Prometheus scrape jobs for 8 business services

Fixes tech-debt #6: Prometheus only scraped 3/11 services. Now all 11 services are scraped."
```

---

## Task 14: Run full test suite + manual smoke verification

**Files:**
- None (verification only)

- [ ] **Step 1: Run mvn test for all modules**

Run: `cd D:/project/Ecom/BE && mvn test -q`
Expected: `BUILD SUCCESS` (tất cả test pass, bao gồm test mới ở Task 3, 4, 6, 7, 8, 9, 10)

- [ ] **Step 2: Start infrastructure**

Run: `cd D:/project/Ecom/BE && docker compose up -d`
Expected: All containers healthy sau ~30s. Verify: `docker compose ps` shows all services `Up`.

- [ ] **Step 3: Start discovery, config, gateway, auth, cart**

Run ở 5 terminals riêng (hoặc dùng `mvn spring-boot:run` parallel trong script):
```
cd D:/project/Ecom/BE && mvn spring-boot:run -pl discovery-server
cd D:/project/Ecom/BE && mvn spring-boot:run -pl config-server
cd D:/project/Ecom/BE && mvn spring-boot:run -pl api-gateway
cd D:/project/Ecom/BE && mvn spring-boot:run -pl auth-service
cd D:/project/Ecom/BE && mvn spring-boot:run -pl cart-service
```

Đợi tất cả service start (~60s). Verify: `curl http://localhost:8761` returns Eureka UI.

- [ ] **Step 4: Smoke test 1 — Feign fail-fast returns 503**

Register 1 user trước:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"Password123!","displayName":"Smoke"}'
```

Lưu accessToken. Sau đó stop `product-service` (chỉ `cart-service` chạy):
```bash
# Stop product-service (Ctrl+C trong terminal của nó, hoặc kill PID)
```

Gọi addItem với productId bất kỳ (product service đã chết):
```bash
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: smoke-001" \
  -d '{"productId":"00000000-0000-0000-0000-000000000000","quantity":1}'
```

Expected: HTTP 503 với body `{"code":"SERVICE_UNAVAILABLE",...,"correlationId":"smoke-001"}`. Log ở `cart-service` terminal có dòng `WARN [...,smoke-001] Downstream unavailable: product-service`.

- [ ] **Step 5: Smoke test 2 — Rate limiter returns 429**

Restart `product-service`. Gọi 6 lần login liên tiếp cùng email:
```bash
for i in 1 2 3 4 5 6; do
  curl -i -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"smoke@test.com","password":"Password123!"}'
done
```

Expected: Request thứ 1-5 trả 401 (vì password sai) hoặc 200. Request thứ 6 trả HTTP 429 với body `{"code":"RATE_LIMITED",...}` và header `Retry-After: 60`.

- [ ] **Step 6: Smoke test 3 — Prometheus metric exposed**

Truy cập (giả sử cart-service đang chạy):
```bash
curl http://localhost:8084/actuator/prometheus | grep resilience4j
```

Expected: Có ít nhất 1 dòng `resilience4j_circuitbreaker_*` (vd: `resilience4j_circuitbreaker_state{name="productService",state="closed"} 1.0`).

- [ ] **Step 7: Cleanup — stop all services**

```bash
cd D:/project/Ecom/BE && docker compose down
# Ctrl+C các mvn spring-boot:run terminals
```

- [ ] **Step 8: Final commit (no code change, but ensure clean tree)**

```bash
cd D:/project/Ecom && git status
```
Expected: Working tree clean (no uncommitted changes).

---

## Definition of Done Verification Checklist

Đối chiếu với spec section 12:

- [ ] Tất cả file mục 4.4 (spec) đã tạo: `ServiceUnavailableException`, `KafkaListenerResilienceWrapper`, `RateLimitConfig`
- [ ] Tất cả file mục 4.5 (spec) đã sửa: 8 Application class, 2 service method, 2 YAML, GlobalExceptionHandler, prometheus.yml
- [ ] `BE/config-repo/application.yml` có section `resilience4j:` đầy đủ ✓ (Task 2)
- [ ] `BE/api-gateway/.../application.yml` có 3 routes mới với `RequestRateLimiter` ✓ (Task 11)
- [ ] 8 Application class có `@ComponentScan` bao gồm `com.ecom.common` ✓ (Task 5)
- [ ] `BE/infra/prometheus/prometheus.yml` có đủ 11 jobs ✓ (Task 13)
- [ ] Tất cả test mục 7 (spec) pass: ~17 test mới + các test cũ không broken ✓ (Task 14)
- [ ] `mvn clean install` pass ✓ (Task 14 Step 1)
- [ ] Manual smoke 3 case pass ✓ (Task 14 Steps 4, 5, 6)
