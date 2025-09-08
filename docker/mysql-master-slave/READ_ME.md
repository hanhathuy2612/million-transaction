# MySQL Master-Slave Replication Setup Guide

## 📋 Overview

This guide provides a complete step-by-step setup for MySQL 8.0 Master-Slave replication using Docker. The setup includes:

- **Master Server**: Handles all write operations (INSERT, UPDATE, DELETE)
- **Slave Server**: Handles read operations and replicates data from master
- **GTID-based Replication**: Global Transaction Identifier for reliable replication
- **Adminer**: Web-based database administration tool

## 🎯 Architecture

```
┌─────────────────┐    ┌─────────────────┐
│   MySQL Master  │    │   MySQL Slave   │
│   Port: 3307    │───▶│   Port: 3308    │
│   Read/Write    │    │   Read-Only     │
└─────────────────┘    └─────────────────┘
         │                       │
         └───────────────────────┼─────────────────┐
                                 │                 │
                    ┌─────────────────┐    ┌─────────────────┐
                    │    Adminer      │    │   Application   │
                    │   Port: 8089    │    │   Database      │
                    │   Web Admin     │    │   millions_     │
                    └─────────────────┘    │   transaction   │
                                          └─────────────────┘
```

## 🔧 Prerequisites

### System Requirements
- **Docker**: Version 20.10+ 
- **Docker Compose**: Version 2.0+
- **RAM**: Minimum 4GB (8GB recommended)
- **Disk Space**: At least 10GB free space
- **OS**: Linux, macOS, or Windows with Docker Desktop

### Software Installation
```bash
# Check Docker installation
docker --version
docker-compose --version

# If not installed, install Docker:
# Ubuntu/Debian:
sudo apt update && sudo apt install docker.io docker-compose

# macOS: Download Docker Desktop from https://docker.com
# Windows: Download Docker Desktop from https://docker.com
```

## 🚀 Quick Start (Automated Setup)

### 1. Navigate to the Setup Directory
```bash
cd /path/to/your/project/docker/mysql-master-slave
```

### 2. Run the Automated Setup Script
```bash
# Make the script executable
chmod +x start-mysql-replication.sh

# Run the setup script
./start-mysql-replication.sh
```

The script will:
- ✅ Clean up any previous setup
- ✅ Start MySQL containers
- ✅ Configure master-slave replication
- ✅ Create application database and users
- ✅ Test replication functionality
- ✅ Enable read-only mode on slave

### 3. Verify Setup
```bash
# Check container status
docker ps

# Test master connection
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "SELECT VERSION();"

# Test slave connection
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SELECT VERSION();"

# Check replication status
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SHOW REPLICA STATUS\G"
```

## 📖 Manual Setup (Step-by-Step)

If you prefer to understand each step or need to troubleshoot, follow this manual process:

### Step 1: Prepare Configuration Files

The setup includes these configuration files:

#### `mysql-master.cnf`
```ini
[mysqld]
server-id = 1
default_authentication_plugin = mysql_native_password
log-bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce_gtid_consistency = ON
# ... (see full file for complete configuration)
```

#### `mysql-slave.cnf`
```ini
[mysqld]
server-id = 2
default_authentication_plugin = mysql_native_password
log-bin = mysql-bin
gtid_mode = ON
enforce_gtid_consistency = ON
# ... (see full file for complete configuration)
```

### Step 2: Start MySQL Containers

```bash
# Start the containers
docker-compose -f mysql-master-slave.yml up -d

# Check container status
docker ps
```

Expected output:
```
CONTAINER ID   IMAGE       COMMAND                  CREATED         STATUS         PORTS                    NAMES
abc123def456   mysql:8.0   "docker-entrypoint.s…"   2 minutes ago   Up 2 minutes   0.0.0.0:3307->3306/tcp   mysql-master
def456ghi789   mysql:8.0   "docker-entrypoint.s…"   2 minutes ago   Up 2 minutes   0.0.0.0:3308->3306/tcp   mysql-slave
ghi789jkl012   adminer     "entrypoint.sh php -S…"  2 minutes ago   Up 2 minutes   0.0.0.0:8089->8080/tcp   adminer
```

