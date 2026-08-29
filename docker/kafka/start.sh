#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.yml"

if command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  DC="docker compose"
fi

echo "🚀 Starting Kafka stack..."
$DC -f "$COMPOSE_FILE" up -d zookeeper kafka kafka-ui

echo ""
echo "Kafka UI: http://localhost:8080"
echo "Kafka:    localhost:9092"
