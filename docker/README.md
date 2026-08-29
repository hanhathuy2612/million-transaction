# Docker Setup — MySQL, Kafka & Redis

Hạ tầng local cho **Million Transaction**, tách theo từng stack rồi gom qua `include`.

## Cấu trúc

```
docker/
├── docker-compose.yml      # File tổng (include 3 stack bên dưới)
├── start.sh                # Start toàn bộ infra
├── mysql/
│   ├── docker-compose.yml
│   ├── Dockerfile
│   ├── mysql-master.cnf
│   ├── mysql-slave.cnf
│   └── start.sh            # Start + setup GTID replication
├── kafka/
│   ├── docker-compose.yml
│   └── start.sh
└── redis/
    ├── docker-compose.yml
    └── start.sh
```

File `docker-compose.yml` ở **thư mục gốc project** include `docker/docker-compose.yml`.

## Khởi động

### Toàn bộ infra

```bash
# Từ thư mục gốc
docker compose up -d --build

# Hoặc
bash docker/start.sh
```

### Từng stack riêng

```bash
bash docker/mysql/start.sh
bash docker/kafka/start.sh
bash docker/redis/start.sh
```

### MySQL replication

Slave **tự cấu hình GTID replication** khi container start — không cần chạy script riêng sau `docker compose up -d`.

Reset volume MySQL (xóa data):

```bash
bash docker/mysql/start.sh --fresh
```

## Services & ports

| Stack | Container | Port |
|-------|-----------|------|
| MySQL | mt-mysql-master | 3307 |
| MySQL | mt-mysql-slave | 3308 |
| MySQL | mt-adminer | 8089 |
| Kafka | mt-zookeeper | 2181 |
| Kafka | mt-kafka | 9092 |
| Kafka | mt-kafka-ui | 8080 |
| Redis | mt-redis | 6379 |
| Redis | mt-redis-commander | 8081 |

## Credentials

| | Giá trị |
|---|--------|
| MySQL root | `FormosVN@123` |
| Database | `millions_transaction` |
| App user | `millions_user` / `millions_pass` |
| Redis | password `redis123` |

## Lệnh hữu ích

```bash
docker compose ps
docker compose down          # dừng, giữ data
docker compose down -v       # dừng + xóa data

bash scripts/test-master-slave.sh
```