### Step 3: Wait for MySQL to be Ready

```bash
# Wait for master to be ready
docker exec mysql-master mysqladmin ping -uroot -pFormosVN@123 --silent

# Wait for slave to be ready  
docker exec mysql-slave mysqladmin ping -uroot -pFormosVN@123 --silent
```

### Step 4: Configure Master Server

```bash
# Connect to master and create replication user
docker exec -i mysql-master mysql -uroot -pFormosVN@123 -e "
CREATE USER IF NOT EXISTS 'repl_user'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'FormosVN@123';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
"

# Create application database and user
docker exec -i mysql-master mysql -uroot -pFormosVN@123 -e "
CREATE DATABASE IF NOT EXISTS \`millions_transaction\`;
CREATE USER IF NOT EXISTS 'millions_user'@'%' IDENTIFIED BY 'millions_pass';
GRANT ALL PRIVILEGES ON \`millions_transaction\`.* TO 'millions_user'@'%';
FLUSH PRIVILEGES;
"
```

### Step 5: Configure Slave Server

```bash
# Configure replication on slave
docker exec -i mysql-slave mysql -uroot -pFormosVN@123 -e "
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='mysql-master',
  SOURCE_USER='repl_user',
  SOURCE_PASSWORD='repl_password',
  SOURCE_AUTO_POSITION=1;
START REPLICA;
"
```

### Step 6: Verify Replication

```bash
# Check replication status
docker exec -i mysql-slave mysql -uroot -pFormosVN@123 -e "SHOW REPLICA STATUS\G"

# Test replication with sample data
docker exec -i mysql-master mysql -uroot -pFormosVN@123 -e "
USE \`millions_transaction\`;
CREATE TABLE IF NOT EXISTS replication_test (
  id INT PRIMARY KEY AUTO_INCREMENT,
  test_data VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO replication_test (test_data) VALUES ('Test data from master');
"

# Check if data replicated to slave
docker exec -i mysql-slave mysql -uroot -pFormosVN@123 -e "
SELECT * FROM \`millions_transaction\`.replication_test;
"
```

### Step 7: Enable Read-Only Mode on Slave

```bash
# Enable read-only mode on slave
docker exec -i mysql-slave mysql -uroot -pFormosVN@123 -e "
SET GLOBAL read_only = 1;
SET GLOBAL super_read_only = 1;
"
```

## 🔍 Verification and Testing

### Connection Details
- **Master**: `localhost:3307` (Read/Write)
- **Slave**: `localhost:3308` (Read-Only)
- **Adminer**: `http://localhost:8089` (Web Admin)

### Test Commands

#### Basic Connectivity
```bash
# Test master
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "SELECT VERSION();"

# Test slave
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SELECT VERSION();"
```

#### Replication Status
```bash
# Check replication status
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SHOW REPLICA STATUS\G"

# Look for these key fields:
# - Replica_IO_Running: Yes
# - Replica_SQL_Running: Yes
# - Seconds_Behind_Master: 0 (or small number)
```

#### Application Database Access
```bash
# Connect to application database on master
mysql -h localhost -P 3307 -u millions_user -pmillions_pass millions_transaction

# Connect to application database on slave (read-only)
mysql -h localhost -P 3308 -u millions_user -pmillions_pass millions_transaction
```

### Web Administration
1. Open browser and go to `http://localhost:8089`
2. Use these credentials:
   - **System**: MySQL
   - **Server**: `mysql-master` or `mysql-slave`
   - **Username**: `root`
   - **Password**: `FormosVN@123`
   - **Database**: `millions_transaction`

## 🛠️ Troubleshooting

### Common Issues and Solutions

