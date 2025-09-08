# MySQL Master-Slave Deployment trên 2 Máy Ubuntu Riêng Biệt

## 📋 Tổng Quan

Hướng dẫn này sẽ giúp bạn deploy MySQL master-slave replication trên 2 máy chủ Ubuntu riêng biệt bằng cách cài đặt trực tiếp MySQL (không dùng Docker). Đây là cách triển khai được khuyến nghị cho môi trường production với hiệu suất cao.

## 🏗️ Kiến Trúc

```
┌─────────────────────────────────┐    ┌─────────────────────────────────┐
│         MÁY CHỦ 1              │    │         MÁY CHỦ 2              │
│      Ubuntu 20.04/22.04        │    │      Ubuntu 20.04/22.04        │
│                                 │    │                                 │
│  ┌─────────────────────────┐    │    │  ┌─────────────────────────┐    │
│  │    MySQL Master         │    │    │  │    MySQL Slave          │    │
│  │    IP: 192.168.1.100    │────┼────┼──│    IP: 192.168.1.101    │    │
│  │    Port: 3306           │    │    │  │    Port: 3306           │    │
│  │    Read/Write           │    │    │  │    Read-Only            │    │
│  └─────────────────────────┘    │    │  └─────────────────────────┘    │
│                                 │    │                                 │
│  ┌─────────────────────────┐    │    │  ┌─────────────────────────┐    │
│  │    Application          │    │    │  │    Application          │    │
│  │    (Write Operations)   │    │    │  │    (Read Operations)    │    │
│  └─────────────────────────┘    │    │  └─────────────────────────┘    │
└─────────────────────────────────┘    └─────────────────────────────────┘
```

## 🔧 Yêu Cầu Hệ Thống

### Máy Chủ 1 (Master)
- **OS**: Ubuntu 20.04 LTS hoặc 22.04 LTS
- **RAM**: Tối thiểu 4GB (8GB khuyến nghị)
- **CPU**: 2 cores trở lên
- **Disk**: 50GB trở lên (SSD khuyến nghị)
- **Network**: IP tĩnh (ví dụ: 192.168.1.100)

### Máy Chủ 2 (Slave)
- **OS**: Ubuntu 20.04 LTS hoặc 22.04 LTS
- **RAM**: Tối thiểu 4GB (8GB khuyến nghị)
- **CPU**: 2 cores trở lên
- **Disk**: 50GB trở lên (SSD khuyến nghị)
- **Network**: IP tĩnh (ví dụ: 192.168.1.101)

### Yêu Cầu Mạng
- Cả 2 máy phải có thể kết nối với nhau qua port 3306
- Firewall phải cho phép traffic MySQL giữa 2 máy
- Độ trễ mạng < 10ms (khuyến nghị)
- Cả 2 máy trong cùng subnet hoặc có routing phù hợp

## 🚀 Cài Đặt MySQL trên Cả 2 Máy Ubuntu

### Bước 1: Chuẩn Bị Hệ Thống

#### Trên Cả 2 Máy:
```bash
# Cập nhật hệ thống
sudo apt update && sudo apt upgrade -y

# Cài đặt các package cần thiết
sudo apt install -y wget curl gnupg2 software-properties-common apt-transport-https ca-certificates

# Kiểm tra phiên bản Ubuntu
lsb_release -a
```

### Bước 2: Cài Đặt MySQL 8.0

#### Trên Cả 2 Máy:
```bash
# Tải và cài đặt MySQL APT repository
wget https://dev.mysql.com/get/mysql-apt-config_0.8.24-1_all.deb
sudo dpkg -i mysql-apt-config_0.8.24-1_all.deb

# Cập nhật package list
sudo apt update

# Cài đặt MySQL Server 8.0
sudo apt install -y mysql-server

# Khởi động và enable MySQL
sudo systemctl start mysql
sudo systemctl enable mysql

# Kiểm tra trạng thái
sudo systemctl status mysql
```

### Bước 3: Bảo Mật MySQL

