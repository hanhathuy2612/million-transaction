#!/bin/bash

echo "Testing MySQL Master-Slave functionality..."

# Test master database connection
echo "Testing master database connection..."
docker exec mysql-master mysql -uroot -pFormosVN@123 -e "
USE millions_transaction;
CREATE TABLE IF NOT EXISTS test_replication (
    id INT AUTO_INCREMENT PRIMARY KEY,
    message VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO test_replication (message) VALUES ('Test from master');
SELECT * FROM test_replication;
"

# Wait for replication
echo "Waiting for replication to slave..."
sleep 5

# Test slave database connection and check replicated data
echo "Testing slave database connection and checking replicated data..."
docker exec mysql-slave mysql -uroot -pFormosVN@123 -e "
USE millions_transaction;
SELECT * FROM test_replication;
"

# Test read-only on slave
echo "Testing read-only constraint on slave..."
docker exec mysql-slave mysql -uroot -pFormosVN@123 -e "
USE millions_transaction;
INSERT INTO test_replication (message) VALUES ('This should fail on slave');
" 2>&1 | grep -q "read-only" && echo "✓ Slave is properly read-only" || echo "✗ Slave is not read-only"

# Check replication status
echo "Checking replication status..."
docker exec mysql-slave mysql -uroot -pFormosVN@123 -e "SHOW SLAVE STATUS\G" | grep -E "(Slave_IO_Running|Slave_SQL_Running|Seconds_Behind_Master)"

# Cleanup test data
echo "Cleaning up test data..."
docker exec mysql-master mysql -uroot -pFormosVN@123 -e "
USE millions_transaction;
DROP TABLE IF EXISTS test_replication;
"

echo "Master-Slave test completed!"
