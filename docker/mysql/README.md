# MySQL Master-Slave

Stack MySQL gồm `mt-mysql-master`, `mt-mysql-slave`, `mt-adminer`.

## Tự động cấu hình replication

Khi chạy `docker compose up -d`, **slave tự cấu hình GTID replication** qua `slave-bootstrap.sh` (không cần script riêng).

```bash
# Từ thư mục gốc — replication tự chạy
docker compose up -d --build

# Chỉ MySQL stack
bash docker/mysql/start.sh
```

## Reset data MySQL

```bash
bash docker/mysql/start.sh --fresh
```

## Files

| File | Mô tả |
|------|-------|
| `docker-compose.yml` | Compose riêng cho MySQL |
| `Dockerfile` | Image master |
| `Dockerfile.slave` | Image slave + auto bootstrap |
| `slave-bootstrap.sh` | Tự cấu hình replication khi slave start |
| `mysql-master.cnf` | GTID, binlog, server-id=1 |
| `mysql-slave.cnf` | GTID, relay log, server-id=2 |

Xem thêm: [../README.md](../README.md)
