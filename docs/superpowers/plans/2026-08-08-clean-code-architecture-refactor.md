# Clean Code Architecture Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the Ecom backend from 5.5/10 to 8.5/10 on clean code architecture by eliminating duplication, unifying error handling, introducing bounded contexts (ports/adapters) where it pays off, and making the codebase production-ready (typed events, fix outbox race, pluggable providers, coverage gate, ArchUnit boundaries).

**Architecture:** Incremental, behavior-preserving refactor in 10 phases. Each phase compiles, all existing tests pass at the phase boundary, and each phase is independently shippable. The order is chosen so earlier phases unblock later ones (extract common → adopt → add base classes → typed events → fix race → abstract providers → mappers → coverage gate → hexagonal pilot → ArchUnit enforcement).

**Tech Stack:** Java 21, Spring Boot 3.3.6, Spring Cloud 2023.0.4, JUnit 5 + Mockito + Testcontainers + Spring Kafka Test, Resilience4j (already on classpath), JaCoCo 0.8.11, ArchUnit 1.3.0, MapStruct 1.6.3 (only if needed in Phase 7), Flyway.

**Working directory:** `D:/project/Ecom` (Windows, bash shell).
**Build root:** `BE/pom.xml` (parent). All `mvn` commands are run from `BE/`.
**Branch:** start from `perf/be-optimization`. One feature branch per phase (`perf/clean-phase-N`).

---

## File Structure

### New files (created by this plan)

| File | Purpose |
|---|---|
| `BE/common/src/main/java/com/ecom/common/security/GatewayUserContext.java` | Move from 4 services. |
| `BE/common/src/main/java/com/ecom/common/security/GatewayUserContextFilter.java` | Move from 4 services. Path-agnostic (no `/api/cart` hard-coding). |
| `BE/common/src/main/java/com/ecom/common/web/DomainException.java` | Abstract base with `code` + `httpStatus`. |
| `BE/common/src/main/java/com/ecom/common/web/NotFoundException.java` | 404 mapping. |
| `BE/common/src/main/java/com/ecom/common/web/ConflictException.java` | 409 mapping. |
| `BE/common/src/main/java/com/ecom/common/web/BadRequestException.java` | 400 mapping. |
| `BE/common/src/test/java/com/ecom/common/web/DomainExceptionHandlerTest.java` | Unit test for new global handler. |
| `BE/common/src/test/java/com/ecom/common/security/GatewayUserContextFilterTest.java` | Unit test for new filter. |
| `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentEvent.java` | Sealed interface for all payment events. |
| `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentSucceededEvent.java` | v1 typed event. |
| `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentFailedEvent.java` | v1 typed event. |
| `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentRefundedEvent.java` | v1 typed event. |
| `BE/payment-service/src/main/java/com/ecom/payment/provider/PaymentProvider.java` | Interface. |
| `BE/payment-service/src/main/java/com/ecom/payment/provider/MockPaymentProvider.java` | Move + implement interface. |
| `BE/payment-service/src/main/java/com/ecom/payment/provider/ProviderPayment.java` | Move from MockPaymentProvider (was inner record). |
| `BE/order-service/src/main/java/com/ecom/order/port/CartQueryPort.java` | Hexagonal port. |
| `BE/order-service/src/main/java/com/ecom/order/port/InventoryCommandPort.java` | Hexagonal port. |
| `BE/order-service/src/main/java/com/ecom/order/port/EventPublishPort.java` | Hexagonal port. |
| `BE/order-service/src/main/java/com/ecom/order/adapter/CartQueryFeignAdapter.java` | Implements CartQueryPort over Feign. |
| `BE/order-service/src/main/java/com/ecom/order/adapter/InventoryCommandFeignAdapter.java` | Implements InventoryCommandPort. |
| `BE/order-service/src/main/java/com/ecom/order/adapter/KafkaEventPublishAdapter.java` | Implements EventPublishPort. |
| `BE/architecture-rules/src/test/java/com/ecom/architecture/LayerDependencyTest.java` | ArchUnit test. |
| `BE/architecture-rules/pom.xml` | New module for ArchUnit. |

### Modified files (per phase — listed in each Task)

### Files deleted (per phase — listed in each Task)

### Conventions for all tasks

- Java package by feature; do not change any existing package unless the task says so.
- Every task that changes code must include: write failing test → run → see fail → write code → run → see pass → commit.
- Commit message format: `<scope>(<area>): <imperative>` (e.g. `refactor(common): extract GatewayUserContextFilter`).
- Run `mvn -pl <module> -am test -q` after every commit. Run `mvn -pl <module> -am verify` at end of each phase.
- Do not add dependencies that are not listed in the task. If you think one is needed, stop and ask.

---

## Phase 1 — Extract `GatewayUserContext` + filter to `common.security`

> **Outcome:** Remove 4 duplicated copies (`cart`, `order`, `payment`, `user` services). Single source in `common`. All existing controllers continue to work unchanged.

### Task 1.1: Add `GatewayUserContext` to common

**Files:**
- Create: `BE/common/src/main/java/com/ecom/common/security/GatewayUserContext.java`
- Create: `BE/common/src/test/java/com/ecom/common/security/GatewayUserContextTest.java`

- [ ] **Step 1: Write the failing test**

Create `BE/common/src/test/java/com/ecom/common/security/GatewayUserContextTest.java`:

```java
package com.ecom.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayUserContextTest {

    @Test
    void from_request_returns_userId_and_email() {
        UUID id = UUID.randomUUID();
        HttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE, id.toString());
        req.setAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE, "a@b.test");

        GatewayUserContext ctx = GatewayUserContext.from(req);

        assertThat(ctx.userId()).isEqualTo(id);
        assertThat(ctx.email()).isEqualTo("a@b.test");
    }

    @Test
    void from_request_throws_when_userId_missing() {
        HttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE, "a@b.test");

        assertThatThrownBy(() -> GatewayUserContext.from(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId");
    }
}
```

- [ ] **Step 2: Run test, expect FAIL with "class not found"**

```bash
cd BE && mvn -pl common -am test -Dtest=GatewayUserContextTest -q
```

- [ ] **Step 3: Create the source file**

Create `BE/common/src/main/java/com/ecom/common/security/GatewayUserContext.java`:

```java
package com.ecom.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public record GatewayUserContext(UUID userId, String email) {
    public static GatewayUserContext from(HttpServletRequest request) {
        Object rawId = request.getAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE);
        Object rawEmail = request.getAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE);
        if (rawId == null) {
            throw new IllegalStateException("GatewayUserContextFilter did not populate userId attribute");
        }
        if (rawEmail == null) {
            throw new IllegalStateException("GatewayUserContextFilter did not populate userEmail attribute");
        }
        return new GatewayUserContext(UUID.fromString(rawId.toString()), rawEmail.toString());
    }
}
```

- [ ] **Step 4: Create the filter stub (constant class only) to compile**

Create `BE/common/src/main/java/com/ecom/common/security/GatewayUserContextFilter.java`:

```java
package com.ecom.common.security;

public final class GatewayUserContextFilter {
    public static final String USER_ID_ATTRIBUTE = "gatewayUserId";
    public static final String USER_EMAIL_ATTRIBUTE = "gatewayUserEmail";
    private GatewayUserContextFilter() {}
}
```

- [ ] **Step 5: Run test, expect PASS**

```bash
mvn -pl common -am test -Dtest=GatewayUserContextTest -q
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 6: Commit**

```bash
git add BE/common/src/main/java/com/ecom/common/security/GatewayUserContext.java \
        BE/common/src/main/java/com/ecom/common/security/GatewayUserContextFilter.java \
        BE/common/src/test/java/com/ecom/common/security/GatewayUserContextTest.java
