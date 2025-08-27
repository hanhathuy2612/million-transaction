package com.hnh.example.transaction_example.config.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Health indicator to check the health of master and slave databases.
 * Reports UP if both are healthy, DOWN if master is down, and
 */
@Component
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource masterDataSource;
    private final DataSource slaveDataSource;

    public DatabaseHealthIndicator(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource") DataSource slaveDataSource) {
        this.masterDataSource = masterDataSource;
        this.slaveDataSource = slaveDataSource;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();

        boolean masterHealthy = checkDataSource(masterDataSource, "Master");
        boolean slaveHealthy = checkDataSource(slaveDataSource, "Slave");

        if (masterHealthy && slaveHealthy) {
            builder.up()
                    .withDetail("master", "UP")
                    .withDetail("slave", "UP")
                    .withDetail("message", "Both master and slave databases are healthy");
        } else if (masterHealthy) {
            builder.up()
                    .withDetail("master", "UP")
                    .withDetail("slave", "DOWN")
                    .withDetail("message", "Master database is healthy, slave is down");
        } else {
            builder.down()
                    .withDetail("master", "DOWN")
                    .withDetail("slave", slaveHealthy ? "UP" : "DOWN")
                    .withDetail("message", "Master database is down");
        }

        return builder.build();
    }

    private boolean checkDataSource(DataSource dataSource, String name) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                log.debug("{} database connection is healthy", name);
                return true;
            } else {
                log.warn("{} database connection is not valid", name);
                return false;
            }
        } catch (SQLException e) {
            log.error("Error checking {} database health: {}", name, e.getMessage());
            return false;
        }
    }
}
