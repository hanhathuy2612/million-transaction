package com.hnh.example.transaction_example.service.outbox;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hnh.example.transaction_example.domain.OutboxEvent;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentCapturedPayload;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentEventPayload;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentFailedPayload;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentRefundedPayload;

public final class OutboxPayloadUtil {
    public static final String PAYMENT_ID = "paymentId";
    public static final String MERCHANT_ID = "merchantId";
    public static final String AMOUNT = "amount";
    public static final String CURRENCY = "currency";
    public static final String PAYMENT_METHOD_ID = "paymentMethodId";
    public static final String DESCRIPTION = "description";
    public static final String REFERENCE_ID = "referenceId";
    public static final String EVENT_TYPE = "eventType";
    public static final String TIMESTAMP = "timestamp";
    public static final String FAILURE_REASON = "failureReason";
    public static final String REFUNDED_AMOUNT = "refundedAmount";
    public static final String TOTAL_REFUNDED_AMOUNT = "totalRefundedAmount";
    public static final String CAPTURED_AMOUNT = "capturedAmount";

    public static PaymentEventPayload createPaymentEventPayload(Payment payment, OutboxEvent.EventType eventType) {
        return PaymentEventPayload.builder()
                .paymentId(payment.getId().toString())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .eventType(eventType.getEventType())
                .timestamp(LocalDateTime.now().toString())
                .referenceId(payment.getReferenceId() != null ? payment.getReferenceId() : "")
                .paymentMethodId(payment.getPaymentMethodId())
                .build();
    }

    public static PaymentCapturedPayload createCapturedPayload(Payment payment, BigDecimal capturedAmount) {
        return PaymentCapturedPayload.builder()
                .paymentId(payment.getId().toString())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .eventType(OutboxEvent.EventType.PAYMENT_CAPTURED.getEventType())
                .timestamp(LocalDateTime.now().toString())
                .referenceId(payment.getReferenceId() != null ? payment.getReferenceId() : "")
                .paymentMethodId(payment.getPaymentMethodId())
                .capturedAmount(capturedAmount)
                .totalCapturedAmount(payment.getCapturedAmount().add(capturedAmount))
                .build();
    }

    public static PaymentFailedPayload createFailedPayload(Payment payment, String failureReason) {
        return PaymentFailedPayload.builder()
                .paymentId(payment.getId().toString())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .eventType(OutboxEvent.EventType.PAYMENT_FAILED.getEventType())
                .timestamp(LocalDateTime.now().toString())
                .referenceId(payment.getReferenceId() != null ? payment.getReferenceId() : "")
                .paymentMethodId(payment.getPaymentMethodId())
                .failureReason(failureReason)
                .build();
    }

    public static PaymentRefundedPayload createRefundedPayload(Payment payment, BigDecimal refundedAmount) {
        return PaymentRefundedPayload.builder()
                .paymentId(payment.getId().toString())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .eventType(OutboxEvent.EventType.PAYMENT_REFUNDED.getEventType())
                .timestamp(LocalDateTime.now().toString())
                .referenceId(payment.getReferenceId() != null ? payment.getReferenceId() : "")
                .paymentMethodId(payment.getPaymentMethodId())
                .refundedAmount(refundedAmount)
                .totalRefundedAmount(payment.getRefundedAmount().add(refundedAmount))
                .build();
    }
}
