package com.hnh.example.transaction_example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.hnh.example.transaction_example.domain.IdempotencyKey;
import com.hnh.example.transaction_example.dto.IdempotencyCacheDto;
import com.hnh.example.transaction_example.repository.IdempotencyKeyRepository;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private static final String MERCHANT_ID = "merchant123";
    private static final String IDEMPOTENCY_KEY = "key123";
    private static final String REQUEST_BODY = "{\"amount\":100}";

    @BeforeEach
    void setUp() {
        // Only set up Redis mock when needed
    }

    @Test
    @DisplayName("Check Idempotency with valid Redis cache hit")
    void testCheckIdempotency_WithValidRedisCache() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String actualHash = idempotencyService.generateRequestHash(REQUEST_BODY);
        IdempotencyCacheDto cacheDto = new IdempotencyCacheDto(
                actualHash, 200, "{\"status\":\"success\"}", "{}");
        when(valueOperations.get(anyString())).thenReturn(cacheDto);

        // When
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                MERCHANT_ID, IDEMPOTENCY_KEY, REQUEST_BODY);

        // Then
        assertTrue(result.isPresent());
        assertEquals(200, result.get().getStatusCode().value());
        verify(valueOperations).get(contains("idempotency:merchant123:key123"));
    }

    @Test
    @DisplayName("Check Idempotency with database fallback")
    void testCheckIdempotency_WithDatabaseFallback() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        // Generate the actual hash that will be used
        String actualHash = idempotencyService.generateRequestHash(REQUEST_BODY);

        IdempotencyKey dbKey = IdempotencyKey.create(
                MERCHANT_ID, IDEMPOTENCY_KEY, actualHash, 200, "{\"status\":\"success\"}");
        when(idempotencyKeyRepository.findByMerchantIdAndKey(MERCHANT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(dbKey));

        // When
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                MERCHANT_ID, IDEMPOTENCY_KEY, REQUEST_BODY);

        // Then
        assertTrue(result.isPresent());
        assertEquals(200, result.get().getStatusCode().value());
        verify(valueOperations).set(anyString(), any(IdempotencyCacheDto.class), anyLong(), any());
    }

    @Test
    void testStoreIdempotentResponse() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ResponseEntity<Object> response = ResponseEntity.ok("Success");
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenReturn(new IdempotencyKey());

        // When
        idempotencyService.storeIdempotentResponse(MERCHANT_ID, IDEMPOTENCY_KEY, REQUEST_BODY, response);

        // Then
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
        verify(valueOperations).set(anyString(), any(IdempotencyCacheDto.class), anyLong(), any());
    }

    @Test
    void testCheckIdempotency_WithEmptyKey() {
        // When
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                MERCHANT_ID, "", REQUEST_BODY);

        // Then
        assertFalse(result.isPresent());
        verifyNoInteractions(redisTemplate, idempotencyKeyRepository);
    }

    @Test
    void testCheckIdempotency_WithNullKey() {
        // When
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                MERCHANT_ID, null, REQUEST_BODY);

        // Then
        assertFalse(result.isPresent());
        verifyNoInteractions(redisTemplate, idempotencyKeyRepository);
    }

    @Test
    void testCheckIdempotency_WithExpiredKey() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        // Generate the actual hash that will be used
        String actualHash = idempotencyService.generateRequestHash(REQUEST_BODY);

        IdempotencyKey expiredKey = IdempotencyKey.create(
                MERCHANT_ID, IDEMPOTENCY_KEY, actualHash, 200, "{\"status\":\"success\"}");
        // Set expired time
        expiredKey.setExpiresAt(LocalDateTime.now().minusDays(2));

        when(idempotencyKeyRepository.findByMerchantIdAndKey(MERCHANT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(expiredKey));

        // When
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                MERCHANT_ID, IDEMPOTENCY_KEY, REQUEST_BODY);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testCheckIdempotency_WithDifferentRequestHash() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        IdempotencyKey dbKey = IdempotencyKey.create(
                MERCHANT_ID, IDEMPOTENCY_KEY, "differentHash", 200, "{\"status\":\"success\"}");
        when(idempotencyKeyRepository.findByMerchantIdAndKey(MERCHANT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(dbKey));

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> idempotencyService.checkIdempotency(MERCHANT_ID, IDEMPOTENCY_KEY, REQUEST_BODY));
    }

    @Test
    void testHeadersSerialization() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer token123");

        ResponseEntity<Object> response = ResponseEntity.ok()
                .headers(headers)
                .body("Success");

        when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenReturn(new IdempotencyKey());

        // When
        idempotencyService.storeIdempotentResponse(MERCHANT_ID, IDEMPOTENCY_KEY, REQUEST_BODY, response);

        // Then
        verify(valueOperations).set(anyString(), any(IdempotencyCacheDto.class), anyLong(), any());
    }
}