#### Trên Cả 2 Máy:
```bash
# Chạy script bảo mật tự động
sudo mysql_secure_installation

# Hoặc thực hiện thủ công (nhanh hơn):
sudo mysql -e "
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'FormosVN@123';
DELETE FROM mysql.user WHERE User='';
DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1', '::1');
DROP DATABASE IF EXISTS test;
DELETE FROM mysql.db WHERE Db='test' OR Db='test\\_%';
FLUSH PRIVILEGES;
"

# Kiểm tra kết nối
mysql -u root -pFormosVN@123 -e "SELECT VERSION();"
```

## ⚙️ Cấu Hình Master Server (Máy 1 - IP: 192.168.1.100)

### Bước 1: Backup và Tạo File Cấu Hình

```bash
# Backup file cấu hình hiện tại
sudo cp /etc/mysql/mysql.conf.d/mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf.backup

# Tạo file cấu hình mới cho Master
sudo tee /etc/mysql/mysql.conf.d/mysqld.cnf > /dev/null <<EOF
[mysqld]
# Basic Settings
user = mysql
pid-file = /var/run/mysqld/mysqld.pid
socket = /var/run/mysqld/mysqld.sock
port = 3306
basedir = /usr
datadir = /var/lib/mysql
tmpdir = /tmp
lc-messages-dir = /usr/share/mysql

# Network
bind-address = 0.0.0.0
max_connections = 300
max_connect_errors = 1000

# Identity
server-id = 1

# Authentication
default_authentication_plugin = mysql_native_password

# Binary Logging & GTID
log-bin = mysql-bin
binlog_format = ROW
binlog_row_image = MINIMAL
gtid_mode = ON
enforce_gtid_consistency = ON
sync_binlog = 1
log_slave_updates = ON

# InnoDB Settings
innodb_buffer_pool_size = 1G
innodb_log_file_size = 512M
innodb_flush_log_at_trx_commit = 1
innodb_flush_method = O_DIRECT

# Character Set
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# Timezone
default-time-zone = '+07:00'

# Performance (MySQL 8.0 compatible)
thread_cache_size = 64
table_open_cache = 4096

# Logging
log-error = /var/log/mysql/error.log
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 2
EOF
```

### Bước 2: Khởi Động Lại MySQL

```bash
# Restart MySQL để áp dụng cấu hình
sudo systemctl restart mysql

# Kiểm tra trạng thái
sudo systemctl status mysql

# Kiểm tra log nếu có lỗi
sudo tail -f /var/log/mysql/error.log
```

### Bước 3: Tạo Replication User và Database

```bash
# Kết nối MySQL và tạo user replication
mysql -u root -pFormosVN@123 -e "
-- Tạo user replication
CREATE USER 'repl_user'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';

-- Tạo user root từ xa (cho quản lý)
CREATE USER 'root'@'%' IDENTIFIED BY 'FormosVN@123';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

-- Tạo database và user ứng dụng
CREATE DATABASE IF NOT EXISTS \`millions_transaction\`;
CREATE USER 'millions_user'@'%' IDENTIFIED BY 'FormosVN@123';
GRANT ALL PRIVILEGES ON \`millions_transaction\`.* TO 'millions_user'@'%';

-- Tạo user chỉ đọc cho slave
CREATE USER 'app_readonly'@'192.168.1.101' IDENTIFIED BY 'FormosVN@123';
GRANT SELECT ON millions_transaction.* TO 'app_readonly'@'192.168.1.101';

FLUSH PRIVILEGES;
"

echo "✅ Đã tạo users và database trên Master"
```

### Bước 4: Kiểm Tra Cấu Hình Master

```bash
# Kiểm tra GTID và server-id
mysql -u root -pFormosVN@123 -e "
SELECT 
  'GTID Mode' as Setting, 
  @@gtid_mode as Value
UNION ALL
SELECT 
  'Server ID' as Setting, 
  @@server_id as Value
UNION ALL
SELECT 
  'Binary Log' as Setting, 
  @@log_bin as Value;
"

# Kiểm tra binary log status
mysql -u root -pFormosVN@123 -e "SHOW MASTER STATUS;"

# Kiểm tra users đã tạo
mysql -u root -pFormosVN@123 -e "
SELECT User, Host, authentication_string 
FROM mysql.user 
WHERE User IN ('repl_user', 'millions_user', 'app_readonly');"
```

