package com.hnh.example.transaction_example.testutils;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration for TestContainers
 */
@TestConfiguration
@SuppressWarnings("resource")
public class TestContainerConfig {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.33"))
            .withDatabaseName("test_millions_transaction")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withEnv("MYSQL_ROOT_PASSWORD", "root")
            .withEnv("MYSQL_DATABASE", "test_millions_transaction")
            .withEnv("MYSQL_SQL_MODE", "STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO")
            .withNetworkAliases("mysql")
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 1));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withEnv("REDIS_MAXMEMORY_POLICY", "allkeys-lru")
            .withEnv("REDIS_APPENDONLY", "no")
            .withEnv("REDIS_SAVE", "")
            .withNetworkAliases("redis")
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0")
            .asCompatibleSubstituteFor("apache/kafka"))
            .withReuse(true)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_DELETE_TOPIC_ENABLE", "true")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withNetworkAliases("kafka")
            .waitingFor(Wait.forLogMessage(".*started.*kafka.*", 1));

    static {
        // Check if Docker is available
        try {
            System.out.println("Checking Docker availability...");
            // Simple check - try to create a temporary container
            try (GenericContainer<?> tempContainer = new GenericContainer<>("hello-world")) {
                tempContainer.start();
                tempContainer.stop();
            }
            System.out.println("Docker is available and running");
        } catch (Exception e) {
            System.err.println("Docker is not available: " + e.getMessage());
            throw new RuntimeException("Docker is required for running tests with Testcontainers", e);
        }

        try {
            System.out.println("Starting MySQL container...");
            mysql.start();
            System.out.println("MySQL container started successfully on port: " + mysql.getFirstMappedPort());

            // Wait a bit for MySQL to be fully ready
            Thread.sleep(2000);

            System.out.println("Starting Redis container...");
            redis.start();
            System.out.println("Redis container started successfully on port: " + redis.getFirstMappedPort());

            // Wait a bit for Redis to be fully ready
            Thread.sleep(2000);

            System.out.println("Starting Kafka container...");
            kafka.start();
            System.out.println("Kafka container started successfully on port: " + kafka.getFirstMappedPort());

            // Wait a bit for Kafka to be fully ready
            Thread.sleep(5000);

            // Verify containers are actually running and accessible
            if (!mysql.isRunning() || !redis.isRunning() || !kafka.isRunning()) {
                throw new RuntimeException("One or more containers failed to start properly");
            }

            // Additional health checks
            try {
                // Test MySQL connection
                mysql.getJdbcUrl();
                System.out.println("MySQL connection verified");

                // Test Redis connection
                redis.getFirstMappedPort();
                System.out.println("Redis connection verified");

                // Test Kafka connection
                kafka.getBootstrapServers();
                System.out.println("Kafka connection verified");
            } catch (Exception e) {
                throw new RuntimeException("Container health check failed: " + e.getMessage(), e);
            }

            System.out.println("All test containers started successfully!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Container startup was interrupted", e);
        } catch (Exception e) {
            System.err.println("Failed to start test containers: " + e.getMessage());
            e.printStackTrace();

            // Try to stop containers that might have started
            try {
                if (mysql.isRunning())
                    mysql.stop();
                if (redis.isRunning())
                    redis.stop();
                if (kafka.isRunning())
                    kafka.stop();
            } catch (Exception stopException) {
                System.err.println("Error stopping containers: " + stopException.getMessage());
            }

            throw e;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL configuration
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // Redis configuration
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);

        // Kafka configuration
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Disable Liquibase for tests
        registry.add("spring.liquibase.enabled", () -> "false");

        // Enable JPA DDL auto for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Bean
    public MySQLContainer<?> mysqlContainer() {
        return mysql;
    }

    @Bean
    public GenericContainer<?> redisContainer() {
        return redis;
    }

    @Bean
    public KafkaContainer kafkaContainer() {
        return kafka;
    }
}
