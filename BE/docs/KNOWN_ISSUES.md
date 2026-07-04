# Known Issues — Keycloak Migration

> **Source of truth**: issues found while running `docker compose up` +
> `mvn spring-boot:run` + E2E walkthrough for the Keycloak migration
> (Phases 0–3) on 2026-07-05.
> **Companion doc**: [`keycloak-migration.md`](keycloak-migration.md) —
> full migration runbook.
> **Format**: each issue has Symptom · Root cause · Severity · Fix ·
> Status · Verification.

---

## Summary table

| # | Issue | Severity | Status | Commit |
|---|-------|----------|--------|--------|
| A | `accessPolicy` field rejected by Keycloak 26 | **P0** | ✅ Fixed | `e080a78` |
| B | `passwordPolicy` string causes i18n error | **P0** | ✅ Fixed (workaround) | `e080a78` |
| C | Port 8081 conflict (Keycloak vs auth-service) | **P1** | ✅ Fixed | `e080a78` |
| D | YAML `${...:?msg}` syntax broken | **P0** | ✅ Fixed | `e080a78` |
| E | `directAccessGrantsEnabled: false` blocks ROPG | **P2** | 📝 Documented | — |
| F | `roles` OIDC scope missing from token | **P0** | ✅ Fixed | `e080a78` |
| G | `ecom-backend` client secret was placeholder | **P1** | ✅ Fixed | `e080a78` |
| H | No integration test for gateway + Keycloak | **P2** | ⏳ Open | — |
| I | Header spoofing (Q13 from `qa-scenarios.html`) | **P0** | 📝 Deferred | — |
| J | `pkill` not available on Windows | cosmetic | 📝 Note | — |

Legend: **P0** = blocks production · **P1** = workaround exists · **P2** = nice-to-have · ✅ Fixed · 📝 Documented · ⏳ Open.

---

## Issue A — `accessPolicy` field rejected by Keycloak 26

- **Symptom**: Keycloak container restart loop. Log shows:
  ```
  ERROR Unrecognized field "accessPolicy" (class RealmRepresentation),
  not marked as ignorable
  ```
- **Root cause**: The `accessPolicy.inferFromClientComponents` field was
  removed in newer Keycloak versions; the realm JSON still contained it.
- **Fix**: Delete the `accessPolicy` block from `BE/infra/keycloak/realm-ecom.json`.
- **Verification**:
  ```bash
  docker compose logs keycloak --tail=50 | grep -i error
  # → no output (clean start)
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8089/realms/ecom/.well-known/openid-configuration
  # → 200
  ```

---

## Issue B — `passwordPolicy` string causes import failure

- **Symptom**: Keycloak fails to start with:
  ```
  ERROR invalidPasswordMinSpecialCharsMessage
  ```
- **Root cause**: The `passwordPolicy: "length(8) and lowerCase(1) ..."` string,
  combined with the default message bundle, produced an unresolvable i18n
  key in Keycloak 26.0. (Likely a missing translation in the new release —
  the message key itself does not exist in `messages_*.properties`.)
- **Workaround applied**: **remove `passwordPolicy` entirely** from the
  realm JSON. Default Keycloak password policy (length ≥ 8) still applies.
  To add a stronger policy later, set it via the admin console after
  first start, not via realm import.
- **Follow-up**: investigate the correct property name for the new
  message bundle, then re-add via the Keycloak UI and export.
- **Verification**:
  ```bash
  curl -s -X POST http://localhost:8089/realms/ecom/protocol/openid-connect/token \
    -d "username=customer1&password=customer1&grant_type=password&client_id=ecom-frontend"
  # → 200 with access_token
  ```

---

## Issue C — Port 8081 conflict (Keycloak vs auth-service)

- **Symptom**: auth-service cannot start because Keycloak already binds
  8081.
- **Root cause**: Both defaulted to 8081 in `BE/config-repo/auth-service.yml`
  (`server.port: 8081`) and `KEYCLOAK_PORT` env var (default 8081).
