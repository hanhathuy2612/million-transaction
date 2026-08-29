#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.yml"

FRESH=false
for arg in "$@"; do
  if [[ "$arg" == "--fresh" ]]; then
    FRESH=true
  fi
done

if command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  DC="docker compose"
fi

if [[ "$FRESH" == true ]]; then
  echo "🧹 Resetting MySQL volumes (Kafka/Redis data is kept)..."
  $DC -f "$COMPOSE_FILE" stop mysql-master mysql-slave adminer 2>/dev/null || true
  $DC -f "$COMPOSE_FILE" rm -f mysql-master mysql-slave adminer 2>/dev/null || true
  docker volume rm million-transaction_mysql_master_data million-transaction_mysql_slave_data 2>/dev/null || true
else
  echo "ℹ️  Keeping existing MySQL data. Use --fresh to wipe and recreate volumes."
fi

echo "🚀 Starting MySQL Master-Slave (replication auto-configures on slave startup)..."
$DC -f "$COMPOSE_FILE" up -d --build mysql-master mysql-slave adminer

echo ""
echo "⏳ Waiting for replication bootstrap..."
for i in $(seq 1 60); do
  if docker exec mt-mysql-slave mysql -uroot -pFormosVN@123 -N -s -e "SHOW REPLICA STATUS\G" 2>/dev/null \
    | grep -qE "Replica_IO_Running: Yes" \
    && docker exec mt-mysql-slave mysql -uroot -pFormosVN@123 -N -s -e "SHOW REPLICA STATUS\G" 2>/dev/null \
    | grep -qE "Replica_SQL_Running: Yes"; then
    echo "✅ Replication is running"
    echo "Master: localhost:3307 | Slave: localhost:3308 | Admin: http://localhost:8089"
    exit 0
  fi
  sleep 3
done

echo "⚠️  Replication not ready yet. Check logs:"
echo "   docker logs mt-mysql-slave"
exit 1
