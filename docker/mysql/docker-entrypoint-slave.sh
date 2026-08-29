#!/bin/bash
set -e

(
  for i in $(seq 1 90); do
    if mysqladmin ping -h127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
      /usr/local/bin/slave-bootstrap.sh && exit 0
      echo "[mysql-slave] bootstrap failed; check logs"
      exit 0
    fi
    sleep 2
  done
  echo "[mysql-slave] bootstrap timed out waiting for mysql"
) &

exec /usr/local/bin/docker-entrypoint.sh "$@"