## ⚙️ Cấu Hình Slave Server (Máy 2 - IP: 192.168.1.101)

### Bước 1: Backup và Tạo File Cấu Hình

```bash
# Backup file cấu hình hiện tại
sudo cp /etc/mysql/mysql.conf.d/mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf.backup

# Tạo file cấu hình mới cho Slave
sudo tee /etc/mysql/mysql.conf.d/mysqld.cnf > /dev/null <<EOF
[mysqld]
# Basic Settings
user = mysql
pid-file = /var/run/mysqld/mysqld.pid
socket = /var/run/mysqld/mysqld.sock
port = 3306
basedir = /usr
datadir = /var/lib/mysql
tmpdir = /tmp
lc-messages-dir = /usr/share/mysql

# Network
bind-address = 0.0.0.0
max_connections = 300
max_connect_errors = 1000

# Identity - QUAN TRỌNG: server-id phải khác với master
server-id = 2

# Authentication
default_authentication_plugin = mysql_native_password

# Binary Logging & GTID - CẤU HÌNH REPLICATION
log-bin = mysql-bin
binlog_format = ROW
binlog_row_image = MINIMAL
gtid_mode = ON
enforce_gtid_consistency = ON
sync_binlog = 1
log_slave_updates = ON
relay_log = mysql-relay-bin

# InnoDB Settings - TỐI ƯU CHO READ OPERATIONS
innodb_buffer_pool_size = 1G
innodb_log_file_size = 512M
innodb_flush_log_at_trx_commit = 1
innodb_flush_method = O_DIRECT

# Character Set
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# Timezone
default-time-zone = '+07:00'

# Performance - TỐI ƯU CHO READ
thread_cache_size = 64
table_open_cache = 4096
query_cache_size = 256M
query_cache_type = 1
read_buffer_size = 2M
read_rnd_buffer_size = 8M
sort_buffer_size = 2M

# Logging
log-error = /var/log/mysql/error.log
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 2

# Read-only mode (sẽ được enable sau khi setup xong)
# read_only = 1
# super_read_only = 1
EOF
```

### Bước 2: Khởi Động Lại MySQL

```bash
# Restart MySQL để áp dụng cấu hình
sudo systemctl restart mysql

# Kiểm tra trạng thái
sudo systemctl status mysql

# Kiểm tra log nếu có lỗi
sudo tail -f /var/log/mysql/error.log
```

### Bước 3: Cấu Hình Replication

```bash
# Cấu hình slave kết nối đến master
mysql -u root -pFormosVN@123 -e "
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='192.168.1.100',
  SOURCE_USER='repl_user',
  SOURCE_PASSWORD='repl_password',
  SOURCE_AUTO_POSITION=1;
START REPLICA;
"

echo "✅ Đã cấu hình replication trên Slave"
```

### Bước 4: Kiểm Tra Cấu Hình Slave

```bash
# Kiểm tra GTID và server-id
mysql -u root -pFormosVN@123 -e "
SELECT 
  'GTID Mode' as Setting, 
  @@gtid_mode as Value
UNION ALL
SELECT 
  'Server ID' as Setting, 
  @@server_id as Value
UNION ALL
SELECT 
  'Binary Log' as Setting, 
  @@log_bin as Value;
"

# Kiểm tra trạng thái replication
mysql -u root -pFormosVN@123 -e "SHOW REPLICA STATUS\G"

# Kiểm tra kết nối đến master
mysql -u root -pFormosVN@123 -e "
SELECT 
  CASE WHEN Replica_IO_Running = 'Yes' THEN '✅' ELSE '❌' END as IO_Status,
  CASE WHEN Replica_SQL_Running = 'Yes' THEN '✅' ELSE '❌' END as SQL_Status,
  Seconds_Behind_Master as Lag_Seconds
FROM information_schema.replica_status;
"
```

