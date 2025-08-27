package com.hnh.example.transaction_example.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of payment refund from payment processor
 */
@Data
@Builder
public class PaymentRefundResult {
    
    private boolean success;
    private String processorRefundId;
    private String failureReason;
    private String failureCode;
    private BigDecimal refundedAmount;
    private String currency;
    private LocalDateTime refundedAt;
    private String processorName;
    
    // Additional processor-specific fields
    private String processorResponse;
    private String processorStatus;
    private String processorErrorCode;
}

