package com.hnh.example.transaction_example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.hnh.example.transaction_example.constant.PaymentEvent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY_PREFIX = "analytics:";
    private static final long CACHE_TTL_MINUTES = 15;

    private static final String METRIC_LOG_FORMAT = "METRIC: payment.{} merchant={} currency={} amount={} timestamp={}";

    // Mock data constants for getPaymentMetrics
    private static final long MOCK_TOTAL_AUTHORIZATIONS = 150L;
    private static final long MOCK_TOTAL_CAPTURES = 145L;
    private static final long MOCK_TOTAL_REFUNDS = 5L;
    private static final long MOCK_TOTAL_FAILURES = 10L;
    private static final String MOCK_AUTHORIZED_AMOUNT = "15000.00";
    private static final String MOCK_CAPTURED_AMOUNT = "14750.00";
    private static final String MOCK_REFUNDED_AMOUNT = "250.00";
    private static final double MOCK_CONVERSION_RATE = 96.67;
    private static final double MOCK_REFUND_RATE = 3.45;
    private static final double MOCK_FAILURE_RATE = 6.25;

    /**
     * Record payment event for analytics and monitoring
     */
    public void recordPaymentEvent(String eventType, JsonNode eventData) {
        try {
            String paymentId = eventData.get("paymentId").asText();
            String merchantId = eventData.get("merchantId").asText();
            String currency = eventData.get("currency").asText();
            BigDecimal amount = new BigDecimal(eventData.get("amount").asText());

            // Record metrics based on event type
            switch (eventType) {
                case EVENT_TYPE_AUTHORIZED:
                    recordPaymentAuthorized(merchantId, currency, amount);
                    break;
                case EVENT_TYPE_CAPTURED:
                    BigDecimal capturedAmount = eventData.has("capturedAmount")
                            ? new BigDecimal(eventData.get("capturedAmount").asText())
                            : amount;
                    recordPaymentCaptured(merchantId, currency, capturedAmount);
                    break;
                case EVENT_TYPE_REFUNDED:
                    BigDecimal refundedAmount = new BigDecimal(eventData.get("refundedAmount").asText());
                    recordPaymentRefunded(merchantId, currency, refundedAmount);
                    break;
                case EVENT_TYPE_FAILED:
                    recordPaymentFailed(merchantId, currency, amount);
                    break;
                case EVENT_TYPE_PENDING:
                    recordPaymentEvent(eventType, eventData);
                    break;
                default:
                    log.warn("Unknown event type for analytics: {}", eventType);
            }

            log.debug("Recorded analytics for payment: {} event: {}", paymentId, eventType);

        } catch (Exception e) {
            log.error("Error recording analytics for event: {}", eventType, e);
        }
    }

    /**
     * Get payment analytics for a merchant
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPaymentAnalytics(String merchantId, LocalDateTime fromDate, LocalDateTime toDate) {
        String cacheKey = CACHE_KEY_PREFIX + merchantId + ":" + fromDate + ":" + toDate;

        try {
            // Check cache first
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return (Map<String, Object>) cached;
            }
        } catch (Exception e) {
            log.warn("Error reading from cache: {}", e.getMessage());
        }

        // Get payments from database
        List<Payment> payments = paymentRepository.findByMerchantIdAndCreatedDateBetween(merchantId, fromDate, toDate);

        // Calculate analytics
        Map<String, Object> analytics = calculateAnalytics(payments);

        try {
            // Cache the results
            redisTemplate.opsForValue().set(cacheKey, analytics, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Error writing to cache: {}", e.getMessage());
        }

        return analytics;
    }

    /**
     * Get payment metrics for a merchant
     */
    public PaymentMetrics getPaymentMetrics(String merchantId, LocalDateTime from, LocalDateTime to) {
        return PaymentMetrics.builder()
                .merchantId(merchantId)
                .periodStart(from)
                .periodEnd(to)
                .totalAuthorizations(MOCK_TOTAL_AUTHORIZATIONS)
                .totalCaptures(MOCK_TOTAL_CAPTURES)
                .totalRefunds(MOCK_TOTAL_REFUNDS)
                .totalFailures(MOCK_TOTAL_FAILURES)
                .totalAuthorizedAmount(new BigDecimal(MOCK_AUTHORIZED_AMOUNT))
                .totalCapturedAmount(new BigDecimal(MOCK_CAPTURED_AMOUNT))
                .totalRefundedAmount(new BigDecimal(MOCK_REFUNDED_AMOUNT))
                .conversionRate(MOCK_CONVERSION_RATE) // (captures / authorizations) * 100
                .refundRate(MOCK_REFUND_RATE) // (refunds / captures) * 100
                .failureRate(MOCK_FAILURE_RATE) // (failures / attempts) * 100
                .build();
    }

    // Private helper methods

    private Map<String, Object> calculateAnalytics(List<Payment> payments) {
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal capturedVolume = BigDecimal.ZERO;
        long totalCount = payments.size();
        long capturedCount = 0;
        long failedCount = 0;

        Map<String, Long> statusBreakdown = payments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus().toString(),
                        Collectors.counting()));

        for (Payment payment : payments) {
            totalVolume = totalVolume.add(payment.getAmount());

            if (payment.getStatus() == Payment.PaymentStatus.CAPTURED) {
                capturedVolume = capturedVolume.add(payment.getCapturedAmount());
                capturedCount++;
            } else if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
                failedCount++;
            }
        }

        double successRate = totalCount > 0 ? (capturedCount * 100.0) / totalCount : 0.0;
        BigDecimal averageTransactionAmount = totalCount > 0
                ? totalVolume.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return Map.of(
                "totalVolume", totalVolume,
                "capturedVolume", capturedVolume,
                "totalCount", totalCount,
                "capturedCount", capturedCount,
                "failedCount", failedCount,
                "successRate", successRate,
                "statusBreakdown", statusBreakdown,
                "averageTransactionAmount", averageTransactionAmount);
    }

    private void recordPaymentAuthorized(String merchantId, String currency, BigDecimal amount) {
        log.info(METRIC_LOG_FORMAT, EVENT_TYPE_AUTHORIZED, merchantId, currency, amount, getCurrentTimestamp());
    }

    private void recordPaymentCaptured(String merchantId, String currency, BigDecimal amount) {
        log.info(METRIC_LOG_FORMAT, EVENT_TYPE_CAPTURED, merchantId, currency, amount, getCurrentTimestamp());
    }

    private void recordPaymentRefunded(String merchantId, String currency, BigDecimal amount) {
        log.warn(METRIC_LOG_FORMAT, EVENT_TYPE_REFUNDED, merchantId, currency, amount, getCurrentTimestamp());
    }

    private void recordPaymentFailed(String merchantId, String currency, BigDecimal amount) {
        log.error(METRIC_LOG_FORMAT, EVENT_TYPE_FAILED, merchantId, currency, amount, getCurrentTimestamp());
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentMetrics {
        private String merchantId;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private Long totalAuthorizations;
        private Long totalCaptures;
        private Long totalRefunds;
        private Long totalFailures;
        private BigDecimal totalAuthorizedAmount;
        private BigDecimal totalCapturedAmount;
        private BigDecimal totalRefundedAmount;
        private Double conversionRate;
        private Double refundRate;
        private Double failureRate;
    }
}
