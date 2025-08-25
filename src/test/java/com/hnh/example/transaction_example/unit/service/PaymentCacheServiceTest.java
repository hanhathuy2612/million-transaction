package com.hnh.example.transaction_example.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.service.PaymentCacheService;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Cache Service Tests")
class PaymentCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private PaymentCacheService paymentCacheService;

    private UUID paymentId;
    private Payment payment;
    private String cacheKey;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        payment = TestDataBuilder.payment()
                .id(paymentId)
                .merchantId("merchant_1")
                .amount(java.math.BigDecimal.valueOf(100.00))
                .build();

        cacheKey = "payment:" + paymentId.toString();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("Cache Payment Tests")
    class CachePaymentTests {

        @Test
        @DisplayName("Should cache payment successfully")
        void shouldCachePaymentSuccessfully() {
            // Act
            paymentCacheService.cachePayment(payment);

            // Assert
            verify(valueOperations).set(eq(cacheKey), eq(payment), eq(30L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("Should handle null payment gracefully")
        void shouldHandleNullPaymentGracefully() {
            // Act & Assert
            assertThatCode(() -> paymentCacheService.cachePayment(null))
                    .doesNotThrowAnyException();

            verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should handle payment with null ID gracefully")
        void shouldHandlePaymentWithNullIdGracefully() {
            // Arrange
            Payment paymentWithNullId = TestDataBuilder.payment()
                    .id(null)
                    .build();

            // Act & Assert
            assertThatCode(() -> paymentCacheService.cachePayment(paymentWithNullId))
                    .doesNotThrowAnyException();

            verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should handle Redis exceptions gracefully")
        void shouldHandleRedisExceptionsGracefully() {
            // Arrange
            doThrow(new RuntimeException("Redis connection error"))
                    .when(valueOperations).set(anyString(), any(), anyLong(), any());

            // Act & Assert
            assertThatCode(() -> paymentCacheService.cachePayment(payment))
                    .doesNotThrowAnyException();

            verify(valueOperations).set(eq(cacheKey), eq(payment), eq(30L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("Should use correct cache key format")
        void shouldUseCorrectCacheKeyFormat() {
            // Act
            paymentCacheService.cachePayment(payment);

            // Assert
            verify(valueOperations).set(
                    eq("payment:" + paymentId.toString()),
                    eq(payment),
                    eq(30L),
                    eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    @DisplayName("Get Cached Payment Tests")
    class GetCachedPaymentTests {

        @Test
        @DisplayName("Should return cached payment when exists")
        void shouldReturnCachedPaymentWhenExists() {
            // Arrange
            when(valueOperations.get(cacheKey)).thenReturn(payment);

            // Act
            Optional<PaymentResponse> result = paymentCacheService.getCachedPayment(paymentId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(payment);
            assertThat(result.get().getId()).isEqualTo(paymentId);

            verify(valueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should return empty when payment not cached")
        void shouldReturnEmptyWhenPaymentNotCached() {
            // Arrange
            when(valueOperations.get(cacheKey)).thenReturn(null);

            // Act
            java.util.Optional<PaymentResponse> result = paymentCacheService.getCachedPayment(paymentId);

            // Assert
            assertThat(result).isEmpty();

            verify(valueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should handle null payment ID gracefully")
        void shouldHandleNullPaymentIdGracefully() {
            // Act
            Optional<PaymentResponse> result = paymentCacheService.getCachedPayment(null);

            // Assert
            assertThat(result).isEmpty();

            verify(valueOperations, never()).get(anyString());
        }

        @Test
        @DisplayName("Should handle Redis exceptions gracefully")
        void shouldHandleRedisExceptionsGracefully() {
            // Arrange
            when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis connection error"));

            // Act
            Optional<PaymentResponse> result = paymentCacheService.getCachedPayment(paymentId);

            // Assert
            assertThat(result).isEmpty();

            verify(valueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should handle invalid cached data gracefully")
        void shouldHandleInvalidCachedDataGracefully() {
            // Arrange
            when(valueOperations.get(cacheKey)).thenReturn("invalid data");

            // Act
            Optional<PaymentResponse> result = paymentCacheService.getCachedPayment(paymentId);

            // Assert
            assertThat(result).isEmpty();

            verify(valueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should use correct cache key format for retrieval")
        void shouldUseCorrectCacheKeyFormatForRetrieval() {
            // Arrange
            when(valueOperations.get(cacheKey)).thenReturn(payment);

            // Act
            paymentCacheService.getCachedPayment(paymentId);

            // Assert
            verify(valueOperations).get("payment:" + paymentId.toString());
        }
    }

    @Nested
    @DisplayName("Invalidate Payment Tests")
    class InvalidatePaymentTests {

        @Test
        @DisplayName("Should invalidate payment from cache successfully")
        void shouldInvalidatePaymentFromCacheSuccessfully() {
            // Arrange
            when(redisTemplate.delete(cacheKey)).thenReturn(true);

            // Act
            paymentCacheService.invalidatePayment(paymentId);

            // Assert
            verify(redisTemplate).delete(cacheKey);
        }

        @Test
        @DisplayName("Should handle null payment ID gracefully")
        void shouldHandleNullPaymentIdGracefully() {
            // Act & Assert
            assertThatCode(() -> paymentCacheService.invalidatePayment(null))
                    .doesNotThrowAnyException();

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("Should handle Redis exceptions gracefully")
        void shouldHandleRedisExceptionsGracefully() {
            // Arrange
            when(redisTemplate.delete(cacheKey)).thenThrow(new RuntimeException("Redis connection error"));

            // Act & Assert
            assertThatCode(() -> paymentCacheService.invalidatePayment(paymentId))
                    .doesNotThrowAnyException();

            verify(redisTemplate).delete(cacheKey);
        }

        @Test
        @DisplayName("Should use correct cache key format for invalidation")
        void shouldUseCorrectCacheKeyFormatForInvalidation() {
            // Arrange
            when(redisTemplate.delete(cacheKey)).thenReturn(true);

            // Act
            paymentCacheService.invalidatePayment(paymentId);

            // Assert
            verify(redisTemplate).delete("payment:" + paymentId.toString());
        }

        @Test
        @DisplayName("Should handle invalidation when key does not exist")
        void shouldHandleInvalidationWhenKeyDoesNotExist() {
            // Arrange
            when(redisTemplate.delete(cacheKey)).thenReturn(false);

            // Act & Assert
            assertThatCode(() -> paymentCacheService.invalidatePayment(paymentId))
                    .doesNotThrowAnyException();

            verify(redisTemplate).delete(cacheKey);
        }
    }

    @Nested
    @DisplayName("Cache Key Management Tests")
    class CacheKeyManagementTests {

        @Test
        @DisplayName("Should generate consistent cache keys")
        void shouldGenerateConsistentCacheKeys() {
            // Arrange
            UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            Payment testPayment = TestDataBuilder.payment().id(testId).build();
            String expectedKey = "payment:550e8400-e29b-41d4-a716-446655440000";

            // Act
            paymentCacheService.cachePayment(testPayment);

            // Assert
            verify(valueOperations).set(eq(expectedKey), eq(testPayment), eq(30L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("Should handle different UUID formats correctly")
        void shouldHandleDifferentUuidFormatsCorrectly() {
            // Arrange
            UUID[] testIds = {
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                    UUID.fromString("12345678-1234-1234-1234-123456789012"),
                    UUID.randomUUID()
            };

            // Act & Assert
            for (UUID testId : testIds) {
                Payment testPayment = TestDataBuilder.payment().id(testId).build();
                String expectedKey = "payment:" + testId.toString();

                paymentCacheService.cachePayment(testPayment);

                verify(valueOperations).set(eq(expectedKey), eq(testPayment), eq(30L), eq(TimeUnit.MINUTES));
            }
        }
    }

    @Nested
    @DisplayName("Cache TTL Tests")
    class CacheTtlTests {

        @Test
        @DisplayName("Should set correct TTL for cached payments")
        void shouldSetCorrectTtlForCachedPayments() {
            // Act
            paymentCacheService.cachePayment(payment);

            // Assert
            verify(valueOperations).set(eq(cacheKey), eq(payment), eq(30L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("Should use minutes as time unit")
        void shouldUseMinutesAsTimeUnit() {
            // Act
            paymentCacheService.cachePayment(payment);

            // Assert
            verify(valueOperations).set(anyString(), any(), anyLong(), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    @DisplayName("Integration with Redis Template Tests")
    class IntegrationWithRedisTemplateTests {

        @Test
        @DisplayName("Should properly interact with Redis template")
        void shouldProperlyInteractWithRedisTemplate() {
            // Act
            paymentCacheService.cachePayment(payment);
            paymentCacheService.getCachedPayment(paymentId);
            paymentCacheService.invalidatePayment(paymentId);

            // Assert
            verify(redisTemplate, times(2)).opsForValue(); // Called for cache and get
            verify(redisTemplate).delete(cacheKey); // Called for invalidate
            verify(valueOperations).set(eq(cacheKey), eq(payment), eq(30L), eq(TimeUnit.MINUTES));
            verify(valueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should handle Redis template returning null operations")
        void shouldHandleRedisTemplateReturningNullOperations() {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(null);

            // Act & Assert
            assertThatCode(() -> {
                paymentCacheService.cachePayment(payment);
                paymentCacheService.getCachedPayment(paymentId);
            }).doesNotThrowAnyException();
        }
    }
}