## 🔥 Cấu Hình Firewall Ubuntu

### Trên Cả 2 Máy:

#### Cấu Hình UFW (Ubuntu Firewall):
```bash
# Kiểm tra trạng thái UFW
sudo ufw status

# Nếu UFW chưa được enable
sudo ufw enable

# Cho phép SSH (quan trọng!)
sudo ufw allow ssh

# Cho phép MySQL từ mạng local
sudo ufw allow from 192.168.1.0/24 to any port 3306

# Hoặc cho phép từ IP cụ thể (an toàn hơn)
sudo ufw allow from 192.168.1.100 to any port 3306
sudo ufw allow from 192.168.1.101 to any port 3306

# Cho phép từ subnet khác nếu cần
sudo ufw allow from 10.0.0.0/8 to any port 3306

# Kiểm tra trạng thái
sudo ufw status numbered

# Test kết nối từ máy khác
telnet 192.168.1.100 3306
telnet 192.168.1.101 3306
```

#### Cấu Hình iptables (nếu không dùng UFW):
```bash
# Cho phép MySQL từ mạng local
sudo iptables -A INPUT -s 192.168.1.0/24 -p tcp --dport 3306 -j ACCEPT

# Lưu rules
sudo iptables-save > /etc/iptables/rules.v4

# Kiểm tra rules
sudo iptables -L -n
```

## 🧪 Kiểm Tra và Test Replication

### Bước 1: Kiểm Tra Kết Nối Mạng

```bash
# Từ máy 1, test kết nối đến máy 2
ping 192.168.1.101
telnet 192.168.1.101 3306

# Từ máy 2, test kết nối đến máy 1
ping 192.168.1.100
telnet 192.168.1.100 3306

# Test MySQL connection
mysql -h 192.168.1.101 -u root -pFormosVN@123 -e "SELECT VERSION();"
mysql -h 192.168.1.100 -u root -pFormosVN@123 -e "SELECT VERSION();"
```

### Bước 2: Kiểm Tra Replication Status

```bash
# Trên máy slave (192.168.1.101)
mysql -u root -pFormosVN@123 -e "SHOW REPLICA STATUS\G"

# Kiểm tra các trường quan trọng:
mysql -u root -pFormosVN@123 -e "
SELECT 
  CASE WHEN Replica_IO_Running = 'Yes' THEN '✅' ELSE '❌' END as IO_Status,
  CASE WHEN Replica_SQL_Running = 'Yes' THEN '✅' ELSE '❌' END as SQL_Status,
  Seconds_Behind_Master as Lag_Seconds,
  Last_IO_Error as Last_IO_Error,
  Last_SQL_Error as Last_SQL_Error
FROM information_schema.replica_status;
"
```

### Bước 3: Test Replication với Dữ Liệu Thực

```bash
# Trên máy master (192.168.1.100)
mysql -u root -pFormosVN@123 -e "
USE \`millions_transaction\`;
CREATE TABLE IF NOT EXISTS replication_test (
  id INT PRIMARY KEY AUTO_INCREMENT,
  test_data VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO replication_test (test_data) VALUES ('Test from master - $(date)');
"

echo "✅ Đã insert dữ liệu test trên Master"

# Đợi 10 giây để replication hoàn thành
sleep 10

# Trên máy slave (192.168.1.101)
mysql -u root -pFormosVN@123 -e "
SELECT 
  COUNT(*) as Record_Count,
  MAX(created_at) as Latest_Record
FROM \`millions_transaction\`.replication_test;
"

# Kiểm tra dữ liệu chi tiết
mysql -u root -pFormosVN@123 -e "
SELECT * FROM \`millions_transaction\`.replication_test 
ORDER BY created_at DESC LIMIT 5;
"
```

### Bước 4: Enable Read-Only Mode trên Slave

