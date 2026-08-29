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

echo "🚀 Starting Redis stack..."
$DC -f "$COMPOSE_FILE" up -d redis redis-commander

echo ""
echo "Redis Commander: http://localhost:8081"
echo "Redis:           localhost:6379 (password: redis123)"
