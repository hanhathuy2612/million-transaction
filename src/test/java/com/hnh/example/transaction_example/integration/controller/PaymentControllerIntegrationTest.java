package com.hnh.example.transaction_example.integration.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.CaptureRequest;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.RefundRequest;
import com.hnh.example.transaction_example.repository.PaymentRepository;
import com.hnh.example.transaction_example.testutils.TestContainerConfig;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Payment Controller Integration Tests")
class PaymentControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String merchantId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        merchantId = "merchant_1";
        idempotencyKey = "idem_" + UUID.randomUUID().toString();
    }

    @Nested
    @DisplayName("Create Payment Tests")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should create payment successfully")
        void shouldCreatePaymentSuccessfully() throws Exception {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .currency("USD")
                    .paymentMethodId("pm_test_123")
                    .description("Test payment")
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments")
                    .header("X-Merchant-ID", merchantId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.merchantId").value(merchantId))
                    .andExpect(jsonPath("$.amount").value(100.00))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("Should return 400 for invalid payment request")
        void shouldReturn400ForInvalidPaymentRequest() throws Exception {
            // Arrange
            PaymentRequest invalidRequest = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(-100.00)) // Invalid negative amount
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments")
                    .header("X-Merchant-ID", merchantId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when merchant ID mismatch")
        void shouldReturn400WhenMerchantIdMismatch() throws Exception {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId("different_merchant")
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments")
                    .header("X-Merchant-ID", merchantId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle missing headers")
        void shouldHandleMissingHeaders() throws Exception {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .build();

            // Act & Assert - Missing X-Merchant-ID header
            mockMvc.perform(post("/api/v1/payments")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            // Act & Assert - Missing Idempotency-Key header
            mockMvc.perform(post("/api/v1/payments")
                    .header("X-Merchant-ID", merchantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle idempotency correctly")
        void shouldHandleIdempotencyCorrectly() throws Exception {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            // Act - First request
            String firstResponse = mockMvc.perform(post("/api/v1/payments")
                    .header("X-Merchant-ID", merchantId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Act - Second request with same idempotency key
            String secondResponse = mockMvc.perform(post("/api/v1/payments")
                    .header("X-Merchant-ID", merchantId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Assert - Both responses should be identical
            assertEquals(firstResponse, secondResponse);
        }

        @Test
        @DisplayName("Should handle malformed JSON")
        void shouldHandleMalformedJson() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/payments")
                    .header("X-Merchant-ID", merchantId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Get Payment Tests")
    class GetPaymentTests {

        @Test
        @DisplayName("Should get payment by ID successfully")
        void shouldGetPaymentByIdSuccessfully() throws Exception {
            // Arrange
            Payment savedPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(150.00))
                            .status(Payment.PaymentStatus.AUTHORIZED)
                            .build());

            // Act & Assert
            mockMvc.perform(get("/api/v1/payments/{paymentId}", savedPayment.getId()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(savedPayment.getId().toString()))
                    .andExpect(jsonPath("$.merchantId").value(merchantId))
                    .andExpect(jsonPath("$.amount").value(150.00))
                    .andExpect(jsonPath("$.status").value("AUTHORIZED"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent payment")
        void shouldReturn404ForNonExistentPayment() throws Exception {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(get("/api/v1/payments/{paymentId}", nonExistentId))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should handle invalid UUID format")
        void shouldHandleInvalidUuidFormat() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/payments/{paymentId}", "invalid-uuid"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Capture Payment Tests")
    class CapturePaymentTests {

        @Test
        @DisplayName("Should capture payment successfully")
        void shouldCapturePaymentSuccessfully() throws Exception {
            // Arrange
            Payment authorizedPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(200.00))
                            .status(Payment.PaymentStatus.AUTHORIZED)
                            .build());

            CaptureRequest captureRequest = TestDataBuilder.captureRequest()
                    .amount(BigDecimal.valueOf(200.00))
                    .description("Full capture")
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", authorizedPayment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(captureRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.capturedAmount").value(200.00));
        }

        @Test
        @DisplayName("Should handle partial capture")
        void shouldHandlePartialCapture() throws Exception {
            // Arrange
            Payment authorizedPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(200.00))
                            .status(Payment.PaymentStatus.AUTHORIZED)
                            .build());

            CaptureRequest partialCaptureRequest = TestDataBuilder.captureRequest()
                    .amount(BigDecimal.valueOf(100.00))
                    .description("Partial capture")
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", authorizedPayment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(partialCaptureRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.capturedAmount").value(100.00));
        }

        @Test
        @DisplayName("Should reject capture for non-authorized payment")
        void shouldRejectCaptureForNonAuthorizedPayment() throws Exception {
            // Arrange
            Payment pendingPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.PENDING)
                            .build());

            CaptureRequest captureRequest = TestDataBuilder.captureRequest()
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", pendingPayment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(captureRequest)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject capture amount greater than authorized")
        void shouldRejectCaptureAmountGreaterThanAuthorized() throws Exception {
            // Arrange
            Payment authorizedPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(100.00))
                            .status(Payment.PaymentStatus.AUTHORIZED)
                            .build());

            CaptureRequest excessiveCaptureRequest = TestDataBuilder.captureRequest()
                    .amount(BigDecimal.valueOf(150.00))
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", authorizedPayment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(excessiveCaptureRequest)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Refund Payment Tests")
    class RefundPaymentTests {

        @Test
        @DisplayName("Should refund payment successfully")
        void shouldRefundPaymentSuccessfully() throws Exception {
            // Arrange
            Payment capturedPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(200.00))
                            .status(Payment.PaymentStatus.CAPTURED)
                            .capturedAmount(BigDecimal.valueOf(200.00))
                            .build());

            RefundRequest refundRequest = TestDataBuilder.refundRequest()
                    .amount(BigDecimal.valueOf(50.00))
                    .reason("Customer request")
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", capturedPayment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refundRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.refundedAmount").value(50.00));
        }

        @Test
        @DisplayName("Should handle full refund")
        void shouldHandleFullRefund() throws Exception {
            // Arrange
            Payment capturedPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(200.00))
                            .status(Payment.PaymentStatus.CAPTURED)
                            .capturedAmount(BigDecimal.valueOf(200.00))
                            .build());

            RefundRequest fullRefundRequest = TestDataBuilder.refundRequest()
                    .amount(BigDecimal.valueOf(200.00))
                    .reason("Full refund")
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", capturedPayment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(fullRefundRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REFUNDED"))
                    .andExpect(jsonPath("$.refundedAmount").value(200.00));
        }

        @Test
        @DisplayName("Should reject refund for non-captured payment")
        void shouldRejectRefundForNonCapturedPayment() throws Exception {
            // Arrange
            Payment authorizedPayment = paymentRepository.save(
                    TestDataBuilder.payment()
                            .merchantId(merchantId)
                            .status(Payment.PaymentStatus.AUTHORIZED)
                            .build());

            RefundRequest refundRequest = TestDataBuilder.refundRequest()
                    .amount(BigDecimal.valueOf(50.00))
                    .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", authorizedPayment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refundRequest)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("List Payments Tests")
    class ListPaymentsTests {

        @Test
        @DisplayName("Should list payments for merchant")
        void shouldListPaymentsForMerchant() throws Exception {
            // Arrange
            paymentRepository.save(TestDataBuilder.payment().merchantId(merchantId).build());
            paymentRepository.save(TestDataBuilder.payment().merchantId(merchantId).build());
            paymentRepository.save(TestDataBuilder.payment().merchantId("other_merchant").build());

            // Act & Assert
            mockMvc.perform(get("/api/v1/payments")
                    .param("merchantId", merchantId)
                    .param("page", "0")
                    .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isNotEmpty())
                    .andExpect(jsonPath("$.content[*].merchantId").value(everyItem(equalTo(merchantId))))
                    .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
        }

        @Test
        @DisplayName("Should handle pagination")
        void shouldHandlePagination() throws Exception {
            // Arrange
            for (int i = 0; i < 15; i++) {
                paymentRepository.save(TestDataBuilder.payment().merchantId(merchantId).build());
            }

            // Act & Assert - First page
            mockMvc.perform(get("/api/v1/payments")
                    .param("merchantId", merchantId)
                    .param("page", "0")
                    .param("size", "5"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content", hasSize(5)))
                    .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(15)))
                    .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(3)));

            // Act & Assert - Second page
            mockMvc.perform(get("/api/v1/payments")
                    .param("merchantId", merchantId)
                    .param("page", "1")
                    .param("size", "5"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content", hasSize(5)));
        }

        @Test
        @DisplayName("Should return empty page when no payments found")
        void shouldReturnEmptyPageWhenNoPaymentsFound() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/payments")
                    .param("merchantId", "non_existent_merchant")
                    .param("page", "0")
                    .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }
}
