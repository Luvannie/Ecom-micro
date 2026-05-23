# Phase 3 Catalog and Cart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Product Service and Cart Service so authenticated customers can browse products, search categories, and manage a Redis-backed shopping cart through the API Gateway.

**Architecture:** Product Service owns catalog data in PostgreSQL and uses Redis for read-through cache on product detail and product list queries. Cart Service owns per-user cart state in Redis, validates product snapshots through Product Service, and exposes cart APIs only for authenticated customers through Gateway-forwarded user headers.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Web MVC, Spring Data JPA, PostgreSQL, Flyway, Redis, Spring Cache, Spring Cloud OpenFeign, Eureka Client, Springdoc OpenAPI, JUnit 5, Testcontainers.

---

## File Structure

- Modify `pom.xml`: add `product-service` and `cart-service` modules.
- Modify `docker-compose.yml`: add `product-db`; keep Redis shared for Product cache and Cart state.
- Modify `config-repo/api-gateway.yml`: add `/api/products/**`, `/api/categories/**`, and `/api/cart/**` routes.
- Create `config-repo/product-service.yml`: Product Service datasource, Redis cache, service port.
- Create `config-repo/cart-service.yml`: Cart Service Redis, Product Service client, service port.
- Create `product-service/pom.xml`: Product Service dependencies.
- Create `product-service/src/main/java/com/ecom/product/ProductServiceApplication.java`: entrypoint.
- Create `product-service/src/main/resources/db/migration/V1__create_catalog_tables.sql`: category/product schema.
- Create `product-service/src/main/java/com/ecom/product/domain/Category.java`: category aggregate.
- Create `product-service/src/main/java/com/ecom/product/domain/Product.java`: product aggregate.
- Create `product-service/src/main/java/com/ecom/product/repository/CategoryRepository.java`: category persistence.
- Create `product-service/src/main/java/com/ecom/product/repository/ProductRepository.java`: product persistence and search.
- Create `product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java`: catalog use cases.
- Create `product-service/src/main/java/com/ecom/product/web/ProductController.java`: public product APIs.
- Create `product-service/src/main/java/com/ecom/product/web/AdminProductController.java`: admin catalog management APIs.
- Create `product-service/src/main/java/com/ecom/product/web/dto/*.java`: product/category DTOs.
- Create `cart-service/pom.xml`: Cart Service dependencies.
- Create `cart-service/src/main/java/com/ecom/cart/CartServiceApplication.java`: entrypoint.
- Create `cart-service/src/main/java/com/ecom/cart/domain/Cart.java`: cart aggregate.
- Create `cart-service/src/main/java/com/ecom/cart/domain/CartItem.java`: cart item value object.
- Create `cart-service/src/main/java/com/ecom/cart/client/ProductClient.java`: Product Service client.
- Create `cart-service/src/main/java/com/ecom/cart/repository/CartRepository.java`: Redis cart persistence.
- Create `cart-service/src/main/java/com/ecom/cart/service/CartService.java`: cart use cases.
- Create `cart-service/src/main/java/com/ecom/cart/web/CartController.java`: authenticated cart APIs.
- Create `cart-service/src/main/java/com/ecom/cart/web/dto/*.java`: cart DTOs.

## API Contract

### Product Service

- `GET /api/categories`: list active categories.
- `GET /api/products`: search products with `keyword`, `categoryId`, `minPrice`, `maxPrice`, `page`, `size`.
- `GET /api/products/{productId}`: get product detail.
- `POST /api/admin/categories`: create category, requires `ADMIN`.
- `POST /api/admin/products`: create product, requires `ADMIN`.
- `PUT /api/admin/products/{productId}`: update product, requires `ADMIN`.
- `PATCH /api/admin/products/{productId}/status`: activate/deactivate product, requires `ADMIN`.

### Cart Service

- `GET /api/cart`: get current user's cart.
- `POST /api/cart/items`: add item by product ID and quantity.
- `PUT /api/cart/items/{productId}`: update quantity.
- `DELETE /api/cart/items/{productId}`: remove item.
- `DELETE /api/cart`: clear cart.

