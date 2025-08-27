package com.hnh.example.transaction_example.service;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentAuthorizationResult;
import com.hnh.example.transaction_example.dto.PaymentCaptureResult;
import com.hnh.example.transaction_example.dto.PaymentRefundResult;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Stripe implementation of PaymentProcessorService
 */
@Slf4j
@Service
public class StripePaymentProcessorService implements PaymentProcessorService {
    
    @Value("${stripe.secret-key}")
    private String stripeSecretKey;
    
    @Value("${stripe.currency:usd}")
    private String defaultCurrency;
    
    public StripePaymentProcessorService(@Value("${stripe.secret-key}") String stripeSecretKey) {
        Stripe.apiKey = stripeSecretKey;
    }
    
    @Override
    public PaymentAuthorizationResult authorizePayment(Payment payment, PaymentRequest request) {
        try {
            log.info("Authorizing payment {} with Stripe", payment.getId());
            
            // Convert amount to cents (Stripe uses smallest currency unit)
            long amountInCents = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
            
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(payment.getCurrency().toLowerCase())
                    .setPaymentMethod(request.getPaymentMethodId())
                    .setConfirm(true)
                    .setDescription(request.getDescription())
                    .build();
            
            PaymentIntent paymentIntent = PaymentIntent.create(params);
            
            if ("succeeded".equals(paymentIntent.getStatus())) {
                log.info("Payment {} authorized successfully with Stripe", payment.getId());
                return PaymentAuthorizationResult.builder()
                        .success(true)
                        .processorTransactionId(paymentIntent.getId())
                        .authorizedAmount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .authorizedAt(LocalDateTime.now())
                        .processorName("Stripe")
                        .processorResponse(paymentIntent.getStatus())
                        .processorStatus(paymentIntent.getStatus())
                        .build();
            } else {
                log.warn("Payment {} authorization failed with Stripe. Status: {}", 
                        payment.getId(), paymentIntent.getStatus());
                return PaymentAuthorizationResult.builder()
                        .success(false)
                        .processorTransactionId(paymentIntent.getId())
                        .failureReason("Payment authorization failed: " + paymentIntent.getStatus())
                        .failureCode(paymentIntent.getLastPaymentError() != null ? 
                                paymentIntent.getLastPaymentError().getCode() : "unknown")
                        .processorName("Stripe")
                        .processorResponse(paymentIntent.getStatus())
                        .processorStatus(paymentIntent.getStatus())
                        .build();
            }
            
        } catch (StripeException e) {
            log.error("Stripe authorization error for payment {}: {}", payment.getId(), e.getMessage(), e);
            return PaymentAuthorizationResult.builder()
                    .success(false)
                    .failureReason("Stripe error: " + e.getMessage())
                    .failureCode(e.getCode())
                    .processorName("Stripe")
                    .processorErrorCode(e.getCode())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error during Stripe authorization for payment {}: {}", 
                    payment.getId(), e.getMessage(), e);
            return PaymentAuthorizationResult.builder()
                    .success(false)
                    .failureReason("Unexpected error: " + e.getMessage())
                    .failureCode("UNEXPECTED_ERROR")
                    .processorName("Stripe")
                    .build();
        }
    }
    
    @Override
    public PaymentCaptureResult capturePayment(Payment payment, BigDecimal amount) {
        try {
            log.info("Capturing payment {} with Stripe, amount: {}", payment.getId(), amount);
            
            // Convert amount to cents
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
            
            PaymentIntentCaptureParams params = PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(amountInCents)
                    .build();
            
            PaymentIntent paymentIntent = PaymentIntent.retrieve(payment.getProcessorTransactionId());
            PaymentIntent capturedIntent = paymentIntent.capture(params);
            
            if ("succeeded".equals(capturedIntent.getStatus())) {
                log.info("Payment {} captured successfully with Stripe", payment.getId());
                return PaymentCaptureResult.builder()
                        .success(true)
                        .processorCaptureId(capturedIntent.getId())
                        .capturedAmount(amount)
                        .currency(payment.getCurrency())
                        .capturedAt(LocalDateTime.now())
                        .processorName("Stripe")
                        .processorResponse(capturedIntent.getStatus())
                        .processorStatus(capturedIntent.getStatus())
                        .build();
            } else {
                log.warn("Payment {} capture failed with Stripe. Status: {}", 
                        payment.getId(), capturedIntent.getStatus());
                return PaymentCaptureResult.builder()
                        .success(false)
                        .failureReason("Payment capture failed: " + capturedIntent.getStatus())
                        .failureCode("CAPTURE_FAILED")
                        .processorName("Stripe")
                        .processorResponse(capturedIntent.getStatus())
                        .processorStatus(capturedIntent.getStatus())
                        .build();
            }
            
        } catch (StripeException e) {
            log.error("Stripe capture error for payment {}: {}", payment.getId(), e.getMessage(), e);
            return PaymentCaptureResult.builder()
                    .success(false)
                    .failureReason("Stripe error: " + e.getMessage())
                    .failureCode(e.getCode())
                    .processorName("Stripe")
                    .processorErrorCode(e.getCode())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error during Stripe capture for payment {}: {}", 
                    payment.getId(), e.getMessage(), e);
            return PaymentCaptureResult.builder()
                    .success(false)
                    .failureReason("Unexpected error: " + e.getMessage())
                    .failureCode("UNEXPECTED_ERROR")
                    .processorName("Stripe")
                    .build();
        }
    }
    
    @Override
    public PaymentRefundResult refundPayment(Payment payment, BigDecimal amount) {
        try {
            log.info("Refunding payment {} with Stripe, amount: {}", payment.getId(), amount);
            
            // Convert amount to cents
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
            
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getProcessorTransactionId())
                    .setAmount(amountInCents)
                    .setMetadata(createMetadata(payment))
                    .build();
            
            Refund refund = Refund.create(params);
            
            if ("succeeded".equals(refund.getStatus())) {
                log.info("Payment {} refunded successfully with Stripe", payment.getId());
                return PaymentRefundResult.builder()
                        .success(true)
                        .processorRefundId(refund.getId())
                        .refundedAmount(amount)
                        .currency(payment.getCurrency())
                        .refundedAt(LocalDateTime.now())
                        .processorName("Stripe")
                        .processorResponse(refund.getStatus())
                        .processorStatus(refund.getStatus())
                        .build();
            } else {
                log.warn("Payment {} refund failed with Stripe. Status: {}", 
                        payment.getId(), refund.getStatus());
                return PaymentRefundResult.builder()
                        .success(false)
                        .failureReason("Payment refund failed: " + refund.getStatus())
                        .failureCode("REFUND_FAILED")
                        .processorName("Stripe")
                        .processorResponse(refund.getStatus())
                        .processorStatus(refund.getStatus())
                        .build();
            }
            
        } catch (StripeException e) {
            log.error("Stripe refund error for payment {}: {}", payment.getId(), e.getMessage(), e);
            return PaymentRefundResult.builder()
                    .success(false)
                    .failureReason("Stripe error: " + e.getMessage())
                    .failureCode(e.getCode())
                    .processorName("Stripe")
                    .processorErrorCode(e.getCode())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error during Stripe refund for payment {}: {}", 
                    payment.getId(), e.getMessage(), e);
            return PaymentRefundResult.builder()
                    .success(false)
                    .failureReason("Unexpected error: " + e.getMessage())
                    .failureCode("UNEXPECTED_ERROR")
                    .processorName("Stripe")
                    .build();
        }
    }
    
    private Map<String, String> createMetadata(Payment payment) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("payment_id", payment.getId().toString());
        metadata.put("merchant_id", payment.getMerchantId());
        metadata.put("reference_id", payment.getReferenceId() != null ? payment.getReferenceId() : "");
        metadata.put("description", payment.getDescription() != null ? payment.getDescription() : "");
        return metadata;
    }
}
