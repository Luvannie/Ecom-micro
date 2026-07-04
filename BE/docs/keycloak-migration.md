# Keycloak Migration Runbook

> **Status**: ✅ Migration complete (Phases 0–3 committed).
> **Date**: 2026-07-05
> **Plan**: `docs/superpowers/specs/2026-07-04-qa-scenarios-design.md` — see "Keycloak Integration" section.

This document captures the end-to-end Keycloak migration: what changed, how to run it locally, how to verify, and how to roll back.

---

## 1. What changed

| Component | Before (legacy HS256) | After (Keycloak OIDC) |
|-----------|----------------------|----------------------|
| **Auth source of truth** | `auth-service` Postgres (user_credentials, user_roles, refresh_tokens) | Keycloak realm `ecom` |
| **JWT algorithm** | HS256 (symmetric, shared secret) | RS256 (asymmetric, JWKS) |
| **Token issuer** | `ecom-auth-service` (custom) | `http://localhost:8081/realms/ecom` (Keycloak) |
| **User registration** | `POST /api/auth/register` (custom) | Keycloak registration page (via `keycloak.register()`) |
| **User login** | `POST /api/auth/login` (custom, BCrypt) | Keycloak login page (Authorization Code + PKCE) |
| **Refresh token** | Custom DB-backed (`refresh_tokens` table) | Keycloak refresh token (auto-managed) |
| **Logout** | `POST /api/auth/logout` (custom DB revoke) | `keycloak.logout()` → Keycloak session termination |
| **Brute force protection** | Manual rate-limit (gateway) | Built-in Keycloak (5 fails → temp lockout) |
| **Password policy** | `@Pattern` regex in `RegisterRequest` | Keycloak realm `passwordPolicy` (length, upper, lower, digit, special) |
| **Token revocation** | Only refresh token (DB) | Keycloak session/admin API |
| **FE token storage** | `localStorage` (XSS-vulnerable) | Keycloak in-memory (no XSS exposure) |

### Services affected

- **Removed**:
  - `BE/auth-service/src/main/java/com/ecom/auth/domain/{UserCredential, RefreshToken, UserRole}.java`
  - `BE/auth-service/src/main/java/com/ecom/auth/repository/`
  - `BE/auth-service/src/main/java/com/ecom/auth/service/{AuthService, AuthResult, DuplicateEmailException, InvalidCredentialsException}.java`
  - `BE/auth-service/src/main/java/com/ecom/auth/security/{JwtTokenService, JwtProperties, JwtPrincipal, InvalidJwtException}.java`
  - `BE/auth-service/src/main/java/com/ecom/auth/config/SecurityConfig.java` (replaced)
  - `BE/auth-service/src/main/resources/db/migration/V1__create_auth_tables.sql`
  - `BE/api-gateway/src/main/java/com/ecom/gateway/security/{GatewayJwtTokenService, GatewayJwtPrincipal, GatewayJwtProperties}.java`
  - `BE/auth-service/src/test/java/.../{AuthServiceTest, JwtTokenServiceTest, AuthControllerIT, UserCredentialRepositoryTest}.java`
- **Added**:
  - `BE/infra/keycloak/realm-ecom.json` — Keycloak realm export
  - `BE/api-gateway/src/main/java/com/ecom/gateway/config/KeycloakSecurityConfig.java`
  - `BE/auth-service/src/main/java/com/ecom/auth/config/KeycloakResourceServerConfig.java`
  - `FE/src/keycloak.ts`
  - `FE/src/vite-env.d.ts`
  - `FE/public/silent-check-sso.html`
  - `FE/.env.example`
- **Modified**:
  - `BE/api-gateway/.../security/JwtAuthenticationFilter.java` — now uses `ReactiveJwtDecoder`
  - `BE/api-gateway/pom.xml` — adds `spring-boot-starter-oauth2-resource-server`
  - `BE/auth-service/pom.xml` — removes JPA/Flyway/Postgres/Redis, adds `oauth2-resource-server`
  - `BE/auth-service/.../web/AuthController.java` — only `/api/auth/me` remains
  - `BE/config-repo/{api-gateway,auth-service}.yml` — replaces `security.jwt.*` with `keycloak.*`
  - `FE/src/{context/AuthContext.tsx, api.ts, main.tsx, components/ProtectedRoute.tsx, pages/{Login,Register}.tsx, App.tsx}`
  - `FE/package.json` — adds `keycloak-js@^26.2.4`
  - `BE/docker-compose.yml` — adds `keycloak` + `keycloak-db` services (port 8081 + 5440)

