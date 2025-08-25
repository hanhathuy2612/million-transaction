package com.hnh.example.transaction_example.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.dto.CaptureRequest;
import com.hnh.example.transaction_example.dto.RefundRequest;
import com.hnh.example.transaction_example.repository.PaymentRepository;
import com.hnh.example.transaction_example.testutils.TestContainerConfig;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;
import com.hnh.example.transaction_example.testutils.MockWebhookServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
@DisplayName("Payment End-to-End Tests")
class PaymentE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockWebhookServer mockWebhookServer;
    private String baseUrl;
    private String merchantId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/payments";
        merchantId = "merchant_1";
        idempotencyKey = "idem_" + UUID.randomUUID().toString();

        mockWebhookServer = new MockWebhookServer();
        mockWebhookServer.start();
        mockWebhookServer.stubSuccessfulWebhook();
    }

    @AfterEach
    void tearDown() {
        if (mockWebhookServer != null) {
            mockWebhookServer.stop();
        }
        // Clean up Redis
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Nested
    @DisplayName("Complete Payment Flow")
    class CompletePaymentFlow {

        @Test
        @DisplayName("Should complete full payment lifecycle successfully")
        @Transactional
        void shouldCompleteFullPaymentLifecycleSuccessfully() throws Exception {
            // Step 1: Create Payment
            PaymentRequest createRequest = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(200.00))
                    .currency("USD")
                    .paymentMethodId("pm_test_card")
                    .description("E2E Test Payment")
                    .referenceId("order_e2e_123")
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Merchant-ID", merchantId);
            headers.set("Idempotency-Key", idempotencyKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> createEntity = new HttpEntity<>(createRequest, headers);

            ResponseEntity<PaymentResponse> createResponse = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, createEntity, PaymentResponse.class);

            // Verify payment creation
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().getStatus()).isEqualTo("AUTHORIZED");
            assertThat(createResponse.getBody().getAmount()).isEqualTo(BigDecimal.valueOf(200.00));

            UUID paymentId = createResponse.getBody().getId();

            // Step 2: Verify payment is persisted
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                Payment savedPayment = paymentRepository.findById(paymentId).orElse(null);
                assertThat(savedPayment).isNotNull();
                assertThat(savedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.AUTHORIZED);
            });

            // Step 3: Capture Payment
            CaptureRequest captureRequest = TestDataBuilder.captureRequest()
                    .amount(BigDecimal.valueOf(150.00))
                    .description("Partial capture for shipped items")
                    .build();

            HttpEntity<CaptureRequest> captureEntity = new HttpEntity<>(captureRequest, new HttpHeaders());

            ResponseEntity<PaymentResponse> captureResponse = restTemplate.exchange(
                    baseUrl + "/" + paymentId + "/capture", HttpMethod.POST, captureEntity, PaymentResponse.class);

            // Verify capture
            assertThat(captureResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(captureResponse.getBody()).isNotNull();
            assertThat(captureResponse.getBody().getStatus()).isEqualTo("CAPTURED");
            assertThat(captureResponse.getBody().getCapturedAmount()).isEqualTo(BigDecimal.valueOf(150.00));

            // Step 4: Verify capture is persisted
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                Payment capturedPayment = paymentRepository.findById(paymentId).orElse(null);
                assertThat(capturedPayment).isNotNull();
                assertThat(capturedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.CAPTURED);
                assertThat(capturedPayment.getCapturedAmount()).isEqualTo(BigDecimal.valueOf(150.00));
            });

            // Step 5: Refund Part of Payment
            RefundRequest refundRequest = TestDataBuilder.refundRequest()
                    .amount(BigDecimal.valueOf(50.00))
                    .reason("Customer returned one item")
                    .build();

            HttpEntity<RefundRequest> refundEntity = new HttpEntity<>(refundRequest, new HttpHeaders());

            ResponseEntity<PaymentResponse> refundResponse = restTemplate.exchange(
                    baseUrl + "/" + paymentId + "/refund", HttpMethod.POST, refundEntity, PaymentResponse.class);

            // Verify refund
            assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(refundResponse.getBody()).isNotNull();
            assertThat(refundResponse.getBody().getRefundedAmount()).isEqualTo(BigDecimal.valueOf(50.00));

            // Step 6: Verify refund is persisted
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                Payment refundedPayment = paymentRepository.findById(paymentId).orElse(null);
                assertThat(refundedPayment).isNotNull();
                assertThat(refundedPayment.getRefundedAmount()).isEqualTo(BigDecimal.valueOf(50.00));
            });

            // Step 7: Get Payment Details
            ResponseEntity<PaymentResponse> getResponse = restTemplate.getForEntity(
                    baseUrl + "/" + paymentId, PaymentResponse.class);

            // Verify final state
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().getId()).isEqualTo(paymentId);
            assertThat(getResponse.getBody().getAmount()).isEqualTo(BigDecimal.valueOf(200.00));
            assertThat(getResponse.getBody().getCapturedAmount()).isEqualTo(BigDecimal.valueOf(150.00));
            assertThat(getResponse.getBody().getRefundedAmount()).isEqualTo(BigDecimal.valueOf(50.00));
        }

        @Test
        @DisplayName("Should handle payment failure gracefully")
        void shouldHandlePaymentFailureGracefully() {
            // Create a payment that will fail (simulate failure scenario)
            PaymentRequest failureRequest = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(0.01)) // Simulate a failure condition
                    .currency("USD")
                    .paymentMethodId("pm_fail_card")
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Merchant-ID", merchantId);
            headers.set("Idempotency-Key", idempotencyKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> entity = new HttpEntity<>(failureRequest, headers);

            ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, PaymentResponse.class);

            // Verify failure handling
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo("FAILED");
        }
    }

    @Nested
    @DisplayName("Idempotency Behavior")
    class IdempotencyBehavior {

        @Test
        @DisplayName("Should handle duplicate requests with same idempotency key")
        void shouldHandleDuplicateRequestsWithSameIdempotencyKey() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Merchant-ID", merchantId);
            headers.set("Idempotency-Key", idempotencyKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

            // Act - First request
            ResponseEntity<PaymentResponse> firstResponse = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, PaymentResponse.class);

            // Act - Second request with same idempotency key
            ResponseEntity<PaymentResponse> secondResponse = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, PaymentResponse.class);

            // Assert
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            assertThat(firstResponse.getBody()).isNotNull();
            assertThat(secondResponse.getBody()).isNotNull();

            // Should return the same payment
            assertThat(firstResponse.getBody().getId()).isEqualTo(secondResponse.getBody().getId());
            assertThat(firstResponse.getBody().getAmount()).isEqualTo(secondResponse.getBody().getAmount());
        }

        @Test
        @DisplayName("Should reject different request with same idempotency key")
        void shouldRejectDifferentRequestWithSameIdempotencyKey() {
            // Arrange - First request
            PaymentRequest firstRequest = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Merchant-ID", merchantId);
            headers.set("Idempotency-Key", idempotencyKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> firstEntity = new HttpEntity<>(firstRequest, headers);

            // Act - First request
            ResponseEntity<PaymentResponse> firstResponse = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, firstEntity, PaymentResponse.class);

            // Arrange - Different request with same idempotency key
            PaymentRequest differentRequest = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(200.00)) // Different amount
                    .build();

            HttpEntity<PaymentRequest> differentEntity = new HttpEntity<>(differentRequest, headers);

            // Act - Second request with different data but same idempotency key
            ResponseEntity<String> secondResponse = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, differentEntity, String.class);

            // Assert
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Caching Behavior")
    class CachingBehavior {

        @Test
        @DisplayName("Should cache payment data in Redis")
        void shouldCachePaymentDataInRedis() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Merchant-ID", merchantId);
            headers.set("Idempotency-Key", idempotencyKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

            // Act
            ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, PaymentResponse.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            UUID paymentId = response.getBody().getId();

            // Verify caching in Redis
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                String cacheKey = "payment:" + paymentId.toString();
                Object cachedPayment = redisTemplate.opsForValue().get(cacheKey);
                assertThat(cachedPayment).isNotNull();
            });
        }

        @Test
        @DisplayName("Should cache idempotency responses")
        void shouldCacheIdempotencyResponses() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Merchant-ID", merchantId);
            headers.set("Idempotency-Key", idempotencyKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

            // Act
            ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, PaymentResponse.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Verify idempotency caching in Redis
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                String idempotencyRedisKey = "idempotency:" + merchantId + ":" + idempotencyKey;
                Object cachedResponse = redisTemplate.opsForValue().get(idempotencyRedisKey);
                assertThat(cachedResponse).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle validation errors gracefully")
        void shouldHandleValidationErrorsGracefully() {
            // Arrange - Invalid request
            PaymentRequest invalidRequest = PaymentRequest.builder()
                    .merchantId("") // Invalid empty merchant ID
                    .amount(BigDecimal.valueOf(-100.00)) // Invalid negative amount
                    .currency("INVALID") // Invalid currency
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Merchant-ID", merchantId);
            headers.set("Idempotency-Key", idempotencyKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> entity = new HttpEntity<>(invalidRequest, headers);

            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, String.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should handle missing headers gracefully")
        void shouldHandleMissingHeadersGracefully() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            // Missing required headers
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, String.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should handle non-existent payment gracefully")
        void shouldHandleNonExistentPaymentGracefully() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();

            // Act
            ResponseEntity<String> response = restTemplate.getForEntity(
                    baseUrl + "/" + nonExistentId, String.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Performance and Load")
    class PerformanceAndLoad {

        @Test
        @DisplayName("Should handle multiple concurrent requests")
        void shouldHandleMultipleConcurrentRequests() throws InterruptedException {
            // Arrange
            int numberOfRequests = 10;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(numberOfRequests);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

            // Act
            for (int i = 0; i < numberOfRequests; i++) {
                final int requestId = i;
                new Thread(() -> {
                    try {
                        PaymentRequest request = TestDataBuilder.paymentRequest()
                                .merchantId(merchantId)
                                .amount(BigDecimal.valueOf(100.00 + requestId))
                                .referenceId("concurrent_" + requestId)
                                .build();

                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-Merchant-ID", merchantId);
                        headers.set("Idempotency-Key", "concurrent_" + requestId);
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

                        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                                baseUrl, HttpMethod.POST, entity, PaymentResponse.class);

                        if (response.getStatusCode().is2xxSuccessful()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            // Assert
            latch.await(30, TimeUnit.SECONDS);
            assertThat(successCount.get()).isEqualTo(numberOfRequests);
        }
    }
}