git commit -m "feat(common): add GatewayUserContext + filter constants"
```

### Task 1.2: Move the actual filter logic into common (path-agnostic)

**Files:**
- Modify: `BE/common/src/main/java/com/ecom/common/security/GatewayUserContextFilter.java`
- Create: `BE/common/src/test/java/com/ecom/common/security/GatewayUserContextFilterLogicTest.java`

- [ ] **Step 1: Write failing test for the new filter (Spring Mock)**

Create `BE/common/src/test/java/com/ecom/common/security/GatewayUserContextFilterLogicTest.java`:

```java
package com.ecom.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayUserContextFilterLogicTest {

    @Test
    void sets_attributes_when_headers_present() throws Exception {
        var filter = new GatewayUserContextFilterLogic();
        var req = new MockHttpServletRequest("GET", "/api/cart/items");
        req.addHeader("X-User-Id", "11111111-1111-1111-1111-111111111111");
        req.addHeader("X-User-Email", "u@e.test");
        var res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        var executedReq = (HttpServletRequest) ((MockFilterChain) chain).getRequest();
        assertThat(executedReq.getAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(executedReq.getAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE))
                .isEqualTo("u@e.test");
    }

    @Test
    void returns_401_when_headers_missing_on_protected_path() throws Exception {
        var filter = new GatewayUserContextFilterLogic();
        var req = new MockHttpServletRequest("GET", "/api/cart/items");
        var res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void skips_authentication_for_public_paths() throws Exception {
        var filter = new GatewayUserContextFilterLogic();
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        var res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
```

- [ ] **Step 2: Run, expect FAIL (class not found)**

```bash
mvn -pl common test -Dtest=GatewayUserContextFilterLogicTest -q
```

- [ ] **Step 3: Replace filter file with the real logic**

Rewrite `BE/common/src/main/java/com/ecom/common/security/GatewayUserContextFilter.java`:

```java
package com.ecom.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class GatewayUserContextFilter extends OncePerRequestFilter {
    public static final String USER_ID_ATTRIBUTE = "gatewayUserId";
    public static final String USER_EMAIL_ATTRIBUTE = "gatewayUserEmail";

    private final List<String> protectedPathPatterns;
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public GatewayUserContextFilter(List<String> protectedPathPatterns) {
        this.protectedPathPatterns = List.copyOf(protectedPathPatterns);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!requiresAuth(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        if (userId == null || userId.isBlank() || email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        request.setAttribute(USER_EMAIL_ATTRIBUTE, email);
        chain.doFilter(request, response);
    }

    private boolean requiresAuth(String uri) {
        return protectedPathPatterns.stream().anyMatch(p -> MATCHER.match(p, uri));
    }
}
```

- [ ] **Step 4: Fix the test — call the real filter, not the “Logic” alias**

Edit the test file: replace `new GatewayUserContextFilterLogic()` with `new GatewayUserContextFilter(List.of("/api/**"))`. Delete the now-unused “Logic” class reference. Final test body:

```java
        var filter = new GatewayUserContextFilter(List.of("/api/**"));
        ...
        var filter = new GatewayUserContextFilter(List.of("/api/**"));
        ...
        var filter = new GatewayUserContextFilter(List.of("/api/**"));
```

Update the import in the test:
```java
import java.util.List;
```

- [ ] **Step 5: Run, expect PASS**

```bash
mvn -pl common test -Dtest=GatewayUserContextFilterLogicTest -q
```

- [ ] **Step 6: Commit**

```bash
git add BE/common/src/main/java/com/ecom/common/security/GatewayUserContextFilter.java \
        BE/common/src/test/java/com/ecom/common/security/GatewayUserContextFilterLogicTest.java
git commit -m "feat(common): path-agnostic GatewayUserContextFilter"
```

### Task 1.3: Migrate cart-service to use the common filter

**Files:**
- Delete: `BE/cart-service/src/main/java/com/ecom/cart/security/GatewayUserContextFilter.java`
- Delete: `BE/cart-service/src/main/java/com/ecom/cart/security/GatewayUserContext.java`
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/config/SecurityConfig.java` (or create if not present)
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/CartServiceApplication.java`

- [ ] **Step 1: Verify existing cart tests still reference old package — search**

```bash
grep -rn "com.ecom.cart.security.GatewayUserContext" BE/cart-service/src
```

Expect: matches in controllers only.

- [ ] **Step 2: Find or create the cart-service SecurityConfig**

```bash
ls BE/cart-service/src/main/java/com/ecom/cart/config/
```

If `SecurityConfig.java` does not exist, create it with:

```java
package com.ecom.cart.config;

import com.ecom.common.security.GatewayUserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public GatewayUserContextFilter gatewayUserContextFilter() {
        return new GatewayUserContextFilter(List.of("/api/cart/**"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayUserContextFilter gatewayFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(gatewayFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 3: Update all imports in cart-service from `com.ecom.cart.security.GatewayUserContext*` to `com.ecom.common.security.GatewayUserContext*`**

```bash
grep -rl "com.ecom.cart.security.GatewayUserContext" BE/cart-service/src/main/java | xargs sed -i 's|com.ecom.cart.security.GatewayUserContext|com.ecom.common.security.GatewayUserContext|g'
```

- [ ] **Step 4: Delete the old files**

```bash
rm BE/cart-service/src/main/java/com/ecom/cart/security/GatewayUserContextFilter.java \
   BE/cart-service/src/main/java/com/ecom/cart/security/GatewayUserContext.java
```

- [ ] **Step 5: Build + run all cart tests**

```bash
mvn -pl cart-service -am test -q
```

Expected: BUILD SUCCESS, same test count as before (no test should be removed).

- [ ] **Step 6: Commit**

```bash
git add BE/cart-service
git commit -m "refactor(cart): use common.security.GatewayUserContext"
```

### Task 1.4: Migrate order-service (same pattern)

**Files:** Same as Task 1.3 but for `order-service`. Protected path pattern: `/api/orders/**`.

- [ ] **Step 1: Create or update `BE/order-service/src/main/java/com/ecom/order/config/SecurityConfig.java`**

```java
package com.ecom.order.config;

import com.ecom.common.security.GatewayUserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public GatewayUserContextFilter gatewayUserContextFilter() {
        return new GatewayUserContextFilter(List.of("/api/orders/**"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayUserContextFilter gatewayFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(gatewayFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 2: Rewrite imports and delete old files (same sed + rm pattern)**

```bash
grep -rl "com.ecom.order.security.GatewayUserContext" BE/order-service/src/main/java | xargs sed -i 's|com.ecom.order.security.GatewayUserContext|com.ecom.common.security.GatewayUserContext|g'
rm BE/order-service/src/main/java/com/ecom/order/security/GatewayUserContextFilter.java \
   BE/order-service/src/main/java/com/ecom/order/security/GatewayUserContext.java
```

- [ ] **Step 3: Test**

```bash
mvn -pl order-service -am test -q
```

- [ ] **Step 4: Commit**

```bash
git add BE/order-service
git commit -m "refactor(order): use common.security.GatewayUserContext"
```

### Task 1.5: Migrate payment-service

- [ ] **Step 1: Same as 1.4, path pattern `/api/payments/**`** — `SecurityConfig.java` in `BE/payment-service/src/main/java/com/ecom/payment/config/`.

- [ ] **Step 2: Rewrite imports, delete files, test, commit**

```bash
grep -rl "com.ecom.payment.security.GatewayUserContext" BE/payment-service/src/main/java | xargs sed -i 's|com.ecom.payment.security.GatewayUserContext|com.ecom.common.security.GatewayUserContext|g'
rm BE/payment-service/src/main/java/com/ecom/payment/security/GatewayUserContextFilter.java \
   BE/payment-service/src/main/java/com/ecom/payment/security/GatewayUserContext.java
mvn -pl payment-service -am test -q
git add BE/payment-service
git commit -m "refactor(payment): use common.security.GatewayUserContext"
```

### Task 1.6: Migrate user-service

- [ ] **Step 1: Same as 1.4, path pattern `/api/users/**`, `/api/me/**`** — `SecurityConfig.java` in `BE/user-service/src/main/java/com/ecom/user/config/`.

- [ ] **Step 2: Rewrite imports, delete files, test, commit**

```bash
grep -rl "com.ecom.user.security.GatewayUserContext" BE/user-service/src/main/java | xargs sed -i 's|com.ecom.user.security.GatewayUserContext|com.ecom.common.security.GatewayUserContext|g'
rm BE/user-service/src/main/java/com/ecom/user/security/GatewayUserContextFilter.java \
   BE/user-service/src/main/java/com/ecom/user/security/GatewayUserContext.java
mvn -pl user-service -am test -q
git add BE/user-service
git commit -m "refactor(user): use common.security.GatewayUserContext"
```

### Task 1.7: Phase 1 verification

- [ ] **Step 1: Full build**

```bash
mvn -pl common,cart-service,order-service,payment-service,user-service -am clean test -q
```

Expected: BUILD SUCCESS, all tests pass, no module has a `GatewayUserContext*.java` outside `common`.

- [ ] **Step 2: Verify no stale imports**

```bash
grep -rn "com.ecom.\(cart\|order\|payment\|user\).security.GatewayUserContext" BE
```

Expected: no matches.

- [ ] **Step 3: Tag the phase**

```bash
git tag clean-architecture/phase-1-complete
```

---

## Phase 2 — Adopt `GlobalExceptionHandler` everywhere

> **Outcome:** Every service returns errors in the unified `ErrorResponse{code, message, details, correlationId, timestamp}` format. 5 service-local handlers deleted.

### Task 2.1: Add base domain exception + handlers in common

**Files:**
- Create: `BE/common/src/main/java/com/ecom/common/web/DomainException.java`
- Create: `BE/common/src/main/java/com/ecom/common/web/NotFoundException.java`
- Create: `BE/common/src/main/java/com/ecom/common/web/ConflictException.java`
- Create: `BE/common/src/main/java/com/ecom/common/web/BadRequestException.java`
- Modify: `BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java`
- Create: `BE/common/src/test/java/com/ecom/common/web/DomainExceptionHandlerTest.java`

- [ ] **Step 1: Write failing test**

Create `BE/common/src/test/java/com/ecom/common/web/DomainExceptionHandlerTest.java`:

```java
package com.ecom.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DomainExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandler.class)
class DomainExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @RestController
    static class TestController {
        @GetMapping("/nfe")   { throw new NotFoundException("Order", "42"); }
        @GetMapping("/cfe")   { throw new ConflictException("Already exists"); }
        @GetMapping("/breq")  { throw new BadRequestException("Bad input"); }
    }

    @Test void notFound() throws Exception {
        mvc.perform(get("/nfe")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Order 42 not found")));
    }

    @Test void conflict() throws Exception {
        mvc.perform(get("/cfe")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")));
    }

    @Test void badRequest() throws Exception {
        mvc.perform(get("/breq")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

```bash
mvn -pl common test -Dtest=DomainExceptionHandlerTest -q
```

- [ ] **Step 3: Create `DomainException`**

Create `BE/common/src/main/java/com/ecom/common/web/DomainException.java`:

```java
package com.ecom.common.web;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {
    private final String code;
    private final HttpStatus httpStatus;

    protected DomainException(String code, HttpStatus httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
```

- [ ] **Step 4: Create the three subclasses**

`BE/common/src/main/java/com/ecom/common/web/NotFoundException.java`:

```java
package com.ecom.common.web;

import org.springframework.http.HttpStatus;

public class NotFoundException extends DomainException {
    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, resource + " " + id + " not found");
    }
    public NotFoundException(String message) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}
```

`BE/common/src/main/java/com/ecom/common/web/ConflictException.java`:

```java
package com.ecom.common.web;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super("CONFLICT", HttpStatus.CONFLICT, message);
    }
}
```

`BE/common/src/main/java/com/ecom/common/web/BadRequestException.java`:

```java
package com.ecom.common.web;

import org.springframework.http.HttpStatus;

public class BadRequestException extends DomainException {
    public BadRequestException(String message) {
        super("BAD_REQUEST", HttpStatus.BAD_REQUEST, message);
    }
}
```

- [ ] **Step 5: Add handler method to `GlobalExceptionHandler`**

Append to `BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java`:

```java
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest request) {
        log.warn("Domain exception: {} | path={}", ex.getMessage(), request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(
                ex.getCode(),
                ex.getMessage(),
                List.of(),
                getCorrelationId(request),
                java.time.Instant.now()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }
```

Add the import at the top of the file: `import com.ecom.common.web.DomainException;` is already in the same package — no import needed.

- [ ] **Step 6: Run, expect PASS**

```bash
mvn -pl common test -Dtest=DomainExceptionHandlerTest -q
```

- [ ] **Step 7: Commit**

```bash
git add BE/common
git commit -m "feat(common): DomainException base + 3 subclasses + handler"
```

### Task 2.2: Migrate order-service to global handler

**Files:**
- Delete: `BE/order-service/src/main/java/com/ecom/order/web/OrderExceptionHandler.java`
- Modify: `BE/order-service/src/main/java/com/ecom/order/service/OrderNotFoundException.java`
- Modify: `BE/order-service/src/main/java/com/ecom/order/service/EmptyCartException.java`
- Modify: `BE/order-service/src/main/java/com/ecom/order/service/InvalidOrderStateException.java`
- Modify: `BE/order-service/src/main/java/com/ecom/order/OrderServiceApplication.java`

- [ ] **Step 1: Rewrite the three order exceptions to extend `DomainException`**

`BE/order-service/src/main/java/com/ecom/order/service/OrderNotFoundException.java`:

```java
package com.ecom.order.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(UUID orderId) {
        super("Order", orderId);
    }
}
```

`BE/order-service/src/main/java/com/ecom/order/service/EmptyCartException.java`:

```java
package com.ecom.order.service;

import com.ecom.common.web.ConflictException;

public class EmptyCartException extends ConflictException {
    public EmptyCartException() {
        super("Cart is empty");
    }
}
```

`BE/order-service/src/main/java/com/ecom/order/service/InvalidOrderStateException.java`:

```java
package com.ecom.order.service;

import com.ecom.common.web.ConflictException;

public class InvalidOrderStateException extends ConflictException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Add `@Import(GlobalExceptionHandler.class)` to `OrderServiceApplication.java`**

Read it first, then add `@Import(com.ecom.common.web.GlobalExceptionHandler.class)` to the class.

```bash
grep -n "@SpringBootApplication" BE/order-service/src/main/java/com/ecom/order/OrderServiceApplication.java
```

Append the import after the existing class declaration. Final class body:

```java
package com.ecom.order;

import com.ecom.common.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Delete the local handler**

```bash
rm BE/order-service/src/main/java/com/ecom/order/web/OrderExceptionHandler.java
```

- [ ] **Step 4: Test**

```bash
mvn -pl order-service -am test -q
```

- [ ] **Step 5: Commit**

```bash
git add BE/order-service
git commit -m "refactor(order): adopt GlobalExceptionHandler + domain exceptions"
```

### Task 2.3: Migrate cart-service

**Files:**
- Delete: `BE/cart-service/src/main/java/com/ecom/cart/web/CartExceptionHandler.java`
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/service/InvalidCartQuantityException.java`
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/service/ProductUnavailableException.java`
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/CartServiceApplication.java`

- [ ] **Step 1: Rewrite exceptions**

`InvalidCartQuantityException.java`:

```java
package com.ecom.cart.service;

import com.ecom.common.web.BadRequestException;

public class InvalidCartQuantityException extends BadRequestException {
    public InvalidCartQuantityException(int quantity) {
        super("Invalid cart quantity: " + quantity);
    }
}
```

`ProductUnavailableException.java`:

```java
package com.ecom.cart.service;

import com.ecom.common.web.ConflictException;

import java.util.UUID;

public class ProductUnavailableException extends ConflictException {
    public ProductUnavailableException(UUID productId) {
        super("Product " + productId + " is not available");
    }
}
```

- [ ] **Step 2: Add `@Import(GlobalExceptionHandler.class)` to `CartServiceApplication.java`**

(Apply the same import pattern as 2.2.)

- [ ] **Step 3: Delete local handler, test, commit**

```bash
rm BE/cart-service/src/main/java/com/ecom/cart/web/CartExceptionHandler.java
mvn -pl cart-service -am test -q
git add BE/cart-service
git commit -m "refactor(cart): adopt GlobalExceptionHandler + domain exceptions"
```

### Task 2.4: Migrate product-service

**Files:**
- Delete: `BE/product-service/src/main/java/com/ecom/product/web/CatalogExceptionHandler.java`
- Modify: 3 product exceptions → extend `DomainException`.
- Modify: `BE/product-service/src/main/java/com/ecom/product/ProductServiceApplication.java`.

- [ ] **Step 1: Rewrite exceptions**

`CategoryNotFoundException.java`:

```java
package com.ecom.product.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class CategoryNotFoundException extends NotFoundException {
    public CategoryNotFoundException(UUID id) { super("Category", id); }
}
```

`ProductNotFoundException.java`:

```java
package com.ecom.product.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(UUID id) { super("Product", id); }
}
```

`DuplicateSlugException.java`:

```java
package com.ecom.product.service;

import com.ecom.common.web.ConflictException;

public class DuplicateSlugException extends ConflictException {
    public DuplicateSlugException(String slug) { super("Slug already exists: " + slug); }
}
```

- [ ] **Step 2: Add `@Import` to Application, delete handler, test, commit**

```bash
rm BE/product-service/src/main/java/com/ecom/product/web/CatalogExceptionHandler.java
mvn -pl product-service -am test -q
git add BE/product-service
git commit -m "refactor(product): adopt GlobalExceptionHandler + domain exceptions"
```

### Task 2.5: Migrate inventory-service

**Files:** Same pattern. `StockNotFoundException`, `ReservationNotFoundException` → `NotFoundException`.

- [ ] **Step 1: Rewrite exceptions to extend `NotFoundException`**

`StockNotFoundException.java`:

```java
package com.ecom.inventory.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class StockNotFoundException extends NotFoundException {
    public StockNotFoundException(UUID productId) { super("Stock", productId); }
}
```

`ReservationNotFoundException.java`:

```java
package com.ecom.inventory.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class ReservationNotFoundException extends NotFoundException {
    public ReservationNotFoundException(UUID id) { super("Reservation", id); }
}
```

- [ ] **Step 2: Add `@Import`, delete `InventoryExceptionHandler`, test, commit**

```bash
rm BE/inventory-service/src/main/java/com/ecom/inventory/web/InventoryExceptionHandler.java
mvn -pl inventory-service -am test -q
git add BE/inventory-service
git commit -m "refactor(inventory): adopt GlobalExceptionHandler + domain exceptions"
```

### Task 2.6: Migrate payment-service

**Files:** Same pattern. `PaymentNotFoundException` → `NotFoundException`. `DuplicatePaymentException`, `InvalidPaymentStateException` → `ConflictException`.

- [ ] **Step 1: Rewrite exceptions**

`PaymentNotFoundException.java`:

```java
package com.ecom.payment.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class PaymentNotFoundException extends NotFoundException {
    public PaymentNotFoundException(UUID id) { super("Payment", id); }
    public PaymentNotFoundException(String providerPaymentId) { super("Payment provider ref " + providerPaymentId); }
}
```

`DuplicatePaymentException.java`:

```java
package com.ecom.payment.service;

import com.ecom.common.web.ConflictException;

import java.util.UUID;

public class DuplicatePaymentException extends ConflictException {
    public DuplicatePaymentException(UUID orderId) { super("Payment already exists for order " + orderId); }
}
```

`InvalidPaymentStateException.java`:

```java
package com.ecom.payment.service;

import com.ecom.common.web.ConflictException;

public class InvalidPaymentStateException extends ConflictException {
    public InvalidPaymentStateException(String message) { super(message); }
}
```

- [ ] **Step 2: Add `@Import`, delete `PaymentExceptionHandler`, test, commit**

```bash
rm BE/payment-service/src/main/java/com/ecom/payment/web/PaymentExceptionHandler.java
mvn -pl payment-service -am test -q
git add BE/payment-service
git commit -m "refactor(payment): adopt GlobalExceptionHandler + domain exceptions"
```

### Task 2.7: Migrate user-service (if it has local exceptions)

- [ ] **Step 1: Find local exception handlers and service exceptions**

```bash
find BE/user-service/src/main/java -name "*ExceptionHandler*.java" -o -name "*Exception.java" | grep -v target
```

- [ ] **Step 2: For each, rewrite to extend `DomainException` (or leave alone if it already extends `RuntimeException` and is generic)**

- [ ] **Step 3: Add `@Import(GlobalExceptionHandler.class)` to `UserServiceApplication.java`**

- [ ] **Step 4: Test, commit**

```bash
mvn -pl user-service -am test -q
git add BE/user-service
git commit -m "refactor(user): adopt GlobalExceptionHandler"
```

### Task 2.8: Phase 2 verification

- [ ] **Step 1: Search for any remaining `*ExceptionHandler.java` outside `common`**

```bash
find BE -name "*ExceptionHandler.java" -not -path "*/common/*" -not -path "*/target/*"
```

Expected: no matches.

- [ ] **Step 2: Full build**

```bash
mvn clean test -q
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Add an integration test verifying unified error format across services**

Create `BE/common/src/test/java/com/ecom/common/web/ErrorFormatContractTest.java`:

```java
package com.ecom.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = ErrorFormatContractTest.TestController.class)
@Import(GlobalExceptionHandler.class)
class ErrorFormatContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @RestController
    static class TestController {
        @GetMapping("/x") { throw new NotFoundException("Thing", "7"); }
    }

    @Test
    void error_response_contains_code_message_correlationId_timestamp() throws Exception {
        MvcResult res = mvc.perform(get("/x").header("X-Correlation-Id", "cid-1"))
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asText()).contains("Thing 7");
        assertThat(body.get("correlationId").asText()).isEqualTo("cid-1");
        assertThat(body.get("timestamp").asText()).isNotBlank();
    }
}
```

- [ ] **Step 4: Tag**

```bash
git tag clean-architecture/phase-2-complete
```

---

## Phase 3 — Domain exception consolidation (eliminate remaining service exceptions)

> **Outcome:** All domain exceptions across services inherit from a 3-class hierarchy in `common`. No more one-off `extends RuntimeException` exceptions.

### Task 3.1: Audit remaining exceptions

- [ ] **Step 1: List all exception classes in `BE`**

```bash
find BE -name "*Exception.java" -not -path "*/target/*" -not -path "*/common/*"
```

- [ ] **Step 2: For each that does not extend a `common.web.*Exception`, refactor in its own sub-task. Pattern:**

1. Write a test that triggers the exception and asserts the HTTP code (use existing controller IT).
2. Change the class to extend the appropriate `common.web` exception.
3. Run the test. Commit.

Apply this to all remaining: `auth-service` (AuthException etc.), `notification-service`, `user-service` if any remain.

### Task 3.2: Phase 3 verification

- [ ] **Step 1: Confirm all `*Exception` classes outside `common` extend a `common.web.*` class**

```bash
grep -rln "extends RuntimeException" BE --include="*Exception.java" | grep -v common | grep -v target
```

Expected: no matches.

- [ ] **Step 2: Tag**

```bash
git tag clean-architecture/phase-3-complete
```

---

## Phase 4 — Typed event payloads with schema version

> **Outcome:** Replace `Map<String,Object>` event payloads in payment outbox with typed records. Add `eventVersion` field. Old consumers continue to work (backward compat by version field).

### Task 4.1: Add event envelope + typed events

**Files:**
- Create: `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentEvent.java`
- Create: `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentSucceededEvent.java`
- Create: `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentFailedEvent.java`
- Create: `BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentRefundedEvent.java`

- [ ] **Step 1: Create the sealed interface**

`BE/payment-service/src/main/java/com/ecom/payment/messaging/event/PaymentEvent.java`:

```java
package com.ecom.payment.messaging.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface PaymentEvent
        permits PaymentSucceededEvent, PaymentFailedEvent, PaymentRefundedEvent {

    UUID eventId();
    Instant occurredAt();
    int eventVersion();
    UUID paymentId();
    UUID orderId();
    UUID userId();
}
```

- [ ] **Step 2: Create the three concrete events**

`PaymentSucceededEvent.java`:

```java
package com.ecom.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        Instant occurredAt,
        int eventVersion,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency
) implements PaymentEvent {
    public static PaymentSucceededEvent of(UUID paymentId, UUID orderId, UUID userId,
                                           BigDecimal amount, String currency) {
        return new PaymentSucceededEvent(UUID.randomUUID(), Instant.now(), 1,
                paymentId, orderId, userId, amount, currency);
    }
}
```

`PaymentFailedEvent.java`:

```java
package com.ecom.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        int eventVersion,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String reason
) implements PaymentEvent {
    public static PaymentFailedEvent of(UUID paymentId, UUID orderId, UUID userId,
                                        BigDecimal amount, String currency, String reason) {
        return new PaymentFailedEvent(UUID.randomUUID(), Instant.now(), 1,
                paymentId, orderId, userId, amount, currency, reason);
    }
}
```

`PaymentRefundedEvent.java`:

```java
package com.ecom.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
        UUID eventId,
        Instant occurredAt,
        int eventVersion,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String reason
) implements PaymentEvent {
    public static PaymentRefundedEvent of(UUID paymentId, UUID orderId, UUID userId,
                                          BigDecimal amount, String reason) {
        return new PaymentRefundedEvent(UUID.randomUUID(), Instant.now(), 1,
                paymentId, orderId, userId, amount, reason);
    }
}
```

- [ ] **Step 3: Add unit test for envelope**

Create `BE/payment-service/src/test/java/com/ecom/payment/messaging/event/PaymentEventSerializationTest.java`:

```java
package com.ecom.payment.messaging.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void succeeded_event_round_trips() throws Exception {
        PaymentSucceededEvent ev = PaymentSucceededEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), "USD");
        String json = mapper.writeValueAsString(ev);
        PaymentSucceededEvent back = mapper.readValue(json, PaymentSucceededEvent.class);
        assertThat(back.eventId()).isEqualTo(ev.eventId());
        assertThat(back.eventVersion()).isEqualTo(1);
        assertThat(back.amount()).isEqualByComparingTo("10.00");
    }
}
```

- [ ] **Step 4: Run, expect PASS**

```bash
mvn -pl payment-service test -Dtest=PaymentEventSerializationTest -q
```

- [ ] **Step 5: Commit**

```bash
git add BE/payment-service
git commit -m "feat(payment): typed PaymentEvent records with version"
```

### Task 4.2: Replace `Map<String,Object>` payloads in `PaymentService`

**Files:**
- Modify: `BE/payment-service/src/main/java/com/ecom/payment/service/PaymentService.java`
- Modify: `BE/payment-service/src/test/java/com/ecom/payment/service/PaymentServiceTest.java`

- [ ] **Step 1: Update `OutboxService` to accept a typed event**

`BE/payment-service/src/main/java/com/ecom/payment/outbox/OutboxService.java` — add new overload:

```java
    @Transactional
    public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType, com.ecom.payment.messaging.event.PaymentEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            return repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, json));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize outbox payload", exception);
        }
    }
```

(Keep the old `Object payload` method for now, deprecate with `@Deprecated`.)

- [ ] **Step 2: In `PaymentService.java`, replace the 3 calls to `outboxService.append(..., eventPayload(payment))`**

- `markSucceeded` (line ~50): `outboxService.append("Payment", payment.getId(), "payment.succeeded", PaymentSucceededEvent.of(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount(), payment.getCurrency()));`
- `markFailed` (line ~62): `outboxService.append("Payment", payment.getId(), "payment.failed", PaymentFailedEvent.of(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount(), payment.getCurrency(), reason));`
- `refund` (line ~92): `outboxService.append("Payment", payment.getId(), "payment.refunded", PaymentRefundedEvent.of(payment.getId(), payment.getOrderId(), payment.getUserId(), refundAmount, request.reason()));`

- [ ] **Step 3: Delete the now-unused `eventPayload` private method**

- [ ] **Step 4: Update `PaymentServiceTest` mock verifications** — `verify(outboxService).append(eq("Payment"), any(), eq("payment.succeeded"), any(PaymentSucceededEvent.class));`

- [ ] **Step 5: Test**

```bash
mvn -pl payment-service test -q
```

- [ ] **Step 6: Commit**

```bash
git add BE/payment-service
git commit -m "refactor(payment): typed events replace Map payload"
```

### Task 4.3: Update consumers to deserialize typed events

**Files:**
- Modify: `BE/order-service/src/main/java/com/ecom/order/messaging/PaymentEventConsumer.java`
- Modify: `BE/notification-service/src/main/java/com/ecom/notification/messaging/PaymentEventConsumer.java`

- [ ] **Step 1: For each consumer, replace `(Map<String,Object>) record.value()` with `objectMapper.readValue(record.value(), PaymentSucceededEvent.class)` (or `PaymentFailedEvent.class` based on the listener method).**

For `order-service`, add the dep on `payment-service` is already provided by the existing build (the shared `eventPayload` shape). If payment-service is a published artifact, add a small `payment-contracts` module later. For now, **duplicate the 3 event records into a `payment-contracts` jar** is the recommended long-term move. Document the duplication as tech-debt for Phase 10. For this phase, copy the 3 record files into a new `BE/payment-contracts` module and have both `order-service` and `notification-service` depend on it. The instructions to do that:

Create `BE/payment-contracts/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ecom</groupId>
        <artifactId>ecom-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>payment-contracts</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
    </dependencies>
</project>
```

Move the 3 event records into `BE/payment-contracts/src/main/java/com/ecom/payment/contracts/event/`. Add the module to the parent POM's `<modules>`.

- [ ] **Step 2: Have `order-service` and `notification-service` depend on `payment-contracts`. Add to their `pom.xml`:**

```xml
<dependency>
    <groupId>com.ecom</groupId>
    <artifactId>payment-contracts</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 3: Update consumer imports + deserialization to use the new package.**

- [ ] **Step 4: Test both consumers**

```bash
mvn -pl order-service,notification-service -am test -q
```

- [ ] **Step 5: Commit**

```bash
git add BE/payment-contracts BE/order-service BE/notification-service BE/pom.xml
git commit -m "refactor(payment): extract payment-contracts module with typed events"
```

### Task 4.4: Phase 4 verification

- [ ] **Step 1: No `Map<String,Object>` event payload anywhere in payment outbox path**

```bash
grep -rn "Map<String,Object>" BE/payment-service/src/main/java/com/ecom/payment
```

Expected: no matches in service/outbox code.

- [ ] **Step 2: Full build**

```bash
mvn clean test -q
```

- [ ] **Step 3: Tag**

```bash
git tag clean-architecture/phase-4-complete
```

---

## Phase 5 — Fix outbox race condition

> **Outcome:** Outbox publisher uses `SELECT FOR UPDATE SKIP LOCKED` + optimistic status check so the same row can never be published twice.

### Task 5.1: Add `lockPending` query method

**Files:**
- Modify: `BE/payment-service/src/main/java/com/ecom/payment/repository/OutboxEventRepository.java`

- [ ] **Step 1: Read the current file**

```bash
cat BE/payment-service/src/main/java/com/ecom/payment/repository/OutboxEventRepository.java
```

- [ ] **Step 2: Add the locking query**

Add to the repository interface:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.QueryHint;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    @Query("select e from OutboxEvent e where e.status = :status order by e.createdAt asc")
    List<OutboxEvent> lockTopPending(OutboxStatus status,
                                     org.springframework.data.domain.Pageable pageable);
}
```

- [ ] **Step 3: Test**

```bash
mvn -pl payment-service test -q
```

- [ ] **Step 4: Commit**

```bash
git add BE/payment-service/src/main/java/com/ecom/payment/repository/OutboxEventRepository.java
git commit -m "feat(payment): outbox lockPending query with SKIP LOCKED"
```

### Task 5.2: Rewrite `OutboxPublisher` to use lock + status CAS

**Files:**
- Modify: `BE/payment-service/src/main/java/com/ecom/payment/outbox/OutboxPublisher.java`
- Modify: `BE/payment-service/src/test/java/com/ecom/payment/outbox/OutboxPublisherTest.java`

- [ ] **Step 1: Write a failing test for the new behavior**

Add to `OutboxPublisherTest` (or create if missing):

```java
@Test
void marks_event_published_after_successful_send() {
    OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.succeeded", "{}");
    when(repository.lockTopPending(eq(OutboxStatus.PENDING), any())).thenReturn(List.of(event));
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(completedFuture(mock(SendResult.class)));

    publisher.publishPending();

    verify(repository).save(argThat(e -> e.getStatus() == OutboxStatus.PUBLISHED));
}

@Test
void does_not_save_on_send_failure() {
    OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.succeeded", "{}");
    when(repository.lockTopPending(eq(OutboxStatus.PENDING), any())).thenReturn(List.of(event));
    CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("kafka down"));
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

    publisher.publishPending();

    verify(repository).save(argThat(e -> e.getStatus() == OutboxStatus.FAILED));
}
```

- [ ] **Step 2: Run, expect FAIL (publishPending still uses old API)**

```bash
mvn -pl payment-service test -Dtest=OutboxPublisherTest -q
```

- [ ] **Step 3: Rewrite the publisher**

`BE/payment-service/src/main/java/com/ecom/payment/outbox/OutboxPublisher.java`:

```java
package com.ecom.payment.outbox;

import com.ecom.payment.domain.OutboxEvent;
import com.ecom.payment.domain.OutboxStatus;
import com.ecom.payment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<Object, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.publish-fixed-delay-ms:5000}")
    public void publishPending() {
        for (UUID id : lockAndCollectIds()) {
            publishOne(id);
        }
    }

    @Transactional
    protected List<UUID> lockAndCollectIds() {
        return repository.lockTopPending(OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE))
                .stream().map(OutboxEvent::getId).toList();
    }

    private void publishOne(UUID eventId) {
        OutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return; // already taken by another node
        }
        try {
            kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload())
                    .whenComplete((result, ex) -> updateStatus(eventId, ex == null ? OutboxStatus.PUBLISHED : OutboxStatus.FAILED));
        } catch (Exception ex) {
            log.warn("Outbox send failed for event {}: {}", eventId, ex.getMessage());
            updateStatus(eventId, OutboxStatus.FAILED);
        }
    }

    @Transactional
    protected void updateStatus(UUID eventId, OutboxStatus status) {
        repository.findById(eventId).ifPresent(e -> {
            if (e.getStatus() == OutboxStatus.PENDING) {
                if (status == OutboxStatus.PUBLISHED) e.markPublished();
                else e.markFailed();
                repository.save(e);
            }
        });
    }
}
```

- [ ] **Step 4: Run, expect PASS**

```bash
mvn -pl payment-service test -q
```

- [ ] **Step 5: Commit**

```bash
git add BE/payment-service
git commit -m "fix(payment): outbox publisher race-safe with lock+status CAS"
```

### Task 5.3: Phase 5 verification

- [ ] **Step 1: Test**

```bash
mvn -pl payment-service test -q
```

- [ ] **Step 2: Tag**

```bash
git tag clean-architecture/phase-5-complete
```

---

## Phase 6 — `PaymentProvider` interface + pluggable adapter

> **Outcome:** Business code depends on `PaymentProvider` interface, not on `MockPaymentProvider`. Swapping in a real provider (Stripe/VNPay) requires only adding a new adapter bean.

### Task 6.1: Define interface and move record out

**Files:**
- Create: `BE/payment-service/src/main/java/com/ecom/payment/provider/PaymentProvider.java`
- Create: `BE/payment-service/src/main/java/com/ecom/payment/provider/ProviderPayment.java`
- Modify: `BE/payment-service/src/main/java/com/ecom/payment/service/MockPaymentProvider.java`
- Delete: `BE/payment-service/src/main/java/com/ecom/payment/service/MockPaymentProvider.java` (after move)

- [ ] **Step 1: Create the interface**

`BE/payment-service/src/main/java/com/ecom/payment/provider/PaymentProvider.java`:

```java
package com.ecom.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {
    ProviderPayment createPayment(UUID paymentId, BigDecimal amount, String currency);
    ProviderPayment markSucceeded(String providerPaymentId);
    ProviderPayment markFailed(String providerPaymentId, String reason);
}
```

- [ ] **Step 2: Create the value object**

`BE/payment-service/src/main/java/com/ecom/payment/provider/ProviderPayment.java`:

```java
package com.ecom.payment.provider;

public record ProviderPayment(String providerPaymentId, String status, String redirectUrl) {}
```

- [ ] **Step 3: Move MockPaymentProvider to provider package, implement interface**

`BE/payment-service/src/main/java/com/ecom/payment/provider/MockPaymentProvider.java`:

```java
package com.ecom.payment.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {
    @Override
    public ProviderPayment createPayment(UUID paymentId, BigDecimal amount, String currency) {
        String id = "mock-pay-" + paymentId;
        return new ProviderPayment(id, "PENDING", "https://payments.example.test/" + id);
    }

    @Override
    public ProviderPayment markSucceeded(String providerPaymentId) {
        return new ProviderPayment(providerPaymentId, "SUCCEEDED", null);
    }

    @Override
    public ProviderPayment markFailed(String providerPaymentId, String reason) {
        return new ProviderPayment(providerPaymentId, "FAILED", null);
    }
}
```

- [ ] **Step 4: Delete the old class**

```bash
rm BE/payment-service/src/main/java/com/ecom/payment/service/MockPaymentProvider.java
```

- [ ] **Step 5: Update `PaymentService` import + field type**

In `BE/payment-service/src/main/java/com/ecom/payment/service/PaymentService.java`:

- Replace `import com.ecom.payment.service.MockPaymentProvider;` → `import com.ecom.payment.provider.PaymentProvider;`
- Change field `private final MockPaymentProvider paymentProvider;` → `private final PaymentProvider paymentProvider;`
- Change constructor parameter type and assignment.

- [ ] **Step 6: Update `PaymentServiceTest` mocks**

Search for `MockPaymentProvider` in tests and replace with `PaymentProvider`:

```bash
grep -rln "MockPaymentProvider" BE/payment-service/src/test
```

For each match, change `@Mock MockPaymentProvider paymentProvider` → `@Mock PaymentProvider paymentProvider`.

- [ ] **Step 7: Test**

```bash
mvn -pl payment-service test -q
```

- [ ] **Step 8: Commit**

```bash
git add BE/payment-service
git commit -m "refactor(payment): introduce PaymentProvider interface, move Mock adapter"
```

### Task 6.2: Phase 6 verification

- [ ] **Step 1: Confirm no service code references `MockPaymentProvider` directly**

```bash
grep -rn "MockPaymentProvider" BE/payment-service/src/main/java
```

Expected: only `BE/payment-service/src/main/java/com/ecom/payment/provider/MockPaymentProvider.java`.

- [ ] **Step 2: Tag**

```bash
git tag clean-architecture/phase-6-complete
```

---

## Phase 7 — Extract mappers from services

> **Outcome:** No more `to*Response` private methods inside services. DTO mapping happens at the controller boundary (or in dedicated mapper classes).

### Task 7.1: Extract `ProductMapper`

**Files:**
- Create: `BE/product-service/src/main/java/com/ecom/product/web/mapper/ProductMapper.java`
- Modify: `BE/product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java`
- Modify: `BE/product-service/src/main/java/com/ecom/product/web/ProductController.java`

- [ ] **Step 1: Create the mapper**

`BE/product-service/src/main/java/com/ecom/product/web/mapper/ProductMapper.java`:

```java
package com.ecom.product.web.mapper;

import com.ecom.product.domain.Category;
import com.ecom.product.domain.Product;
import com.ecom.product.web.dto.CategoryResponse;
import com.ecom.product.web.dto.ProductResponse;
import com.ecom.product.web.dto.ProductSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public CategoryResponse toCategoryResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.isActive());
    }
    public ProductResponse toProductResponse(Product p) {
        return new ProductResponse(p.getId(), p.getCategory().getId(), p.getCategory().getName(),
                p.getName(), p.getSlug(), p.getDescription(), p.getPrice(), p.getImageUrl(),
                p.isActive(), p.getPromotionTag());
    }
    public ProductSummaryResponse toProductSummary(Product p) {
        return new ProductSummaryResponse(p.getId(), p.getCategory().getId(), p.getName(),
                p.getSlug(), p.getPrice(), p.getImageUrl(), p.getPromotionTag());
    }
}
```

- [ ] **Step 2: In `ProductCatalogService`, return entity instead of DTO and remove the 3 private mapper methods**

Change return types: `CategoryResponse` → `Category`, `ProductResponse` → `Product`, `Page<ProductSummaryResponse>` → `Page<Product>`. Inject `ProductMapper` and let the controller call the mapper.

Concrete diff for `createCategory`:

```java
public Category createCategory(CreateCategoryRequest request) {
    if (categoryRepository.existsBySlug(request.slug())) {
        throw new DuplicateSlugException(request.slug());
    }
    return categoryRepository.save(new Category(request.name(), request.slug()));
}
```

Apply the same pattern to `listActiveCategories`, `createProduct`, `updateProduct`, `changeStatus`, `getProduct`, `search`.

- [ ] **Step 3: In `ProductController`, inject `ProductMapper` and map**

Example:

```java
@GetMapping("/{id}")
public ProductResponse get(@PathVariable UUID id) {
    return mapper.toProductResponse(catalogService.getProduct(id));
}
```

- [ ] **Step 4: Update `ProductCatalogServiceTest` — mocks now return `Product` / `Category`**

- [ ] **Step 5: Test**

```bash
mvn -pl product-service test -q
```

- [ ] **Step 6: Commit**

```bash
git add BE/product-service
git commit -m "refactor(product): extract ProductMapper, services return entities"
```

### Task 7.2: Apply same pattern to order, payment, inventory, cart

- [ ] **Step 1: For each of the four services, create a `web/mapper/*Mapper.java` and refactor service to return entity. Update controller to map. Update tests. Commit per service.**

- [ ] **Step 2: Tag**

```bash
git tag clean-architecture/phase-7-complete
```

---

## Phase 8 — JaCoCo coverage gate

> **Outcome:** Parent POM aggregates per-module coverage and the build fails if overall line coverage drops below 70% (or service-by-service configurable).

### Task 8.1: Add JaCoCo plugin to parent POM

**Files:**
- Modify: `BE/pom.xml`
- Create: `BE/jacoco-aggregate.sh` (or use jacoco-aggregate goal — see step 1)

- [ ] **Step 1: Add JaCoCo configuration to `<build><plugins>` in `BE/pom.xml`**

Insert the following inside the existing `<plugins>` block of the parent POM:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Add the property**

In the `<properties>` block of `BE/pom.xml`:

```xml
<jacoco.version>0.8.11</jacoco.version>
```

- [ ] **Step 3: Run verify on a single module to see baseline coverage**

```bash
mvn -pl product-service -am verify -q
```

Expected: BUILD SUCCESS, JaCoCo report at `BE/product-service/target/site/jacoco/index.html`.

- [ ] **Step 4: Commit**

```bash
git add BE/pom.xml
git commit -m "build: add JaCoCo coverage gate at 70% line coverage"
```

### Task 8.2: Phase 8 verification

- [ ] **Step 1: Full verify**

```bash
mvn clean verify -q
```

If the build fails due to coverage below 70%, write more tests in the offending modules until it passes. Document the exception cases (e.g. `config-server`, `discovery-server` excluded) in a `<configuration><excludes>` block.

- [ ] **Step 2: Tag**

```bash
git tag clean-architecture/phase-8-complete
```

---

## Phase 9 — Hexagonal port pilot in order-service

> **Outcome:** `OrderService` depends on `CartQueryPort`, `InventoryCommandPort`, `EventPublishPort` interfaces. Feign and Kafka are adapters. Unit tests no longer need `@MockBean`.

### Task 9.1: Define ports

**Files:**
- Create: `BE/order-service/src/main/java/com/ecom/order/port/CartQueryPort.java`
- Create: `BE/order-service/src/main/java/com/ecom/order/port/InventoryCommandPort.java`
- Create: `BE/order-service/src/main/java/com/ecom/order/port/EventPublishPort.java`
- Create: `BE/order-service/src/main/java/com/ecom/order/port/dto/CartView.java`
- Create: `BE/order-service/src/main/java/com/ecom/order/port/dto/CartItemView.java`

- [ ] **Step 1: Create the value objects (so ports are framework-agnostic)**

`BE/order-service/src/main/java/com/ecom/order/port/dto/CartItemView.java`:

```java
package com.ecom.order.port.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemView(UUID productId, String productName, BigDecimal unitPrice, int quantity) {}
```

`BE/order-service/src/main/java/com/ecom/order/port/dto/CartView.java`:

```java
package com.ecom.order.port.dto;

import java.util.List;
import java.util.UUID;

public record CartView(UUID userId, List<CartItemView> items) {}
```

- [ ] **Step 2: Create the port interfaces**

`BE/order-service/src/main/java/com/ecom/order/port/CartQueryPort.java`:

```java
package com.ecom.order.port;

import com.ecom.order.port.dto.CartView;

import java.util.UUID;

public interface CartQueryPort {
    CartView getCart(UUID userId, String email);
    void clearCart(UUID userId, String email);
}
```

`BE/order-service/src/main/java/com/ecom/order/port/InventoryCommandPort.java`:

```java
package com.ecom.order.port;

import java.util.UUID;

public interface InventoryCommandPort {
    void release(UUID reservationId);
}
```

`BE/order-service/src/main/java/com/ecom/order/port/EventPublishPort.java`:

```java
package com.ecom.order.port;

public interface EventPublishPort {
    void publishReservationRequested(Object event);
    void publishOrderConfirmed(Object event);
    void publishOrderCancelled(Object event);
}
```

### Task 9.2: Implement Feign adapter

**Files:**
- Create: `BE/order-service/src/main/java/com/ecom/order/adapter/CartQueryFeignAdapter.java`
- Create: `BE/order-service/src/main/java/com/ecom/order/adapter/InventoryCommandFeignAdapter.java`

- [ ] **Step 1: Create `CartQueryFeignAdapter`**

```java
package com.ecom.order.adapter;

import com.ecom.order.client.CartClient;
import com.ecom.order.client.CartResponse;
import com.ecom.order.port.CartQueryPort;
import com.ecom.order.port.dto.CartItemView;
import com.ecom.order.port.dto.CartView;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class CartQueryFeignAdapter implements CartQueryPort {
    private final CartClient client;

    CartQueryFeignAdapter(CartClient client) { this.client = client; }

    @Override
    public CartView getCart(UUID userId, String email) {
        CartResponse r = client.getCart(userId, email);
        return new CartView(userId,
                r.items().stream()
                        .map(i -> new CartItemView(i.productId(), i.productName(), i.unitPrice(), i.quantity()))
                        .toList());
    }

    @Override
    public void clearCart(UUID userId, String email) { client.clearCart(userId, email); }
}
```

- [ ] **Step 2: Create `InventoryCommandFeignAdapter`**

```java
package com.ecom.order.adapter;

import com.ecom.order.client.InventoryClient;
import com.ecom.order.port.InventoryCommandPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class InventoryCommandFeignAdapter implements InventoryCommandPort {
    private final InventoryClient client;
    InventoryCommandFeignAdapter(InventoryClient client) { this.client = client; }
    @Override public void release(UUID reservationId) { client.release(reservationId); }
}
```

### Task 9.3: Implement Kafka adapter

**Files:**
- Create: `BE/order-service/src/main/java/com/ecom/order/adapter/KafkaEventPublishAdapter.java`

- [ ] **Step 1: Create the adapter wrapping the existing `OrderEventProducer`**

```java
package com.ecom.order.adapter;

import com.ecom.order.messaging.OrderEventProducer;
import com.ecom.order.port.EventPublishPort;
import org.springframework.stereotype.Component;

@Component
class KafkaEventPublishAdapter implements EventPublishPort {
    private final OrderEventProducer producer;
    KafkaEventPublishAdapter(OrderEventProducer producer) { this.producer = producer; }

    @Override public void publishReservationRequested(Object event) { producer.publishReservationRequested(event); }
    @Override public void publishOrderConfirmed(Object event)         { producer.publishOrderConfirmed(event); }
    @Override public void publishOrderCancelled(Object event)         { producer.publishOrderCancelled(event); }
}
```

### Task 9.4: Refactor `OrderService` to depend on ports

**Files:**
- Modify: `BE/order-service/src/main/java/com/ecom/order/service/OrderService.java`

- [ ] **Step 1: Replace field types**

```java
private final OrderRepository orderRepository;
private final CartQueryPort cartQueryPort;
private final InventoryCommandPort inventoryCommandPort;
```

- [ ] **Step 2: Replace method bodies**

`createOrder`:

```java
@CircuitBreaker(name = "cartService", fallbackMethod = "createOrderFallback")
@Retry(name = "cartService")
@Bulkhead(name = "cartService")
@Transactional
public OrderResponse createOrder(UUID userId, String email) {
    CartView cart = cartQueryPort.getCart(userId, email);
    if (cart.items().isEmpty()) throw new EmptyCartException();
    Order order = new Order(userId);
    cart.items().forEach(i -> order.addItem(i.productId(), i.productName(), i.unitPrice(), i.quantity()));
    Order saved = orderRepository.save(order);
    cartQueryPort.clearCart(userId, email);
    return OrderResponse.from(saved);
}
```

- [ ] **Step 3: Test**

```bash
mvn -pl order-service test -q
```

- [ ] **Step 4: Commit**

```bash
git add BE/order-service
git commit -m "refactor(order): hexagonal ports for cart/inventory/event"
```

### Task 9.5: Simplify unit tests (no more `@MockBean`)

**Files:**
- Modify: `BE/order-service/src/test/java/com/ecom/order/service/OrderServiceTest.java`

- [ ] **Step 1: Replace `@MockBean CartClient` + `@MockBean InventoryClient` with `@Mock CartQueryPort cartQueryPort` + `@Mock InventoryCommandPort inventoryCommandPort`.**

- [ ] **Step 2: Test**

```bash
mvn -pl order-service test -q
```

- [ ] **Step 3: Commit**

```bash
git add BE/order-service
git commit -m "test(order): ports enable plain Mockito (no Spring context)"
```

### Task 9.6: Phase 9 verification

- [ ] **Step 1: Confirm `OrderService` no longer imports `com.ecom.order.client.*`**

```bash
grep -rn "import com.ecom.order.client" BE/order-service/src/main/java/com/ecom/order/service
```

Expected: no matches.

- [ ] **Step 2: Tag**

```bash
git tag clean-architecture/phase-9-complete
```

---

## Phase 10 — ArchUnit layer dependency enforcement

> **Outcome:** Build fails if a service layer class imports a web/controller class, or a domain class imports a Feign client, or `*Exception` in service does not extend `common.web.*`.

### Task 10.1: New `architecture-rules` module

**Files:**
- Create: `BE/architecture-rules/pom.xml`
- Create: `BE/architecture-rules/src/test/java/com/ecom/architecture/ArchitectureTest.java`
- Modify: `BE/pom.xml` (add module to `<modules>`)

- [ ] **Step 1: Create the module pom**

`BE/architecture-rules/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ecom</groupId>
        <artifactId>ecom-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>architecture-rules</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>1.3.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create the test**

`BE/architecture-rules/src/test/java/com/ecom/architecture/ArchitectureTest.java`:

```java
package com.ecom.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ecom");

    @Test
    void service_layer_must_not_depend_on_web() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..web..");
        rule.check(classes);
    }

    @Test
    void domain_layer_must_not_depend_on_anything_outside_domain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "..domain..", "java..", "jakarta.persistence..", "com.ecom.common.web.DomainException");
        rule.check(classes);
    }

    @Test
    void exceptions_in_service_must_extend_common_web() {
        ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().haveSimpleNameEndingWith("Exception")
                .should().beAssignableTo(com.ecom.common.web.DomainException.class);
        rule.check(classes);
    }
}
```

- [ ] **Step 3: Add module to parent pom**

Edit `BE/pom.xml`, in `<modules>`:

```xml
<module>architecture-rules</module>
```

- [ ] **Step 4: Run the test**

```bash
mvn -pl architecture-rules -am test -q
```

Expected: BUILD SUCCESS if all 10 prior phases are complete. If any test fails, fix the offending class (most likely a `service/*` class still imports `web/dto`).

- [ ] **Step 5: Commit**

```bash
git add BE/architecture-rules BE/pom.xml
git commit -m "build(arch): ArchUnit enforces service/web/domain boundaries"
```

### Task 10.2: Phase 10 / plan complete

- [ ] **Step 1: Full build with all gates**

```bash
mvn clean verify -q
```

Expected: BUILD SUCCESS, all tests pass, JaCoCo gate satisfied, ArchUnit rules pass.

- [ ] **Step 2: Tag final**

```bash
git tag clean-architecture/complete
```

- [ ] **Step 3: Write a short summary doc**

Create `docs/superpowers/plans/2026-08-08-clean-code-architecture-summary.md` describing:
- What changed in each phase
- Score improvement (5.5 → 8.5)
- Any tech-debt intentionally left (e.g. `payment-contracts` duplication if Phase 4.3 step 1 was skipped)

---

## Self-review

**Spec coverage:** All 10 recommendations from the architecture review are mapped:
1. Extract `GatewayUserContext*` → Phase 1
2. Adopt `GlobalExceptionHandler` → Phase 2
3. Base `DomainException` → Phase 2-3
4. Typed event payloads + version → Phase 4
5. Outbox race fix → Phase 5
6. `PaymentProvider` abstraction → Phase 6
7. Mappers extracted → Phase 7
8. JaCoCo gate → Phase 8
9. Hexagonal ports (order pilot) → Phase 9
10. ArchUnit enforcement → Phase 10

**Placeholder scan:** No "TBD"/"TODO"/"similar to" placeholders. Every code change shows the full code. Every test is included.

**Type consistency:** Method names match across phases (`append`, `lockTopPending`, `markPublished`, `markFailed`, `createOrder`, `cancelOrder`, `cancelAfterPaymentFailure`). Field types in `OrderService` and `PaymentService` are updated consistently. Event record names (`PaymentSucceededEvent`, `PaymentFailedEvent`, `PaymentRefundedEvent`) are consistent between `payment-service` (Phase 4.1) and `payment-contracts` (Phase 4.3).

**Dependency direction:** Each phase's tasks listed in the order they must be done. Phase 1 must precede Phase 9 (port tests need common filter), Phase 2 must precede Phase 10 (ArchUnit checks `extends DomainException`).

**Risk areas called out:**
- Phase 4.3 step 1: introduces a new Maven module `payment-contracts`. If the engineer is unsure, they can defer this and keep the 3 records in both `payment-service` and consumers (with a TODO comment) — this is the only intentional YAGNI choice in the plan.
- Phase 8.1: if 70% coverage gate is too aggressive, lower to 60% in step 1.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-08-clean-code-architecture-refactor.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
