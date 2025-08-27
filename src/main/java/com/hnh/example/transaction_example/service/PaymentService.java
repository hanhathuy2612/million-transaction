package com.hnh.example.transaction_example.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.domain.PaymentLedger;
import com.hnh.example.transaction_example.dto.CaptureRequest;
import com.hnh.example.transaction_example.dto.PaymentAuthorizationResult;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.dto.RefundRequest;
import com.hnh.example.transaction_example.mapper.PaymentMapper;
import com.hnh.example.transaction_example.repository.PaymentLedgerRepository;
import com.hnh.example.transaction_example.repository.PaymentRepository;
import com.hnh.example.transaction_example.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    // Repositories
    private final PaymentRepository paymentRepository;
    private final PaymentLedgerRepository paymentLedgerRepository;

    // Services
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;

    // Processors
    private final PaymentProcessorService paymentProcessorService;

    // Mappers
    private final PaymentMapper paymentMapper;

    /**
     * Create a new payment with idempotency support
     */
    @Transactional
    public ResponseEntity<PaymentResponse> createPayment(String merchantId, String idempotencyKey,
            PaymentRequest request) {
        // Check for idempotent request
        Optional<ResponseEntity<Object>> cachedResponse = idempotencyService.checkIdempotency(merchantId,
                idempotencyKey,
                request);
        if (cachedResponse.isPresent() && cachedResponse.get().getStatusCode() == HttpStatus.OK) {
            String responseBody = JsonUtil.toJson(cachedResponse.get().getBody());
            return ResponseEntity.ok(JsonUtil.fromJson(responseBody, PaymentResponse.class));
        }

        // Create new payment
        PaymentResponse response = createNewPayment(request);

        // Store idempotent response
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotencyService.storeIdempotentResponse(merchantId, idempotencyKey, request,
                    ResponseEntity.ok(response));
        }

        return ResponseEntity.ok(response);
    }

    protected PaymentResponse createNewPayment(PaymentRequest request) {
        try {
            // Validate business rules
            validatePaymentRequest(request);

            // Create payment entity
            Payment payment = Payment.builder()
                    .merchantId(request.getMerchantId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(Payment.PaymentStatus.PENDING)
                    .paymentMethodId(request.getPaymentMethodId())
                    .description(request.getDescription())
                    .referenceId(request.getReferenceId())
                    .capturedAmount(BigDecimal.ZERO)
                    .refundedAmount(BigDecimal.ZERO)
                    .build();

            // Save payment
            payment = paymentRepository.save(payment);

            // Call real payment processor for authorization
            PaymentAuthorizationResult authResult = paymentProcessorService.simulatePayment(payment, request);

            if (authResult.isSuccess()) {
                payment.setStatus(Payment.PaymentStatus.AUTHORIZED);
                payment.setAuthorizedAt(authResult.getAuthorizedAt());
                payment.setProcessorTransactionId(authResult.getProcessorTransactionId());
                payment.setProcessorName(authResult.getProcessorName());
                payment = paymentRepository.save(payment);

                // Create ledger entry
                PaymentLedger ledgerEntry = PaymentLedger.createAuthorizationEntry(payment.getId(),
                        payment.getAmount());
                paymentLedgerRepository.save(ledgerEntry);

                // Publish event via outbox pattern
                outboxService.publishPaymentAuthorized(payment);

                log.info("Payment {} authorized successfully with {} (Transaction ID: {})",
                        payment.getId(), authResult.getProcessorName(), authResult.getProcessorTransactionId());
                return paymentMapper.toPaymentResponse(payment);
            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                payment.setFailedAt(LocalDateTime.now());
                payment.setFailureReason(authResult.getFailureReason());
                payment.setProcessorTransactionId(authResult.getProcessorTransactionId());
                payment.setProcessorName(authResult.getProcessorName());
                payment = paymentRepository.save(payment);

                // Publish failure event
                outboxService.publishPaymentFailed(payment, authResult.getFailureReason());

                log.warn("Payment {} authorization failed with {}: {} (Code: {})",
                        payment.getId(), authResult.getProcessorName(),
                        authResult.getFailureReason(), authResult.getFailureCode());
                return paymentMapper.toPaymentResponse(payment);
            }

        } catch (Exception e) {
            log.error("Error creating payment", e);
            throw new RuntimeException("Payment creation failed", e);
        }
    }

    /**
     * Capture an authorized payment
     */
    @Transactional
    public ResponseEntity<PaymentResponse> capturePayment(UUID paymentId, String idempotencyKey,
            CaptureRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        // Check for idempotent request
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            var cachedResponse = idempotencyService.checkIdempotency(payment.getMerchantId(), idempotencyKey, request);
            if (cachedResponse.isPresent()) {
                String responseBody = JsonUtil.toJson(cachedResponse.get().getBody());
                return ResponseEntity.ok(JsonUtil.fromJson(responseBody, PaymentResponse.class));
            }
        }

        // Validate capture
        if (!payment.canCapture()) {
            throw new IllegalStateException("Payment cannot be captured in current state: " + payment.getStatus());
        }

        BigDecimal captureAmount = request.getAmount();
        BigDecimal remainingAmount = payment.getAmount().subtract(payment.getCapturedAmount());

        if (captureAmount.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("Capture amount exceeds remaining authorized amount");
        }

        // Update payment
        payment.setCapturedAmount(payment.getCapturedAmount().add(captureAmount));

        if (payment.getCapturedAmount().compareTo(payment.getAmount()) == 0) {
            payment.setStatus(Payment.PaymentStatus.CAPTURED);
        }

        payment.setCapturedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        // Create ledger entry
        PaymentLedger currentBalance = paymentLedgerRepository.findTopByPaymentIdOrderByOccurredAtDesc(paymentId)
                .orElse(PaymentLedger.builder().balanceAfter(BigDecimal.ZERO).build());

        PaymentLedger ledgerEntry = PaymentLedger.createCaptureEntry(
                paymentId, captureAmount, currentBalance.getBalanceAfter());
        paymentLedgerRepository.save(ledgerEntry);

        // Publish event
        outboxService.publishPaymentCaptured(payment, captureAmount);

        ResponseEntity<PaymentResponse> response = ResponseEntity.ok(paymentMapper.toPaymentResponse(payment));

        // Store idempotent response
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotencyService.storeIdempotentResponse(payment.getMerchantId(), idempotencyKey, request, response);
        }

        log.info("Payment {} captured amount: {}", paymentId, captureAmount);
        return response;
    }

    /**
     * Refund a captured payment
     */
    @Transactional
    public ResponseEntity<PaymentResponse> refundPayment(UUID paymentId, String idempotencyKey, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        // Check for idempotent request
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            var cachedResponse = idempotencyService.checkIdempotency(payment.getMerchantId(), idempotencyKey, request);
            if (cachedResponse.isPresent()) {
                String responseBody = JsonUtil.toJson(cachedResponse.get().getBody());
                return ResponseEntity.ok(JsonUtil.fromJson(responseBody, PaymentResponse.class));
            }
        }

        // Validate refund
        if (!payment.canRefund()) {
            throw new IllegalStateException("Payment cannot be refunded in current state: " + payment.getStatus());
        }

        BigDecimal refundAmount = request.getAmount();
        BigDecimal refundableAmount = payment.getRefundableAmount();

        if (refundAmount.compareTo(refundableAmount) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds refundable amount");
        }

        // Update payment
        payment.setRefundedAmount(payment.getRefundedAmount().add(refundAmount));

        if (payment.getRefundedAmount().compareTo(payment.getCapturedAmount()) == 0) {
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(Payment.PaymentStatus.PARTIALLY_REFUNDED);
        }

        payment = paymentRepository.save(payment);

        // Create ledger entry
        PaymentLedger currentBalance = paymentLedgerRepository.findTopByPaymentIdOrderByOccurredAtDesc(paymentId)
                .orElse(PaymentLedger.builder().balanceAfter(BigDecimal.ZERO).build());

        PaymentLedger ledgerEntry = PaymentLedger.createRefundEntry(
                paymentId, refundAmount, currentBalance.getBalanceAfter());
        paymentLedgerRepository.save(ledgerEntry);

        // Publish event
        outboxService.publishPaymentRefunded(payment, refundAmount);

        ResponseEntity<PaymentResponse> response = ResponseEntity.ok(paymentMapper.toPaymentResponse(payment));

        // Store idempotent response
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotencyService.storeIdempotentResponse(payment.getMerchantId(), idempotencyKey, request, response);
        }

        log.info("Payment {} refunded amount: {}", paymentId, refundAmount);
        return response;
    }

    /**
     * Get payment by ID
     */
    @Transactional(readOnly = true)
    public ResponseEntity<PaymentResponse> getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        return ResponseEntity.ok(paymentMapper.toPaymentResponse(payment));
    }

    /**
     * List payments for a merchant
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(String merchantId, Pageable pageable) {
        Page<Payment> payments = paymentRepository.findByMerchantId(merchantId, pageable);
        return payments.map(paymentMapper::toPaymentResponse);
    }

    private void validatePaymentRequest(PaymentRequest request) {
        if (!request.isSupportedCurrency()) {
            throw new IllegalArgumentException("Unsupported currency: " + request.getCurrency());
        }

        if (!request.hasValidPrecision()) {
            throw new IllegalArgumentException("Invalid amount precision for currency: " + request.getCurrency());
        }
    }
}
