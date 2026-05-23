# Phase 2 Identity Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first business vertical slice with Auth Service, User Service, JWT authentication, refresh tokens, RBAC, Gateway JWT validation, and user profile/address APIs.

**Architecture:** Add two Spring Boot services behind the API Gateway. Auth Service owns credentials, roles, refresh token sessions, and JWT issuing; User Service owns profile and address data. Services use separate PostgreSQL schemas, register with Discovery Server, load shared config from Config Server, and expose OpenAPI plus actuator endpoints.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Security, Spring Cloud Gateway, Spring Data JPA, PostgreSQL, Flyway, Redis, JWT via Nimbus JOSE JWT, OpenAPI via springdoc, JUnit 5, Testcontainers, WireMock where service isolation is needed.

---

## File Structure

- Modify `pom.xml`: add `auth-service` and `user-service` modules.
- Modify `docker-compose.yml`: add `auth-db`, `user-db`, and keep Redis for refresh token denylist/session cache.
- Modify `config-repo/application.yml`: add shared datasource, Flyway, OpenAPI, and JWT config keys.
- Create `config-repo/auth-service.yml`: Auth Service port, datasource, Redis, JWT config.
- Create `config-repo/user-service.yml`: User Service port and datasource.
- Modify `config-repo/api-gateway.yml`: route `/api/auth/**` and `/api/users/**`.
- Modify `api-gateway`: add JWT validation filter and public route matcher.
- Create `auth-service/pom.xml`: Auth Service dependencies.
- Create `auth-service/src/main/java/com/ecom/auth/AuthServiceApplication.java`: entrypoint.
- Create `auth-service/src/main/java/com/ecom/auth/domain/UserCredential.java`: credential aggregate.
- Create `auth-service/src/main/java/com/ecom/auth/domain/UserRole.java`: role enum.
- Create `auth-service/src/main/java/com/ecom/auth/domain/RefreshToken.java`: refresh token aggregate.
- Create `auth-service/src/main/java/com/ecom/auth/repository/UserCredentialRepository.java`: credential persistence.
- Create `auth-service/src/main/java/com/ecom/auth/repository/RefreshTokenRepository.java`: refresh token persistence.
- Create `auth-service/src/main/java/com/ecom/auth/security/JwtTokenService.java`: JWT issuer and parser.
- Create `auth-service/src/main/java/com/ecom/auth/service/AuthService.java`: register, login, refresh, logout orchestration.
- Create `auth-service/src/main/java/com/ecom/auth/web/AuthController.java`: REST API.
- Create `auth-service/src/main/java/com/ecom/auth/web/dto/*.java`: request/response DTOs.
- Create `auth-service/src/main/resources/db/migration/V1__create_auth_tables.sql`: schema migration.
- Create `auth-service/src/test/java/com/ecom/auth/service/AuthServiceTest.java`: service tests.
- Create `auth-service/src/test/java/com/ecom/auth/web/AuthControllerIT.java`: integration tests.
- Create `user-service/pom.xml`: User Service dependencies.
- Create `user-service/src/main/java/com/ecom/user/UserServiceApplication.java`: entrypoint.
- Create `user-service/src/main/java/com/ecom/user/domain/UserProfile.java`: profile aggregate.
- Create `user-service/src/main/java/com/ecom/user/domain/UserAddress.java`: address aggregate.
- Create `user-service/src/main/java/com/ecom/user/repository/UserProfileRepository.java`: profile persistence.
- Create `user-service/src/main/java/com/ecom/user/repository/UserAddressRepository.java`: address persistence.
- Create `user-service/src/main/java/com/ecom/user/service/UserProfileService.java`: profile and address use cases.
- Create `user-service/src/main/java/com/ecom/user/web/UserProfileController.java`: REST API.
- Create `user-service/src/main/java/com/ecom/user/web/dto/*.java`: request/response DTOs.
- Create `user-service/src/main/resources/db/migration/V1__create_user_tables.sql`: schema migration.
- Create `user-service/src/test/java/com/ecom/user/service/UserProfileServiceTest.java`: service tests.
- Create `user-service/src/test/java/com/ecom/user/web/UserProfileControllerIT.java`: integration tests.

## API Contract

### Auth Service

