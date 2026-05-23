# Phase 1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the foundational monorepo, shared conventions, platform services, local infrastructure, observability baseline, and CI pipeline for the e-commerce / food delivery microservices system.

**Architecture:** Use a Maven multi-module Spring Boot monorepo with one runnable application per service and one shared library module for common web contracts. Phase 1 creates infrastructure and platform services only: Discovery Server, Config Server, API Gateway skeleton, Docker Compose dependencies, OpenAPI wiring, correlation ID logging, and basic CI.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Cloud 2023.x, Maven, Docker Compose, PostgreSQL, Redis, Kafka, Zipkin, Prometheus, Grafana, JUnit 5, Testcontainers, GitHub Actions.

---

## File Structure

- Create `pom.xml`: root Maven parent with dependency management, plugin management, module list, Java 21 configuration.
- Create `.gitignore`: ignore build outputs, IDE files, logs, environment files.
- Create `.editorconfig`: consistent formatting for Java, YAML, XML, Markdown.
- Create `README.md`: local startup, service map, ports, development workflow.
- Create `docker-compose.yml`: PostgreSQL, Redis, Kafka, Zipkin, Prometheus, Grafana.
- Create `config-repo/application.yml`: shared config for logging, tracing, actuator.
- Create `config-repo/api-gateway.yml`: gateway port and service discovery config.
- Create `config-repo/discovery-server.yml`: Eureka server config.
- Create `config-repo/config-server.yml`: Config Server config.
- Create `infra/prometheus/prometheus.yml`: scrape config for service actuator endpoints.
- Create `.github/workflows/ci.yml`: Maven build and tests.
- Create `common/pom.xml`: shared library module.
- Create `common/src/main/java/com/ecom/common/web/ApiResponse.java`: standard response wrapper.
- Create `common/src/main/java/com/ecom/common/web/ErrorResponse.java`: standard error response.
- Create `common/src/main/java/com/ecom/common/web/CorrelationIdFilter.java`: servlet correlation ID propagation.
- Create `common/src/test/java/com/ecom/common/web/CorrelationIdFilterTest.java`: unit tests for correlation ID behavior.
- Create `discovery-server/pom.xml`: Eureka Server module.
- Create `discovery-server/src/main/java/com/ecom/discovery/DiscoveryServerApplication.java`: application entrypoint.
- Create `discovery-server/src/test/java/com/ecom/discovery/DiscoveryServerApplicationTest.java`: context load test.
- Create `config-server/pom.xml`: Spring Cloud Config Server module.
- Create `config-server/src/main/java/com/ecom/config/ConfigServerApplication.java`: application entrypoint.
- Create `config-server/src/test/java/com/ecom/config/ConfigServerApplicationTest.java`: context load test.
- Create `api-gateway/pom.xml`: Spring Cloud Gateway module.
- Create `api-gateway/src/main/java/com/ecom/gateway/ApiGatewayApplication.java`: application entrypoint.
- Create `api-gateway/src/main/java/com/ecom/gateway/config/CorsConfig.java`: CORS config.
- Create `api-gateway/src/main/java/com/ecom/gateway/filter/GatewayCorrelationIdFilter.java`: reactive correlation ID filter.
- Create `api-gateway/src/test/java/com/ecom/gateway/filter/GatewayCorrelationIdFilterTest.java`: WebFlux filter tests.

## Phase Acceptance Criteria

- `mvn test` passes from repository root.
- `docker compose up -d` starts all infrastructure dependencies.
- Discovery Server runs on `http://localhost:8761`.
- Config Server runs on `http://localhost:8888`.
- API Gateway runs on `http://localhost:8080`.
- Gateway exposes `/actuator/health`.
- All HTTP responses through Gateway include or preserve `X-Correlation-Id`.
- GitHub Actions workflow runs `mvn -B test`.

### Task 1: Initialize Repository Baseline

**Files:**
- Create: `.gitignore`
- Create: `.editorconfig`
- Create: `README.md`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
target/
.idea/
.vscode/
*.iml
*.log
.env
.DS_Store
docker-data/
```

- [ ] **Step 2: Create `.editorconfig`**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 2

[*.java]
indent_size = 4

[*.md]
trim_trailing_whitespace = false
```

- [ ] **Step 3: Create `README.md` with service map**

```markdown
# Ecom Food Delivery Microservices

Production-like Java microservices project for an e-commerce / food delivery domain.

## Services

| Service | Port | Responsibility |
| --- | ---: | --- |
| API Gateway | 8080 | Routing, CORS, correlation ID, future JWT validation |
| Discovery Server | 8761 | Service discovery |
| Config Server | 8888 | Centralized configuration |

## Local Development

```bash
docker compose up -d
mvn test
mvn spring-boot:run -pl discovery-server
mvn spring-boot:run -pl config-server
mvn spring-boot:run -pl api-gateway
```
```

- [ ] **Step 4: Verify files exist**