#### 1. Containers Won't Start
```bash
# Check logs
docker logs mysql-master
docker logs mysql-slave

# Check if ports are in use
netstat -tulpn | grep :3307
netstat -tulpn | grep :3308

# Clean up and restart
docker-compose -f mysql-master-slave.yml down -v
docker system prune -f
```

#### 2. Replication Not Working
```bash
# Check GTID configuration
docker exec mysql-master mysql -uroot -pFormosVN@123 -e "SHOW VARIABLES LIKE 'gtid_mode';"
docker exec mysql-slave mysql -uroot -pFormosVN@123 -e "SHOW VARIABLES LIKE 'gtid_mode';"

# Check server IDs
docker exec mysql-master mysql -uroot -pFormosVN@123 -e "SHOW VARIABLES LIKE 'server_id';"
docker exec mysql-slave mysql -uroot -pFormosVN@123 -e "SHOW VARIABLES LIKE 'server_id';"

# Reset replication if needed
docker exec mysql-slave mysql -uroot -pFormosVN@123 -e "
STOP REPLICA;
RESET REPLICA ALL;
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='mysql-master',
  SOURCE_USER='repl_user',
  SOURCE_PASSWORD='repl_password',
  SOURCE_AUTO_POSITION=1;
START REPLICA;
"
```

#### 3. Permission Denied Errors
```bash
# Check user permissions
docker exec mysql-master mysql -uroot -pFormosVN@123 -e "
SELECT User, Host FROM mysql.user WHERE User IN ('repl_user', 'millions_user');
SHOW GRANTS FOR 'repl_user'@'%';
SHOW GRANTS FOR 'millions_user'@'%';
"
```

#### 4. Data Not Replicating
```bash
# Check binary log status on master
docker exec mysql-master mysql -uroot -pFormosVN@123 -e "SHOW MASTER STATUS;"

# Check for errors in replication
docker exec mysql-slave mysql -uroot -pFormosVN@123 -e "
SELECT THREAD_ID, SERVICE_STATE, LAST_ERROR_NUMBER, LAST_ERROR_MESSAGE 
FROM performance_schema.replication_applier_status_by_worker 
WHERE LAST_ERROR_MESSAGE != '';
"
```

### Log Locations
```bash
# Container logs
docker logs mysql-master
docker logs mysql-slave

# MySQL error logs (inside containers)
docker exec mysql-master tail -f /var/log/mysql/error.log
docker exec mysql-slave tail -f /var/log/mysql/error.log
```

## 📊 Monitoring and Maintenance

### Daily Monitoring Commands

#### Check Replication Health
```bash
#!/bin/bash
# save as check_replication.sh

echo "=== Replication Status ==="
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "
SELECT 
  'IO Thread' as Thread,
  CASE WHEN Replica_IO_Running = 'Yes' THEN 'Running' ELSE 'Stopped' END as Status,
  Last_IO_Error as Last_Error
FROM information_schema.replica_status
UNION ALL
SELECT 
  'SQL Thread' as Thread,
  CASE WHEN Replica_SQL_Running = 'Yes' THEN 'Running' ELSE 'Stopped' END as Status,
  Last_SQL_Error as Last_Error
FROM information_schema.replica_status;
"

echo "=== Lag Check ==="
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "
SELECT Seconds_Behind_Master as 'Lag (seconds)' 
FROM information_schema.replica_status;
"
```

#### Performance Monitoring
```bash
# Check connection counts
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "SHOW STATUS LIKE 'Threads_connected';"
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SHOW STATUS LIKE 'Threads_connected';"

# Check binary log size
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "SHOW BINARY LOGS;"
```

### Backup Procedures

#### Master Backup
```bash
# Create backup
docker exec mysql-master mysqldump -uroot -pFormosVN@123 \
  --single-transaction \
  --routines \
  --triggers \
  --all-databases > master_backup_$(date +%Y%m%d_%H%M%S).sql

# Compress backup
gzip master_backup_*.sql
```

