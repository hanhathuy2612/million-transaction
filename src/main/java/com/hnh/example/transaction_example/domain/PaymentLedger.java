package com.hnh.example.transaction_example.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "delta_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal deltaAmount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "description")
    private String description;

    @Column(name = "reference_id")
    private String referenceId;

    public enum EntryType {
        AUTHORIZATION,
        CAPTURE,
        REFUND,
        VOID,
        FEE,
        CHARGEBACK
    }

    // Immutable builder pattern for creating ledger entries
    public static PaymentLedger createAuthorizationEntry(UUID paymentId, BigDecimal amount) {
        return PaymentLedger.builder()
                .paymentId(paymentId)
                .entryType(EntryType.AUTHORIZATION)
                .deltaAmount(amount)
                .balanceAfter(amount)
                .description("Payment authorized")
                .build();
    }

    public static PaymentLedger createCaptureEntry(UUID paymentId, BigDecimal amount, BigDecimal previousBalance) {
        return PaymentLedger.builder()
                .paymentId(paymentId)
                .entryType(EntryType.CAPTURE)
                .deltaAmount(amount)
                .balanceAfter(previousBalance.add(amount))
                .description("Payment captured")
                .build();
    }

    public static PaymentLedger createRefundEntry(UUID paymentId, BigDecimal amount, BigDecimal previousBalance) {
        return PaymentLedger.builder()
                .paymentId(paymentId)
                .entryType(EntryType.REFUND)
                .deltaAmount(amount.negate())
                .balanceAfter(previousBalance.subtract(amount))
                .description("Payment refunded")
                .build();
    }
}
