package com.hnh.example.transaction_example.testutils;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test configuration using local docker-compose services
 * Make sure to run: cd docker && docker-compose up -d
 */
@TestConfiguration
public class TestContainerConfig {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL configuration - using H2 for tests
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.jdbc.Driver");

        // Kafka configuration - using local docker-compose service
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");

        // Disable Liquibase for tests
        registry.add("spring.liquibase.enabled", () -> "false");

        // Enable JPA DDL auto for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Test-specific configurations
        registry.add("logging.level.com.hnh.example", () -> "DEBUG");
        registry.add("logging.level.org.springframework.security", () -> "DEBUG");
    }

    @Bean
    public String testInfo() {
        return "Using local docker-compose services. Make sure to run: cd docker && docker-compose up -d";
    }
}
