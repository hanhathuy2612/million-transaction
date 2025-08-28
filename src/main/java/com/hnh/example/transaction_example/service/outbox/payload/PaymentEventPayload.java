package com.hnh.example.transaction_example.service.outbox.payload;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventPayload implements Serializable {
    private String paymentId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethodId;
    private String description;
    private String referenceId;
    private String eventType;
    private String timestamp;
}
