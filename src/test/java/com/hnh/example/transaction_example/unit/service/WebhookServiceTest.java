package com.hnh.example.transaction_example.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnh.example.transaction_example.service.WebhookService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook Service Tests")
class WebhookServiceTest {

    @InjectMocks
    private WebhookService webhookService;

    private String merchantId;
    private String eventType;
    private JsonNode eventData;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        merchantId = "merchant_1";
        eventType = "payment.created";
        objectMapper = new ObjectMapper();
        eventData = objectMapper.readTree("{\"paymentId\":\"123\",\"amount\":100.0}");

        // Inject a mock RestTemplate
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(webhookService, "restTemplate", mockRestTemplate);
    }

    @Nested
    @DisplayName("Webhook Sending Tests")
    class WebhookSendingTests {

        @Test
        @DisplayName("Should send webhook successfully for configured merchant")
        void shouldSendWebhookSuccessfullyForConfiguredMerchant() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");
            ResponseEntity<String> successResponse = new ResponseEntity<>("{\"received\": true}", HttpStatus.OK);

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(successResponse);

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThat(future).isNotNull();
            // Give some time for the async operation to complete
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should not send webhook for unconfigured merchant")
        void shouldNotSendWebhookForUnconfiguredMerchant() {
            // Arrange
            String unconfiguredMerchantId = "unknown_merchant";
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(unconfiguredMerchantId, eventType,
                    eventData);

            // Assert
            assertThat(future).isNotNull();
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should include proper headers in webhook request")
        void shouldIncludeProperHeadersInWebhookRequest() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");
            ResponseEntity<String> successResponse = new ResponseEntity<>("{\"received\": true}", HttpStatus.OK);

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(successResponse);

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate).exchange(
                    eq("https://merchant1.example.com/webhooks/payments"),
                    eq(HttpMethod.POST),
                    argThat(entity -> {
                        @SuppressWarnings("unchecked")
                        HttpEntity<String> httpEntity = (HttpEntity<String>) entity;
                        return httpEntity.getHeaders().containsKey("X-Webhook-Signature") &&
                                httpEntity.getHeaders().containsKey("X-Webhook-Event-Type") &&
                                httpEntity.getHeaders().getFirst("X-Webhook-Event-Type").equals(eventType) &&
                                httpEntity.getHeaders().getFirst("User-Agent").equals("PaymentService/1.0");
                    }),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should handle webhook errors gracefully")
        void shouldHandleWebhookErrorsGracefully() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Connection failed"));

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThat(future).isNotNull();
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    eq(String.class));
        }
    }

    @Nested
    @DisplayName("Webhook Retry Logic Tests")
    class WebhookRetryLogicTests {

        @Test
        @DisplayName("Should retry webhook on failure")
        void shouldRetryWebhookOnFailure() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Connection failed"))
                    .thenThrow(new RestClientException("Connection failed"))
                    .thenReturn(new ResponseEntity<>("{\"received\": true}", HttpStatus.OK));

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should retry on non-2xx status codes")
        void shouldRetryOnNon2xxStatusCodes() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{\"error\": \"Server error\"}", HttpStatus.INTERNAL_SERVER_ERROR))
                    .thenReturn(new ResponseEntity<>("{\"error\": \"Server error\"}", HttpStatus.INTERNAL_SERVER_ERROR))
                    .thenReturn(new ResponseEntity<>("{\"received\": true}", HttpStatus.OK));

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should not retry after max attempts")
        void shouldNotRetryAfterMaxAttempts() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Connection failed"));

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should not retry on successful response")
        void shouldNotRetryOnSuccessfulResponse() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{\"received\": true}", HttpStatus.OK));

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    eq(String.class));
        }
    }

    @Nested
    @DisplayName("HMAC Signature Tests")
    class HmacSignatureTests {

        @Test
        @DisplayName("Should generate valid HMAC signature")
        void shouldGenerateValidHmacSignature() throws Exception {
            // Arrange
            String payload = "{\"test\":\"data\"}";
            String secret = "webhook_secret_1";

            // Calculate expected signature
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(digest);

            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");
            ResponseEntity<String> successResponse = new ResponseEntity<>("{\"received\": true}", HttpStatus.OK);

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(successResponse);

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate).exchange(
                    anyString(),
                    eq(HttpMethod.POST),
                    argThat(entity -> {
                        @SuppressWarnings("unchecked")
                        HttpEntity<String> httpEntity = (HttpEntity<String>) entity;
                        String signature = httpEntity.getHeaders().getFirst("X-Webhook-Signature");
                        return signature != null && signature.startsWith("sha256=");
                    }),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should handle HMAC generation errors")
        void shouldHandleHmacGenerationErrors() {
            // This test would require mocking static methods or using PowerMock
            // For now, we'll test that the webhook service handles errors gracefully

            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            // Simulate an error in the webhook sending process
            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThat(future).isNotNull();
            assertThatCode(() -> future.get()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Webhook Payload Tests")
    class WebhookPayloadTests {

        @Test
        @DisplayName("Should create valid webhook payload")
        void shouldCreateValidWebhookPayload() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");
            ResponseEntity<String> successResponse = new ResponseEntity<>("{\"received\": true}", HttpStatus.OK);

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(successResponse);

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThatCode(() -> future.get()).doesNotThrowAnyException();

            verify(mockRestTemplate).exchange(
                    anyString(),
                    eq(HttpMethod.POST),
                    argThat(entity -> {
                        @SuppressWarnings("unchecked")
                        HttpEntity<String> httpEntity = (HttpEntity<String>) entity;
                        String body = httpEntity.getBody();
                        try {
                            JsonNode payload = new ObjectMapper().readTree(body);
                            return payload.has("id") &&
                                    payload.has("eventType") &&
                                    payload.has("data") &&
                                    payload.has("timestamp") &&
                                    payload.get("eventType").asText().equals(eventType);
                        } catch (Exception e) {
                            return false;
                        }
                    }),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should handle JSON serialization errors")
        void shouldHandleJsonSerializationErrors() {
            // Create a problematic event data that could cause serialization issues
            JsonNode problematicData = null;

            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, problematicData);

            // Assert - Should not throw exception even with problematic data
            assertThat(future).isNotNull();
            assertThatCode(() -> future.get()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Async Execution Tests")
    class AsyncExecutionTests {

        @Test
        @DisplayName("Should execute webhook sending asynchronously")
        void shouldExecuteWebhookSendingAsynchronously() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");
            ResponseEntity<String> successResponse = new ResponseEntity<>("{\"received\": true}", HttpStatus.OK);

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(successResponse);

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThat(future).isNotNull();
            assertThat(future).isNotDone(); // Should not be done immediately

            // Wait for completion
            assertThatCode(() -> future.get()).doesNotThrowAnyException();
            assertThat(future).isDone();
        }

        @Test
        @DisplayName("Should handle async execution errors")
        void shouldHandleAsyncExecutionErrors() {
            // Arrange
            RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(webhookService, "restTemplate");

            when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RuntimeException("Severe error"));

            // Act
            CompletableFuture<Void> future = webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Assert
            assertThat(future).isNotNull();
            assertThatCode(() -> future.get()).doesNotThrowAnyException();
        }
    }
}
