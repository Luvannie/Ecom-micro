@echo off
REM Database Migration & Seed Script for Ecom Food Delivery
REM Usage: seed.bat

echo [MIGRATE] Seeding databases...

echo Seeding auth-service...
docker exec -i be-auth-db-1 psql -U auth_user -d auth_service < auth-service\V1__seed_auth_data.sql

echo Seeding user-service...
docker exec -i be-user-db-1 psql -U user_user -d user_service < user-service\V1__seed_user_data.sql

echo Seeding product-service...
docker exec -i be-product-db-1 psql -U product_user -d product_service < product-service\V1__seed_product_data.sql

echo Seeding inventory-service...
docker exec -i be-inventory-db-1 psql -U inventory_user -d inventory_service < inventory-service\V1__seed_inventory_data.sql

echo Seeding order-service...
docker exec -i be-order-db-1 psql -U order_user -d order_service < order-service\V1__seed_order_data.sql

echo Seeding payment-service...
docker exec -i be-payment-db-1 psql -U payment_user -d payment_service < payment-service\V1__seed_payment_data.sql

echo Seeding notification-service...
docker exec -i be-notification-db-1 psql -U notification_user -d notification_service < notification-service\V1__seed_notification_data.sql

echo [MIGRATE] Done!