## Security Rules

- Product read APIs are public.
- Admin product APIs require `ADMIN` role from `X-User-Roles`.
- Cart APIs require `X-User-Id` and `X-User-Email`.
- Cart Service never trusts price from client; it stores product name and price from Product Service snapshots.

## Phase Acceptance Criteria

- `mvn test` passes from repository root.
- Product Service runs on `http://localhost:8083`.
- Cart Service runs on `http://localhost:8084`.
- Gateway routes Product and Cart APIs through `http://localhost:8080`.
- Product CRUD, category list, product search, and product detail work.
- Product detail and search cache use Redis with explicit eviction on product update.
- Cart add/update/remove/clear works for authenticated users.
- Cart item price is sourced from Product Service, not request body.

### Task 1: Extend Build, Config, Compose, and Gateway Routes

**Files:**
- Modify: `pom.xml`
- Modify: `docker-compose.yml`
- Modify: `config-repo/api-gateway.yml`
- Create: `config-repo/product-service.yml`
- Create: `config-repo/cart-service.yml`

- [ ] **Step 1: Add Maven modules**

Add to root `pom.xml`:

```xml
<module>product-service</module>
<module>cart-service</module>
```

- [ ] **Step 2: Add Product database**

Add `product-db` to `docker-compose.yml`:

```yaml
product-db:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: product_service
    POSTGRES_USER: product_user
    POSTGRES_PASSWORD: product_password
  ports:
    - "5435:5432"
```

- [ ] **Step 3: Add Product Service config**

`config-repo/product-service.yml`:

```yaml
server:
  port: 8083

spring:
  application:
    name: product-service
  datasource:
    url: jdbc:postgresql://localhost:5435/product_service
    username: product_user
    password: product_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379
  cache:
    type: redis
```

- [ ] **Step 4: Add Cart Service config**

`config-repo/cart-service.yml`:

```yaml
server:
  port: 8084

spring:
  application:
    name: cart-service
  data:
    redis:
      host: localhost
      port: 6379

clients:
  product-service:
    name: product-service
```

- [ ] **Step 5: Add Gateway routes**

Add route entries:

```yaml
- id: product-service-products
  uri: lb://product-service
  predicates:
    - Path=/api/products/**
- id: product-service-categories
  uri: lb://product-service
  predicates:
    - Path=/api/categories/**
- id: product-service-admin
  uri: lb://product-service
  predicates:
    - Path=/api/admin/products/**,/api/admin/categories/**
- id: cart-service
  uri: lb://cart-service
  predicates:
    - Path=/api/cart/**
```

- [ ] **Step 6: Run validation**

Run: `mvn -q validate`

Expected: Maven fails until the new modules are created.

- [ ] **Step 7: Commit**

```bash
git add pom.xml docker-compose.yml config-repo
git commit -m "chore: prepare catalog and cart modules"
```

### Task 2: Create Product Service Skeleton and Schema

**Files:**
- Create: `product-service/pom.xml`
- Create: `product-service/src/main/java/com/ecom/product/ProductServiceApplication.java`
- Create: `product-service/src/main/resources/application.yml`
- Create: `product-service/src/main/resources/db/migration/V1__create_catalog_tables.sql`
- Create: `product-service/src/test/java/com/ecom/product/ProductServiceApplicationTest.java`

- [ ] **Step 1: Create Product Service Maven module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-cache`
- `spring-boot-starter-validation`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql`
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test`
- `testcontainers-postgresql`
- `testcontainers-junit-jupiter`

- [ ] **Step 2: Create application class**

```java
package com.ecom.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create local fallback config**

```yaml
spring:
  application:
    name: product-service
  config:
    import: optional:configserver:http://localhost:8888

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

- [ ] **Step 4: Create catalog schema**

```sql
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id),
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    image_url VARCHAR(500),
    active BOOLEAN NOT NULL,
    promotion_tag VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_name_lower ON products(LOWER(name));