- `POST /api/auth/register`: creates credentials and initial user identity.
- `POST /api/auth/login`: returns access token and refresh token.
- `POST /api/auth/refresh`: rotates refresh token and returns a new access token.
- `POST /api/auth/logout`: revokes refresh token.
- `GET /api/auth/me`: returns authenticated subject, email, and roles.

### User Service

- `GET /api/users/me`: returns current user profile.
- `PUT /api/users/me`: updates display name, phone, and preferences.
- `GET /api/users/me/addresses`: lists addresses.
- `POST /api/users/me/addresses`: creates address.
- `PUT /api/users/me/addresses/{addressId}`: updates address.
- `DELETE /api/users/me/addresses/{addressId}`: deletes address.

## Security Rules

- Public routes: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`.
- Authenticated routes: all `/api/users/**`, `POST /api/auth/logout`, `GET /api/auth/me`.
- Roles: `CUSTOMER` by default, `ADMIN` reserved for later phases.
- JWT claims: `sub`, `email`, `roles`, `iat`, `exp`.
- Access token TTL: 15 minutes.
- Refresh token TTL: 30 days.

## Phase Acceptance Criteria

- `mvn test` passes from repository root.
- Auth Service runs on `http://localhost:8081`.
- User Service runs on `http://localhost:8082`.
- Gateway routes Auth and User APIs through `http://localhost:8080`.
- Register/login/refresh/logout flow works through Gateway.
- Gateway rejects protected routes without JWT.
- Gateway forwards authenticated user context headers to User Service.
- User profile and address CRUD works for the authenticated user.
- Auth and User each use separate PostgreSQL schemas/databases.

### Task 1: Extend Build and Compose for Identity Services

**Files:**
- Modify: `pom.xml`
- Modify: `docker-compose.yml`
- Create: `config-repo/auth-service.yml`
- Create: `config-repo/user-service.yml`
- Modify: `config-repo/api-gateway.yml`

- [ ] **Step 1: Add modules to root `pom.xml`**

Add:

```xml
<module>auth-service</module>
<module>user-service</module>
```

- [ ] **Step 2: Add identity databases to `docker-compose.yml`**

Add PostgreSQL services:
- `auth-db`: database `auth_service`, user `auth_user`, password `auth_password`, port `5433`.
- `user-db`: database `user_service`, user `user_user`, password `user_password`, port `5434`.

- [ ] **Step 3: Add Auth Service config**

`config-repo/auth-service.yml`:

```yaml
server:
  port: 8081

spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:postgresql://localhost:5433/auth_service
    username: auth_user
    password: auth_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379

security:
  jwt:
    issuer: ecom-auth-service
    access-token-ttl-minutes: 15
    refresh-token-ttl-days: 30
    secret: local-development-secret-must-be-32-bytes
```

- [ ] **Step 4: Add User Service config**

`config-repo/user-service.yml`:

```yaml
server:
  port: 8082

spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://localhost:5434/user_service
    username: user_user
    password: user_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

- [ ] **Step 5: Add Gateway routes**

In `config-repo/api-gateway.yml`, add explicit route IDs:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
```

- [ ] **Step 6: Run validation**

Run: `mvn -q validate`

Expected: Maven fails until the new service modules are created.

- [ ] **Step 7: Commit**

```bash
git add pom.xml docker-compose.yml config-repo
git commit -m "chore: prepare identity service modules"
```

### Task 2: Create Auth Service Skeleton and Schema

**Files:**
- Create: `auth-service/pom.xml`
- Create: `auth-service/src/main/java/com/ecom/auth/AuthServiceApplication.java`
- Create: `auth-service/src/main/resources/application.yml`
- Create: `auth-service/src/main/resources/db/migration/V1__create_auth_tables.sql`
- Create: `auth-service/src/test/java/com/ecom/auth/AuthServiceApplicationTest.java`

- [ ] **Step 1: Create Auth Service Maven module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-validation`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql`
- `nimbus-jose-jwt`
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test`
- `testcontainers-postgresql`

- [ ] **Step 2: Create application class**

```java
package com.ecom.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create local fallback config**

```yaml
spring:
  application:
    name: auth-service
  config:
    import: optional:configserver:http://localhost:8888

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

- [ ] **Step 4: Create auth schema migration**

```sql
CREATE TABLE user_credentials (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES user_credentials(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_credentials(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
```

- [ ] **Step 5: Add context test**

```java
package com.ecom.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: Run Auth Service test**

Run: `mvn -q -pl auth-service test`

Expected: context test passes once datasource test properties are added in the next task. If it fails because no datasource exists, continue to Task 3 and re-run.

- [ ] **Step 7: Commit**

```bash
git add auth-service pom.xml
git commit -m "feat: add auth service skeleton"
```

### Task 3: Implement Auth Domain, Repositories, and Password Policy

**Files:**
- Create: `auth-service/src/main/java/com/ecom/auth/domain/UserCredential.java`
- Create: `auth-service/src/main/java/com/ecom/auth/domain/UserRole.java`
- Create: `auth-service/src/main/java/com/ecom/auth/domain/RefreshToken.java`
- Create: `auth-service/src/main/java/com/ecom/auth/repository/UserCredentialRepository.java`
- Create: `auth-service/src/main/java/com/ecom/auth/repository/RefreshTokenRepository.java`
- Create: `auth-service/src/test/java/com/ecom/auth/repository/UserCredentialRepositoryTest.java`

- [ ] **Step 1: Define roles**

```java
package com.ecom.auth.domain;

public enum UserRole {
    CUSTOMER,
    ADMIN
}
```

- [ ] **Step 2: Create `UserCredential` entity**

Fields:
- `UUID id`
- `String email`
- `String passwordHash`
- `boolean enabled`
- `Set<UserRole> roles`
- `Instant createdAt`
- `Instant updatedAt`

Entity rules:
- email stored lowercase.
- default role is `CUSTOMER`.
- password hash is never exposed through DTOs.

- [ ] **Step 3: Create `RefreshToken` entity**

Fields:
- `UUID id`
- `UUID userId`
- `String tokenHash`
- `Instant expiresAt`
- `Instant revokedAt`
- `Instant createdAt`

Methods:
- `boolean isRevoked()`
- `boolean isExpired(Instant now)`
- `void revoke(Instant now)`

- [ ] **Step 4: Create repositories**

`UserCredentialRepository` methods:
- `Optional<UserCredential> findByEmail(String email)`
- `boolean existsByEmail(String email)`

`RefreshTokenRepository` methods:
- `Optional<RefreshToken> findByTokenHash(String tokenHash)`
- `List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId)`

- [ ] **Step 5: Write repository integration test**

Test that:
- email lookup works with lowercase email.
- default `CUSTOMER` role is persisted.
- refresh token can be revoked and loaded again.

- [ ] **Step 6: Run repository tests**

Run: `mvn -q -pl auth-service test -Dtest=UserCredentialRepositoryTest`

Expected: tests pass using Testcontainers PostgreSQL.

- [ ] **Step 7: Commit**

```bash
git add auth-service
git commit -m "feat: add auth persistence model"
```

### Task 4: Implement JWT and Auth Use Cases

**Files:**
- Create: `auth-service/src/main/java/com/ecom/auth/security/JwtProperties.java`
- Create: `auth-service/src/main/java/com/ecom/auth/security/JwtTokenService.java`
- Create: `auth-service/src/main/java/com/ecom/auth/service/AuthService.java`
- Create: `auth-service/src/main/java/com/ecom/auth/service/AuthResult.java`
- Create: `auth-service/src/test/java/com/ecom/auth/security/JwtTokenServiceTest.java`
- Create: `auth-service/src/test/java/com/ecom/auth/service/AuthServiceTest.java`

- [ ] **Step 1: Create JWT properties**

Properties:
- `issuer`
- `secret`
- `accessTokenTtlMinutes`
- `refreshTokenTtlDays`

- [ ] **Step 2: Implement JWT token service**

Required methods:
- `String createAccessToken(UUID userId, String email, Set<UserRole> roles)`
- `JwtPrincipal parse(String token)`

`JwtPrincipal` fields:
- `UUID userId`
- `String email`
- `Set<String> roles`

- [ ] **Step 3: Test JWT service**

Test cases:
- created token parses back to the same user ID, email, and role.
- expired token is rejected.
- token signed with a different secret is rejected.

- [ ] **Step 4: Implement AuthService**

Methods:
- `AuthResult register(RegisterRequest request)`
- `AuthResult login(LoginRequest request)`
- `AuthResult refresh(RefreshRequest request)`
- `void logout(LogoutRequest request)`
- `CurrentUserResponse me(UUID userId)`

Rules:
- register rejects duplicate email.
- login rejects invalid password.
- refresh rotates refresh token by revoking the old token and storing a new token hash.
- logout revokes the submitted refresh token.

- [ ] **Step 5: Test AuthService**

Test cases:
- register creates enabled credential with `CUSTOMER` role.
- duplicate register fails.
- login returns access and refresh tokens.
- refresh revokes old refresh token.
- logout revokes refresh token.

- [ ] **Step 6: Run tests**

Run: `mvn -q -pl auth-service test -Dtest=JwtTokenServiceTest,AuthServiceTest`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add auth-service
git commit -m "feat: implement auth token use cases"
```

### Task 5: Expose Auth REST API

**Files:**
- Create: `auth-service/src/main/java/com/ecom/auth/web/AuthController.java`
- Create: `auth-service/src/main/java/com/ecom/auth/web/dto/RegisterRequest.java`
- Create: `auth-service/src/main/java/com/ecom/auth/web/dto/LoginRequest.java`
- Create: `auth-service/src/main/java/com/ecom/auth/web/dto/RefreshRequest.java`
- Create: `auth-service/src/main/java/com/ecom/auth/web/dto/LogoutRequest.java`
- Create: `auth-service/src/main/java/com/ecom/auth/web/dto/TokenResponse.java`
- Create: `auth-service/src/main/java/com/ecom/auth/web/dto/CurrentUserResponse.java`
- Create: `auth-service/src/test/java/com/ecom/auth/web/AuthControllerIT.java`

- [ ] **Step 1: Define DTO validation**

Rules:
- email fields use `@Email` and `@NotBlank`.
- password uses `@Size(min = 8, max = 72)`.
- refresh token uses `@NotBlank`.

- [ ] **Step 2: Create controller endpoints**

Expose:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

- [ ] **Step 3: Add controller integration tests**

Test through `MockMvc`:
- register returns `201 Created` and token body.
- login returns `200 OK` and token body.
- invalid login returns `401 Unauthorized`.
- refresh returns a new access token.
- logout returns `204 No Content`.

- [ ] **Step 4: Run controller tests**

Run: `mvn -q -pl auth-service test -Dtest=AuthControllerIT`

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add auth-service
git commit -m "feat: expose auth api"
```

### Task 6: Create User Service Skeleton and Schema

**Files:**
- Create: `user-service/pom.xml`
- Create: `user-service/src/main/java/com/ecom/user/UserServiceApplication.java`
- Create: `user-service/src/main/resources/application.yml`
- Create: `user-service/src/main/resources/db/migration/V1__create_user_tables.sql`
- Create: `user-service/src/test/java/com/ecom/user/UserServiceApplicationTest.java`

- [ ] **Step 1: Create User Service Maven module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql`
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test`
- `testcontainers-postgresql`

- [ ] **Step 2: Create application class**

```java
package com.ecom.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create user schema migration**

```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30),
    preferences JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    recipient_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(120) NOT NULL,
    district VARCHAR(120) NOT NULL,
    postal_code VARCHAR(20),
    is_default BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

- [ ] **Step 4: Add context test**

```java
package com.ecom.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run user service test**

Run: `mvn -q -pl user-service test`

Expected: context test passes once test datasource properties are present.

- [ ] **Step 6: Commit**

```bash
git add user-service pom.xml
git commit -m "feat: add user service skeleton"
```

### Task 7: Implement User Profile and Address Use Cases

**Files:**
- Create: `user-service/src/main/java/com/ecom/user/domain/UserProfile.java`
- Create: `user-service/src/main/java/com/ecom/user/domain/UserAddress.java`
- Create: `user-service/src/main/java/com/ecom/user/repository/UserProfileRepository.java`
- Create: `user-service/src/main/java/com/ecom/user/repository/UserAddressRepository.java`
- Create: `user-service/src/main/java/com/ecom/user/service/UserProfileService.java`
- Create: `user-service/src/test/java/com/ecom/user/service/UserProfileServiceTest.java`

- [ ] **Step 1: Implement profile entity**

Fields:
- `UUID id`
- `String email`
- `String displayName`
- `String phone`
- `String preferences`
- `Instant createdAt`
- `Instant updatedAt`

- [ ] **Step 2: Implement address entity**

Fields:
- `UUID id`
- `UUID userId`
- `String recipientName`
- `String phone`
- `String line1`
- `String line2`
- `String city`
- `String district`
- `String postalCode`
- `boolean defaultAddress`
- `Instant createdAt`
- `Instant updatedAt`

- [ ] **Step 3: Implement repositories**

`UserProfileRepository` methods:
- `Optional<UserProfile> findByEmail(String email)`
- `boolean existsByEmail(String email)`

`UserAddressRepository` methods:
- `List<UserAddress> findByUserId(UUID userId)`
- `Optional<UserAddress> findByIdAndUserId(UUID id, UUID userId)`

- [ ] **Step 4: Implement service methods**

Methods:
- `UserProfile getOrCreateProfile(UUID userId, String email)`
- `UserProfile updateProfile(UUID userId, UpdateProfileRequest request)`
- `List<UserAddress> listAddresses(UUID userId)`
- `UserAddress addAddress(UUID userId, CreateAddressRequest request)`
- `UserAddress updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request)`
- `void deleteAddress(UUID userId, UUID addressId)`

- [ ] **Step 5: Add service tests**

Test cases:
- first access creates a profile from authenticated user ID and email.
- update profile changes display name and phone.
- adding first address marks it default.
- setting a second address default clears default on the first address.
- user cannot update another user's address.

- [ ] **Step 6: Run service tests**

Run: `mvn -q -pl user-service test -Dtest=UserProfileServiceTest`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add user-service
git commit -m "feat: implement user profile use cases"
```

### Task 8: Expose User REST API

**Files:**
- Create: `user-service/src/main/java/com/ecom/user/security/GatewayUserContextFilter.java`
- Create: `user-service/src/main/java/com/ecom/user/web/UserProfileController.java`
- Create: `user-service/src/main/java/com/ecom/user/web/dto/ProfileResponse.java`
- Create: `user-service/src/main/java/com/ecom/user/web/dto/UpdateProfileRequest.java`
- Create: `user-service/src/main/java/com/ecom/user/web/dto/AddressResponse.java`
- Create: `user-service/src/main/java/com/ecom/user/web/dto/CreateAddressRequest.java`
- Create: `user-service/src/main/java/com/ecom/user/web/dto/UpdateAddressRequest.java`
- Create: `user-service/src/test/java/com/ecom/user/web/UserProfileControllerIT.java`

- [ ] **Step 1: Read authenticated context headers**

Gateway will forward:
- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`

The User Service filter must reject `/api/users/**` requests missing `X-User-Id` or `X-User-Email`.

- [ ] **Step 2: Create controller endpoints**

Expose:
- `GET /api/users/me`
- `PUT /api/users/me`
- `GET /api/users/me/addresses`
- `POST /api/users/me/addresses`
- `PUT /api/users/me/addresses/{addressId}`
- `DELETE /api/users/me/addresses/{addressId}`

- [ ] **Step 3: Add controller integration tests**

Test through `MockMvc`:
- missing user headers returns `401 Unauthorized`.
- `GET /api/users/me` creates and returns profile.
- `PUT /api/users/me` updates profile.
- address create/list/update/delete works for the current user.

- [ ] **Step 4: Run controller tests**

Run: `mvn -q -pl user-service test -Dtest=UserProfileControllerIT`

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add user-service
git commit -m "feat: expose user profile api"
```

### Task 9: Add Gateway JWT Validation

**Files:**
- Modify: `api-gateway/pom.xml`
- Create: `api-gateway/src/main/java/com/ecom/gateway/security/JwtProperties.java`
- Create: `api-gateway/src/main/java/com/ecom/gateway/security/JwtAuthenticationFilter.java`
- Create: `api-gateway/src/main/java/com/ecom/gateway/security/PublicRouteMatcher.java`
- Create: `api-gateway/src/test/java/com/ecom/gateway/security/JwtAuthenticationFilterTest.java`
- Modify: `config-repo/api-gateway.yml`

- [ ] **Step 1: Add JWT dependency to Gateway**

Add `com.nimbusds:nimbus-jose-jwt`.

- [ ] **Step 2: Add JWT config**

`config-repo/api-gateway.yml`:

```yaml
security:
  jwt:
    issuer: ecom-auth-service
    secret: local-development-secret-must-be-32-bytes
  public-paths:
    - /api/auth/register
    - /api/auth/login
    - /api/auth/refresh
    - /actuator/health
    - /v3/api-docs
    - /swagger-ui
```

- [ ] **Step 3: Implement public route matcher**

Rules:
- exact match for `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/actuator/health`.
- prefix match for `/v3/api-docs` and `/swagger-ui`.

- [ ] **Step 4: Implement JWT authentication filter**

Behavior:
- public route proceeds without token.
- protected route without `Authorization: Bearer <token>` returns `401`.
- invalid token returns `401`.
- valid token mutates downstream request with `X-User-Id`, `X-User-Email`, `X-User-Roles`.

- [ ] **Step 5: Add Gateway security tests**

Test cases:
- public route does not require token.
- protected route without token returns `401`.
- valid token forwards user headers.
- invalid token returns `401`.

- [ ] **Step 6: Run Gateway tests**

Run: `mvn -q -pl api-gateway test -Dtest=JwtAuthenticationFilterTest`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add api-gateway config-repo/api-gateway.yml
git commit -m "feat: validate jwt at gateway"
```

### Task 10: End-to-End Identity Verification

**Files:**
- Modify: `README.md`
- Verify: Auth Service, User Service, API Gateway, Config Server, Discovery Server.

- [ ] **Step 1: Run full tests**

Run: `mvn -B test`

Expected: all modules pass.

- [ ] **Step 2: Start dependencies**

Run: `docker compose up -d`

Expected: PostgreSQL databases, Redis, Kafka, Zipkin, Prometheus, and Grafana are running.

- [ ] **Step 3: Start services**

Open five terminals:

```bash
mvn spring-boot:run -pl discovery-server
mvn spring-boot:run -pl config-server
mvn spring-boot:run -pl api-gateway
mvn spring-boot:run -pl auth-service
mvn spring-boot:run -pl user-service
```

Expected:
- Auth Service registers as `AUTH-SERVICE`.
- User Service registers as `USER-SERVICE`.
- API Gateway registers as `API-GATEWAY`.

- [ ] **Step 4: Register user through Gateway**

Run:

```bash
curl -sS -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@example.com","password":"Password123!","displayName":"Demo Customer"}'
```

Expected: response contains `accessToken` and `refreshToken`.

- [ ] **Step 5: Login through Gateway**

Run:

```bash
curl -sS -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@example.com","password":"Password123!"}'
```

Expected: response contains a fresh `accessToken`.

- [ ] **Step 6: Reject unauthenticated profile request**

Run:

```bash
curl -i http://localhost:8080/api/users/me
```

Expected: `HTTP/1.1 401 Unauthorized`.

- [ ] **Step 7: Read profile with token**

Run:

```bash
ACCESS_TOKEN="<token from login response>"
curl -sS http://localhost:8080/api/users/me -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: response contains `customer@example.com`.

- [ ] **Step 8: Create address with token**

Run:

```bash
curl -sS -X POST http://localhost:8080/api/users/me/addresses \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"recipientName":"Demo Customer","phone":"0900000000","line1":"1 Main Street","city":"Ho Chi Minh City","district":"District 1","postalCode":"700000","defaultAddress":true}'
```

Expected: response contains an address ID and `defaultAddress: true`.

- [ ] **Step 9: Update README with identity flow**

Document the register, login, profile, and address curl commands.

- [ ] **Step 10: Commit**

```bash
git add README.md
git commit -m "docs: document identity slice verification"
```

## Self-Review

- Spec coverage: Phase 2 covers Auth Service register/login/refresh/logout/RBAC, User Service profile/address APIs, Gateway JWT validation, separate databases, service discovery, config, OpenAPI readiness, and end-to-end identity flow.
- Placeholder scan: No unspecified future work is required for the Phase 2 acceptance criteria.
- Type consistency: JWT claims, forwarded headers, service names, route paths, database names, and DTO responsibilities are consistent across Auth, Gateway, and User Service tasks.