### Endpoints removed from Ecom API

| Endpoint | Reason |
|----------|--------|
| `POST /api/auth/register` | Replaced by Keycloak registration page |
| `POST /api/auth/login` | Replaced by Keycloak login page |
| `POST /api/auth/refresh` | Replaced by `keycloak.updateToken()` |
| `POST /api/auth/logout` | Replaced by `keycloak.logout()` |

The `auth-service` Pod is still required (it hosts `/api/auth/me`), but is now stateless and DB-free.

### Endpoints kept

| Endpoint | Behavior |
|----------|----------|
| `GET /api/auth/me` | Decodes the Keycloak JWT (verified by `oauth2ResourceServer().jwt()`) and returns `{ userId, email, roles }` where `roles` come from `realm_access.roles`. |

---

## 2. Local setup

### 2.1 Prerequisites

- Docker + Docker Compose v2
- JDK 21
- Maven 3.9+
- Node 18+ (for FE)

### 2.2 Environment variables

Copy `BE/.env.example` → `BE/.env` and fill in:

```bash
# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=your_secure_keycloak_admin_password
KEYCLOAK_DB_PASSWORD=your_secure_keycloak_db_password
KEYCLOAK_PORT=8081
KEYCLOAK_ISSUER=http://localhost:8081/realms/ecom
KEYCLOAK_JWK_SET_URI=http://localhost:8081/realms/ecom/protocol/openid-connect/certs
KEYCLOAK_TOKEN_URI=http://localhost:8081/realms/ecom/protocol/openid-connect/token
KEYCLOAK_LOGIN_URI=http://localhost:8081/realms/ecom/protocol/openid-connect/auth
KEYCLOAK_BACKEND_CLIENT_SECRET=your_secure_backend_client_secret
```

Copy `FE/.env.example` → `FE/.env`:

```bash
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=ecom
VITE_KEYCLOAK_CLIENT_ID=ecom-frontend
```

### 2.3 Start infrastructure

```bash
cd BE
docker compose up -d
```

This starts Postgres DBs, Redis, Kafka, Zipkin, Prometheus, Grafana, **Keycloak** (port 8081), and **Keycloak DB** (port 5440).

Keycloak auto-imports the `ecom` realm from `infra/keycloak/realm-ecom.json` on first start (thanks to `--import-realm`).

### 2.4 Verify Keycloak

```bash
# 1. Check realm is reachable
curl http://localhost:8081/realms/ecom/.well-known/openid-configuration

# 2. Open admin console
open http://localhost:8081
# Login: admin / $KEYCLOAK_ADMIN_PASSWORD
# Switch realm to 'ecom' in top-left dropdown
# Verify: 2 realm roles (customer, admin), 1 public client (ecom-frontend), 2 test users

# 3. Test user login (resource owner password credentials grant)
# Note: only works if 'Direct access grants' is enabled on the client
curl -X POST http://localhost:8081/realms/ecom/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=customer1&password=customer1&grant_type=password&client_id=ecom-frontend"
```

### 2.5 Start backend services

```bash
cd BE
mvn spring-boot:run -pl discovery-server  # :8761
mvn spring-boot:run -pl config-server     # :8888
mvn spring-boot:run -pl api-gateway       # :8080
mvn spring-boot:run -pl auth-service      # :8081 — wait, this conflicts with Keycloak
# ⚠️ auth-service port changed to 8089 in docker-compose? See "Port conflict" below.
```

### 2.6 Port conflict

**Keycloak uses 8081, auth-service also uses 8081.** Two options:

1. **Change Keycloak port** to e.g. 8089 (`KEYCLOAK_PORT=8089` in `.env`).
2. **Change auth-service port** to e.g. 8082 in `config-repo/auth-service.yml`.

This should be addressed before merging to production. For local dev, the recommended approach is **option 1** so that the Ecom auth-service is still on its conventional port 8081.

> **TODO**: Add `KEYCLOAK_PORT=8089` to `.env.example` and update `KEYCLOAK_ISSUER` / `KEYCLOAK_JWK_SET_URI` accordingly. This is a follow-up to the migration.

### 2.7 Start frontend

```bash
cd FE
npm install
npm run dev   # http://localhost:5173
```

---

## 3. End-to-end verification

### 3.1 Smoke test (browser)