```bash
# Trên máy slave (192.168.1.101)
mysql -u root -pFormosVN@123 -e "
SET GLOBAL read_only = 1;
SET GLOBAL super_read_only = 1;
"

echo "✅ Đã enable read-only mode trên Slave"

# Test write operation (sẽ bị từ chối)
mysql -u root -pFormosVN@123 -e "
USE \`millions_transaction\`;
INSERT INTO replication_test (test_data) VALUES ('This should fail');
" 2>&1 | grep -i "read-only" && echo "✅ Read-only mode hoạt động đúng!"
```

### Bước 5: Test Performance

```bash
# Trên máy master - test write performance
time mysql -u root -pFormosVN@123 -e "
USE \`millions_transaction\`;
INSERT INTO replication_test (test_data) 
SELECT CONCAT('Performance test ', NOW()) 
FROM information_schema.tables 
LIMIT 100;
"

# Trên máy slave - test read performance
time mysql -u root -pFormosVN@123 -e "
USE \`millions_transaction\`;
SELECT COUNT(*) FROM replication_test;
"
```

## 📊 Monitoring và Maintenance

### Script Monitoring Tự Động

Tạo script monitoring trên cả 2 máy:

```bash
# Tạo file monitoring script
sudo tee /usr/local/bin/mysql-replication-monitor.sh > /dev/null <<'EOF'
#!/bin/bash

MASTER_IP="192.168.1.100"
SLAVE_IP="192.168.1.101"
ROOT_PASSWORD="FormosVN@123"
LOG_FILE="/var/log/mysql-replication-monitor.log"

echo "=== MySQL Replication Monitor ===" | tee -a $LOG_FILE
echo "Time: $(date)" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE

# Check master status
echo "--- Master Status ($MASTER_IP) ---" | tee -a $LOG_FILE
mysql -h $MASTER_IP -u root -p$ROOT_PASSWORD -e "
SELECT 
  'Master' as Server,
  @@hostname as Hostname,
  @@server_id as Server_ID,
  @@gtid_mode as GTID_Mode,
  (SELECT COUNT(*) FROM information_schema.processlist WHERE command != 'Sleep') as Active_Connections;
" 2>/dev/null | tee -a $LOG_FILE || echo "❌ Cannot connect to master" | tee -a $LOG_FILE

echo "" | tee -a $LOG_FILE

# Check slave status
echo "--- Slave Status ($SLAVE_IP) ---" | tee -a $LOG_FILE
mysql -h $SLAVE_IP -u root -p$ROOT_PASSWORD -e "
SELECT 
  'Slave' as Server,
  @@hostname as Hostname,
  @@server_id as Server_ID,
  @@gtid_mode as GTID_Mode,
  (SELECT COUNT(*) FROM information_schema.processlist WHERE command != 'Sleep') as Active_Connections;
" 2>/dev/null | tee -a $LOG_FILE || echo "❌ Cannot connect to slave" | tee -a $LOG_FILE

echo "" | tee -a $LOG_FILE

# Check replication status
echo "--- Replication Status ---" | tee -a $LOG_FILE
mysql -h $SLAVE_IP -u root -p$ROOT_PASSWORD -e "
SELECT 
  CASE WHEN Replica_IO_Running = 'Yes' THEN '✅' ELSE '❌' END as IO_Status,
  CASE WHEN Replica_SQL_Running = 'Yes' THEN '✅' ELSE '❌' END as SQL_Status,
  Seconds_Behind_Master as Lag_Seconds,
  Last_IO_Error as Last_IO_Error,
  Last_SQL_Error as Last_SQL_Error
FROM information_schema.replica_status;
" 2>/dev/null | tee -a $LOG_FILE || echo "❌ Cannot get replication status" | tee -a $LOG_FILE

echo "" | tee -a $LOG_FILE
echo "=== End Monitor ===" | tee -a $LOG_FILE
EOF

# Cấp quyền thực thi
sudo chmod +x /usr/local/bin/mysql-replication-monitor.sh

# Test script
/usr/local/bin/mysql-replication-monitor.sh
```

### Cron Job cho Monitoring

