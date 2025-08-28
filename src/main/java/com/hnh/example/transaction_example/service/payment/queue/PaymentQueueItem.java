package com.hnh.example.transaction_example.service.payment.queue;

import java.util.UUID;

import com.hnh.example.transaction_example.dto.PaymentRequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQueueItem {
    private UUID paymentId;
    private PaymentRequest request;
    private long enqueuedAt;
    private int retryCount;
    private String errorMessage;
}
