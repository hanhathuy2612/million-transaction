package com.hnh.example.transaction_example.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

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