#### Slave Backup (Point-in-Time Recovery)
```bash
# Get slave position
SLAVE_POS=$(mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SHOW SLAVE STATUS\G" | grep "Exec_Master_Log_Pos" | awk '{print $2}')

# Create backup with position
docker exec mysql-slave mysqldump -uroot -pFormosVN@123 \
  --single-transaction \
  --routines \
  --triggers \
  --all-databases > slave_backup_$(date +%Y%m%d_%H%M%S).sql

echo "Slave position: $SLAVE_POS" >> slave_backup_*.sql
```

### Maintenance Tasks

#### Weekly Tasks
```bash
# Optimize tables
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "OPTIMIZE TABLE millions_transaction.*;"

# Clean old binary logs (keep last 7 days)
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "PURGE BINARY LOGS BEFORE DATE_SUB(NOW(), INTERVAL 7 DAY);"
```

#### Monthly Tasks
```bash
# Update table statistics
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "ANALYZE TABLE millions_transaction.*;"

# Check for table corruption
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "CHECK TABLE millions_transaction.*;"
```

## 🔧 Configuration Tuning

### Performance Optimization

#### Master Configuration (`mysql-master.cnf`)
```ini
# For high-write workloads
innodb_buffer_pool_size = 2G          # 50-70% of available RAM
innodb_log_file_size = 1G             # 25% of buffer pool size
innodb_flush_log_at_trx_commit = 2    # Better performance, less durability
sync_binlog = 0                       # Better performance, less durability

# For high-read workloads
query_cache_size = 256M
query_cache_type = 1
```

#### Slave Configuration (`mysql-slave.cnf`)
```ini
# Optimize for read operations
innodb_buffer_pool_size = 2G
read_buffer_size = 2M
read_rnd_buffer_size = 8M
sort_buffer_size = 2M
```

### Security Hardening

#### Network Security
```bash
# Restrict access to specific IPs (modify docker-compose.yml)
# Add to environment section:
# MYSQL_ROOT_HOST: "192.168.1.100"

# Use strong passwords
# Change default passwords in start-mysql-replication.sh
```

#### User Privileges
```bash
# Create application-specific users with minimal privileges
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "
CREATE USER 'app_readonly'@'%' IDENTIFIED BY 'strong_password';
GRANT SELECT ON millions_transaction.* TO 'app_readonly'@'%';

CREATE USER 'app_readwrite'@'%' IDENTIFIED BY 'strong_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON millions_transaction.* TO 'app_readwrite'@'%';
"
```

## 📚 Additional Resources

### Useful Commands Reference

#### Replication Management
```bash
# Stop replication
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "STOP REPLICA;"

# Start replication
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "START REPLICA;"

# Skip error and continue
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SET GLOBAL sql_slave_skip_counter = 1; START REPLICA;"

# Reset replication completely
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "STOP REPLICA; RESET REPLICA ALL;"
```

#### GTID Management
```bash
# Check GTID status
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "SELECT @@GLOBAL.gtid_executed;"
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "SELECT @@GLOBAL.gtid_executed;"

# Reset GTID (use with caution)
mysql -h localhost -P 3307 -u root -pFormosVN@123 -e "RESET MASTER;"
mysql -h localhost -P 3308 -u root -pFormosVN@123 -e "RESET MASTER;"
```

### Documentation Links
- [MySQL 8.0 Replication Documentation](https://dev.mysql.com/doc/refman/8.0/en/replication.html)
- [GTID Replication Guide](https://dev.mysql.com/doc/refman/8.0/en/replication-gtids.html)
- [Docker MySQL Official Image](https://hub.docker.com/_/mysql)

## 🆘 Support

If you encounter issues not covered in this guide:

1. **Check the logs**: `docker logs mysql-master` and `docker logs mysql-slave`
2. **Verify configuration**: Ensure all `.cnf` files are properly mounted
3. **Test connectivity**: Use the verification commands in this guide
4. **Reset if needed**: Use the automated script to start fresh

---

**Happy Replicating! 🚀**

*This guide provides a production-ready MySQL master-slave replication setup. For production use, consider additional security measures, monitoring, and backup strategies.*
