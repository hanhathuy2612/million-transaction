#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

if command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  DC="docker compose"
fi

echo "🚀 Starting Million Transaction infrastructure..."
$DC -f "$COMPOSE_FILE" up -d --build

echo "⏳ Waiting for services to start..."
sleep 10

echo "📊 Service status:"
$DC -f "$COMPOSE_FILE" ps

echo ""
echo "✅ Infrastructure started!"
echo ""
echo "🔗 Web UI:"
echo "   Adminer:           http://localhost:8089"
echo "   Kafka UI:          http://localhost:8080"
echo "   Redis Commander:   http://localhost:8081"
echo ""
echo "🔌 Connections:"
echo "   MySQL Master:       localhost:3307"
echo "   MySQL Slave:        localhost:3308"
echo "   Redis:              localhost:6379 (password: redis123)"
echo "   Kafka:              localhost:9092"
