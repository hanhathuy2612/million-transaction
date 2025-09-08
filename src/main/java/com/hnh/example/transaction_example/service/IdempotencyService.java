package com.hnh.example.transaction_example.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hnh.example.transaction_example.domain.IdempotencyKey;
import com.hnh.example.transaction_example.dto.CaptureRequest;
import com.hnh.example.transaction_example.dto.IdempotencyCacheDto;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.RefundRequest;
import com.hnh.example.transaction_example.repository.IdempotencyKeyRepository;
import com.hnh.example.transaction_example.util.JsonUtil;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_PREFIX = "idempotency:";
    private static final Duration DEFAULT_REDIS_TTL = Duration.ofHours(24);
    private static final Duration DEFAULT_DB_TTL = Duration.ofDays(7);

    // TTL strategies based on operation type
    private static final Duration PAYMENT_CREATE_TTL = Duration.ofDays(7);
    private static final Duration PAYMENT_CAPTURE_TTL = Duration.ofDays(3);
    private static final Duration PAYMENT_REFUND_TTL = Duration.ofDays(30);

    /**
     * Generate a standardized idempotency key
     */
    public String generateIdempotencyKey(String merchantId, String operation) {
        return String.format("%s_%s_%d_%s",
                merchantId,
                operation,
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Generate and validate idempotency key
     */
    public String generateAndValidateIdempotencyKey(String merchantId, String operation, String clientKey) {
        // If the client doesn't provide a key, auto generate key
        if (clientKey == null || clientKey.trim().isEmpty()) {
            String generatedKey = generateIdempotencyKey(merchantId, operation);
            log.info("Generated idempotency key: {} for merchant: {} operation: {}", generatedKey, merchantId,
                    operation);
            return generatedKey;
        }

        // Validate client key format
        if (!isValidKeyFormat(clientKey)) {
            log.warn("Invalid client key format: {}. Generating new key.", clientKey);
            String generatedKey = generateIdempotencyKey(merchantId, operation);
            log.info("Generated new idempotency key: {} for merchant: {} operation: {}", generatedKey, merchantId,
                    operation);
            return generatedKey;
        }

        log.debug("Using client provided idempotency key: {} for merchant: {} operation: {}", clientKey, merchantId,
                operation);
        return clientKey;
    }

    /**
     * Check if request is idempotent and return cached response if exists
     */
    @Transactional(readOnly = true)
    public Optional<ResponseEntity<Object>> checkIdempotency(String merchantId, String idempotencyKey,
            Object requestBody) {
        if (!isValidIdempotencyRequest(merchantId, idempotencyKey)) {
            return Optional.empty();
        }

        String requestHash = generateSmartRequestHash(requestBody);
        String redisKey = buildRedisKey(merchantId, idempotencyKey);

        // First check Redis (faster)
        Optional<ResponseEntity<Object>> cachedResponse = checkRedisCache(redisKey, requestHash);
        if (cachedResponse.isPresent()) {
            log.debug("Idempotency hit in Redis for key: {}", idempotencyKey);
            return cachedResponse;
        }

        // Then check database
        Optional<IdempotencyKey> dbKey = idempotencyKeyRepository.findByMerchantIdAndKey(merchantId, idempotencyKey);
        if (dbKey.isPresent()) {
            IdempotencyKey key = dbKey.get();

            if (key.isExpired()) {
                log.debug("Idempotency key expired: {} (expired at: {})", idempotencyKey, key.getExpiresAt());
                return Optional.empty();
            }

            if (!key.matchesRequest(requestHash)) {
                log.warn("Idempotency key reused with different request body for key: {}", idempotencyKey);
                throw new IllegalArgumentException("Idempotency key reused with different request body");
            }

            // Increment request count for monitoring
            key.incrementRequestCount();
            idempotencyKeyRepository.save(key);

            // Cache in Redis for future requests
            ResponseEntity<Object> response = buildResponseFromKey(key);
            Duration ttl = key.getRemainingTime();
            cacheInRedis(redisKey, requestHash, response, ttl);

            log.debug("Idempotency hit in database for key: {} (request count: {})", idempotencyKey,
                    key.getRequestCount());
            return Optional.of(response);
        }

        return Optional.empty();
    }

    /**
     * Store idempotent response for future requests
     */
    @Transactional
    public <T> void storeIdempotentResponse(String merchantId, String idempotencyKey, Object requestBody,
            ResponseEntity<T> response) {
        if (!isValidIdempotencyRequest(merchantId, idempotencyKey)) {
            return;
        }

        String requestHash = generateSmartRequestHash(requestBody);
        String responseBodyString = serializeResponse(response.getBody());
        Duration ttl = calculateTTL(requestBody);
        String operationType = extractOperationType(idempotencyKey);

        // Store in database
        IdempotencyKey key = IdempotencyKey.create(
                merchantId,
                idempotencyKey,
                requestHash,
                response.getStatusCode().value(),
                responseBodyString,
                ttl,
                operationType);
        idempotencyKeyRepository.save(key);

        // Cache in Redis
        String redisKey = buildRedisKey(merchantId, idempotencyKey);
        cacheInRedis(redisKey, requestHash, response, ttl);

        log.debug("Stored idempotent response for key: {} with TTL: {} operation: {}",
                idempotencyKey, ttl, operationType);
    }

    /**
     * Cleanup expired keys - runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredKeys() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
            int deletedCount = idempotencyKeyRepository.deleteExpiredKeys(cutoff);
            log.info("Cleaned up {} expired idempotency keys", deletedCount);
        } catch (Exception e) {
            log.error("Error during idempotency key cleanup", e);
        }
    }

    /**
     * Get current count of active keys for monitoring
     */
    public Long getActiveKeyCount(String merchantId) {
        return idempotencyKeyRepository.countActiveKeysByMerchant(merchantId, LocalDateTime.now());
    }

    /**
     * Get idempotency key statistics for monitoring
     */
    public IdempotencyStats getStats(String merchantId) {
        Long activeCount = getActiveKeyCount(merchantId);
        Long totalCount = idempotencyKeyRepository.countByMerchantId(merchantId);

        return IdempotencyStats.builder()
                .merchantId(merchantId)
                .activeKeys(activeCount)
                .totalKeys(totalCount)
                .build();
    }

    // Private methods

    private boolean isValidIdempotencyRequest(String merchantId, String idempotencyKey) {
        return merchantId != null && !merchantId.trim().isEmpty() &&
                idempotencyKey != null && !idempotencyKey.trim().isEmpty();
    }

    private boolean isValidKeyFormat(String key) {
        // Format: {merchant_id}_{operation}_{timestamp}_{random}
        String pattern = "^[a-zA-Z0-9]+_[a-zA-Z_]+_\\d+_[a-f0-9]{8}$";
        return key.matches(pattern);
    }

    private Optional<ResponseEntity<Object>> checkRedisCache(String redisKey, String requestHash) {
        try {
            IdempotencyCacheDto cached = (IdempotencyCacheDto) redisTemplate.opsForValue().get(redisKey);

            if (cached != null) {
                if (!requestHash.equals(cached.getRequestHash())) {
                    log.warn("Request hash mismatch in Redis cache for key: {}", redisKey);
                    throw new IllegalArgumentException("Idempotency key reused with different request body");
                }

                return Optional.of(buildResponseFromCache(cached));
            }
        } catch (Exception e) {
            log.warn("Error checking Redis cache for idempotency key: {}", redisKey, e);
        }

        return Optional.empty();
    }

    private <T> void cacheInRedis(String redisKey, String requestHash, ResponseEntity<T> response, Duration ttl) {
        try {
            String headers = serializeHeaders(response.getHeaders());
            IdempotencyCacheDto cacheDto = new IdempotencyCacheDto(
                    requestHash,
                    response.getStatusCode().value(),
                    serializeResponse(response.getBody()),
                    headers);

            long ttlSeconds = Math.min(ttl.toSeconds(), DEFAULT_REDIS_TTL.toSeconds());
            redisTemplate.opsForValue().set(redisKey, cacheDto, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Error caching idempotency response in Redis: {}", redisKey, e);
        }
    }

    private ResponseEntity<Object> buildResponseFromKey(IdempotencyKey key) {
        Object responseBody = parseResponseBody(key.getResponseBody());
        return ResponseEntity.status(key.getResponseCode()).body(responseBody);
    }

    private ResponseEntity<Object> buildResponseFromCache(IdempotencyCacheDto cached) {
        Object responseBody = parseResponseBody(cached.getResponseBody());
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(cached.getStatusCode());

        // Add headers if they exist
        if (cached.getHeaders() != null && !cached.getHeaders().isEmpty()) {
            HttpHeaders headers = parseHeaders(cached.getHeaders());
            headers.forEach((key, values) -> responseBuilder.header(key, values.toArray(new String[0])));
        }

        return responseBuilder.body(responseBody);
    }

    /**
     * Generate smart request hash based on request type
     * Only hash critical business fields, ignore timestamps and IDs
     */
    private String generateSmartRequestHash(Object requestBody) {
        try {
            String criticalData = extractCriticalData(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(criticalData.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating request hash", e);
        }
    }

    /**
     * Extract only critical business data for hashing
     */
    private String extractCriticalData(Object requestBody) {
        if (requestBody instanceof PaymentRequest request) {
            return String.format("%s_%s_%s_%s_%s",
                    request.getMerchantId(),
                    request.getAmount().toString(),
                    request.getCurrency(),
                    request.getPaymentMethodId(),
                    request.getDescription() != null ? request.getDescription() : "");
        } else if (requestBody instanceof CaptureRequest captureRequest) {
            return String.format("CAPTURE_%s", captureRequest.getAmount().toString());
        } else if (requestBody instanceof RefundRequest refundRequest) {
            return String.format("REFUND_%s_%s",
                    refundRequest.getAmount().toString(),
                    refundRequest.getReason() != null ? refundRequest.getReason() : "");
        }

        // Fallback to full JSON hash for unknown types
        return JsonUtil.toJson(requestBody);
    }

    /**
     * Calculate TTL based on request type and business logic
     */
    private Duration calculateTTL(Object requestBody) {
        if (requestBody instanceof PaymentRequest paymentRequest) {
            // High-value payments get longer TTL
            if (paymentRequest.getAmount().compareTo(java.math.BigDecimal.valueOf(1000)) > 0) {
                return Duration.ofDays(30);
            }
            return PAYMENT_CREATE_TTL;
        } else if (requestBody instanceof CaptureRequest) {
            return PAYMENT_CAPTURE_TTL;
        } else if (requestBody instanceof RefundRequest) {
            return PAYMENT_REFUND_TTL;
        }

        return DEFAULT_DB_TTL;
    }

    /**
     * Extract operation type from idempotency key
     */
    private String extractOperationType(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return "UNKNOWN";
        }

        String[] parts = idempotencyKey.split("_");
        if (parts.length >= 2) {
            return parts[1].toUpperCase();
        }
        return "UNKNOWN";
    }

    private String serializeResponse(Object responseBody) {
        try {
            return JsonUtil.toJson(responseBody);
        } catch (Exception e) {
            log.warn("Error serializing response body", e);
            return "{}";
        }
    }

    private Object parseResponseBody(String responseBody) {
        try {
            return JsonUtil.fromJson(responseBody, Object.class);
        } catch (Exception e) {
            log.warn("Error parsing response body", e);
            return responseBody;
        }
    }

    private String buildRedisKey(String merchantId, String idempotencyKey) {
        return REDIS_PREFIX + merchantId + ":" + idempotencyKey;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String serializeHeaders(HttpHeaders headers) {
        try {
            return JsonUtil.toJson(headers.toSingleValueMap());
        } catch (Exception e) {
            log.warn("Error serializing headers", e);
            return "{}";
        }
    }

    private HttpHeaders parseHeaders(String headersJson) {
        try {
            if (headersJson == null || headersJson.isEmpty() || "{}".equals(headersJson)) {
                return new HttpHeaders();
            }
            Object parsed = JsonUtil.fromJson(headersJson, Object.class);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> headersMap = (Map<String, String>) parsed;
                HttpHeaders headers = new HttpHeaders();
                headersMap.forEach((key, values) -> {
                    if (values != null) {
                        headers.add(key, values);
                    }
                });
                return headers;
            }
            return new HttpHeaders();
        } catch (Exception e) {
            log.warn("Error parsing headers from JSON", e);
            return new HttpHeaders();
        }
    }

    // Inner class for statistics
    @Data
    @Builder
    public static class IdempotencyStats {
        private String merchantId;
        private Long activeKeys;
        private Long totalKeys;
    }
}