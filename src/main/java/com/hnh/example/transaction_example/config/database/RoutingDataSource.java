package com.hnh.example.transaction_example.config.database;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.lang.NonNull;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Routing data source for master-slave database.
 * Automatically route to slave database for read-only transactions or no
 * transaction.
 * Route to master database for write transactions.
 */
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        // Only route to slave if it's explicitly marked as read-only transaction
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        boolean hasActiveTransaction = TransactionSynchronizationManager.isActualTransactionActive();

        // Route to SLAVE only if:
        // 1. There is an active transaction AND
        // 2. It's explicitly marked as read-only
        if (hasActiveTransaction && isReadOnly) {
            log.debug("Routing to SLAVE datasource (active read-only transaction)");
            return DatabaseConfig.DataSourceType.SLAVE;
        }

        // Route to MASTER in all other cases:
        // - Write transactions (active transaction but not read-only)
        // - No transaction context (default to master for safety)
        log.debug("Routing to MASTER datasource (write transaction or no explicit read-only)");
        return DatabaseConfig.DataSourceType.MASTER;
    }

    @Override
    @NonNull
    public DataSource determineTargetDataSource() {
        DataSource targetDataSource = super.determineTargetDataSource();
        log.debug("Selected datasource: {}", targetDataSource.getClass().getSimpleName());
        return targetDataSource;
    }
}
