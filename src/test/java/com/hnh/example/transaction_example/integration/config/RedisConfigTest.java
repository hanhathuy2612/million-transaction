package com.hnh.example.transaction_example.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import com.hnh.example.transaction_example.testutils.TestContainerConfig;

@SpringBootTest
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
@DisplayName("Redis Configuration Tests")
class RedisConfigTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Nested
    @DisplayName("Redis Template Configuration")
    class RedisTemplateConfiguration {

        @Test
        @DisplayName("Should have Redis template configured")
        void shouldHaveRedisTemplateConfigured() {
            // Assert
            assertThat(redisTemplate).isNotNull();
            assertThat(redisTemplate.getConnectionFactory()).isNotNull();
        }

        @Test
        @DisplayName("Should be able to perform basic operations")
        void shouldBeAbleToPerformBasicOperations() {
            // Arrange
            String key = "test:key";
            String value = "test value";
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            // Act & Assert - Set operation
            assertThatCode(() -> valueOps.set(key, value))
                    .doesNotThrowAnyException();

            // Act & Assert - Get operation
            Object retrievedValue = valueOps.get(key);
            assertThat(retrievedValue).isEqualTo(value);

            // Act & Assert - Delete operation
            Boolean deleted = redisTemplate.delete(key);
            assertThat(deleted).isTrue();

            // Verify deletion
            Object deletedValue = valueOps.get(key);
            assertThat(deletedValue).isNull();
        }

        @Test
        @DisplayName("Should support TTL operations")
        void shouldSupportTtlOperations() {
            // Arrange
            String key = "test:ttl:key";
            String value = "test value with TTL";
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            // Act
            valueOps.set(key, value, 10, TimeUnit.SECONDS);

            // Assert
            Object retrievedValue = valueOps.get(key);
            assertThat(retrievedValue).isEqualTo(value);

            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertThat(ttl).isBetween(1L, 10L);

            // Cleanup
            redisTemplate.delete(key);
        }

        @Test
        @DisplayName("Should handle complex objects")
        void shouldHandleComplexObjects() {
            // Arrange
            String key = "test:complex:key";
            java.util.Map<String, Object> complexValue = java.util.Map.of(
                    "id", "123",
                    "amount", 100.50,
                    "status", "ACTIVE",
                    "metadata", java.util.Map.of("source", "test"));
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            // Act
            valueOps.set(key, complexValue);
            Object retrievedValue = valueOps.get(key);

            // Assert
            assertThat(retrievedValue).isNotNull();
            assertThat(retrievedValue).isInstanceOf(java.util.Map.class);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> retrievedMap = (java.util.Map<String, Object>) retrievedValue;
            assertThat(retrievedMap.get("id")).isEqualTo("123");
            assertThat(retrievedMap.get("amount")).isEqualTo(100.50);
            assertThat(retrievedMap.get("status")).isEqualTo("ACTIVE");

            // Cleanup
            redisTemplate.delete(key);
        }
    }

    @Nested
    @DisplayName("Redis Connection")
    class RedisConnection {

        @Test
        @DisplayName("Should have active Redis connection")
        void shouldHaveActiveRedisConnection() {
            // Act & Assert
            assertThatCode(() -> {
                redisTemplate.getConnectionFactory().getConnection().ping();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should be able to execute Redis commands")
        void shouldBeAbleToExecuteRedisCommands() {
            // Act
            String pong = redisTemplate
                    .execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                        String response = connection.ping();
                        return response;
                    });

            // Assert
            assertThat(pong).isEqualTo("PONG");
        }

        @Test
        @DisplayName("Should handle connection errors gracefully")
        void shouldHandleConnectionErrorsGracefully() {
            // This test verifies that the connection is properly configured
            // and doesn't throw unexpected exceptions

            // Act & Assert
            assertThatCode(() -> {
                redisTemplate.opsForValue().get("non-existent-key");
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Redis Serialization")
    class RedisSerialization {

        @Test
        @DisplayName("Should serialize and deserialize strings correctly")
        void shouldSerializeAndDeserializeStringsCorrectly() {
            // Arrange
            String key = "test:string:key";
            String value = "test string value";
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            // Act
            valueOps.set(key, value);
            Object retrievedValue = valueOps.get(key);

            // Assert
            assertThat(retrievedValue).isEqualTo(value);
            assertThat(retrievedValue).isInstanceOf(String.class);

            // Cleanup
            redisTemplate.delete(key);
        }

        @Test
        @DisplayName("Should serialize and deserialize numbers correctly")
        void shouldSerializeAndDeserializeNumbersCorrectly() {
            // Arrange
            String intKey = "test:int:key";
            String longKey = "test:long:key";
            String doubleKey = "test:double:key";

            Integer intValue = 42;
            Long longValue = 123456789L;
            Double doubleValue = 3.14159;

            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            // Act
            valueOps.set(intKey, intValue);
            valueOps.set(longKey, longValue);
            valueOps.set(doubleKey, doubleValue);

            Object retrievedInt = valueOps.get(intKey);
            Object retrievedLong = valueOps.get(longKey);
            Object retrievedDouble = valueOps.get(doubleKey);

            // Assert
            assertThat(retrievedInt).isEqualTo(intValue);
            assertThat(retrievedLong).isEqualTo(longValue);
            assertThat(retrievedDouble).isEqualTo(doubleValue);

            // Cleanup
            redisTemplate.delete(intKey);
            redisTemplate.delete(longKey);
            redisTemplate.delete(doubleKey);
        }

        @Test
        @DisplayName("Should handle null values correctly")
        void shouldHandleNullValuesCorrectly() {
            // Arrange
            String key = "test:null:key";
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            // Act
            valueOps.set(key, null);
            Object retrievedValue = valueOps.get(key);

            // Assert
            assertThat(retrievedValue).isNull();

            // Cleanup
            redisTemplate.delete(key);
        }
    }

    @Nested
    @DisplayName("Redis Operations Performance")
    class RedisOperationsPerformance {

        @Test
        @DisplayName("Should perform bulk operations efficiently")
        void shouldPerformBulkOperationsEfficiently() {
            // Arrange
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            int numberOfOperations = 100;
            long startTime = System.currentTimeMillis();

            // Act
            for (int i = 0; i < numberOfOperations; i++) {
                String key = "test:bulk:" + i;
                String value = "value " + i;
                valueOps.set(key, value);
            }

            long operationTime = System.currentTimeMillis() - startTime;

            // Assert - Operations should complete within reasonable time
            assertThat(operationTime).isLessThan(5000); // 5 seconds max for 100 operations

            // Verify some of the operations
            for (int i = 0; i < 10; i++) {
                String key = "test:bulk:" + i;
                Object value = valueOps.get(key);
                assertThat(value).isEqualTo("value " + i);
            }

            // Cleanup
            for (int i = 0; i < numberOfOperations; i++) {
                redisTemplate.delete("test:bulk:" + i);
            }
        }

        @Test
        @DisplayName("Should handle concurrent operations")
        void shouldHandleConcurrentOperations() throws InterruptedException {
            // Arrange
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            int numberOfThreads = 5;
            int operationsPerThread = 20;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(numberOfThreads);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

            // Act
            for (int t = 0; t < numberOfThreads; t++) {
                final int threadId = t;
                new Thread(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String key = "test:concurrent:" + threadId + ":" + i;
                            String value = "thread" + threadId + "_value" + i;
                            valueOps.set(key, value);

                            Object retrievedValue = valueOps.get(key);
                            if (value.equals(retrievedValue)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await(10, TimeUnit.SECONDS);

            // Assert
            assertThat(successCount.get()).isEqualTo(numberOfThreads * operationsPerThread);

            // Cleanup
            for (int t = 0; t < numberOfThreads; t++) {
                for (int i = 0; i < operationsPerThread; i++) {
                    redisTemplate.delete("test:concurrent:" + t + ":" + i);
                }
            }
        }
    }
}
