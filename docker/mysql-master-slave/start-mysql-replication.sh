#!/usr/bin/env bash
set -euo pipefail

# -------- Config ----------
COMPOSE_FILE="mysql-master-slave.yml"

MASTER_CN="mysql-master"
SLAVE_CN="mysql-slave"

ROOT_PW="FormosVN@123"
REPL_USER="repl_user"
REPL_PW="repl_password"

APP_DB="millions_transaction"
APP_USER="millions_user"
APP_PW="millions_pass"

# docker compose or docker-compose
if command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  DC="docker compose"
fi
# --------------------------

echo "🧹 Completely cleaning up previous setup..."
$DC -f "$COMPOSE_FILE" down -v
docker volume prune -f

echo "🚀 Starting fresh MySQL Master-Slave replication..."
$DC -f "$COMPOSE_FILE" up -d

echo "⏳ Waiting for MySQL containers to be ready..."

wait_mysql() {
  local cname="$1"
  local pw="$2"
  for i in {1..200}; do
    if docker exec "$cname" mysqladmin ping -uroot -p"$pw" --silent >/dev/null 2>&1; then
      echo "✅ $cname is ready"
      return 0
    fi
    echo "Waiting for $cname... ($i/120)"
    sleep 3
  done
  echo "❌ Error: $cname not ready after 360s"
  docker logs "$cname" || true
  exit 1
}

wait_mysql "$MASTER_CN" "$ROOT_PW"
wait_mysql "$SLAVE_CN" "$ROOT_PW"

mysql_exec() {
  local cname="$1"; shift
  docker exec -i "$cname" mysql -uroot -p"$ROOT_PW" -e "$*"
}

mysql_exec_silent() {
  local cname="$1"; shift
  docker exec -i "$cname" mysql -uroot -p"$ROOT_PW" -N -s -e "$*"
}

echo "🔍 Preflight checks (GTID, server_id)..."
for host in "$MASTER_CN" "$SLAVE_CN"; do
  GMODE=$(mysql_exec_silent "$host" "SHOW VARIABLES LIKE 'gtid_mode';" | awk '{print $2}')
  GCONS=$(mysql_exec_silent "$host" "SHOW VARIABLES LIKE 'enforce_gtid_consistency';" | awk '{print $2}')
  SID=$(mysql_exec_silent "$host" "SHOW VARIABLES LIKE 'server_id';" | awk '{print $2}')
  if [[ "$GMODE" != "ON" || "$GCONS" != "ON" || "$SID" == "0" ]]; then
    echo "❌ $host: gtid_mode=$GMODE, enforce_gtid_consistency=$GCONS, server_id=$SID"
    echo "==> Please check that .cnf files are properly mounted and configured."
    exit 1
  else
    echo "✅ $host: GTID OK (gtid_mode=$GMODE, enforce=$GCONS, server_id=$SID)"
  fi
done

echo "🧹 Resetting GTID state on both servers..."
# Reset GTID state on both master and slave to start fresh
for host in "$MASTER_CN" "$SLAVE_CN"; do
  echo "Resetting GTID state on $host..."
  mysql_exec "$host" "
    STOP REPLICA; 
    STOP SLAVE;
    RESET REPLICA ALL;
    RESET MASTER;
  " 2>/dev/null || mysql_exec "$host" "
    STOP SLAVE;
    RESET SLAVE ALL;
    RESET MASTER;
  " 2>/dev/null || true
done

echo "👤 Creating replication user on master..."
mysql_exec "$MASTER_CN" "
CREATE USER IF NOT EXISTS '$REPL_USER'@'%' IDENTIFIED BY '$REPL_PW';
GRANT REPLICATION SLAVE ON *.* TO '$REPL_USER'@'%';
-- Enable root access from network for management
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '$ROOT_PW';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
"

