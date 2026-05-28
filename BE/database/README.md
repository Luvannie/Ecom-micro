# Database Migration & Seed Data

This folder contains database schema migrations and seed data for all Ecom Food Delivery microservices.

## Structure

```
database/
├── auth-service/
│   ├── V1__create_auth_tables.sql      # Schema (Flyway migration)
│   └── V1__seed_auth_data.sql          # Seed data (4 test users)
├── user-service/
│   ├── V1__create_user_tables.sql      # Schema
│   └── V1__seed_user_data.sql          # Seed data
├── product-service/
│   ├── V1__create_catalog_tables.sql   # Schema
│   └── V1__seed_product_data.sql       # Seed data (6 categories, 22 products)
├── inventory-service/
│   ├── V1__create_inventory_tables.sql # Schema
│   └── V1__seed_inventory_data.sql     # Seed data (22 products stock)
├── order-service/
│   ├── V1__create_order_tables.sql     # Schema
│   └── V1__seed_order_data.sql         # Seed data (3 orders)
├── payment-service/
│   ├── V1__create_payment_tables.sql   # Schema
│   └── V1__seed_payment_data.sql        # Seed data (2 payments + outbox)
├── notification-service/
│   ├── V1__create_notification_tables.sql # Schema
│   └── V1__seed_notification_data.sql  # Seed data (3 notifications)
└── migrate.sh                           # Migration script
```

## Usage

```bash
cd database

# Start databases via Docker
docker compose -f ../docker-compose.yml up -d postgres auth-db user-db product-db inventory-db order-db payment-db notification-db

# Wait 15 seconds for PostgreSQL to initialize
sleep 15

# Seed mock data
./seed.sh

# Or use docker exec directly
docker exec -i <container> psql -U <user> -d <db> < service/V1__seed_*.sql
```

## Test Users

All test users have password: `password123`

| Email | Name | ID |
|-------|------|-----|
| john.doe@example.com | John Doe | 11111111-1111-1111-1111-111111111111 |
| jane.smith@example.com | Jane Smith | 22222222-2222-2222-2222-222222222222 |
| bob.wilson@example.com | Bob Wilson | 33333333-3333-3333-3333-333333333333 |
| alice.jones@example.com | Alice Jones | 44444444-4444-4444-4444-444444444444 |

## Seed Data Includes

- **4 users** with credentials and profiles
- **6 categories** (Fast Food, Vietnamese, Chinese, Japanese, Desserts, Beverages)
- **22 products** with images, prices, and descriptions
- **Inventory stock** for all products (30-200 units each)
- **3 sample orders** in various states (pending, confirmed, delivered)
- **2 payments** linked to orders
- **3 notification logs** for order events

## Database Ports

| Service | Port | Database | User | Password |
|---------|------|----------|------|----------|
| postgres (main) | 5432 | ecom | ecom | ecom |
| auth-db | 5433 | auth_service | auth_user | auth_password |
| user-db | 5434 | user_service | user_user | user_password |
| product-db | 5435 | product_service | product_user | product_password |
| inventory-db | 5436 | inventory_service | inventory_user | inventory_password |
| order-db | 5437 | order_service | order_user | order_password |
| payment-db | 5438 | payment_service | payment_user | payment_password |
| notification-db | 5439 | notification_service | notification_user | notification_password |