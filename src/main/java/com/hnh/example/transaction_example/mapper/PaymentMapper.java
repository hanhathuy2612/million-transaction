package com.hnh.example.transaction_example.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.PaymentResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    default PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethodId(payment.getPaymentMethodId())
                .description(payment.getDescription())
                .referenceId(payment.getReferenceId())
                .capturedAmount(payment.getCapturedAmount())
                .refundedAmount(payment.getRefundedAmount())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .authorizedAt(payment.getAuthorizedAt())
                .capturedAt(payment.getCapturedAt())
                .failedAt(payment.getFailedAt())
                .failureReason(payment.getFailureReason())
                .processorTransactionId(payment.getProcessorTransactionId())
                .processorName(payment.getProcessorName())
                .build();
    }

}
