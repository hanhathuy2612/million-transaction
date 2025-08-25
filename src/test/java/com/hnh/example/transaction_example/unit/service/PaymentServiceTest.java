package com.hnh.example.transaction_example.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.domain.PaymentLedger;
import com.hnh.example.transaction_example.dto.CaptureRequest;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.dto.RefundRequest;
import com.hnh.example.transaction_example.repository.PaymentLedgerRepository;
import com.hnh.example.transaction_example.repository.PaymentRepository;
import com.hnh.example.transaction_example.service.IdempotencyService;
import com.hnh.example.transaction_example.service.OutboxService;
import com.hnh.example.transaction_example.service.PaymentService;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Service Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentLedgerRepository paymentLedgerRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest paymentRequest;
    private Payment payment;
    private String merchantId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        merchantId = "merchant_1";
        idempotencyKey = "idem_12345";
        paymentRequest = TestDataBuilder.paymentRequest()
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();

        payment = TestDataBuilder.payment()
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .status(Payment.PaymentStatus.PENDING)
                .build();
    }

    @Nested
    @DisplayName("Create Payment Tests")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should create payment successfully when no idempotency conflict")
        void shouldCreatePaymentSuccessfully() {
            // Arrange
            when(idempotencyService.checkIdempotency(eq(merchantId), eq(idempotencyKey),
                    eq(paymentRequest)))
                    .thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
            doNothing().when(outboxService).publishPaymentAuthorized(any(Payment.class));
            doNothing().when(idempotencyService).storeIdempotentResponse(
                    eq(merchantId), eq(idempotencyKey), eq(paymentRequest), any());

            // Act
            ResponseEntity<PaymentResponse> response = paymentService.createPayment(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).satisfies(body -> {
                assertThat(body.getMerchantId()).isEqualTo(merchantId);
                assertThat(body.getAmount()).isEqualTo(BigDecimal.valueOf(100.00));
            });

            verify(paymentRepository).save(any(Payment.class));
            verify(outboxService).publishPaymentAuthorized(any(Payment.class));
            verify(idempotencyService).storeIdempotentResponse(
                    eq(merchantId), eq(idempotencyKey), eq(paymentRequest), any());
        }

        @Test
        @DisplayName("Should return cached response when idempotency key exists")
        void shouldReturnCachedResponseWhenIdempotencyKeyExists() {
            // Arrange
            PaymentResponse cachedResponse = TestDataBuilder.paymentResponse()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .build();
            ResponseEntity<Object> cachedEntity = ResponseEntity.status(HttpStatus.CREATED)
                    .body(cachedResponse);

            when(idempotencyService.checkIdempotency(eq(merchantId), eq(idempotencyKey),
                    eq(paymentRequest)))
                    .thenReturn(Optional.of(cachedEntity));

            // Act
            ResponseEntity<PaymentResponse> response = paymentService.createPayment(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).satisfies(body -> assertThat(body.getMerchantId()).isEqualTo(merchantId));

            verify(paymentRepository, never()).save(any());
            verify(outboxService, never()).publishPaymentAuthorized(any(Payment.class));
            verify(idempotencyService, never()).storeIdempotentResponse(anyString(), anyString(), any(),
                    any());
        }

        @Test
        @DisplayName("Should validate payment request and throw exception for invalid data")
        void shouldValidatePaymentRequestAndThrowException() {
            // Arrange
            PaymentRequest invalidRequest = TestDataBuilder.paymentRequest()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(-100.00)) // Invalid negative amount
                    .build();

            when(idempotencyService.checkIdempotency(eq(merchantId), eq(idempotencyKey),
                    eq(invalidRequest)))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(
                    () -> paymentService.createPayment(merchantId, idempotencyKey, invalidRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount");

            verify(paymentRepository, never()).save(any());
            verify(outboxService, never()).publishPaymentAuthorized(any(Payment.class));
        }

        @Test
        @DisplayName("Should handle authorization failure")
        void shouldHandleAuthorizationFailure() {
            // Arrange
            when(idempotencyService.checkIdempotency(eq(merchantId), eq(idempotencyKey),
                    eq(paymentRequest)))
                    .thenReturn(Optional.empty());

            Payment failedPayment = TestDataBuilder.payment()
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .status(Payment.PaymentStatus.FAILED)
                    .build();

            when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);
            doNothing().when(outboxService).publishPaymentFailed(any(Payment.class), anyString());

            // Act
            ResponseEntity<PaymentResponse> response = paymentService.createPayment(
                    merchantId, idempotencyKey, paymentRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo("FAILED");

            verify(paymentRepository).save(any(Payment.class));
            verify(outboxService).publishPaymentFailed(any(Payment.class), anyString());
        }
    }

    @Nested
    @DisplayName("Get Payment Tests")
    class GetPaymentTests {

        @Test
        @DisplayName("Should get payment by ID successfully")
        void shouldGetPaymentByIdSuccessfully() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // Act
            ResponseEntity<PaymentResponse> response = paymentService.getPayment(paymentId);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).satisfies(body -> assertThat(body.getMerchantId()).isEqualTo(merchantId));

            verify(paymentRepository).findById(paymentId);
        }

        @Test
        @DisplayName("Should return 404 when payment not found")
        void shouldReturn404WhenPaymentNotFound() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            // Act
            ResponseEntity<PaymentResponse> response = paymentService.getPayment(paymentId);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNull();

            verify(paymentRepository).findById(paymentId);
        }
    }

    @Nested
    @DisplayName("Capture Payment Tests")
    class CapturePaymentTests {

        @Test
        @DisplayName("Should capture payment successfully")
        void shouldCapturePaymentSuccessfully() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment authorizedPayment = TestDataBuilder.payment()
                    .id(paymentId)
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .status(Payment.PaymentStatus.AUTHORIZED)
                    .build();

            CaptureRequest captureRequest = TestDataBuilder.captureRequest()
                    .amount(BigDecimal.valueOf(100.00))
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(authorizedPayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(authorizedPayment);
            when(paymentLedgerRepository.save(any(PaymentLedger.class)))
                    .thenReturn(mock(PaymentLedger.class));
            doNothing().when(outboxService).publishPaymentCaptured(any(Payment.class),
                    any(BigDecimal.class));

            // Act
            ResponseEntity<PaymentResponse> response = paymentService.capturePayment(paymentId,
                    idempotencyKey,
                    captureRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo("CAPTURED");

            verify(paymentRepository).save(any(Payment.class));
            verify(paymentLedgerRepository).save(any(PaymentLedger.class));
            verify(outboxService).publishPaymentCaptured(any(Payment.class), any(BigDecimal.class));
        }

        @Test
        @DisplayName("Should reject capture for non-authorized payment")
        void shouldRejectCaptureForNonAuthorizedPayment() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment pendingPayment = TestDataBuilder.payment()
                    .id(paymentId)
                    .status(Payment.PaymentStatus.PENDING)
                    .build();

            CaptureRequest captureRequest = TestDataBuilder.captureRequest().build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(pendingPayment));

            // Act & Assert
            assertThatThrownBy(
                    () -> paymentService.capturePayment(paymentId, idempotencyKey, captureRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("authorized");

            verify(paymentRepository, never()).save(any());
            verify(paymentLedgerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject capture amount greater than authorized amount")
        void shouldRejectCaptureAmountGreaterThanAuthorized() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment authorizedPayment = TestDataBuilder.payment()
                    .id(paymentId)
                    .amount(BigDecimal.valueOf(100.00))
                    .status(Payment.PaymentStatus.AUTHORIZED)
                    .build();

            CaptureRequest captureRequest = TestDataBuilder.captureRequest()
                    .amount(BigDecimal.valueOf(150.00)) // Greater than authorized
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(authorizedPayment));

            // Act & Assert
            assertThatThrownBy(
                    () -> paymentService.capturePayment(paymentId, idempotencyKey, captureRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount");

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Refund Payment Tests")
    class RefundPaymentTests {

        @Test
        @DisplayName("Should refund payment successfully")
        void shouldRefundPaymentSuccessfully() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment capturedPayment = TestDataBuilder.payment()
                    .id(paymentId)
                    .merchantId(merchantId)
                    .amount(BigDecimal.valueOf(100.00))
                    .status(Payment.PaymentStatus.CAPTURED)
                    .capturedAmount(BigDecimal.valueOf(100.00))
                    .refundedAmount(BigDecimal.ZERO)
                    .build();

            RefundRequest refundRequest = TestDataBuilder.refundRequest()
                    .amount(BigDecimal.valueOf(50.00))
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(capturedPayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(capturedPayment);
            when(paymentLedgerRepository.save(any(PaymentLedger.class)))
                    .thenReturn(mock(PaymentLedger.class));
            doNothing().when(outboxService).publishPaymentRefunded(any(Payment.class),
                    any(BigDecimal.class));

            // Act
            ResponseEntity<PaymentResponse> response = paymentService.refundPayment(paymentId,
                    idempotencyKey, refundRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            verify(paymentRepository).save(any(Payment.class));
            verify(paymentLedgerRepository).save(any(PaymentLedger.class));
            verify(outboxService).publishPaymentRefunded(any(Payment.class), any(BigDecimal.class));
        }

        @Test
        @DisplayName("Should reject refund for non-captured payment")
        void shouldRejectRefundForNonCapturedPayment() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment authorizedPayment = TestDataBuilder.payment()
                    .id(paymentId)
                    .status(Payment.PaymentStatus.AUTHORIZED)
                    .build();

            RefundRequest refundRequest = TestDataBuilder.refundRequest().build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(authorizedPayment));

            // Act & Assert
            assertThatThrownBy(() -> paymentService.refundPayment(paymentId, idempotencyKey, refundRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("captured");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject refund amount greater than available")
        void shouldRejectRefundAmountGreaterThanAvailable() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment capturedPayment = TestDataBuilder.payment()
                    .id(paymentId)
                    .amount(BigDecimal.valueOf(100.00))
                    .status(Payment.PaymentStatus.CAPTURED)
                    .capturedAmount(BigDecimal.valueOf(100.00))
                    .refundedAmount(BigDecimal.valueOf(50.00))
                    .build();

            RefundRequest refundRequest = TestDataBuilder.refundRequest()
                    .amount(BigDecimal.valueOf(60.00)) // Only 50 available
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(capturedPayment));

            // Act & Assert
            assertThatThrownBy(() -> paymentService.refundPayment(paymentId, idempotencyKey, refundRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refund amount");

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("List Payments Tests")
    class ListPaymentsTests {

        @Test
        @DisplayName("Should list payments for merchant")
        void shouldListPaymentsForMerchant() {
            // Arrange
            List<Payment> payments = List.of(payment, TestDataBuilder.payment().build());
            Page<Payment> paymentPage = new PageImpl<>(payments);

            when(paymentRepository.findByMerchantId(eq(merchantId), any(Pageable.class)))
                    .thenReturn(paymentPage);

            // Act
            Page<PaymentResponse> response = paymentService.listPayments(merchantId, Pageable.unpaged());

            // Assert
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent().get(0).getMerchantId()).isEqualTo(merchantId);

            verify(paymentRepository).findByMerchantId(eq(merchantId), any(Pageable.class));
        }

        @Test
        @DisplayName("Should return empty page when no payments found")
        void shouldReturnEmptyPageWhenNoPaymentsFound() {
            // Arrange
            Page<Payment> emptyPage = new PageImpl<>(List.of());

            when(paymentRepository.findByMerchantId(eq(merchantId), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act
            Page<PaymentResponse> response = paymentService.listPayments(merchantId, Pageable.unpaged());

            // Assert
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();

            verify(paymentRepository).findByMerchantId(eq(merchantId), any(Pageable.class));
        }
    }
}
