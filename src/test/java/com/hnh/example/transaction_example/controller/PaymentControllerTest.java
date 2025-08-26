package com.hnh.example.transaction_example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.service.PaymentService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Controller Tests")
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private PaymentRequest paymentRequest;
    private PaymentResponse paymentResponse;
    private String merchantId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        merchantId = "merchant_1";
        idempotencyKey = "test_" + UUID.randomUUID().toString().substring(0, 8);
        
        paymentRequest = PaymentRequest.builder()
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .paymentMethodId("pm_test_123")
                .description("Test payment")
                .build();

        paymentResponse = PaymentResponse.builder()
                .id(UUID.randomUUID())
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .status("AUTHORIZED")
                .paymentMethodId("pm_test_123")
                .description("Test payment")
                .capturedAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("Should create payment successfully")
    void shouldCreatePaymentSuccessfully() {
        // Given
        when(paymentService.createPayment(anyString(), anyString(), any(PaymentRequest.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(paymentResponse));

        // When
        ResponseEntity<PaymentResponse> response = paymentController.createPayment(
                merchantId, idempotencyKey, paymentRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualTo(BigDecimal.valueOf(100.00));
        assertThat(response.getBody().getStatus()).isEqualTo("AUTHORIZED");
    }

    @Test
    @DisplayName("Should handle payment creation failure")
    void shouldHandlePaymentCreationFailure() {
        // Given
        when(paymentService.createPayment(anyString(), anyString(), any(PaymentRequest.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());

        // When
        ResponseEntity<PaymentResponse> response = paymentController.createPayment(
                merchantId, idempotencyKey, paymentRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should validate required headers")
    void shouldValidateRequiredHeaders() {
        // Given
        String invalidMerchantId = null;
        String invalidIdempotencyKey = null;

        // When
        ResponseEntity<PaymentResponse> response = paymentController.createPayment(
                invalidMerchantId, invalidIdempotencyKey, paymentRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle service exceptions")
    void shouldHandleServiceExceptions() {
        // Given
        when(paymentService.createPayment(anyString(), anyString(), any(PaymentRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // When & Then
        try {
            paymentController.createPayment(merchantId, idempotencyKey, paymentRequest);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Service error");
        }
    }
}
