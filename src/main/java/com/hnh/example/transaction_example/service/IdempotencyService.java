package com.hnh.example.transaction_example.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hnh.example.transaction_example.util.JsonUtil;
import com.hnh.example.transaction_example.domain.IdempotencyKey;
import com.hnh.example.transaction_example.repository.IdempotencyKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_PREFIX = "idempotency:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    /**
     * Check if request is idempotent and return cached response if exists
     */
    public Optional<ResponseEntity<Object>> checkIdempotency(String merchantId, String idempotencyKey,
            Object requestBody) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return Optional.empty();
        }

        String requestHash = generateRequestHash(requestBody);
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
                log.debug("Idempotency key expired: {}", idempotencyKey);
                return Optional.empty();
            }

            if (!key.matchesRequest(requestHash)) {
                throw new IllegalArgumentException("Idempotency key reused with different request body");
            }

            // Cache in Redis for future requests
            ResponseEntity<Object> response = buildResponseFromKey(key);
            cacheInRedis(redisKey, requestHash, response);

            log.debug("Idempotency hit in database for key: {}", idempotencyKey);
            return Optional.of(response);
        }

        return Optional.empty();
    }

    /**
     * Store idempotent response for future requests
     */
    @Transactional
    public void storeIdempotentResponse(String merchantId, String idempotencyKey, Object requestBody,
            ResponseEntity<Object> response) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return;
        }

        String requestHash = generateRequestHash(requestBody);
        String responseBody = serializeResponse(response.getBody());

        // Store in database
        IdempotencyKey key = IdempotencyKey.create(
                merchantId,
                idempotencyKey,
                requestHash,
                response.getStatusCode().value(),
                responseBody);
        idempotencyKeyRepository.save(key);

        // Cache in Redis
        String redisKey = buildRedisKey(merchantId, idempotencyKey);
        cacheInRedis(redisKey, requestHash, response);

        log.debug("Stored idempotent response for key: {}", idempotencyKey);
    }

    private Optional<ResponseEntity<Object>> checkRedisCache(String redisKey, String requestHash) {
        try {
            @SuppressWarnings("unchecked")
            var cached = (java.util.Map<String, Object>) redisTemplate.opsForValue().get(redisKey);

            if (cached != null) {
                String cachedHash = (String) cached.get("requestHash");
                if (!requestHash.equals(cachedHash)) {
                    throw new IllegalArgumentException("Idempotency key reused with different request body");
                }

                Integer statusCode = (Integer) cached.get("statusCode");
                String responseBody = (String) cached.get("responseBody");

                Object parsedBody = parseResponseBody(responseBody);
                return Optional.of(ResponseEntity.status(statusCode).body(parsedBody));
            }
        } catch (Exception e) {
            log.warn("Error checking Redis cache for idempotency key: {}", redisKey, e);
        }

        return Optional.empty();
    }

    private void cacheInRedis(String redisKey, String requestHash, ResponseEntity<Object> response) {
        try {
            var cacheValue = java.util.Map.of(
                    "requestHash", requestHash,
                    "statusCode", response.getStatusCode().value(),
                    "responseBody", serializeResponse(response.getBody()));

            redisTemplate.opsForValue().set(redisKey, cacheValue, REDIS_TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Error caching idempotency response in Redis: {}", redisKey, e);
        }
    }

    private ResponseEntity<Object> buildResponseFromKey(IdempotencyKey key) {
        Object responseBody = parseResponseBody(key.getResponseBody());
        return ResponseEntity.status(key.getResponseCode()).body(responseBody);
    }

    private String generateRequestHash(Object requestBody) {
        try {
            String json = JsonUtil.toJson(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating request hash", e);
        }
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
}
