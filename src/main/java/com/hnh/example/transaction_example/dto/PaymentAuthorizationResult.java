package com.hnh.example.transaction_example.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of payment authorization from payment processor
 */
@Data
@Builder
public class PaymentAuthorizationResult {
    
    private boolean success;
    private String processorTransactionId;
    private String failureReason;
    private String failureCode;
    private BigDecimal authorizedAmount;
    private String currency;
    private LocalDateTime authorizedAt;
    private String processorName;
    
    // Additional processor-specific fields
    private String processorResponse;
    private String processorStatus;
    private String processorErrorCode;
}