```bash
# Thêm vào crontab để chạy mỗi 5 phút
echo "*/5 * * * * /usr/local/bin/mysql-replication-monitor.sh" | sudo crontab -

# Kiểm tra crontab
sudo crontab -l

# Xem log monitoring
sudo tail -f /var/log/mysql-replication-monitor.log
```

### Backup Script

```bash
# Tạo script backup
sudo tee /usr/local/bin/mysql-backup.sh > /dev/null <<'EOF'
#!/bin/bash

BACKUP_DIR="/backup/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
ROOT_PASSWORD="FormosVN@123"

# Tạo thư mục backup
sudo mkdir -p $BACKUP_DIR

# Backup master
echo "Backing up Master..."
mysqldump -h 192.168.1.100 -u root -p$ROOT_PASSWORD \
  --single-transaction \
  --routines \
  --triggers \
  --all-databases > $BACKUP_DIR/master_backup_$DATE.sql

# Backup slave
echo "Backing up Slave..."
mysqldump -h 192.168.1.101 -u root -p$ROOT_PASSWORD \
  --single-transaction \
  --routines \
  --triggers \
  --all-databases > $BACKUP_DIR/slave_backup_$DATE.sql

# Compress backups
gzip $BACKUP_DIR/master_backup_$DATE.sql
gzip $BACKUP_DIR/slave_backup_$DATE.sql

# Xóa backup cũ hơn 7 ngày
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete

echo "Backup completed: $DATE"
EOF

sudo chmod +x /usr/local/bin/mysql-backup.sh

# Chạy backup hàng ngày lúc 2:00 AM
echo "0 2 * * * /usr/local/bin/mysql-backup.sh" | sudo crontab -
```

## 🔧 Troubleshooting

### Vấn Đề Thường Gặp

#### 1. Không Kết Nối Được Giữa 2 Máy

```bash
# Kiểm tra kết nối mạng
ping 192.168.1.100
ping 192.168.1.101

# Kiểm tra port
telnet 192.168.1.100 3306
telnet 192.168.1.101 3306

# Kiểm tra firewall
sudo ufw status
sudo iptables -L

# Kiểm tra MySQL service
sudo systemctl status mysql
sudo systemctl restart mysql
```

#### 2. Replication Không Hoạt Động

```bash
# Kiểm tra lỗi trên slave
mysql -u root -pFormosVN@123 -e "SHOW REPLICA STATUS\G" | grep -i error

# Kiểm tra log chi tiết
sudo tail -f /var/log/mysql/error.log

# Reset replication nếu cần
mysql -u root -pFormosVN@123 -e "
STOP REPLICA;
RESET REPLICA ALL;
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='192.168.1.100',
  SOURCE_USER='repl_user',
  SOURCE_PASSWORD='repl_password',
  SOURCE_AUTO_POSITION=1;
START REPLICA;
"
```

#### 3. GTID Mismatch

```bash
# Kiểm tra GTID trên cả 2 máy
mysql -h 192.168.1.100 -u root -pFormosVN@123 -e "SELECT @@GLOBAL.gtid_executed;"
mysql -h 192.168.1.101 -u root -pFormosVN@123 -e "SELECT @@GLOBAL.gtid_executed;"

# Reset GTID nếu cần (CẨN THẬN!)
mysql -u root -pFormosVN@123 -e "RESET MASTER;"
```

#### 4. Performance Issues

```bash
# Kiểm tra slow queries
sudo tail -f /var/log/mysql/slow.log

# Kiểm tra process list
mysql -u root -pFormosVN@123 -e "SHOW PROCESSLIST;"

# Kiểm tra InnoDB status
mysql -u root -pFormosVN@123 -e "SHOW ENGINE INNODB STATUS\G"
```

## 📋 Checklist Deploy Hoàn Chỉnh

### Pre-Deployment
- [ ] Cả 2 máy Ubuntu 20.04/22.04 LTS
- [ ] Cả 2 máy có IP tĩnh
- [ ] Firewall đã được cấu hình
- [ ] Có thể ping giữa 2 máy
- [ ] MySQL 8.0 đã được cài đặt

