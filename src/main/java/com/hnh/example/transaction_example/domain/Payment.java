package com.hnh.example.transaction_example.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class Payment extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "payment_method_id")
    private String paymentMethodId;

    @Column(name = "description")
    private String description;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "captured_amount", precision = 19, scale = 2, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    @Builder.Default
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", precision = 19, scale = 2, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "processor_transaction_id")
    private String processorTransactionId;

    @Column(name = "processor_name")
    private String processorName;

    public enum PaymentStatus {
        PENDING,
        AUTHORIZED,
        CAPTURED,
        PARTIALLY_REFUNDED,
        REFUNDED,
        FAILED,
        CANCELLED
    }

    // Business logic methods
    public boolean canCapture() {
        return status == PaymentStatus.AUTHORIZED &&
                capturedAmount.compareTo(amount) < 0;
    }

    public boolean canRefund() {
        return (status == PaymentStatus.CAPTURED || status == PaymentStatus.PARTIALLY_REFUNDED) &&
                refundedAmount.compareTo(capturedAmount) < 0;
    }

    public BigDecimal getRefundableAmount() {
        return capturedAmount.subtract(refundedAmount);
    }
}
