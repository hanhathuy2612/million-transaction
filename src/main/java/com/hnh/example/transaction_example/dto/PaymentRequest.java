package com.hnh.example.transaction_example.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "Merchant ID is required")
    private String merchantId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be in ISO 4217 format (e.g., USD, EUR)")
    private String currency;

    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Size(max = 100, message = "Reference ID cannot exceed 100 characters")
    private String referenceId;

    // Validation for supported currencies
    public boolean isSupportedCurrency() {
        return currency != null && 
               (currency.equals("USD") || currency.equals("EUR") || 
                currency.equals("GBP") || currency.equals("JPY"));
    }

    // Validation for amount precision based on currency
    public boolean hasValidPrecision() {
        if (currency == null || amount == null) return false;
        
        // JPY doesn't support decimal places
        if (currency.equals("JPY")) {
            return amount.scale() == 0;
        }
        
        // Other currencies support 2 decimal places
        return amount.scale() <= 2;
    }
}
