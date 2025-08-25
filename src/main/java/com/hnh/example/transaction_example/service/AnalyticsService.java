package com.hnh.example.transaction_example.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

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
                case "authorized":
                    recordPaymentAuthorized(merchantId, currency, amount);
                    break;
                case "captured":
                    BigDecimal capturedAmount = eventData.has("capturedAmount") 
                            ? new BigDecimal(eventData.get("capturedAmount").asText())
                            : amount;
                    recordPaymentCaptured(merchantId, currency, capturedAmount);
                    break;
                case "refunded":
                    BigDecimal refundedAmount = new BigDecimal(eventData.get("refundedAmount").asText());
                    recordPaymentRefunded(merchantId, currency, refundedAmount);
                    break;
                case "failed":
                    recordPaymentFailed(merchantId, currency, amount);
                    break;
                default:
                    log.warn("Unknown event type for analytics: {}", eventType);
            }
            
            log.debug("Recorded analytics for payment: {} event: {}", paymentId, eventType);
            
        } catch (Exception e) {
            log.error("Error recording analytics for event: {}", eventType, e);
        }
    }

    private void recordPaymentAuthorized(String merchantId, String currency, BigDecimal amount) {
        // In production, this would send metrics to your monitoring system
        // Examples: Prometheus, DataDog, CloudWatch, etc.
        
        log.info("METRIC: payment.authorized merchant={} currency={} amount={} timestamp={}", 
                merchantId, currency, amount, getCurrentTimestamp());
        
        // Example metric recording (pseudo-code):
        // meterRegistry.counter("payments.authorized.count", 
        //     Tags.of("merchant", merchantId, "currency", currency)).increment();
        // meterRegistry.gauge("payments.authorized.amount", 
        //     Tags.of("merchant", merchantId, "currency", currency), amount.doubleValue());
    }

    private void recordPaymentCaptured(String merchantId, String currency, BigDecimal amount) {
        log.info("METRIC: payment.captured merchant={} currency={} amount={} timestamp={}", 
                merchantId, currency, amount, getCurrentTimestamp());
        
        // Record revenue metrics
        // This is actual money collected
    }

    private void recordPaymentRefunded(String merchantId, String currency, BigDecimal amount) {
        log.info("METRIC: payment.refunded merchant={} currency={} amount={} timestamp={}", 
                merchantId, currency, amount, getCurrentTimestamp());
        
        // Track refund rates and amounts
    }

    private void recordPaymentFailed(String merchantId, String currency, BigDecimal amount) {
        log.info("METRIC: payment.failed merchant={} currency={} amount={} timestamp={}", 
                merchantId, currency, amount, getCurrentTimestamp());
        
        // Track failure rates for monitoring and alerting
    }

    /**
     * Get payment metrics for a merchant
     */
    public PaymentMetrics getPaymentMetrics(String merchantId, LocalDateTime from, LocalDateTime to) {
        // In production, this would query your analytics database
        // For now, return mock data
        
        return PaymentMetrics.builder()
                .merchantId(merchantId)
                .periodStart(from)
                .periodEnd(to)
                .totalAuthorizations(150L)
                .totalCaptures(145L)
                .totalRefunds(5L)
                .totalFailures(10L)
                .totalAuthorizedAmount(new BigDecimal("15000.00"))
                .totalCapturedAmount(new BigDecimal("14750.00"))
                .totalRefundedAmount(new BigDecimal("250.00"))
                .conversionRate(96.67) // (captures / authorizations) * 100
                .refundRate(3.45) // (refunds / captures) * 100
                .failureRate(6.25) // (failures / attempts) * 100
                .build();
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
