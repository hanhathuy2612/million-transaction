#!/bin/bash
set -euo pipefail

MASTER_HOST="${MASTER_HOST:-mysql-master}"
SLAVE_HOST="${SLAVE_HOST:-127.0.0.1}"

ROOT_PW="${MYSQL_ROOT_PASSWORD:-FormosVN@123}"
REPL_USER="${REPL_USER:-repl_user}"
REPL_PW="${REPL_PW:-repl_password}"

APP_DB="${APP_DB:-millions_transaction}"
APP_USER="${APP_USER:-millions_user}"
APP_PW="${APP_PW:-millions_pass}"

mysql_master() {
  mysql -h "$MASTER_HOST" -uroot -p"$ROOT_PW" -N -s "$@"
}

mysql_master_exec() {
  mysql -h "$MASTER_HOST" -uroot -p"$ROOT_PW" "$@"
}

mysql_slave() {
  mysql -h "$SLAVE_HOST" -uroot -p"$ROOT_PW" -N -s "$@"
}

mysql_slave_exec() {
  mysql -h "$SLAVE_HOST" -uroot -p"$ROOT_PW" "$@"
}

replication_healthy() {
  local status
  status=$(mysql_slave_exec -e "SHOW REPLICA STATUS\G" 2>/dev/null \
    || mysql_slave_exec -e "SHOW SLAVE STATUS\G" 2>/dev/null \
    || true)
  echo "$status" | grep -qE "Replica_IO_Running: Yes|Slave_IO_Running: Yes" \
    && echo "$status" | grep -qE "Replica_SQL_Running: Yes|Slave_SQL_Running: Yes"
}

echo "[mysql-slave bootstrap] waiting for master..."
for i in $(seq 1 60); do
  if mysqladmin ping -h "$MASTER_HOST" -uroot -p"$ROOT_PW" --silent >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[mysql-slave bootstrap] waiting for local mysql..."
for i in $(seq 1 60); do
  if mysqladmin ping -h "$SLAVE_HOST" -uroot -p"$ROOT_PW" --silent >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if replication_healthy; then
  echo "[mysql-slave bootstrap] replication already running"
  mysql_slave_exec -e "SET GLOBAL read_only = 1; SET GLOBAL super_read_only = 1;" 2>/dev/null || true
  exit 0
fi

echo "[mysql-slave bootstrap] configuring replication..."

for host in "$MASTER_HOST" "$SLAVE_HOST"; do
  GMODE=$(mysql -h "$host" -uroot -p"$ROOT_PW" -N -s -e "SHOW VARIABLES LIKE 'gtid_mode';" | awk '{print $2}')
  GCONS=$(mysql -h "$host" -uroot -p"$ROOT_PW" -N -s -e "SHOW VARIABLES LIKE 'enforce_gtid_consistency';" | awk '{print $2}')
  SID=$(mysql -h "$host" -uroot -p"$ROOT_PW" -N -s -e "SHOW VARIABLES LIKE 'server_id';" | awk '{print $2}')
  if [[ "$GMODE" != "ON" || "$GCONS" != "ON" || "$SID" == "0" ]]; then
    echo "[mysql-slave bootstrap] GTID check failed on $host: gtid_mode=$GMODE enforce=$GCONS server_id=$SID"
    exit 1
  fi
done

mysql_slave_exec -e "STOP REPLICA; RESET REPLICA ALL;" 2>/dev/null \
  || mysql_slave_exec -e "STOP SLAVE; RESET SLAVE ALL;" 2>/dev/null \
  || true

mysql_master_exec -e "
CREATE USER IF NOT EXISTS '$REPL_USER'@'%' IDENTIFIED WITH mysql_native_password BY '$REPL_PW';
GRANT REPLICATION SLAVE ON *.* TO '$REPL_USER'@'%';
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED WITH mysql_native_password BY '$ROOT_PW';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
CREATE DATABASE IF NOT EXISTS \`$APP_DB\`;
CREATE USER IF NOT EXISTS '$APP_USER'@'%' IDENTIFIED WITH mysql_native_password BY '$APP_PW';
GRANT ALL PRIVILEGES ON \`$APP_DB\`.* TO '$APP_USER'@'%';
FLUSH PRIVILEGES;
"

if mysql_slave_exec -e "
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='$MASTER_HOST',
  SOURCE_USER='$REPL_USER',
  SOURCE_PASSWORD='$REPL_PW',
  SOURCE_AUTO_POSITION=1;
START REPLICA;
" 2>/dev/null; then
  echo "[mysql-slave bootstrap] started replica (SOURCE syntax)"
else
  mysql_slave_exec -e "
  CHANGE MASTER TO
    MASTER_HOST='$MASTER_HOST',
    MASTER_USER='$REPL_USER',
    MASTER_PASSWORD='$REPL_PW',
    MASTER_AUTO_POSITION=1;
  START SLAVE;
  "
  echo "[mysql-slave bootstrap] started replica (MASTER syntax)"
fi

for i in $(seq 1 30); do
  if replication_healthy; then
    mysql_slave_exec -e "SET GLOBAL read_only = 1; SET GLOBAL super_read_only = 1;"
    echo "[mysql-slave bootstrap] replication is healthy"
    exit 0
  fi
  sleep 2
done

echo "[mysql-slave bootstrap] replication failed to become healthy"
mysql_slave_exec -e "SHOW REPLICA STATUS\G" 2>/dev/null || mysql_slave_exec -e "SHOW SLAVE STATUS\G" 2>/dev/null || true
exit 1