### Deployment
- [ ] Cấu hình master server (server-id=1)
- [ ] Cấu hình slave server (server-id=2)
- [ ] Tạo replication user
- [ ] Cấu hình replication
- [ ] Test replication
- [ ] Enable read-only trên slave

### Post-Deployment
- [ ] Setup monitoring script
- [ ] Setup backup script
- [ ] Test failover scenario
- [ ] Document connection strings
- [ ] Setup cron jobs

## 🎯 Connection Strings cho Ứng Dụng

### Master (Write Operations)
```yaml
# application.yml
spring:
  datasource:
    master:
      url: jdbc:mysql://192.168.1.100:3306/millions_transaction?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh
      username: millions_user
      password: millions_pass
      driver-class-name: com.mysql.cj.jdbc.Driver
```

### Slave (Read Operations)
```yaml
# application.yml
spring:
  datasource:
    slave:
      url: jdbc:mysql://192.168.1.101:3306/millions_transaction?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh
      username: app_readonly
      password: readonly_pass
      driver-class-name: com.mysql.cj.jdbc.Driver
```

### Direct MySQL Connection
```bash
# Master
mysql -h 192.168.1.100 -u millions_user -pmillions_pass millions_transaction

# Slave
mysql -h 192.168.1.101 -u app_readonly -preadonly_pass millions_transaction
```

## 🚀 Performance Tuning

### Master Server Optimization
```bash
# Thêm vào /etc/mysql/mysql.conf.d/mysqld.cnf
sudo tee -a /etc/mysql/mysql.conf.d/mysqld.cnf > /dev/null <<EOF

# Performance Tuning for Master
innodb_buffer_pool_size = 2G
innodb_log_file_size = 1G
innodb_flush_log_at_trx_commit = 2
sync_binlog = 0
query_cache_size = 512M
max_connections = 500
EOF

sudo systemctl restart mysql
```

### Slave Server Optimization
```bash
# Thêm vào /etc/mysql/mysql.conf.d/mysqld.cnf
sudo tee -a /etc/mysql/mysql.conf.d/mysqld.cnf > /dev/null <<EOF

# Performance Tuning for Slave
innodb_buffer_pool_size = 2G
read_buffer_size = 2M
read_rnd_buffer_size = 8M
sort_buffer_size = 2M
query_cache_size = 512M
max_connections = 500
EOF

sudo systemctl restart mysql
```

## 🔒 Security Best Practices

### 1. Tạo User Riêng cho Ứng Dụng
```bash
# Trên master
mysql -u root -pFormosVN@123 -e "
-- User chỉ đọc cho slave
CREATE USER 'app_readonly'@'192.168.1.101' IDENTIFIED BY 'readonly_pass';
GRANT SELECT ON millions_transaction.* TO 'app_readonly'@'192.168.1.101';

-- User đọc/ghi cho master
CREATE USER 'app_readwrite'@'192.168.1.100' IDENTIFIED BY 'readwrite_pass';
GRANT SELECT, INSERT, UPDATE, DELETE ON millions_transaction.* TO 'app_readwrite'@'192.168.1.100';

FLUSH PRIVILEGES;
"
```

### 2. SSL/TLS Configuration
```bash
# Tạo SSL certificates (trên master)
sudo mysql_ssl_rsa_setup --uid=mysql

# Cấu hình SSL trong my.cnf
sudo tee -a /etc/mysql/mysql.conf.d/mysqld.cnf > /dev/null <<EOF

# SSL Configuration
ssl-ca=/var/lib/mysql/ca.pem
ssl-cert=/var/lib/mysql/server-cert.pem
ssl-key=/var/lib/mysql/server-key.pem
EOF
```

---

**🎉 Chúc bạn deploy thành công MySQL Master-Slave trên 2 máy Ubuntu!**

*Hướng dẫn này cung cấp setup production-ready với hiệu suất cao, monitoring tự động và backup strategy. Nhớ backup dữ liệu trước khi thực hiện!*
