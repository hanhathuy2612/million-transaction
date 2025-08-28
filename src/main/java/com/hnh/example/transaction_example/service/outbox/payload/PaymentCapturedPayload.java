package com.hnh.example.transaction_example.service.outbox.payload;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCapturedPayload extends PaymentEventPayload {

    private BigDecimal capturedAmount;
    private BigDecimal totalCapturedAmount;

}
