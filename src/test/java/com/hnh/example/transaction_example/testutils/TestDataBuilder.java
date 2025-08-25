package com.hnh.example.transaction_example.testutils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.hnh.example.transaction_example.domain.IdempotencyKey;
import com.hnh.example.transaction_example.domain.OutboxEvent;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.domain.Payment.PaymentStatus;
import com.hnh.example.transaction_example.domain.PaymentLedger;
import com.hnh.example.transaction_example.dto.CaptureRequest;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.dto.RefundRequest;

import net.datafaker.Faker;

/**
 * Builder class for creating test data objects
 */
public class TestDataBuilder {

    private static final Faker faker = new Faker();

    public static PaymentRequestBuilder paymentRequest() {
        return new PaymentRequestBuilder();
    }

    public static PaymentBuilder payment() {
        return new PaymentBuilder();
    }

    public static PaymentResponseBuilder paymentResponse() {
        return new PaymentResponseBuilder();
    }

    public static CaptureRequestBuilder captureRequest() {
        return new CaptureRequestBuilder();
    }

    public static RefundRequestBuilder refundRequest() {
        return new RefundRequestBuilder();
    }

    public static PaymentLedgerBuilder paymentLedger() {
        return new PaymentLedgerBuilder();
    }

    public static OutboxEventBuilder outboxEvent() {
        return new OutboxEventBuilder();
    }

    public static IdempotencyKeyBuilder idempotencyKey() {
        return new IdempotencyKeyBuilder();
    }

    public static class PaymentRequestBuilder {
        private String merchantId = "merchant_" + faker.random().nextInt(1, 5);
        private BigDecimal amount = BigDecimal.valueOf(faker.number().randomDouble(2, 1, 10000));
        private String currency = "USD";
        private String paymentMethodId = "pm_" + faker.random().hex(16);
        private String description = faker.commerce().productName();
        private String referenceId = "ref_" + faker.random().hex(8);

        public PaymentRequestBuilder merchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public PaymentRequestBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public PaymentRequestBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public PaymentRequestBuilder paymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
            return this;
        }

        public PaymentRequestBuilder description(String description) {
            this.description = description;
            return this;
        }