```

- [ ] **Step 5: Add context test**

```java
package com.ecom.product;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProductServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: Run test**

Run: `mvn -q -pl product-service test`

Expected: context test passes after test datasource properties are added with repository tests.

- [ ] **Step 7: Commit**

```bash
git add product-service pom.xml
git commit -m "feat: add product service skeleton"
```

### Task 3: Implement Product Catalog Domain and Use Cases

**Files:**
- Create: `product-service/src/main/java/com/ecom/product/domain/Category.java`
- Create: `product-service/src/main/java/com/ecom/product/domain/Product.java`
- Create: `product-service/src/main/java/com/ecom/product/repository/CategoryRepository.java`
- Create: `product-service/src/main/java/com/ecom/product/repository/ProductRepository.java`
- Create: `product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java`
- Create: `product-service/src/test/java/com/ecom/product/service/ProductCatalogServiceTest.java`

- [ ] **Step 1: Implement entities**

`Category` fields: `UUID id`, `String name`, `String slug`, `boolean active`, `Instant createdAt`, `Instant updatedAt`.

`Product` fields: `UUID id`, `Category category`, `String name`, `String slug`, `String description`, `BigDecimal price`, `String imageUrl`, `boolean active`, `String promotionTag`, `Instant createdAt`, `Instant updatedAt`.

- [ ] **Step 2: Implement repositories**

`CategoryRepository` methods:
- `List<Category> findByActiveTrueOrderByNameAsc()`
- `boolean existsBySlug(String slug)`

`ProductRepository` methods:
- `Page<Product> findByActiveTrue(Pageable pageable)`
- `Optional<Product> findByIdAndActiveTrue(UUID id)`
- custom search using `JpaSpecificationExecutor<Product>`.

- [ ] **Step 3: Implement service methods**

Methods:
- `CategoryResponse createCategory(CreateCategoryRequest request)`
- `List<CategoryResponse> listActiveCategories()`
- `ProductResponse createProduct(CreateProductRequest request)`
- `ProductResponse updateProduct(UUID productId, UpdateProductRequest request)`
- `ProductResponse changeStatus(UUID productId, boolean active)`
- `Page<ProductSummaryResponse> search(ProductSearchCriteria criteria, Pageable pageable)`
- `ProductResponse getProduct(UUID productId)`

- [ ] **Step 4: Add cache rules**

Use:
- `@Cacheable(cacheNames = "product-detail", key = "#productId")` on `getProduct`.
- `@Cacheable(cacheNames = "product-search", key = "#criteria.toCacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")` on `search`.
- `@CacheEvict(cacheNames = {"product-detail", "product-search"}, allEntries = true)` on product create/update/status change.

- [ ] **Step 5: Add service tests**

Test cases:
- create category rejects duplicate slug.
- create product requires existing category.
- search returns only active products.
- inactive product detail returns not found.
- update product evicts cache by verifying repository is called again after update.

- [ ] **Step 6: Run tests**

