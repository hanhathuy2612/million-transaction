#!/bin/bash

# MySQL Master-Slave Replication Docker Services Startup Script

echo "🚀 Starting MySQL Master-Slave Replication Docker Services..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Check if docker-compose is available
if ! command -v docker-compose > /dev/null 2>&1; then
    echo "❌ docker-compose is not installed. Please install docker-compose first."
    exit 1
fi

# Navigate to the docker directory
cd "$(dirname "$0")"

echo "📁 Current directory: $(pwd)"

# Start services
echo "🔧 Starting services..."
docker-compose up -d

# Wait a moment for services to start
echo "⏳ Waiting for services to start..."
sleep 10

# Check service status
echo "📊 Service Status:"
docker-compose ps

echo ""
echo "✅ Services started successfully!"
echo ""
echo "🔗 Service URLs:"
echo "   📊 Kafka UI: http://localhost:8080"
echo "   🔴 Redis Commander: http://localhost:8081"
echo ""
echo "🔌 Connection Details:"
echo "   🔴 Redis: redis://localhost:6379 (password: redis123)"
echo "   📨 Kafka: localhost:9092"
echo "   🐘 Zookeeper: localhost:2181"
echo ""
echo "📝 To view logs: docker-compose logs -f"
echo "🛑 To stop services: docker-compose down"