        public PaymentRequestBuilder referenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public PaymentRequest build() {
            return PaymentRequest.builder()
                    .merchantId(merchantId)
                    .amount(amount)
                    .currency(currency)
                    .paymentMethodId(paymentMethodId)
                    .description(description)
                    .referenceId(referenceId)
                    .build();
        }
    }

    public static class PaymentBuilder {
        private UUID id = UUID.randomUUID();
        private String merchantId = "merchant_" + faker.random().nextInt(1, 5);
        private BigDecimal amount = BigDecimal.valueOf(faker.number().randomDouble(2, 1, 10000));
        private String currency = "USD";
        private Payment.PaymentStatus status = Payment.PaymentStatus.PENDING;
        private String paymentMethodId = "pm_" + faker.random().hex(16);
        private String description = faker.commerce().productName();
        private String referenceId = "ref_" + faker.random().hex(8);
        private BigDecimal capturedAmount = BigDecimal.ZERO;
        private BigDecimal refundedAmount = BigDecimal.ZERO;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public PaymentBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public PaymentBuilder merchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public PaymentBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public PaymentBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public PaymentBuilder status(Payment.PaymentStatus status) {
            this.status = status;
            return this;
        }

        public PaymentBuilder authorized() {
            this.status = Payment.PaymentStatus.AUTHORIZED;
            return this;
        }

        public PaymentBuilder captured() {
            this.status = Payment.PaymentStatus.CAPTURED;
            this.capturedAmount = this.amount;
            return this;
        }

        public PaymentBuilder failed() {
            this.status = Payment.PaymentStatus.FAILED;
            return this;
        }

        public PaymentBuilder paymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
            return this;
        }

        public PaymentBuilder description(String description) {
            this.description = description;
            return this;
        }

        public PaymentBuilder referenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public PaymentBuilder capturedAmount(BigDecimal capturedAmount) {
            this.capturedAmount = capturedAmount;
            return this;
        }

        public PaymentBuilder refundedAmount(BigDecimal refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        public Payment build() {
            return Payment.builder()
                    .id(id)
                    .merchantId(merchantId)
                    .amount(amount)
                    .currency(currency)
                    .status(status)
                    .paymentMethodId(paymentMethodId)
                    .description(description)
                    .referenceId(referenceId)
                    .capturedAmount(capturedAmount)
                    .refundedAmount(refundedAmount)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();
        }
    }

    public static class PaymentResponseBuilder {
        private UUID id = UUID.randomUUID();
        private String merchantId = "merchant_" + faker.random().nextInt(1, 5);
        private BigDecimal amount = BigDecimal.valueOf(faker.number().randomDouble(2, 1, 10000));
        private String currency = "USD";
        private String status = "PENDING";
        private String paymentMethodId = "pm_" + faker.random().hex(16);
        private String description = faker.commerce().productName();
        private String referenceId = "ref_" + faker.random().hex(8);
        private BigDecimal capturedAmount = BigDecimal.ZERO;
        private BigDecimal refundedAmount = BigDecimal.ZERO;
        private LocalDateTime createdAt = LocalDateTime.now();

        public PaymentResponseBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public PaymentResponseBuilder merchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public PaymentResponseBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public PaymentResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public PaymentResponse build() {
            return PaymentResponse.builder()
                    .id(id)
                    .merchantId(merchantId)
                    .amount(amount)
                    .currency(currency)
                    .status(PaymentStatus.valueOf(status))
                    .paymentMethodId(paymentMethodId)
                    .description(description)
                    .referenceId(referenceId)
                    .capturedAmount(capturedAmount)
                    .refundedAmount(refundedAmount)
                    .createdAt(createdAt)
                    .build();
        }
    }

    public static class CaptureRequestBuilder {
        private BigDecimal amount = BigDecimal.valueOf(faker.number().randomDouble(2, 1, 10000));
        private String description = faker.lorem().sentence();
        private String referenceId = faker.random().hex(8);

        public CaptureRequestBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public CaptureRequestBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CaptureRequest build() {
            return CaptureRequest.builder()
                    .amount(amount)
                    .description(description)
                    .referenceId(referenceId)
                    .build();
        }
    }

    public static class RefundRequestBuilder {
        private BigDecimal amount = BigDecimal.valueOf(faker.number().randomDouble(2, 1, 1000));
        private String referenceId = faker.random().hex(8);
        private String reason = faker.lorem().sentence();

        public RefundRequestBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public RefundRequestBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public RefundRequest build() {
            return RefundRequest.builder()
                    .amount(amount)
                    .reason(reason)
                    .referenceId(referenceId)
                    .build();
        }
    }

    public static class PaymentLedgerBuilder {
        private Long seq = faker.number().randomNumber();
        private UUID paymentId = UUID.randomUUID();
        private PaymentLedger.EntryType entryType = PaymentLedger.EntryType.AUTHORIZATION;
        private BigDecimal amount = BigDecimal.valueOf(faker.number().randomDouble(2, 1, 10000));
        private BigDecimal balanceAfter = BigDecimal.ZERO;
        private String description = faker.lorem().sentence();
        private String referenceId = faker.random().hex(8);
        private LocalDateTime createdAt = LocalDateTime.now();

        public PaymentLedger build() {
            return PaymentLedger.builder()
                    .seq(seq)
                    .paymentId(paymentId)
                    .entryType(entryType)
                    .deltaAmount(amount)
                    .balanceAfter(balanceAfter)
                    .description(description)
                    .referenceId(referenceId)
                    .occurredAt(createdAt)
                    .build();
        }
    }

    public static class OutboxEventBuilder {
        private Long id = faker.number().randomNumber();
        private UUID aggregateId = UUID.randomUUID();
        private String aggregateType = "Payment";
        private String eventType = "payment.created";
        private String payload = "{}";
        private Boolean published = false;
        private LocalDateTime createdAt = LocalDateTime.now();

        public OutboxEvent build() {
            return OutboxEvent.builder()
                    .id(id)
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .eventType(eventType)
                    .payload(payload)
                    .published(published)
                    .createdAt(createdAt)
                    .build();
        }
    }

    public static class IdempotencyKeyBuilder {
        private Long id = faker.number().randomNumber();
        private String merchantId = "merchant_" + faker.random().nextInt(1, 5);
        private String key = "idem_" + faker.random().hex(16);
        private String requestHash = faker.random().hex(64);
        private int responseCode = 200;
        private String responseBody = "{}";
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        public IdempotencyKey build() {
            return IdempotencyKey.builder()
                    .id(id)
                    .merchantId(merchantId)
                    .key(key)
                    .requestHash(requestHash)
                    .responseCode(responseCode)
                    .responseBody(responseBody)
                    .createdAt(createdAt)
                    .expiresAt(expiresAt)
                    .build();
        }
    }

    // Utility methods for generating test data
    public static String randomMerchantId() {
        return "merchant_" + faker.random().nextInt(1, 10);
    }

    public static String randomIdempotencyKey() {
        return "idem_" + faker.random().hex(16);
    }

    public static String randomPaymentMethodId() {
        return "pm_" + faker.random().hex(16);
    }

    public static String randomReferenceId() {
        return "ref_" + faker.random().hex(8);
    }

    public static BigDecimal randomAmount() {
        return BigDecimal.valueOf(faker.number().randomDouble(2, 1, 10000));
    }

    public static BigDecimal randomAmount(int min, int max) {
        return BigDecimal.valueOf(faker.number().numberBetween(min, max));
    }
}
