package com.hnh.example.transaction_example.service.payment.processor;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.dto.PaymentAuthorizationResult;
import com.hnh.example.transaction_example.dto.PaymentCaptureResult;
import com.hnh.example.transaction_example.dto.PaymentRefundResult;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaypalProcessorService implements PaymentProcessorService {
    @Override
    public PaymentAuthorizationResult authorizePayment(Payment payment, PaymentRequest request) {
        // Implement PayPal authorization logic here
        return null;
    }
    
    @Override
    public PaymentAuthorizationResult simulatePayment(Payment payment, PaymentRequest request) {
        return null;
    }
    
    @Override
    public PaymentCaptureResult capturePayment(Payment payment, BigDecimal amount) {
        return null;
    }
    
    @Override
    public PaymentRefundResult refundPayment(Payment payment, BigDecimal amount) {
        return null;
    }
}
