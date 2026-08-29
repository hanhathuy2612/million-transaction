# Million Transaction API

API Gateway xử lý giao dịch thanh toán với MySQL Master-Slave, Redis, Kafka và Stripe.

## Yêu cầu hệ thống

| Công cụ | Phiên bản tối thiểu |
|---------|---------------------|
| Java    | 17                  |
| Gradle  | 8.x (dùng `./gradlew`) |
| Docker  | 20+                 |
| Docker Compose | v2 (lệnh `docker compose`) |

## Quick Start

### 1. Khởi động hạ tầng Docker

Xem hướng dẫn chi tiết tại **[docker/README.md](docker/README.md)**.

> **Lưu ý:** Phải chạy lệnh từ **thư mục gốc project** (nơi có file `docker-compose.yml`),  
> hoặc `cd` vào đúng thư mục con. Chạy `docker compose up -d` ở thư mục khác sẽ báo `no configuration file provided`.

**Windows (PowerShell) — từ thư mục gốc:**

```powershell
# Kafka + Redis
.\scripts\start-kafka-redis.ps1

# Hoặc tất cả container (MySQL replication tự cấu hình)
.\scripts\start-infra.ps1

# Hoặc trực tiếp — replication tự chạy khi slave start
docker compose up -d --build
```

**Linux / macOS / Git Bash:**

```bash
# Toàn bộ hạ tầng — replication tự cấu hình khi slave start
docker compose up -d --build

# Hoặc
bash docker/start.sh

# Từng stack riêng
bash docker/mysql/start.sh
bash docker/kafka/start.sh
bash docker/redis/start.sh

# Reset data MySQL (xóa volume)
bash docker/mysql/start.sh --fresh
```

### 2. Chạy database migration (tuỳ chọn)

Migration cũng tự chạy khi start app. Nếu muốn chạy tay trước:

```bash
./gradlew update
```

### 3. Chạy ứng dụng

```bash
./gradlew bootRun
```

- **API:** http://localhost:8888
- **Swagger UI:** http://localhost:8888/swagger-ui.html
- **Health:** http://localhost:8888/actuator/health

## Cấu hình mặc định

Các giá trị dưới đây khớp với `src/main/resources/application.yml` và Docker Compose:

| Service | Host | Port | Credentials |
|---------|------|------|-------------|
| MySQL Master | localhost | 3307 | root / `FormosVN@123` |
| MySQL Slave  | localhost | 3308 | root / `FormosVN@123` |
| Redis        | localhost | 6379 | password: `redis123` |
| Kafka        | localhost | 9092 | — |
| Adminer (DB UI) | localhost | 8089 | — |
| Kafka UI     | localhost | 8080 | — |
| Redis Commander | localhost | 8081 | — |

Database: `millions_transaction`

## Cấu trúc thư mục

```
docker/
├── docker-compose.yml           # Include mysql + kafka + redis
├── start.sh
├── mysql/                       # Master-slave + Adminer
├── kafka/                       # Zookeeper + Kafka + Kafka UI
└── redis/                       # Redis + Redis Commander
src/main/resources/
├── application.yml              # Cấu hình app
└── db/changelog/                # Liquibase migrations
```

## Lệnh hữu ích

```bash
# Test replication MySQL
bash scripts/test-master-slave.sh

# Xem changeset Liquibase chưa apply
./gradlew status

# Chạy test
./gradlew test
```

## Tài liệu thêm

- [Docker Setup](docker/README.md)
- [MySQL](docker/mysql/README.md) · [Kafka](docker/kafka/README.md) · [Redis](docker/redis/README.md)
