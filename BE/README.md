# Ecom Food Delivery Microservices

Production-like Java microservices project for an e-commerce / food delivery domain.

## Services

| Service | Port | Responsibility |
| --- | ---: | --- |
| API Gateway | 8080 | Routing, CORS, correlation ID, JWT validation |
| Discovery Server | 8761 | Service discovery |
| Config Server | 8888 | Centralized configuration |
| Auth Service | 8081 | Register, login, refresh tokens, JWT issuing |
| User Service | 8082 | User profile and address APIs |
| Product Service | 8083 | Product catalog, category APIs, Redis-backed product cache |
| Cart Service | 8084 | Redis-backed authenticated customer carts |
| Inventory Service | 8085 | Product stock, reservations, release and commit flows |
| Order Service | 8086 | Checkout orchestration and order state transitions |
| Payment Service | 8087 | Payment attempts, mock webhooks, refunds, transactional outbox |
| Notification Service | 8088 | Event-driven email/SMS notification logs with mock providers |

## Local Development

```bash
docker compose up -d
mvn test
mvn spring-boot:run -pl discovery-server
mvn spring-boot:run -pl config-server
mvn spring-boot:run -pl api-gateway
mvn spring-boot:run -pl auth-service
mvn spring-boot:run -pl user-service
mvn spring-boot:run -pl product-service
mvn spring-boot:run -pl cart-service
mvn spring-boot:run -pl inventory-service
mvn spring-boot:run -pl order-service
mvn spring-boot:run -pl payment-service
mvn spring-boot:run -pl notification-service
```

## Identity Flow

```bash
curl -sS -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@example.com","password":"Password123!","displayName":"Demo Customer"}'

curl -sS -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@example.com","password":"Password123!"}'

curl -i http://localhost:8080/api/users/me

curl -sS http://localhost:8080/api/users/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

curl -sS -X POST http://localhost:8080/api/users/me/addresses \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"recipientName":"Demo Customer","phone":"0900000000","line1":"1 Main Street","city":"Ho Chi Minh City","district":"District 1","postalCode":"700000","defaultAddress":true}'
```

## Catalog and Cart Flow

```bash
curl -sS -X POST http://localhost:8080/api/admin/categories \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"Pizza","slug":"pizza"}'

curl -sS -X POST http://localhost:8080/api/admin/products \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"categoryId":"'"${CATEGORY_ID}"'","name":"Margherita Pizza","slug":"margherita-pizza","description":"Tomato, mozzarella, basil","price":12.50,"imageUrl":"https://example.com/pizza.jpg","promotionTag":"popular"}'

curl -sS "http://localhost:8080/api/categories"

curl -sS "http://localhost:8080/api/products?keyword=pizza&page=0&size=10"

curl -sS "http://localhost:8080/api/products/${PRODUCT_ID}"

curl -sS -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"productId":"'"${PRODUCT_ID}"'","quantity":2}'

curl -sS -X PUT "http://localhost:8080/api/cart/items/${PRODUCT_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"quantity":3}'

curl -sS -X DELETE "http://localhost:8080/api/cart/items/${PRODUCT_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

curl -i -X DELETE http://localhost:8080/api/cart \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

## Payment and Notification Flow

Create a payment for a reserved order:

```bash
ACCESS_TOKEN="<customer access token>"
ORDER_ID="<reserved order id>"

curl -sS -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Idempotency-Key: pay-${ORDER_ID}" \
  -H "Content-Type: application/json" \
  -d '{"orderId":"'"${ORDER_ID}"'","currency":"USD"}'
```

Send a mock provider success webhook:

```bash
PAYMENT_ID="<payment id>"
PROVIDER_PAYMENT_ID="<provider payment id>"

curl -sS -X POST http://localhost:8080/api/payments/webhooks/mock \
  -H "Content-Type: application/json" \
  -d '{"providerEventId":"evt-success-'"${PAYMENT_ID}"'","providerPaymentId":"'"${PROVIDER_PAYMENT_ID}"'","eventType":"PAYMENT_SUCCEEDED"}'
```

Send a mock provider failure webhook:

```bash
curl -sS -X POST http://localhost:8080/api/payments/webhooks/mock \
  -H "Content-Type: application/json" \
  -d '{"providerEventId":"evt-fail-'"${PAYMENT_ID}"'","providerPaymentId":"'"${PROVIDER_PAYMENT_ID}"'","eventType":"PAYMENT_FAILED","reason":"mock failure"}'
```

Expected event flow:

- `payment.succeeded` confirms the order and produces order confirmation notification logs.
- `payment.failed` cancels the order through the order flow, releasing inventory through the existing cancellation path.
- Notification Service records mock email/SMS delivery attempts for payment and order events.
