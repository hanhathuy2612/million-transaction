package com.hnh.example.transaction_example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.hnh.example.transaction_example.domain.Payment.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String paymentMethodId;
    private String description;
    private String referenceId;
    private BigDecimal capturedAmount;
    private BigDecimal refundedAmount;
    private LocalDateTime createdDate;
    private LocalDateTime updatedAt;
    private LocalDateTime authorizedAt;
    private LocalDateTime capturedAt;
    private LocalDateTime failedAt;
    private String failureReason;
    private String processorTransactionId;
    private String processorName;

    // Helper methods for API responses
    public BigDecimal getRefundableAmount() {
        if (capturedAmount == null || refundedAmount == null) {
            return BigDecimal.ZERO;
        }
        return capturedAmount.subtract(refundedAmount);
    }

    public boolean isRefundable() {
        return getRefundableAmount().compareTo(BigDecimal.ZERO) > 0 &&
                (status == PaymentStatus.CAPTURED || status == PaymentStatus.PARTIALLY_REFUNDED);
    }

    public boolean isCapturable() {
        return status == PaymentStatus.AUTHORIZED &&
                (capturedAmount == null || capturedAmount.compareTo(amount) < 0);
    }
}
