# Database Migration & Seed Script for Windows
# Run this to seed mock data into the databases

$ErrorActionPreference = "Stop"

function log {
    Write-Host "[MIGRATE] $args" -ForegroundColor Green
}
function warn {
    Write-Host "[WARN] $args" -ForegroundColor Yellow
}

# Check if containers are running
$containers = docker ps --format '{{.Names}}' | Where-Object { $_ -match "db|postgres" }
if (-not $containers) {
    warn "No database containers running. Starting them..."
    docker compose up -d postgres auth-db user-db product-db inventory-db order-db payment-db notification-db redis
    Start-Sleep -Seconds 15
}

log "Seeding auth-service..."
docker exec -i be-auth-db-1 psql -U auth_user -d auth_service < auth-service\V1__seed_auth_data.sql 2>&1 | Select-Object -First 3

log "Seeding user-service..."
docker exec -i be-user-db-1 psql -U user_user -d user_service < user-service\V1__seed_user_data.sql 2>&1 | Select-Object -First 3

log "Seeding product-service..."
docker exec -i be-product-db-1 psql -U product_user -d product_service < product-service\V1__seed_product_data.sql 2>&1 | Select-Object -First 3

log "Seeding inventory-service..."
docker exec -i be-inventory-db-1 psql -U inventory_user -d inventory_service < inventory-service\V1__seed_inventory_data.sql 2>&1 | Select-Object -First 3

log "Seeding order-service..."
docker exec -i be-order-db-1 psql -U order_user -d order_service < order-service\V1__seed_order_data.sql 2>&1 | Select-Object -First 3

log "Seeding payment-service..."
docker exec -i be-payment-db-1 psql -U payment_user -d payment_service < payment-service\V1__seed_payment_data.sql 2>&1 | Select-Object -First 3

log "Seeding notification-service..."
docker exec -i be-notification-db-1 psql -U notification_user -d notification_service < notification-service\V1__seed_notification_data.sql 2>&1 | Select-Object -First 3

log "Seeding complete!"