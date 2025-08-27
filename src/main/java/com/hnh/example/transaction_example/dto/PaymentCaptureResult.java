package com.hnh.example.transaction_example.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of payment capture from payment processor
 */
@Data
@Builder
public class PaymentCaptureResult {
    
    private boolean success;
    private String processorCaptureId;
    private String failureReason;
    private String failureCode;
    private BigDecimal capturedAmount;
    private String currency;
    private LocalDateTime capturedAt;
    private String processorName;
    
    // Additional processor-specific fields
    private String processorResponse;
    private String processorStatus;
    private String processorErrorCode;
}