Run: `ls -la && sed -n '1,120p' README.md`

Expected: `.gitignore`, `.editorconfig`, and `README.md` exist with the content above.

- [ ] **Step 5: Commit**

```bash
git add .gitignore .editorconfig README.md
git commit -m "chore: initialize repository baseline"
```

### Task 2: Create Root Maven Multi-Module Build

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Create root `pom.xml`**

Use this dependency baseline:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ecom</groupId>
    <artifactId>ecom-platform</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>ecom-platform</name>

    <modules>
        <module>common</module>
        <module>discovery-server</module>
        <module>config-server</module>
        <module>api-gateway</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring.boot.version>3.3.6</spring.boot.version>
        <spring.cloud.version>2023.0.4</spring.cloud.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring.cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <release>${maven.compiler.release}</release>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring.boot.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: Run Maven validation**

Run: `mvn -q validate`

Expected: Maven fails because the listed modules do not exist yet. This confirms the root build is active.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add root maven multi-module project"
```

### Task 3: Add Shared Common Module

**Files:**
- Create: `common/pom.xml`
- Create: `common/src/main/java/com/ecom/common/web/ApiResponse.java`
- Create: `common/src/main/java/com/ecom/common/web/ErrorResponse.java`
- Create: `common/src/main/java/com/ecom/common/web/CorrelationIdFilter.java`
- Create: `common/src/test/java/com/ecom/common/web/CorrelationIdFilterTest.java`

- [ ] **Step 1: Create `common/pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ecom</groupId>
        <artifactId>ecom-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>common</artifactId>

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
    </dependencies>
</project>
```

- [ ] **Step 2: Create response contracts**

```java
package com.ecom.common.web;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK", Instant.now());
    }
}
```

```java
package com.ecom.common.web;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<String> details,
        String correlationId,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, List.of(), correlationId, Instant.now());
    }
}
```

- [ ] **Step 3: Create servlet correlation filter**

```java
package com.ecom.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

- [ ] **Step 4: Write correlation ID tests**

```java
package com.ecom.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesIncomingCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "corr-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("corr-123");
    }

    @Test
    void createsCorrelationIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
    }
}
```

- [ ] **Step 5: Run module tests**

Run: `mvn -q -pl common test`

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add common pom.xml
git commit -m "feat: add common web contracts"
```

### Task 4: Add Discovery Server

**Files:**
- Create: `discovery-server/pom.xml`
- Create: `discovery-server/src/main/java/com/ecom/discovery/DiscoveryServerApplication.java`
- Create: `discovery-server/src/main/resources/application.yml`
- Create: `discovery-server/src/test/java/com/ecom/discovery/DiscoveryServerApplicationTest.java`

- [ ] **Step 1: Create `discovery-server/pom.xml`**

Dependencies: `spring-cloud-starter-netflix-eureka-server`, `spring-boot-starter-actuator`, `spring-boot-starter-test`.

- [ ] **Step 2: Create application class**

```java
package com.ecom.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

- [ ] **Step 3: Create local config**

```yaml
server:
  port: 8761

spring:
  application:
    name: discovery-server

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

- [ ] **Step 4: Add context test**

```java
package com.ecom.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiscoveryServerApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn -q -pl discovery-server test`

Expected: context loads successfully.

- [ ] **Step 6: Commit**

```bash
git add discovery-server pom.xml
git commit -m "feat: add discovery server"
```

### Task 5: Add Config Server and Config Repository

**Files:**
- Create: `config-server/pom.xml`
- Create: `config-server/src/main/java/com/ecom/config/ConfigServerApplication.java`
- Create: `config-server/src/main/resources/application.yml`
- Create: `config-server/src/test/java/com/ecom/config/ConfigServerApplicationTest.java`
- Create: `config-repo/application.yml`
- Create: `config-repo/discovery-server.yml`
- Create: `config-repo/config-server.yml`
- Create: `config-repo/api-gateway.yml`

- [ ] **Step 1: Create Config Server module**

Dependencies: `spring-cloud-config-server`, `spring-boot-starter-actuator`, `spring-boot-starter-test`.

- [ ] **Step 2: Create application class**

```java
package com.ecom.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

- [ ] **Step 3: Configure native config backend**

```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: file:./config-repo

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

- [ ] **Step 4: Create shared config files**

`config-repo/application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

logging:
  pattern:
    level: "%5p [${spring.application.name:unknown},%X{correlationId:-}]"
```

`config-repo/api-gateway.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```

`config-repo/discovery-server.yml`:

```yaml
server:
  port: 8761
```

`config-repo/config-server.yml`:

```yaml
server:
  port: 8888
```

- [ ] **Step 5: Add context test**

```java
package com.ecom.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConfigServerApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: Run test**

Run: `mvn -q -pl config-server test`

Expected: context loads successfully.

- [ ] **Step 7: Commit**

```bash
git add config-server config-repo pom.xml
git commit -m "feat: add config server"
```

