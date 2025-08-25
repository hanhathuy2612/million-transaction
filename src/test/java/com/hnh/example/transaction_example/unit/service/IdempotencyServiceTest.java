package com.hnh.example.transaction_example.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnh.example.transaction_example.domain.IdempotencyKey;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.repository.IdempotencyKeyRepository;
import com.hnh.example.transaction_example.service.IdempotencyService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Idempotency Service Tests")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private String merchantId;
    private String idempotencyKey;
    private PaymentRequest paymentRequest;
    private PaymentResponse paymentResponse;
    private ResponseEntity<Object> responseEntity;

    @BeforeEach
    void setUp() {
        merchantId = "merchant_1";
        idempotencyKey = "idem_12345";
        paymentRequest = TestDataBuilder.paymentRequest()
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .build();

        paymentResponse = TestDataBuilder.paymentResponse()
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .build();

        responseEntity = ResponseEntity.status(HttpStatus.CREATED).body(paymentResponse);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("Check Idempotency Tests")
    class CheckIdempotencyTests {

        @Test
        @DisplayName("Should return empty when idempotency key is null")
        void shouldReturnEmptyWhenIdempotencyKeyIsNull() {
            // Act
            Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                    merchantId, null, paymentRequest);

            // Assert
            assertThat(result).isEmpty();
            verify(redisTemplate, never()).opsForValue();
            verify(idempotencyKeyRepository, never()).findByMerchantIdAndKey(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return empty when idempotency key is empty")
        void shouldReturnEmptyWhenIdempotencyKeyIsEmpty() {
            // Act
            Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                    merchantId, "", paymentRequest);

            // Assert
            assertThat(result).isEmpty();
            verify(redisTemplate, never()).opsForValue();
            verify(idempotencyKeyRepository, never()).findByMerchantIdAndKey(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return cached response from Redis")
        void shouldReturnCachedResponseFromRedis() throws Exception {
            // Arrange
            String requestHash = "hash123";
            Map<String, Object> cachedValue = Map.of(
                    "requestHash", requestHash,
                    "statusCode", 201,
                    "responseBody", "{\"id\":\"123\"}");

            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(valueOperations.get(anyString())).thenReturn(cachedValue);
            when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of("id", "123"));

            // Act
            Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(idempotencyKeyRepository, never()).findByMerchantIdAndKey(anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw exception when Redis cache has different request hash")
        void shouldThrowExceptionWhenRedisCacheHasDifferentRequestHash() throws Exception {
            // Arrange
            String requestHash = "hash123";
            String differentHash = "different_hash";
            Map<String, Object> cachedValue = Map.of(
                    "requestHash", differentHash,
                    "statusCode", 201,
                    "responseBody", "{\"id\":\"123\"}");

            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(valueOperations.get(anyString())).thenReturn(cachedValue);

            // Act & Assert
            assertThatThrownBy(() -> idempotencyService.checkIdempotency(merchantId, idempotencyKey, paymentRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency key reused with different request body");
        }

        @Test
        @DisplayName("Should return cached response from database")
        void shouldReturnCachedResponseFromDatabase() throws Exception {
            // Arrange
            String requestHash = "hash123";
            IdempotencyKey dbKey = IdempotencyKey.builder()
                    .merchantId(merchantId)
                    .key(idempotencyKey)
                    .requestHash(requestHash)
                    .responseCode(201)
                    .responseBody("{\"id\":\"123\"}")
                    .expiresAt(java.time.LocalDateTime.now().plusHours(24))
                    .build();

            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(valueOperations.get(anyString())).thenReturn(null);
            when(idempotencyKeyRepository.findByMerchantIdAndKey(merchantId, idempotencyKey))
                    .thenReturn(Optional.of(dbKey));
            when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of("id", "123"));

            // Act
            Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(valueOperations).set(anyString(), any(), anyLong(), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should return empty when database key is expired")
        void shouldReturnEmptyWhenDatabaseKeyIsExpired() throws Exception {
            // Arrange
            String requestHash = "hash123";
            IdempotencyKey expiredKey = IdempotencyKey.builder()
                    .merchantId(merchantId)
                    .key(idempotencyKey)
                    .requestHash(requestHash)
                    .expiresAt(java.time.LocalDateTime.now().minusHours(1))
                    .build();

            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(valueOperations.get(anyString())).thenReturn(null);
            when(idempotencyKeyRepository.findByMerchantIdAndKey(merchantId, idempotencyKey))
                    .thenReturn(Optional.of(expiredKey));

            // Act
            Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(result).isEmpty();
            verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should throw exception when database key has different request hash")
        void shouldThrowExceptionWhenDatabaseKeyHasDifferentRequestHash() throws Exception {
            // Arrange
            String requestHash = "hash123";
            String differentHash = "different_hash";
            IdempotencyKey dbKey = IdempotencyKey.builder()
                    .merchantId(merchantId)
                    .key(idempotencyKey)
                    .requestHash(differentHash)
                    .expiresAt(java.time.LocalDateTime.now().plusHours(24))
                    .build();

            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(valueOperations.get(anyString())).thenReturn(null);
            when(idempotencyKeyRepository.findByMerchantIdAndKey(merchantId, idempotencyKey))
                    .thenReturn(Optional.of(dbKey));

            // Act & Assert
            assertThatThrownBy(() -> idempotencyService.checkIdempotency(merchantId, idempotencyKey, paymentRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency key reused with different request body");
        }

        @Test
        @DisplayName("Should return empty when no cached response exists")
        void shouldReturnEmptyWhenNoCachedResponseExists() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(valueOperations.get(anyString())).thenReturn(null);
            when(idempotencyKeyRepository.findByMerchantIdAndKey(merchantId, idempotencyKey))
                    .thenReturn(Optional.empty());

            // Act
            Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle Redis errors gracefully")
        void shouldHandleRedisErrorsGracefully() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));
            when(idempotencyKeyRepository.findByMerchantIdAndKey(merchantId, idempotencyKey))
                    .thenReturn(Optional.empty());

            // Act
            Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(result).isEmpty();
            verify(idempotencyKeyRepository).findByMerchantIdAndKey(merchantId, idempotencyKey);
        }
    }

    @Nested
    @DisplayName("Store Idempotent Response Tests")
    class StoreIdempotentResponseTests {

        @Test
        @DisplayName("Should store idempotent response successfully")
        void shouldStoreIdempotentResponseSuccessfully() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(objectMapper.writeValueAsString(paymentResponse)).thenReturn("{\"id\":\"123\"}");
            when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenReturn(mock(IdempotencyKey.class));

            // Act
            idempotencyService.storeIdempotentResponse(merchantId, idempotencyKey, paymentRequest, responseEntity);

            // Assert
            verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
            verify(valueOperations).set(anyString(), any(), anyLong(), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should not store when idempotency key is null")
        void shouldNotStoreWhenIdempotencyKeyIsNull() {
            // Act
            idempotencyService.storeIdempotentResponse(merchantId, null, paymentRequest, responseEntity);

            // Assert
            verify(idempotencyKeyRepository, never()).save(any());
            verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should not store when idempotency key is empty")
        void shouldNotStoreWhenIdempotencyKeyIsEmpty() {
            // Act
            idempotencyService.storeIdempotentResponse(merchantId, "", paymentRequest, responseEntity);

            // Assert
            verify(idempotencyKeyRepository, never()).save(any());
            verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should handle serialization errors gracefully")
        void shouldHandleSerializationErrorsGracefully() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(objectMapper.writeValueAsString(paymentResponse))
                    .thenThrow(new RuntimeException("Serialization error"));
            when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenReturn(mock(IdempotencyKey.class));

            // Act
            idempotencyService.storeIdempotentResponse(merchantId, idempotencyKey, paymentRequest, responseEntity);

            // Assert
            verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
            // Should still cache in Redis even if serialization fails (with default value)
            verify(valueOperations).set(anyString(), any(), anyLong(), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should handle Redis storage errors gracefully")
        void shouldHandleRedisStorageErrorsGracefully() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");
            when(objectMapper.writeValueAsString(paymentResponse)).thenReturn("{\"id\":\"123\"}");
            when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenReturn(mock(IdempotencyKey.class));
            doThrow(new RuntimeException("Redis error")).when(valueOperations).set(anyString(), any(), anyLong(),
                    any());

            // Act
            idempotencyService.storeIdempotentResponse(merchantId, idempotencyKey, paymentRequest, responseEntity);

            // Assert
            verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
            // Should not throw exception even if Redis fails
        }
    }

    @Nested
    @DisplayName("Request Hash Generation Tests")
    class RequestHashGenerationTests {

        @Test
        @DisplayName("Should generate consistent hash for same request")
        void shouldGenerateConsistentHashForSameRequest() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(paymentRequest)).thenReturn("{\"amount\":100.0}");

            // Act
            Optional<ResponseEntity<Object>> result1 = idempotencyService.checkIdempotency(
                    merchantId, idempotencyKey, paymentRequest);
            Optional<ResponseEntity<Object>> result2 = idempotencyService.checkIdempotency(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert - Both should generate the same hash and therefore both should return
            // empty
            assertThat(result1).isEmpty();
            assertThat(result2).isEmpty();
            verify(objectMapper, times(2)).writeValueAsString(paymentRequest);
        }

        @Test
        @DisplayName("Should handle JSON processing exceptions")
        void shouldHandleJsonProcessingExceptions() throws Exception {
            // Arrange
            when(objectMapper.writeValueAsString(paymentRequest)).thenThrow(new RuntimeException("JSON error"));

            // Act & Assert
            assertThatThrownBy(() -> idempotencyService.checkIdempotency(merchantId, idempotencyKey, paymentRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error generating request hash");
        }
    }
}