- **Fix**: Move Keycloak to **8089** (the Ecom auth-service stays on
  its conventional 8081). Updated:
  - `BE/.env`: `KEYCLOAK_PORT=8089`, `KEYCLOAK_ISSUER`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_TOKEN_URI`, `KEYCLOAK_LOGIN_URI` all point to `:8089`.
  - `BE/.env.example`: same.
  - `FE/.env.example`: `VITE_KEYCLOAK_URL=http://localhost:8089`.
- **Alternative considered**: move auth-service to 8082 — rejected because
  it breaks the convention of `auth-service = 8081` and may break
  discovery / health checks in the broader platform.
- **Verification**:
  ```bash
  ss -tln | grep -E ':8081|:8089'
  # both ports should be LISTENING (auth-service on 8081, Keycloak on 8089)
  ```

---

## Issue D — YAML placeholder syntax broken in `api-gateway.yml`

- **Symptom**: Gateway fails to resolve `${KEYCLOAK_JWK_SET_URI:?...}` and
  tries to fetch JWKS from the literal URL
  `?KEYCLOAK_JWK_SET_URI environment variable is required`. The gateway
  log shows:
  ```
  Caused by: java.lang.IllegalArgumentException: Unable to parse url
    [?KEYCLOAK_JWK_SET_URI%20environment%20variable%20is%20required]
  ```
- **Root cause**: Spring's `${...:?message}` default-value syntax contains
  `:` which YAML interprets as a key-value separator, splitting the value
  at the colon. Spring then sees `?KEYCLOAK_JWK_SET_URI` as the property
  name and the rest as the default.
- **Fix** (`BE/config-repo/api-gateway.yml`): wrap the whole `${...:?...}`
  in double quotes so YAML preserves the colon:
  ```yaml
  keycloak:
    jwk-set-uri: "${KEYCLOAK_JWK_SET_URI:?KEYCLOAK_JWK_SET_URI environment variable is required}"
    issuer: "${KEYCLOAK_ISSUER:?KEYCLOAK_ISSUER environment variable is required}"
  ```
- **Verification**:
  ```bash
  # Start gateway with KEYCLOAK_JWK_SET_URI unset → should fail with the
  # message "KEYCLOAK_JWK_SET_URI environment variable is required"
  # (not the URL-parse error)
  unset KEYCLOAK_JWK_SET_URI
  mvn spring-boot:run -pl api-gateway
  # → clean startup error
  ```

---

## Issue E — `directAccessGrantsEnabled: false` blocks ROPG (Documented limitation)

- **Symptom**: `curl -X POST .../token -d "grant_type=password"` returns:
  ```json
  {"error":"unauthorized_client",
   "error_description":"Client not allowed for direct access grants"}
  ```
- **Root cause**: **Intentional** for a public SPA client. ROPG must
  never be enabled on a public client (no client secret to authenticate
  the caller → anyone with the client_id could request tokens for any
  user). The correct flow is Authorization Code + PKCE.
- **Status**: 📝 Documented. This is **not a bug** — do not enable
  ROPG on `ecom-frontend`.
- **Workaround for automated tests**:
  1. **Option A (recommended)**: create a separate **confidential test
     client** in the realm with `directAccessGrantsEnabled: true`. Use
     this client only in CI / integration tests, never in production.
  2. **Option B**: run the full Authorization Code + PKCE flow with a
     headless browser (Playwright) in tests.
  3. **Option C**: extend the existing `ecom-backend` client
     (`serviceAccountsEnabled: true`) for `client_credentials` flow and
     impersonate users via Keycloak's admin API.

---

## Issue F — `roles` OIDC scope missing from token (CRITICAL)

- **Symptom**: Customer1 token returned by Keycloak **does NOT include
  `realm_access.roles`**. Decoded token claims:
  ```json
  {
    "iss": "http://localhost:8089/realms/ecom",
    "sub": "11abd351-...",
    "email": "customer1@ecom.local",
    "scope": "profile email"
    // ← MISSING: realm_access.roles
  }
  ```