1. Open `http://localhost:5173` → click **"Sign in"**.
2. Redirected to Keycloak login page (`http://localhost:8081/realms/ecom/protocol/openid-connect/auth?...`).
3. Login as `customer1` / `customer1`.
4. Redirected back to FE with `?code=...` in URL → `keycloak-js` exchanges code for tokens.
5. Navbar now shows the user's email and a **Logout** button.
6. Click **Cart** → load cart (empty) → **Products** → add item → **Cart** shows item.
7. Decode the access token at <https://jwt.io>: should show `RS256` signature, `sub` claim = user UUID, `email`, `realm_access.roles: ["customer", "default-roles-ecom"]`.

### 3.2 Admin role test

1. Logout, login as `admin1` / `admin1`.
2. Navbar now shows an **Admin** button (gated by `hasRole('admin')`).
3. Manually `curl -H "Authorization: Bearer <admin-token>" http://localhost:8080/api/admin/products -X POST -H "Content-Type: application/json" -d '{...}'` → should return 201 (gateway forwards `X-User-Roles: admin` → product service's `GatewayRoleFilter` permits).
4. With a customer token, the same curl should return 403.

### 3.3 Brute force test

1. Open Keycloak login page.
2. Try 5 wrong passwords for `customer1` → after the 5th attempt, Keycloak shows a "Login temporarily disabled" message.
3. Wait 60s (per `minimumQuickLoginWaitSeconds` in realm config) → can try again.

### 3.4 Token refresh test

1. In browser devtools, set `keycloak.token` expiry manually (or wait 15 min).
2. Make any API call → `keycloak-js` calls `keycloak.updateToken(30)` automatically → request succeeds.
3. Force a 401 by deleting the access token from `keycloak.token` → next API call triggers 401 handler → `updateToken` → retry → success.

### 3.5 Backend unit/integration tests

```bash
cd BE
mvn -pl api-gateway test    # 14 tests (JwtAuthenticationFilterTest + others)
mvn -pl auth-service test   # 1 test (contextLoads)
mvn -pl user-service test   # existing X-User-Id header-based tests
mvn -pl product-service test
# ... etc
```

---

## 4. Rollback strategy

If something breaks in production:

1. **Revert the auth-service and api-gateway commits** (keep Keycloak running for inspection):
   ```bash
   git revert 328f4d5 3f5735e
   ```
2. **Roll back the FE**:
   ```bash
   git revert 621d561
   ```
3. **Restart gateway + auth-service** → they fall back to the legacy HS256 path. Downstream services (`product`, `order`, ...) are unchanged and still trust `X-User-*` headers.
4. **Keep the Keycloak container running** so the admin console remains accessible for investigation. To fully remove Keycloak: `docker compose down keycloak keycloak-db` and remove the `infra/keycloak/realm-ecom.json` volume.

The legacy `AuthService.register/login/refresh` endpoints will be restored from the previous commit, so any caller still using the old flow will work again.

---

## 5. Open follow-ups (Out of scope for this migration)

These were intentionally deferred. See `docs/superpowers/specs/2026-07-04-qa-scenarios-design.md` "Out of scope" for context.

- **Port 8081 conflict** between Keycloak and auth-service (see §2.6).
- **Header spoofing** (`docs/qa-scenarios.html` Q13): downstream services still trust `X-User-Roles` header without re-verifying. Mitigated by internal network trust. To fully fix: add `spring-boot-starter-oauth2-resource-server` to every service and verify JWT in each.
- **Social login** (Google, Facebook): enable in Keycloak admin console (Identity Providers), no code change needed.
- **MFA / 2FA**: enable per-user or per-realm in Keycloak (Authentication → Required Actions).
- **Multi-realm** for env separation (dev/staging/prod): create additional realms in Keycloak; update `KEYCLOAK_ISSUER` per environment.
- **Service-to-service auth** (`client_credentials` flow): confidential client `ecom-backend` is already configured in the realm. Future services can use it to call Keycloak Admin API.
- **Keycloak cluster mode** for HA: dev uses single instance, production needs cluster + cross-DC replication.

---

## 6. Reference

- [Keycloak 26.0 docs](https://www.keycloak.org/documentation)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [keycloak-js adapter](https://www.keycloak.org/docs/latest/securing_apps/#_javascript_adapter)
- [Ecom Q&A](qa-scenarios.html) Q12 (JWT secret leak — **RESOLVED**), Q13 (header spoofing — partially mitigated)
- Tech-debt #11 (gateway secret length check) — **RESOLVED** by removing HS256 path
