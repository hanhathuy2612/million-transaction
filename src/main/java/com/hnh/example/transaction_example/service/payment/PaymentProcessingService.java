package com.hnh.example.transaction_example.service.payment;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.domain.PaymentLedger;
import com.hnh.example.transaction_example.dto.PaymentAuthorizationResult;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.mapper.PaymentMapper;
import com.hnh.example.transaction_example.repository.PaymentLedgerRepository;
import com.hnh.example.transaction_example.repository.PaymentRepository;
import com.hnh.example.transaction_example.service.outbox.OutboxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {
    // Repositories
    private final PaymentRepository paymentRepository;
    private final PaymentLedgerRepository paymentLedgerRepository;
    // Mappers
    private final PaymentMapper paymentMapper;
    // Services
    private final OutboxService outboxService;
    private final StripePaymentProcessorService stripeProcessor;

    @Transactional(timeout = 3)
    public PaymentResponse createNewPaymentPhase1(PaymentRequest request) {
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

        // Publish event via outbox pattern
        outboxService.publishPendingPayment(payment);

        log.info("Payment {} created and queued for processing", payment.getId());
        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional(timeout = 10)
    public void processPaymentAsync(UUID paymentId) {
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

            // Call real payment processor for authorization
            PaymentAuthorizationResult authResult = stripeProcessor.simulatePayment(payment,
                    buildPaymentRequestFromPayment(payment));

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
            }

        } catch (Exception e) {
            log.error("Error processing payment {}", paymentId, e);
            // Mark as failed if processing error
            try {
                Payment payment = paymentRepository.findById(paymentId).orElse(null);
                if (payment != null) {
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    payment.setFailedAt(LocalDateTime.now());
                    payment.setFailureReason("Processing error: " + e.getMessage());
                    paymentRepository.save(payment);
                }
            } catch (Exception saveEx) {
                log.error("Failed to mark payment {} as failed", paymentId, saveEx);
            }
        }
    }

    private PaymentRequest buildPaymentRequestFromPayment(Payment payment) {
        // Build PaymentRequest from Payment entity for processor
        return PaymentRequest.builder()
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethodId(payment.getPaymentMethodId())
                .description(payment.getDescription())
                .referenceId(payment.getReferenceId())
                .build();
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