- **Impact**: The gateway's `JwtAuthenticationFilter` calls
  `extractRoles(jwt)` which reads `jwt.getClaim("realm_access")`. If that
  claim is missing, `X-User-Roles` header is empty → all admin-only
  endpoints (`/api/admin/**`, `/api/payments/*/refund`) reject
  **everyone**, even Keycloak admins.
- **Root cause**: The `ecom-frontend` client had
  `defaultClientScopes: ['web-origins', 'role_list', 'profile', 'email']`
  — but the `role_list` scope had `protocol: saml` (SAML-only, ignored
  by OIDC). No OIDC scope was mapping `realm_access.roles` into the
  token.
- **Fix**: added a new `roles` client scope (protocol `openid-connect`)
  with a `oidc-usermodel-realm-role-mapper` mapper, and added it to the
  `ecom-frontend.defaultClientScopes` list:
  ```json
  {
    "name": "roles",
    "description": "OpenID Connect scope for add user realm roles to the access token",
    "protocol": "openid-connect",
    "attributes": {
      "include.in.token.scope": "true",
      "display.on.consent.screen": "true"
    },
    "mappers": [{
      "name": "realm-roles",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-realm-role-mapper",
      "config": {
        "claim.name": "realm_access.roles",
        "multivalued": "true"
      }
    }]
  }
  ```
  And in `ecom-frontend`:
  ```json
  "defaultClientScopes": ["web-origins", "roles", "profile", "email"]
  ```
- **Verification**:
  ```bash
  # Inspect via admin API
  TOKEN_ADMIN=$(curl -s -X POST \
    http://localhost:8089/realms/master/protocol/openid-connect/token \
    -d "username=admin&password=$KEYCLOAK_ADMIN_PASSWORD&grant_type=password&client_id=admin-cli" \
    | jq -r .access_token)
  curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
    "http://localhost:8089/admin/realms/ecom/client-scopes" \
    | jq '.[] | select(.name=="roles") | {name, protocol, mappers: [.mappers[].name]}'
  # → {"name":"roles","protocol":"openid-connect","mappers":["realm-roles"]}
  ```

---

## Issue G — `ecom-backend` client secret was a placeholder

- **Symptom**: `curl -X POST .../token -u "ecom-backend:$SECRET" -d
  "grant_type=client_credentials"` returns:
  ```json
  {"error":"unauthorized_client",
   "error_description":"Invalid client or Invalid client credentials"}
  ```
- **Root cause**: The realm JSON had
  `"secret": "${KEYCLOAK_BACKEND_CLIENT_SECRET:-change-me-in-env}"` —
  the placeholder literal was stored as the actual secret because
  Keycloak does not interpolate environment variables during realm
  import.
- **Fix**: Replaced with a hard-coded dev secret. **For production**:
  rotate the secret via the Keycloak admin console and inject via env
  var or secrets manager (Vault, K8s Secret, AWS Secrets Manager).
- **Verification**:
  ```bash
  curl -s -X POST http://localhost:8089/realms/ecom/protocol/openid-connect/token \
    -u "ecom-backend:dev-ecom-backend-secret-change-me-32-chars-aB3xY7" \
    -d "grant_type=client_credentials" | jq -r .access_token
  # → long JWT
  ```

---

## Issue H — No integration test for gateway + Keycloak (Open)

- **Symptom**: No automated test verifies the end-to-end
  `client → gateway → Keycloak JWKS → downstream service` flow.
- **Impact**: Regressions (e.g. forgetting to add the `roles` scope
  again, or breaking the `X-User-Roles` mapping) would only be caught
  manually.
- **Recommended fix**:
  1. Add **Testcontainers Keycloak** (`testcontainers/keycloak:26.0`) to
     `api-gateway`'s `pom.xml` test scope.
  2. Add an integration test (`JwtAuthenticationFilterIT`) that:
     - Boots a real Keycloak container with a test realm.
     - Generates an access token via `client_credentials`.
     - Sends a request to the gateway and asserts
       `X-User-Roles` header matches the realm roles.
  3. Add a similar test for `auth-service` to verify the
     `/api/auth/me` proxy.
