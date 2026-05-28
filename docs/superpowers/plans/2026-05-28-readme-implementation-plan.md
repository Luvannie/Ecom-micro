# README Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a 3-file README documentation system: Root executive summary, enhanced BE backend guide, and new FE frontend guide

**Architecture:** Three independent README files with clear responsibilities and cross-references. No duplication - each file has distinct content focused on its audience.

**Tech Stack:** Markdown, Docker, Maven, React/Vite

---

## File Analysis

| File | Action | Content Focus |
|------|--------|---------------|
| `/README.md` | Create | Executive summary, architecture diagram, quick stats, links to BE/FE README |
| `/BE/README.md` | Enhance | Backend services, API examples, patterns, business flows |
| `/FE/README.md` | Create | Frontend setup, structure, components |

---

## Task 1: Create Root README (`/README.md`)

**Files:**
- Create: `/README.md`

- [ ] **Step 1: Write Root README**

```markdown
# Ecom Food Delivery Microservices

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.6-red.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Production-like Java microservices system for an e-commerce and food delivery platform. Features 11 backend services, event-driven architecture, and modern DevOps practices.

## Architecture

```
                    ┌─────────────────┐
                    │   API Gateway   │ :8080
                    │  (Spring Cloud) │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  Auth Service   │ │ User Service    │ │ Product Service │
│     :8081       │ │     :8082       │ │     :8083       │
└─────────────────┘ └─────────────────┘ └─────────────────┘
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  Cart Service   │ │Order Service    │ │Inventory Service│
│     :8084       │ │     :8086       │ │     :8085       │
└─────────────────┘ └─────────────────┘ └─────────────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             ▼
┌─────────────────┐ ┌─────────────────┐
│Payment Service  │ │Notification Svc │
│     :8087       │ │     :8088       │
└─────────────────┘ └─────────────────┘

┌─────────────────┐ ┌─────────────────┐
│Discovery Server │ │  Config Server  │
│     :8761       │ │     :8888       │
└─────────────────┘ └─────────────────┘
```

## Key Stats

| Metric | Value |
|--------|-------|
| Backend Services | 11 |
| Databases | PostgreSQL (per service) |
| Message Broker | Apache Kafka |
| Frontend | React 18 + TypeScript + Vite |
| Build Tool | Maven (BE), Vite (FE) |
| Container Runtime | Docker + Docker Compose |

## Quick Start

```bash
# Start infrastructure
docker compose up -d

# Run all backend services
cd BE
mvn spring-boot:run -pl discovery-server
# (run remaining services in separate terminals or use -pl to run specific)

# Run frontend
cd FE
npm install
npm run dev
```

## Documentation

- **[Backend Documentation](./BE/README.md)** - Detailed backend services guide with API examples, patterns, and architecture
- **[Frontend Documentation](./FE/README.md)** - Frontend setup, project structure, and components

## Tech Stack

### Backend
- Java 21 + Spring Boot 3.2
- Spring Cloud Gateway, Security, Data
- PostgreSQL, Redis, Elasticsearch
- Apache Kafka
- OpenTelemetry, Prometheus, Grafana

### Frontend
- React 18 + TypeScript
- Vite build tool
- React Router v6
- CSS Modules

### Infrastructure
- Docker + Docker Compose
- Eureka (Discovery)
- Spring Cloud Config
- Zipkin (Distributed Tracing)
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add root README with executive summary"
```

---

## Task 2: Enhance BE README (`/BE/README.md`)

**Files:**
- Read: `/BE/README.md` (existing content)
- Modify: `/BE/README.md` (add sections)

- [ ] **Step 1: Read existing BE README**

Read the current `/BE/README.md` file to understand existing content structure.

- [ ] **Step 2: Add Service Table section**

After existing content, add this new section after the main content:

```markdown
## Services Overview

| Service | Port | Database | Description |
|--------|-----:|----------|-------------|
| API Gateway | 8080 | - | Routing, JWT validation, CORS, rate limiting |
| Discovery Server | 8761 | - | Eureka service registry |
| Config Server | 8888 | - | Centralized configuration |
| Auth Service | 8081 | PostgreSQL + Redis | Authentication, JWT, refresh tokens |
| User Service | 8082 | PostgreSQL | User profiles, addresses |
| Product Service | 8083 | PostgreSQL + Redis | Product catalog, categories, search |
| Cart Service | 8084 | Redis | Shopping cart management |
| Inventory Service | 8085 | PostgreSQL + Kafka | Stock management, reservations |
| Order Service | 8086 | PostgreSQL + Kafka | Order processing, saga orchestration |
| Payment Service | 8087 | PostgreSQL + Kafka | Payments, transactional outbox |
| Notification Service | 8088 | - | Email/SMS notification dispatch |
```

- [ ] **Step 3: Add Cross-Cutting Patterns section**

Add after Service Table:

```markdown
## Cross-Cutting Patterns

### Saga Pattern
Coordinates distributed transactions across Order → Inventory → Payment services using Kafka events. Ensures eventual consistency when operations span multiple services.

### Transactional Outbox
Payment Service uses an outbox table to guarantee event publication within the same transaction as payment state changes.

### Idempotency
All mutation endpoints support `Idempotency-Key` header to safely retry without duplicate side effects.

### Retry with DLQ
Failed Kafka message deliveries retry with exponential backoff, then route to Dead Letter Queue for manual inspection.

### Circuit Breaker
Resilient4j circuit breakers protect inter-service calls, failing fast when downstream services are unavailable.

### Distributed Tracing
Correlation IDs propagate through all service calls, integrated with Zipkin for end-to-end request visibility.
```

- [ ] **Step 4: Add Business Flows section**

