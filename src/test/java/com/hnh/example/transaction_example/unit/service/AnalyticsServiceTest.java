package com.hnh.example.transaction_example.unit.service;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.repository.PaymentRepository;
import com.hnh.example.transaction_example.service.AnalyticsService;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Analytics Service Tests")
class AnalyticsServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AnalyticsService analyticsService;

    private String merchantId;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;

    @BeforeEach
    void setUp() {
        merchantId = "merchant_1";
        fromDate = LocalDateTime.now().minusDays(30);
        toDate = LocalDateTime.now();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("Payment Volume Analytics Tests")
    class PaymentVolumeAnalyticsTests {

        @Test
        @DisplayName("Should calculate payment volume correctly")
        void shouldCalculatePaymentVolumeCorrectly() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(100.00))
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(200.00))
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(50.00))
                            .status(Payment.PaymentStatus.FAILED)
                            .build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("totalVolume")).isEqualTo(BigDecimal.valueOf(350.00));
            assertThat(analytics.get("capturedVolume")).isEqualTo(BigDecimal.valueOf(300.00));
            assertThat(analytics.get("totalCount")).isEqualTo(3L);
            assertThat(analytics.get("capturedCount")).isEqualTo(2L);
            assertThat(analytics.get("failedCount")).isEqualTo(1L);

            verify(paymentRepository).findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should handle empty payment list")
        void shouldHandleEmptyPaymentList() {
            // Arrange
            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("totalVolume")).isEqualTo(BigDecimal.ZERO);
            assertThat(analytics.get("capturedVolume")).isEqualTo(BigDecimal.ZERO);
            assertThat(analytics.get("totalCount")).isEqualTo(0L);
            assertThat(analytics.get("capturedCount")).isEqualTo(0L);
            assertThat(analytics.get("failedCount")).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should calculate success rate correctly")
        void shouldCalculateSuccessRateCorrectly() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.FAILED)
                            .build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("successRate")).isEqualTo(75.0); // 3 successful out of 4 total
            assertThat(analytics.get("totalCount")).isEqualTo(4L);
            assertThat(analytics.get("capturedCount")).isEqualTo(3L);
            assertThat(analytics.get("failedCount")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should handle 100% success rate")
        void shouldHandle100PercentSuccessRate() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("successRate")).isEqualTo(100.0);
            assertThat(analytics.get("totalCount")).isEqualTo(2L);
            assertThat(analytics.get("capturedCount")).isEqualTo(2L);
            assertThat(analytics.get("failedCount")).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should handle 0% success rate")
        void shouldHandle0PercentSuccessRate() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.FAILED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.FAILED)
                            .build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("successRate")).isEqualTo(0.0);
            assertThat(analytics.get("totalCount")).isEqualTo(2L);
            assertThat(analytics.get("capturedCount")).isEqualTo(0L);
            assertThat(analytics.get("failedCount")).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Payment Status Breakdown Tests")
    class PaymentStatusBreakdownTests {

        @Test
        @DisplayName("Should calculate status breakdown correctly")
        void shouldCalculateStatusBreakdownCorrectly() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment().status(Payment.PaymentStatus.PENDING).build(),
                    TestDataBuilder.payment().status(Payment.PaymentStatus.AUTHORIZED).build(),
                    TestDataBuilder.payment().status(Payment.PaymentStatus.CAPTURED).build(),
                    TestDataBuilder.payment().status(Payment.PaymentStatus.CAPTURED).build(),
                    TestDataBuilder.payment().status(Payment.PaymentStatus.FAILED).build(),
                    TestDataBuilder.payment().status(Payment.PaymentStatus.REFUNDED).build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            
            @SuppressWarnings("unchecked")
            Map<String, Long> statusBreakdown = (Map<String, Long>) analytics.get("statusBreakdown");
            
            assertThat(statusBreakdown).isNotNull();
            assertThat(statusBreakdown.get("PENDING")).isEqualTo(1L);
            assertThat(statusBreakdown.get("AUTHORIZED")).isEqualTo(1L);
            assertThat(statusBreakdown.get("CAPTURED")).isEqualTo(2L);
            assertThat(statusBreakdown.get("FAILED")).isEqualTo(1L);
            assertThat(statusBreakdown.get("REFUNDED")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should handle single status")
        void shouldHandleSingleStatus() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment().status(Payment.PaymentStatus.CAPTURED).build(),
                    TestDataBuilder.payment().status(Payment.PaymentStatus.CAPTURED).build(),
                    TestDataBuilder.payment().status(Payment.PaymentStatus.CAPTURED).build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            
            @SuppressWarnings("unchecked")
            Map<String, Long> statusBreakdown = (Map<String, Long>) analytics.get("statusBreakdown");
            
            assertThat(statusBreakdown).isNotNull();
            assertThat(statusBreakdown.get("CAPTURED")).isEqualTo(3L);
            assertThat(statusBreakdown.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Caching Tests")
    class CachingTests {

        @Test
        @DisplayName("Should cache analytics results")
        void shouldCacheAnalyticsResults() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(100.00))
                            .build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            verify(valueOperations).set(anyString(), any(), anyLong(), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("Should return cached results when available")
        void shouldReturnCachedResultsWhenAvailable() {
            // Arrange
            Map<String, Object> cachedAnalytics = Map.of(
                    "totalVolume", BigDecimal.valueOf(500.00),
                    "totalCount", 5L,
                    "cached", true
            );

            when(valueOperations.get(anyString())).thenReturn(cachedAnalytics);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("totalVolume")).isEqualTo(BigDecimal.valueOf(500.00));
            assertThat(analytics.get("totalCount")).isEqualTo(5L);
            assertThat(analytics.get("cached")).isEqualTo(true);

            verify(paymentRepository, never()).findByMerchantIdAndCreatedAtBetween(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should handle cache errors gracefully")
        void shouldHandleCacheErrorsGracefully() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(100.00))
                            .build()
            );

            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));
            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("totalVolume")).isEqualTo(BigDecimal.valueOf(100.00));
            assertThat(analytics.get("totalCount")).isEqualTo(1L);

            verify(paymentRepository).findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should handle cache write errors gracefully")
        void shouldHandleCacheWriteErrorsGracefully() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(100.00))
                            .build()
            );

            when(valueOperations.get(anyString())).thenReturn(null);
            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);
            doThrow(new RuntimeException("Redis write error")).when(valueOperations).set(anyString(), any(), anyLong(), any());

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("totalVolume")).isEqualTo(BigDecimal.valueOf(100.00));
            assertThat(analytics.get("totalCount")).isEqualTo(1L);

            verify(paymentRepository).findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("Average Transaction Amount Tests")
    class AverageTransactionAmountTests {

        @Test
        @DisplayName("Should calculate average transaction amount correctly")
        void shouldCalculateAverageTransactionAmountCorrectly() {
            // Arrange
            List<Payment> payments = List.of(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(100.00))
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(200.00))
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build(),
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(300.00))
                            .status(Payment.PaymentStatus.CAPTURED)
                            .build()
            );

            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("averageTransactionAmount")).isEqualTo(BigDecimal.valueOf(200.00));
        }

        @Test
        @DisplayName("Should handle zero transactions for average calculation")
        void shouldHandleZeroTransactionsForAverageCalculation() {
            // Arrange
            when(paymentRepository.findByMerchantIdAndCreatedAtBetween(eq(merchantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            // Act
            Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);

            // Assert
            assertThat(analytics).isNotNull();
            assertThat(analytics.get("averageTransactionAmount")).isEqualTo(BigDecimal.ZERO);
        }
    }
}