### Task 6: Add API Gateway Skeleton

**Files:**
- Create: `api-gateway/pom.xml`
- Create: `api-gateway/src/main/java/com/ecom/gateway/ApiGatewayApplication.java`
- Create: `api-gateway/src/main/java/com/ecom/gateway/config/CorsConfig.java`
- Create: `api-gateway/src/main/java/com/ecom/gateway/filter/GatewayCorrelationIdFilter.java`
- Create: `api-gateway/src/main/resources/application.yml`
- Create: `api-gateway/src/test/java/com/ecom/gateway/filter/GatewayCorrelationIdFilterTest.java`

- [ ] **Step 1: Create Gateway module**

Dependencies: `spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-actuator`, `spring-boot-starter-test`, `reactor-test`.

- [ ] **Step 2: Create application class**

```java
package com.ecom.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

- [ ] **Step 3: Add CORS config**

Allow local frontend origins `http://localhost:3000` and `http://localhost:5173`, methods `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, and headers `Authorization`, `Content-Type`, `X-Correlation-Id`.

- [ ] **Step 4: Add reactive correlation ID filter**

The filter must read `X-Correlation-Id`, create a UUID if missing, mutate the request header, and set the same response header.

- [ ] **Step 5: Add filter tests**

Test cases:
- incoming `X-Correlation-Id: corr-123` is preserved in response.
- missing correlation ID produces a non-blank response header.

- [ ] **Step 6: Run gateway tests**

Run: `mvn -q -pl api-gateway test`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add api-gateway pom.xml
git commit -m "feat: add api gateway skeleton"
```

### Task 7: Add Local Infrastructure Compose

**Files:**
- Create: `docker-compose.yml`
- Create: `infra/prometheus/prometheus.yml`

- [ ] **Step 1: Create `docker-compose.yml`**

Services:
- `postgres`: image `postgres:16-alpine`, port `5432`, database `ecom`.
- `redis`: image `redis:7-alpine`, port `6379`.
- `kafka`: image `bitnami/kafka:3.7`, port `9092`, KRaft mode enabled.
- `zipkin`: image `openzipkin/zipkin:3`, port `9411`.
- `prometheus`: image `prom/prometheus:v2.55.1`, port `9090`, mounts `infra/prometheus/prometheus.yml`.
- `grafana`: image `grafana/grafana:11.3.0`, port `3001`.

- [ ] **Step 2: Create Prometheus config**

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: api-gateway
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8080"]
  - job_name: discovery-server
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8761"]
  - job_name: config-server
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8888"]
```

- [ ] **Step 3: Start infrastructure**

Run: `docker compose up -d`

Expected: containers for PostgreSQL, Redis, Kafka, Zipkin, Prometheus, and Grafana are running.

- [ ] **Step 4: Verify infrastructure**

Run: `docker compose ps`

Expected: all services show `running` or `healthy`.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml infra/prometheus/prometheus.yml
git commit -m "chore: add local infrastructure compose"
```

### Task 8: Add CI Pipeline

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create GitHub Actions workflow**

```yaml
name: CI

on:
  push:
    branches: ["main"]
  pull_request:
    branches: ["main"]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - name: Run tests
        run: mvn -B test
```

- [ ] **Step 2: Run full local test suite**

Run: `mvn -B test`

Expected: all module tests pass.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add maven test workflow"
```

### Task 9: Final Phase 1 Verification

**Files:**
- Verify: all Phase 1 files.

- [ ] **Step 1: Run tests**

Run: `mvn -B test`

Expected: build success.

- [ ] **Step 2: Start infrastructure**

Run: `docker compose up -d`

Expected: all infrastructure services start.

- [ ] **Step 3: Start platform services**

Open three terminals:

```bash
mvn spring-boot:run -pl discovery-server
mvn spring-boot:run -pl config-server
mvn spring-boot:run -pl api-gateway
```

Expected:
- Discovery Server available at `http://localhost:8761`.
- Config Server health available at `http://localhost:8888/actuator/health`.
- API Gateway health available at `http://localhost:8080/actuator/health`.

- [ ] **Step 4: Verify correlation ID**

Run:

```bash
curl -i http://localhost:8080/actuator/health -H "X-Correlation-Id: corr-final-check"
```

Expected: response headers include `X-Correlation-Id: corr-final-check`.

- [ ] **Step 5: Commit verification notes if README changed**

```bash
git add README.md
git commit -m "docs: document foundation startup checks"
```

## Self-Review

- Spec coverage: Phase 1 covers monorepo, shared web contracts, Discovery Server, Config Server, API Gateway skeleton, Docker Compose infrastructure, observability baseline, and CI.
- Placeholder scan: No deferred implementation language is used; each task has concrete files, commands, and acceptance expectations.
- Type consistency: Package names use `com.ecom.*`; correlation ID header is consistently `X-Correlation-Id`; service names match config files.
