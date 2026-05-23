# E-commerce / Food Delivery Microservices -- Master Plan

## 1) Mục tiêu

Xây dựng hệ thống microservice production-like để showcase năng lực
Middle Java Backend.

## 2) Kiến trúc tổng thể

Core services: - API Gateway - Auth Service - User Service - Product
Service - Cart Service - Order Service - Payment Service - Inventory
Service - Notification Service - Review Service (optional) - Admin
Service (optional)

Platform services: - Discovery Server - Config Server - Message Broker -
Monitoring / Logging stack

------------------------------------------------------------------------

## 3) Chức năng chi tiết từng service

### API Gateway

Chức năng: - Routing - JWT validation - Rate limiting - CORS -
Correlation ID - Request logging - API aggregation (optional)

Tech: - Spring Cloud Gateway - Spring Security - Redis (rate limit)

### Auth Service

Chức năng: - Register - Login - Refresh token - Logout - Forgot
password - RBAC

Tech: - Spring Boot - Spring Security - JWT - PostgreSQL - Redis

### User Service

Chức năng: - Profile - Address book - Preferences

Tech: - Spring Boot - PostgreSQL

### Product Service

Chức năng: - CRUD product - Category - Search - Price - Promotion
tagging - Product detail

Tech: - Spring Boot - PostgreSQL - Redis cache - Elasticsearch
(optional)

### Cart Service

Chức năng: - Add/remove cart item - Update quantity - Save cart state

Tech: - Spring Boot - Redis

### Inventory Service

Chức năng: - Stock - Reserve stock - Release stock - Stock audit

Tech: - Spring Boot - PostgreSQL - Kafka

### Order Service

Chức năng: - Create order - Order detail - Order history - Cancel
order - Saga orchestration

Tech: - Spring Boot - PostgreSQL - OpenFeign/WebClient - Kafka

### Payment Service

Chức năng: - Create payment - Payment callback/webhook - Refund -
Payment history - Outbox Pattern

Tech: - Spring Boot - PostgreSQL - Kafka - Scheduler

### Notification Service

Chức năng: - Email - SMS - Push notification - Template engine

Tech: - Spring Boot - Kafka - Mail sender

------------------------------------------------------------------------

## 4) Cross-cutting patterns

-   Saga Pattern
-   Transactional Outbox
-   Idempotency
-   Retry + DLQ
-   Circuit Breaker
-   Timeout
-   Bulkhead
-   Distributed tracing
-   Centralized logging

------------------------------------------------------------------------

## 5) Infrastructure

Containers: - Docker - Docker Compose

Messaging: - Kafka / RabbitMQ

Cache: - Redis

Observability: - OpenTelemetry - Zipkin - Prometheus - Grafana - ELK /
Loki

Docs: - Swagger / OpenAPI

CI/CD: - GitHub Actions

------------------------------------------------------------------------

## 6) Database per service

Mỗi service sở hữu DB/schema riêng, không query chéo DB.

------------------------------------------------------------------------

## 7) Flow nghiệp vụ chính

User → Cart → Create Order → Reserve Stock → Payment → Confirm Order →
Notify User

Failure: Payment fail → Cancel Order → Release Stock → Notify failure

------------------------------------------------------------------------

## 8) Nâng cao (optional)

-   Coupon Service
-   Recommendation Service
-   Delivery Tracking
-   Admin analytics dashboard
-   Multi-region deployment