echo "📊 Creating application database and users on master..."
mysql_exec "$MASTER_CN" "
CREATE DATABASE IF NOT EXISTS \`$APP_DB\`;
CREATE USER IF NOT EXISTS '$APP_USER'@'%' IDENTIFIED BY '$APP_PW';
GRANT ALL PRIVILEGES ON \`$APP_DB\`.* TO '$APP_USER'@'%';
FLUSH PRIVILEGES;
"

echo "🔗 Configuring replication on slave..."
if mysql_exec "$SLAVE_CN" "
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='$MASTER_CN',
  SOURCE_USER='$REPL_USER',
  SOURCE_PASSWORD='$REPL_PW',
  SOURCE_AUTO_POSITION=1;
START REPLICA;
" 2>/dev/null; then
  echo "✅ Using CHANGE REPLICATION SOURCE syntax"
else
  echo "🔄 Fallback to CHANGE MASTER TO syntax..."
  mysql_exec "$SLAVE_CN" "
  CHANGE MASTER TO
    MASTER_HOST='$MASTER_CN',
    MASTER_USER='$REPL_USER',
    MASTER_PASSWORD='$REPL_PW',
    MASTER_AUTO_POSITION=1;
  START SLAVE;
  "
fi

echo "⏳ Waiting 30s for replica to establish connection..."
sleep 30

echo "🔍 Checking replica status..."
STATUS=$(mysql_exec "$SLAVE_CN" "SHOW REPLICA STATUS\G" 2>/dev/null || mysql_exec "$SLAVE_CN" "SHOW SLAVE STATUS\G" 2>/dev/null || true)

# Show current GTID state
echo ""
echo "📊 Current GTID state:"
MASTER_GTID=$(mysql_exec_silent "$MASTER_CN" "SELECT @@GLOBAL.gtid_executed;" 2>/dev/null || echo "N/A")
SLAVE_GTID=$(mysql_exec_silent "$SLAVE_CN" "SELECT @@GLOBAL.gtid_executed;" 2>/dev/null || echo "N/A")
echo "Master GTID: $MASTER_GTID"
echo "Slave GTID:  $SLAVE_GTID"
echo ""

echo "$STATUS"

ok_flag=0
if echo "$STATUS" | grep -qE "Replica_IO_Running: Yes|Slave_IO_Running: Yes"; then
  if echo "$STATUS" | grep -qE "Replica_SQL_Running: Yes|Slave_SQL_Running: Yes"; then
    ok_flag=1
  fi
fi

if [[ "$ok_flag" -eq 1 ]]; then
  echo "✅ Replication is working!"
  
  echo "⏳ Waiting for initial replication to sync..."
  sleep 5
  
  echo "🔒 Enabling read-only mode on slave..."
  mysql_exec "$SLAVE_CN" "
  SET GLOBAL read_only = 1;
  SET GLOBAL super_read_only = 1;
  "
  
  echo "🧪 Testing replication with a simple test..."
  mysql_exec "$MASTER_CN" "
  USE \`$APP_DB\`;
  CREATE TABLE IF NOT EXISTS replication_test (
    id INT PRIMARY KEY AUTO_INCREMENT,
    test_data VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
  INSERT INTO replication_test (test_data) VALUES ('Replication test - $(date)');
  "
  
  echo "⏳ Waiting 10s for replication..."
  sleep 10
  
  echo "🔍 Checking test data on slave..."
  TEST_RESULT=$(mysql_exec_silent "$SLAVE_CN" "SELECT COUNT(*) FROM \`$APP_DB\`.replication_test;" 2>/dev/null || echo "0")
  if [[ "$TEST_RESULT" -gt 0 ]]; then
    echo "✅ Replication test passed! Found $TEST_RESULT record(s) on slave."
  else
    echo "⚠️  Replication test failed - no data found on slave."
  fi
  
else
  echo "❌ Replication not healthy. Key fields:"
  echo "$STATUS" | grep -E "(Replica_IO_Running|Replica_SQL_Running|Slave_IO_Running|Slave_SQL_Running|Last_IO_Error|Last_SQL_Error|Seconds_Behind_Master)" || true
  
  echo ""
  echo "🔍 Checking for detailed error information..."
  mysql_exec "$SLAVE_CN" "
  SELECT THREAD_ID, SERVICE_STATE, LAST_ERROR_NUMBER, LAST_ERROR_MESSAGE 
  FROM performance_schema.replication_applier_status_by_worker 
  WHERE LAST_ERROR_MESSAGE != '';
  " 2>/dev/null || true
  
  echo ""
  echo "📊 Master binary log status:"
  mysql_exec "$MASTER_CN" "SHOW MASTER STATUS;" || true
  
  exit 2
fi

echo ""
echo "🎉 MySQL 8.x Master–Slave setup completed successfully!"
echo ""
echo "📡 Connection Details:"
echo "Master: localhost:3307"
echo "Slave : localhost:3308"
echo "Admin : http://localhost:8089"
echo ""
echo "🧪 Test Commands:"
echo "Master: mysql -h localhost -P 3307 -u root -p$ROOT_PW -e 'SELECT VERSION();'"
echo "Slave : mysql -h localhost -P 3308 -u root -p$ROOT_PW -e 'SELECT VERSION();'"
echo ""
echo "🔍 Replication Status:"
echo "Check: mysql -h localhost -P 3308 -u root -p$ROOT_PW -e 'SHOW REPLICA STATUS\G'"
echo ""
echo "🗄️  Application Database:"
echo "DB: $APP_DB"
echo "User: $APP_USER"
echo "Password: $APP_PW"