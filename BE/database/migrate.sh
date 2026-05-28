#!/bin/bash
# Database Migration Script for Ecom Food Delivery
# Usage: ./migrate.sh [start|stop|seed|reset|status]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

log() { echo -e "\033[0;32m[MIGRATE]\033[0m $1"; }
warn() { echo -e "\033[1;33m[WARN]\033[0m $1"; }
err() { echo -e "\033[0;31m[ERROR]\033[0m $1"; exit 1; }

check_docker() {
  docker info > /dev/null 2>&1 || err "Docker is not running. Please start Docker Desktop."
}

start_databases() {
  log "Starting PostgreSQL databases..."
  cd "$(dirname "$0")/.."
  docker compose up -d postgres auth-db user-db product-db inventory-db order-db payment-db notification-db redis
  log "Waiting 15s for databases to initialize..."
  sleep 15
  log "Databases started."
}

stop_databases() {
  log "Stopping databases..."
  cd "$(dirname "$0")/.."
  docker compose stop postgres auth-db user-db product-db inventory-db order-db payment-db notification-db redis 2>/dev/null || true
}

seed_data() {
  log "Seeding mock data..."

  docker exec -i be-auth-db-1 psql -U auth_user -d auth_service < auth-service/V1__seed_auth_data.sql && log "auth-service seeded" || warn "auth-service seed failed"
  docker exec -i be-user-db-1 psql -U user_user -d user_service < user-service/V1__seed_user_data.sql && log "user-service seeded" || warn "user-service seed failed"
  docker exec -i be-product-db-1 psql -U product_user -d product_service < product-service/V1__seed_product_data.sql && log "product-service seeded" || warn "product-service seed failed"
  docker exec -i be-inventory-db-1 psql -U inventory_user -d inventory_service < inventory-service/V1__seed_inventory_data.sql && log "inventory-service seeded" || warn "inventory-service seed failed"
  docker exec -i be-order-db-1 psql -U order_user -d order_service < order-service/V1__seed_order_data.sql && log "order-service seeded" || warn "order-service seed failed"
  docker exec -i be-payment-db-1 psql -U payment_user -d payment_service < payment-service/V1__seed_payment_data.sql && log "payment-service seeded" || warn "payment-service seed failed"
  docker exec -i be-notification-db-1 psql -U notification_user -d notification_service < notification-service/V1__seed_notification_data.sql && log "notification-service seeded" || warn "notification-service seed failed"

  log "Seeding complete."
}

reset_databases() {
  check_docker
  log "Resetting all databases (drop + recreate + seed)..."
  stop_databases
  sleep 2
  start_databases
  seed_data
}

status() {
  log "Checking database status..."
  for port in 5432 5433 5434 5435 5436 5437 5438 5439; do
    result=$(timeout 3 bash -c "PGPASSWORD=ecom psql -h localhost -p $port -U ecom -d postgres -c 'SELECT 1' 2>/dev/null" && echo "UP" || echo "DOWN")
    if [ "$result" = "UP" ]; then
      echo -e "Port $port: \033[0;32mUP\033[0m"
    else
      echo -e "Port $port: \033[0;31mDOWN\033[0m"
    fi
  done
}

case "${1:-}" in
  start)   check_docker; start_databases; seed_data ;;
  stop)    stop_databases ;;
  seed)    seed_data ;;
  reset)   reset_databases ;;
  status)  status ;;
  *)
    echo "Usage: ./migrate.sh {start|stop|seed|reset|status}"
    echo "  start  - Start databases + seed data"
    echo "  stop   - Stop database containers"
    echo "  seed   - Seed mock data only"
    echo "  reset  - Drop/recreate all databases + seed"
    echo "  status - Check database connection status"
    ;;
esac