```markdown
## Business Flows

### Order Flow
```
User → Add Cart → Create Order → Reserve Stock → Payment → Confirm Order → Notify User
```

1. Customer adds items to cart
2. Checkout creates a pending order
3. Inventory reserves stock (reduces available quantity)
4. Customer completes payment
5. Payment confirmation triggers order confirmation
6. Notification sent to customer

### Payment Failure Flow
```
Payment Failed → Cancel Order → Release Stock → Notify Failure
```

### Event-Driven Architecture
Services communicate via Kafka topics:
- `order.created` → triggers inventory reservation
- `payment.succeeded` → confirms order, triggers notification
- `payment.failed` → cancels order, releases inventory
- `order.confirmed` → triggers user notification
```

- [ ] **Step 5: Add API Quick Reference section**

```markdown
## API Quick Reference

### Authentication
```bash
POST /api/auth/register    # New customer registration
POST /api/auth/login       # Login, returns JWT
POST /api/auth/refresh     # Refresh access token
POST /api/auth/logout      # Invalidate refresh token
```

### User Profile
```bash
GET  /api/users/me              # Get current user profile
PUT  /api/users/me              # Update profile
POST /api/users/me/addresses     # Add address
GET  /api/users/me/addresses     # List addresses
DELETE /api/users/me/addresses/{id} # Remove address
```

### Products
```bash
GET  /api/categories                    # List categories
POST /api/admin/categories              # Admin: create category
GET  /api/products                      # Search products
GET  /api/products/{id}                 # Get product detail
POST /api/admin/products                # Admin: create product
PUT  /api/admin/products/{id}           # Admin: update product
```

### Cart
```bash
GET    /api/cart                        # Get current cart
POST   /api/cart/items                  # Add item to cart
PUT    /api/cart/items/{productId}      # Update quantity
DELETE /api/cart/items/{productId}       # Remove item
DELETE /api/cart                        # Clear cart
```

### Orders
```bash
POST /api/orders                        # Create order from cart
GET  /api/orders                        # List user orders
GET  /api/orders/{id}                   # Get order detail
POST /api/orders/{id}/cancel            # Cancel order
```

### Payments
```bash
POST /api/payments                      # Create payment
POST /api/payments/webhooks/mock        # Mock provider webhook
```
```

- [ ] **Step 6: Commit**

```bash
git add BE/README.md
git commit -m "docs: enhance BE README with services table, patterns, and API reference"
```

---

## Task 3: Create FE README (`/FE/README.md`)

**Files:**
- Create: `/FE/README.md`

- [ ] **Step 1: Write Frontend README**

```markdown
# Frontend Documentation

React 18 + TypeScript + Vite frontend for the Ecom Food Delivery microservices platform.

## Tech Stack

- **React** 18.x - UI library
- **TypeScript** 5.x - Type-safe JavaScript
- **Vite** 5.x - Build tool and dev server
- **React Router** 6.x - Client-side routing
- **Fetch API** - HTTP client (no external library)

## Project Structure

```
FE/
├── src/
│   ├── api.ts           # API client layer (Axios-like interface)
│   ├── types.ts         # Shared TypeScript interfaces
│   ├── App.tsx          # Root component with routing
│   ├── App.css          # Global styles
│   ├── main.tsx         # Entry point
│   ├── index.css        # CSS reset/normalize
│   ├── components/      # Reusable UI components
│   ├── context/         # React Context providers
│   └── pages/           # Page-level components
├── index.html           # HTML entry
├── vite.config.ts       # Vite configuration
├── tsconfig.json        # TypeScript config
└── package.json        # Dependencies
```

## Setup

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Available Pages

The application includes the following pages (defined in `App.tsx`):

| Page | Component | Description |
|------|-----------|-------------|
| Home | `/` | Main landing page |
| Login | `/login` | Customer login |
| Register | `/register` | Customer registration |
| Profile | `/profile` | User profile with addresses |

## Components

Key reusable components under `src/components/`:

- **Header/Navigation** - Top navigation bar
- **ProductCard** - Product display card
- **CartWidget** - Cart summary in header
- **AddressForm** - Address input form

## State Management

State is managed via React Context:

- **AuthContext** - Authentication state, JWT token storage
- **CartContext** - Shopping cart state

## API Integration

API calls go through `src/api.ts` which provides a typed fetch wrapper:

```typescript
// Example: Fetch products
const products = await api.get('/products', { keyword: 'pizza' });

// Example: Add to cart
await api.post('/cart/items', { productId, quantity });
```

## Configuration

Frontend proxies API calls to `http://localhost:8080` (API Gateway) in development mode (see `vite.config.ts`).

---

*For backend services documentation, see [./BE/README.md](../BE/README.md)*
```

- [ ] **Step 2: Commit**

```bash
git add FE/README.md
git commit -m "docs: add FE README with frontend developer guide"
```

---

## Spec Coverage Check

| Spec Requirement | Task |
|------------------|------|
| Root README - Executive summary | Task 1 |
| Root README - Architecture diagram | Task 1 |
| Root README - Quick links to BE/FE | Task 1 |
| Root README - Key stats | Task 1 |
| Root README - Quick start | Task 1 |
| BE README - Service table | Task 2 |
| BE README - Cross-cutting patterns | Task 2 |
| BE README - Business flows | Task 2 |
| BE README - API quick reference | Task 2 |
| FE README - Tech stack | Task 3 |
| FE README - Project structure | Task 3 |
| FE README - Setup commands | Task 3 |
| FE README - Pages overview | Task 3 |

## Placeholder Scan

No TBD/TODO found. All code blocks contain actual content. All commit messages are complete.

## Type Consistency

Not applicable - this is a documentation-only task with no code changes.

---

**Plan complete.** Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch subagent per task for fast iteration

**2. Inline Execution** - Execute tasks in this session

Which approach?
