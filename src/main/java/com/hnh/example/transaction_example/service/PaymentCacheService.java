package com.hnh.example.transaction_example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PAYMENT_CACHE_PREFIX = "payment:";
    private static final String MERCHANT_PAYMENTS_PREFIX = "merchant_payments:";
    private static final Duration PAYMENT_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration MERCHANT_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Cache payment data for fast retrieval
     */
    public void cachePayment(Payment payment) {
        try {
            String key = buildPaymentKey(payment.getId());
            PaymentResponse response = toPaymentResponse(payment);
            String jsonValue = objectMapper.writeValueAsString(response);
            
            redisTemplate.opsForValue().set(key, jsonValue, PAYMENT_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
            
            log.debug("Cached payment: {}", payment.getId());
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache payment: {}", payment.getId(), e);
        }
    }

    /**
     * Get cached payment
     */
    public Optional<PaymentResponse> getCachedPayment(UUID paymentId) {
        try {
            String key = buildPaymentKey(paymentId);
            String cachedValue = (String) redisTemplate.opsForValue().get(key);
            
            if (cachedValue != null) {
                PaymentResponse response = objectMapper.readValue(cachedValue, PaymentResponse.class);
                log.debug("Cache hit for payment: {}", paymentId);
                return Optional.of(response);
            }
            
            log.debug("Cache miss for payment: {}", paymentId);
            return Optional.empty();
            
        } catch (Exception e) {
            log.warn("Error getting cached payment: {}", paymentId, e);
            return Optional.empty();
        }
    }

    /**
     * Invalidate payment cache
     */
    public void invalidatePayment(UUID paymentId) {
        try {
            String key = buildPaymentKey(paymentId);
            redisTemplate.delete(key);
            log.debug("Invalidated cache for payment: {}", paymentId);
        } catch (Exception e) {
            log.warn("Error invalidating cache for payment: {}", paymentId, e);
        }
    }

    /**
     * Cache merchant payment list summary (for list endpoints)
     */
    public void cacheMerchantPaymentSummary(String merchantId, String summaryData) {
        try {
            String key = buildMerchantPaymentsKey(merchantId);
            redisTemplate.opsForValue().set(key, summaryData, MERCHANT_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
            log.debug("Cached merchant payment summary: {}", merchantId);
        } catch (Exception e) {
            log.warn("Error caching merchant payment summary: {}", merchantId, e);
        }
    }

    /**
     * Get cached merchant payment summary
     */
    public Optional<String> getCachedMerchantPaymentSummary(String merchantId) {
        try {
            String key = buildMerchantPaymentsKey(merchantId);
            String cachedValue = (String) redisTemplate.opsForValue().get(key);
            
            if (cachedValue != null) {
                log.debug("Cache hit for merchant payments: {}", merchantId);
                return Optional.of(cachedValue);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error getting cached merchant payment summary: {}", merchantId, e);
            return Optional.empty();
        }
    }

    /**
     * Invalidate merchant payment cache (when payments change)
     */
    public void invalidateMerchantPaymentCache(String merchantId) {
        try {
            String key = buildMerchantPaymentsKey(merchantId);
            redisTemplate.delete(key);
            log.debug("Invalidated merchant payment cache: {}", merchantId);
        } catch (Exception e) {
            log.warn("Error invalidating merchant payment cache: {}", merchantId, e);
        }
    }

    /**
     * Warm up cache with hot data
     */
    public void warmUpCache(Payment payment) {
        // Cache immediately after creation/update
        cachePayment(payment);
        
        // Invalidate related caches
        invalidateMerchantPaymentCache(payment.getMerchantId());
    }

    /**
     * Cache payment status for quick status checks
     */
    public void cachePaymentStatus(UUID paymentId, Payment.PaymentStatus status) {
        try {
            String key = buildPaymentStatusKey(paymentId);
            redisTemplate.opsForValue().set(key, status.toString(), 
                    PAYMENT_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Error caching payment status: {}", paymentId, e);
        }
    }

    /**
     * Get cached payment status
     */
    public Optional<Payment.PaymentStatus> getCachedPaymentStatus(UUID paymentId) {
        try {
            String key = buildPaymentStatusKey(paymentId);
            String status = (String) redisTemplate.opsForValue().get(key);
            
            if (status != null) {
                return Optional.of(Payment.PaymentStatus.valueOf(status));
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error getting cached payment status: {}", paymentId, e);
            return Optional.empty();
        }
    }

    /**
     * Implement cache-aside pattern with stampede protection
     */
    public Optional<PaymentResponse> getOrCachePayment(UUID paymentId, 
                                                      java.util.function.Supplier<Payment> dataLoader) {
        // Try cache first
        Optional<PaymentResponse> cached = getCachedPayment(paymentId);
        if (cached.isPresent()) {
            return cached;
        }

        // Use distributed lock to prevent cache stampede
        String lockKey = "lock:payment:" + paymentId;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 5, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                // Double-check cache after acquiring lock
                cached = getCachedPayment(paymentId);
                if (cached.isPresent()) {
                    return cached;
                }

                // Load from database
                Payment payment = dataLoader.get();
                if (payment != null) {
                    cachePayment(payment);
                    return Optional.of(toPaymentResponse(payment));
                }

                return Optional.empty();

            } finally {
                // Release lock
                redisTemplate.delete(lockKey);
            }
        } else {
            // Another thread is loading, wait briefly and try cache again
            try {
                Thread.sleep(100);
                return getCachedPayment(paymentId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    private String buildPaymentKey(UUID paymentId) {
        return PAYMENT_CACHE_PREFIX + paymentId.toString();
    }

    private String buildMerchantPaymentsKey(String merchantId) {
        return MERCHANT_PAYMENTS_PREFIX + merchantId;
    }

    private String buildPaymentStatusKey(UUID paymentId) {
        return "payment_status:" + paymentId.toString();
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethodId(payment.getPaymentMethodId())
                .description(payment.getDescription())
                .referenceId(payment.getReferenceId())
                .capturedAmount(payment.getCapturedAmount())
                .refundedAmount(payment.getRefundedAmount())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .authorizedAt(payment.getAuthorizedAt())
                .capturedAt(payment.getCapturedAt())
                .failedAt(payment.getFailedAt())
                .failureReason(payment.getFailureReason())
                .build();
    }
}