- **Effort**: ~1 day.

---

## Issue I — Header spoofing still possible (Deferred)

- **Symptom**: `docs/qa-scenarios.html` Q13 documents that an attacker
  who can reach a downstream service directly (bypassing the gateway)
  can set `X-User-Roles: ADMIN` and gain admin access.
- **Status**: 📝 Deferred per the migration plan. The Keycloak
  migration does not fix this — the gateway still sets the headers and
  downstream services still trust them.
- **Recommended fix** (separate ticket):
  1. Add `spring-boot-starter-oauth2-resource-server` to every
     downstream service (product, inventory, payment, cart, order,
     user).
  2. Configure each service with the same `keycloak.jwk-set-uri` to
     verify the Bearer token locally.
  3. Remove the `GatewayUserContextFilter` and `GatewayRoleFilter`
     filters — let Spring Security handle auth via JWT.
  4. Keep the `X-User-*` headers as a performance optimization
     (set once by the gateway, read by the service without re-parsing
     the JWT).
- **Effort**: ~1 week (8 services to update + tests).

---

## Issue J — `pkill` not available on Windows (cosmetic)

- **Symptom**: Verification scripts using `pkill -f "spring-boot"`
  silently fail on Windows.
- **Fix**: use `taskkill //F //IM java.exe` on Windows, or
  `jps | awk '{print $1}' | xargs kill -9` cross-platform.
- **Status**: 📝 Note for docs.

---

## How to verify all fixes at once

```bash
# 1. Start infrastructure
cd BE && docker compose up -d
# wait ~80s for Keycloak to be ready
sleep 80

# 2. Verify Keycloak
curl -s -o /dev/null -w "OIDC: %{http_code}\n" \
  http://localhost:8089/realms/ecom/.well-known/openid-configuration
# expect: OIDC: 200

# 3. Verify realm + users + roles
TOKEN_ADMIN=$(curl -s -X POST http://localhost:8089/realms/master/protocol/openid-connect/token \
  -d "username=admin&password=$KEYCLOAK_ADMIN_PASSWORD&grant_type=password&client_id=admin-cli" \
  | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  http://localhost:8089/admin/realms/ecom/users?username=customer1 \
  | jq '.[0] | {id, username, email, enabled}'
# expect: id, username=customer1, email=customer1@ecom.local, enabled=true

curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  http://localhost:8089/admin/realms/ecom/client-scopes \
  | jq '.[] | select(.name=="roles") | {name, protocol, mappers: [.mappers[].name]}'
# expect: {name: "roles", protocol: "openid-connect", mappers: ["realm-roles"]}

# 4. Start backend services
mvn spring-boot:run -pl config-server,discovery-server,api-gateway &

# 5. Verify gateway auth flow
sleep 30
# (After enabling directAccessGrantsEnabled on ecom-frontend for testing)
TOKEN=$(curl -s -X POST http://localhost:8089/realms/ecom/protocol/openid-connect/token \
  -d "username=customer1&password=customer1&grant_type=password&client_id=ecom-frontend" \
  | jq -r .access_token)

# Decode the token to verify it now has realm_access.roles
echo "$TOKEN" | cut -d. -f2 | base64 -d | jq '.realm_access'
# expect: { "roles": ["customer", "default-roles-ecom"] }

# Hit the gateway with the token
curl -s -o /dev/null -w "Public: %{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/products
# expect: 200 (gateway forwards to product-service)
```

---

## Related documentation

- `BE/docs/keycloak-migration.md` — full migration runbook
- `docs/qa-scenarios.html` — Q12 (JWT secret leak, **RESOLVED**),
  Q13 (header spoofing, **DEFERRED**), Q32 (rate limiting)
- `BE/infra/keycloak/realm-ecom.json` — realm config
- Commit `e080a78` — all fixes for issues A, B, C, D, F, G