Run: `mvn -q -pl product-service test -Dtest=ProductCatalogServiceTest`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add product-service
git commit -m "feat: implement product catalog use cases"
```

### Task 4: Expose Product and Admin Catalog APIs

**Files:**
- Create: `product-service/src/main/java/com/ecom/product/security/GatewayRoleFilter.java`
- Create: `product-service/src/main/java/com/ecom/product/web/ProductController.java`
- Create: `product-service/src/main/java/com/ecom/product/web/AdminProductController.java`
- Create: `product-service/src/main/java/com/ecom/product/web/dto/CreateCategoryRequest.java`
- Create: `product-service/src/main/java/com/ecom/product/web/dto/CreateProductRequest.java`
- Create: `product-service/src/main/java/com/ecom/product/web/dto/UpdateProductRequest.java`
- Create: `product-service/src/main/java/com/ecom/product/web/dto/ProductResponse.java`
- Create: `product-service/src/main/java/com/ecom/product/web/dto/ProductSummaryResponse.java`
- Create: `product-service/src/main/java/com/ecom/product/web/dto/CategoryResponse.java`
- Create: `product-service/src/test/java/com/ecom/product/web/ProductControllerIT.java`
- Create: `product-service/src/test/java/com/ecom/product/web/AdminProductControllerIT.java`

- [ ] **Step 1: Add admin role filter**

For paths `/api/admin/**`, require `X-User-Roles` contains `ADMIN`; otherwise return `403 Forbidden`.

- [ ] **Step 2: Add public product endpoints**

Expose:
- `GET /api/categories`
- `GET /api/products`
- `GET /api/products/{productId}`

- [ ] **Step 3: Add admin endpoints**

Expose:
- `POST /api/admin/categories`
- `POST /api/admin/products`
- `PUT /api/admin/products/{productId}`
- `PATCH /api/admin/products/{productId}/status`

- [ ] **Step 4: Add integration tests**

Test cases:
- public category list returns active categories.
- public product search supports keyword and category filter.
- public product detail returns `404` for inactive product.
- admin create product without `ADMIN` role returns `403`.
- admin create product with `ADMIN` role returns `201`.

- [ ] **Step 5: Run tests**

Run: `mvn -q -pl product-service test -Dtest=ProductControllerIT,AdminProductControllerIT`

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add product-service
git commit -m "feat: expose catalog APIs"
```

### Task 5: Create Cart Service Skeleton

**Files:**
- Create: `cart-service/pom.xml`
- Create: `cart-service/src/main/java/com/ecom/cart/CartServiceApplication.java`
- Create: `cart-service/src/main/resources/application.yml`
- Create: `cart-service/src/test/java/com/ecom/cart/CartServiceApplicationTest.java`

- [ ] **Step 1: Create Cart Service Maven module**

Dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-validation`
- `spring-cloud-starter-openfeign`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `springdoc-openapi-starter-webmvc-ui`
- `spring-boot-starter-test`
- `testcontainers-junit-jupiter`

- [ ] **Step 2: Create application class**

```java
package com.ecom.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create local fallback config**

```yaml
spring:
  application:
    name: cart-service
  config:
    import: optional:configserver:http://localhost:8888

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

- [ ] **Step 4: Add context test**

```java
package com.ecom.cart;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CartServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn -q -pl cart-service test`

Expected: context test passes.

- [ ] **Step 6: Commit**

```bash
git add cart-service pom.xml
git commit -m "feat: add cart service skeleton"
```

### Task 6: Implement Cart Domain, Redis Repository, and Product Client

**Files:**
- Create: `cart-service/src/main/java/com/ecom/cart/domain/Cart.java`
- Create: `cart-service/src/main/java/com/ecom/cart/domain/CartItem.java`
- Create: `cart-service/src/main/java/com/ecom/cart/client/ProductClient.java`
- Create: `cart-service/src/main/java/com/ecom/cart/client/ProductSnapshot.java`
- Create: `cart-service/src/main/java/com/ecom/cart/repository/CartRepository.java`
- Create: `cart-service/src/main/java/com/ecom/cart/service/CartService.java`
- Create: `cart-service/src/test/java/com/ecom/cart/service/CartServiceTest.java`

- [ ] **Step 1: Define cart model**

`Cart` fields: `UUID userId`, `List<CartItem> items`, `Instant updatedAt`.

`CartItem` fields: `UUID productId`, `String productName`, `BigDecimal unitPrice`, `int quantity`, `String imageUrl`.

Computed values:
- item subtotal = `unitPrice * quantity`.
- cart total = sum of item subtotals.

- [ ] **Step 2: Implement Product client**

`ProductClient`:
- `GET /api/products/{productId}`
- returns `ProductSnapshot` with `id`, `name`, `price`, `imageUrl`, `active`.

- [ ] **Step 3: Implement Redis repository**

Key format: `cart:{userId}`.

Methods:
- `Optional<Cart> findByUserId(UUID userId)`
- `Cart save(Cart cart)`
- `void deleteByUserId(UUID userId)`

TTL: 30 days from last update.

- [ ] **Step 4: Implement CartService**

Methods:
- `Cart getCart(UUID userId)`
- `Cart addItem(UUID userId, UUID productId, int quantity)`
- `Cart updateQuantity(UUID userId, UUID productId, int quantity)`
- `Cart removeItem(UUID userId, UUID productId)`
- `void clear(UUID userId)`

Rules:
- quantity must be between 1 and 99.
- add existing item increments quantity and caps at 99.
- update quantity `0` removes item.
- product snapshot is refreshed on add and update.

- [ ] **Step 5: Add service tests**

Test cases:
- add item stores product name and price from Product Service.
- add same product increments quantity.
- update quantity to zero removes the item.
- clear deletes Redis cart key.
- invalid quantity returns validation error.

- [ ] **Step 6: Run tests**

Run: `mvn -q -pl cart-service test -Dtest=CartServiceTest`

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add cart-service
git commit -m "feat: implement redis-backed cart"
```

### Task 7: Expose Cart API

**Files:**
- Create: `cart-service/src/main/java/com/ecom/cart/security/GatewayUserContextFilter.java`
- Create: `cart-service/src/main/java/com/ecom/cart/web/CartController.java`
- Create: `cart-service/src/main/java/com/ecom/cart/web/dto/AddCartItemRequest.java`
- Create: `cart-service/src/main/java/com/ecom/cart/web/dto/UpdateCartItemRequest.java`
- Create: `cart-service/src/main/java/com/ecom/cart/web/dto/CartResponse.java`
- Create: `cart-service/src/main/java/com/ecom/cart/web/dto/CartItemResponse.java`
- Create: `cart-service/src/test/java/com/ecom/cart/web/CartControllerIT.java`

- [ ] **Step 1: Read authenticated user headers**

Require:
- `X-User-Id`
- `X-User-Email`

Missing headers return `401 Unauthorized`.

- [ ] **Step 2: Expose endpoints**

Create:
- `GET /api/cart`
- `POST /api/cart/items`
- `PUT /api/cart/items/{productId}`
- `DELETE /api/cart/items/{productId}`
- `DELETE /api/cart`

- [ ] **Step 3: Add controller tests**

Test cases:
- unauthenticated request returns `401`.
- add item returns cart total.
- update item changes quantity.
- remove item deletes item.
- clear returns `204`.

- [ ] **Step 4: Run tests**

Run: `mvn -q -pl cart-service test -Dtest=CartControllerIT`

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add cart-service
git commit -m "feat: expose cart api"
```

### Task 8: End-to-End Catalog and Cart Verification

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
```

Expected: Product and Cart register in Eureka.

- [ ] **Step 3: Create product as admin**

Run:

```bash
ADMIN_TOKEN="<admin access token>"
curl -sS -X POST http://localhost:8080/api/admin/categories \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"Pizza","slug":"pizza"}'
```

Expected: category response contains `pizza`.

- [ ] **Step 4: Search products**

Run:

```bash
curl -sS "http://localhost:8080/api/products?keyword=pizza&page=0&size=10"
```

Expected: response contains matching active products.

- [ ] **Step 5: Add product to cart**

Run:

```bash
ACCESS_TOKEN="<customer access token>"
PRODUCT_ID="<product id>"
curl -sS -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":2}"
```

Expected: cart response contains the product and quantity `2`.

- [ ] **Step 6: Document verification commands**

Add Product and Cart curl commands to `README.md`.

- [ ] **Step 7: Commit**

```bash
git add README.md
git commit -m "docs: document catalog and cart verification"
```

## Self-Review

- Spec coverage: Phase 3 covers Product CRUD, category APIs, search, price/promotion fields, Redis cache, Cart add/remove/update/clear, Gateway routes, and authenticated cart ownership.
- Placeholder scan: No unresolved placeholders are present; every task has concrete files, commands, and expected behavior.
- Type consistency: Service names, ports, route paths, Redis keys, DTO responsibilities, and security headers align with Phase 2.
