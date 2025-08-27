package com.hnh.example.transaction_example.service;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentAuthorizationResult;
import com.hnh.example.transaction_example.dto.PaymentCaptureResult;
import com.hnh.example.transaction_example.dto.PaymentRefundResult;

/**
 * Service interface for real payment processor integration
 */
public interface PaymentProcessorService {

    /**
     * Authorize a payment with the payment processor
     * 
     * @param payment The payment to authorize
     * @param request The original payment request
     * @return PaymentAuthorizationResult containing success status and processor
     *         details
     */
    PaymentAuthorizationResult authorizePayment(Payment payment, PaymentRequest request);

    /**
     * Simulate a payment authorization
     * 
     * @param payment The payment to authorize
     * @param request The original payment request
     * @return PaymentAuthorizationResult containing success status and processor
     *         details
     */
    PaymentAuthorizationResult simulatePayment(Payment payment, PaymentRequest request);

    /**
     * Capture an authorized payment
     * 
     * @param payment The payment to capture
     * @param amount  Amount to capture (can be partial)
     * @return PaymentCaptureResult containing success status and processor details
     */
    PaymentCaptureResult capturePayment(Payment payment, java.math.BigDecimal amount);

    /**
     * Refund a captured payment
     * 
     * @param payment The payment to refund
     * @param amount  Amount to refund
     * @return PaymentRefundResult containing success status and processor details
     */
    PaymentRefundResult refundPayment(Payment payment, java.math.BigDecimal amount);
}
