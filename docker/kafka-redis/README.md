# Docker Services for Million Transactions API Gateway

This Docker setup provides Redis and Kafka services for the Million Transactions application.

## Services Included

### Core Services
- **Redis**: In-memory data store for caching (Port: 6379)
- **Kafka**: Distributed messaging platform (Port: 9092)
- **Zookeeper**: Coordination service for Kafka (Port: 2181)

### Management UIs
- **Kafka UI**: Web interface for Kafka management (Port: 8080)
- **Redis Commander**: Web interface for Redis management (Port: 8081)

## Quick Start

1. **Start all services:**
   ```bash
   docker-compose up -d
   ```

2. **Check service status:**
   ```bash
   docker-compose ps
   ```

3. **View logs:**
   ```bash
   # All services
   docker-compose logs -f
   
   # Specific service
   docker-compose logs -f kafka
   docker-compose logs -f redis
   ```

4. **Stop services:**
   ```bash
   docker-compose down
   ```

5. **Stop and remove volumes (WARNING: This deletes all data):**
   ```bash
   docker-compose down -v
   ```

## Service Details

### Redis
- **URL**: `redis://localhost:6379`
- **Password**: `redis123`
- **Management UI**: http://localhost:8081

### Kafka
- **Bootstrap Servers**: `localhost:9092`
- **Internal Bootstrap Servers**: `kafka:29092` (for container-to-container communication)
- **Management UI**: http://localhost:8080

### Zookeeper
- **URL**: `localhost:2181`

## Configuration

The services can be configured using the `.env` file in this directory. Key configuration options:

- `REDIS_PASSWORD`: Redis authentication password
- `KAFKA_PORT`: External Kafka port
- `ZOOKEEPER_PORT`: Zookeeper port
- `KAFKA_UI_PORT`: Kafka UI web interface port
- `REDIS_COMMANDER_PORT`: Redis Commander web interface port

## Application Integration

Update your `application.yml` to connect to these services:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: redis123
  
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      group-id: million-transactions-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

## Health Checks

All services include health checks to ensure they're running properly:

- **Redis**: Ping command
- **Kafka**: Broker API versions check
- **Zookeeper**: Ruok command

## Troubleshooting

### Common Issues

1. **Port conflicts**: If ports are already in use, modify the port mappings in `.env`
2. **Memory issues**: Kafka requires sufficient memory. Increase Docker memory if needed
3. **Startup order**: Services have dependencies. Use `docker-compose up` to ensure proper startup order

### Useful Commands

```bash
# Restart a specific service
docker-compose restart kafka

# Scale Kafka (if needed for testing)
docker-compose up -d --scale kafka=2

# Access Kafka container shell
docker-compose exec kafka bash

# Access Redis container shell
docker-compose exec redis redis-cli -a redis123

# Check Kafka topics
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Create a test topic
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic test-topic --partitions 3 --replication-factor 1
```

## Production Considerations

For production deployment, consider:

1. **Security**: Change default passwords and enable SSL/TLS
2. **Persistence**: Ensure volumes are properly backed up
3. **Monitoring**: Add monitoring solutions like Prometheus/Grafana
4. **Scaling**: Configure multiple Kafka brokers and Redis clustering
5. **Network**: Use proper network isolation and firewall rules
