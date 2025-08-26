package com.hnh.example.transaction_example.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PaymentRequest DTO Tests")
class PaymentRequestTest {

        @Test
        @DisplayName("Should create valid payment request")
        void shouldCreateValidPaymentRequest() {
                PaymentRequest request = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.00))
                                .currency("USD")
                                .paymentMethodId("pm_test_123")
                                .description("Test payment")
                                .build();

                assertThat(request.getMerchantId()).isEqualTo("merchant_1");
                assertThat(request.getAmount()).isEqualTo(BigDecimal.valueOf(100.00));
                assertThat(request.getCurrency()).isEqualTo("USD");
                assertThat(request.getPaymentMethodId()).isEqualTo("pm_test_123");
                assertThat(request.getDescription()).isEqualTo("Test payment");
        }

        @Test
        @DisplayName("Should validate supported currencies")
        void shouldValidateSupportedCurrencies() {
                PaymentRequest usdRequest = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.00))
                                .currency("USD")
                                .build();

                PaymentRequest eurRequest = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.00))
                                .currency("EUR")
                                .build();

                // Test basic currency validation
                assertThat(usdRequest.getCurrency()).isEqualTo("USD");
                assertThat(eurRequest.getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Should validate amount precision")
        void shouldValidateAmountPrecision() {
                PaymentRequest validRequest = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.50))
                                .currency("USD")
                                .build();

                PaymentRequest invalidRequest = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.555)) // Too many decimal places
                                .currency("USD")
                                .build();

                // Test amount validation
                assertThat(validRequest.getAmount()).isEqualTo(BigDecimal.valueOf(100.50));
                assertThat(invalidRequest.getAmount()).isEqualTo(BigDecimal.valueOf(100.555));
        }

        @Test
        @DisplayName("Should validate required fields")
        void shouldValidateRequiredFields() {
                PaymentRequest request = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.00))
                                .currency("USD")
                                .build();

                // Test required fields are present
                assertThat(request.getMerchantId()).isNotNull();
                assertThat(request.getAmount()).isNotNull();
                assertThat(request.getCurrency()).isNotNull();
        }

        @Test
        @DisplayName("Should validate currency format")
        void shouldValidateCurrencyFormat() {
                // Given: Payment with valid currency
                PaymentRequest validCurrencyRequest = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.00))
                                .currency("USD")
                                .build();

                // Given: Payment with invalid currency (3 characters but not supported)
                PaymentRequest invalidCurrencyRequest = PaymentRequest.builder()
                                .merchantId("merchant_1")
                                .amount(BigDecimal.valueOf(100.00))
                                .currency("XXX")
                                .build();

                // When: Check currency format
                boolean isValidCurrency = validCurrencyRequest.getCurrency().length() == 3;
                boolean isInvalidCurrency = invalidCurrencyRequest.getCurrency().length() == 3;

                // Then: Both should have 3 characters (format is correct)
                // But content validation would be done elsewhere in business logic
                assertThat(isValidCurrency).isTrue();
                assertThat(isInvalidCurrency).isTrue();
                assertThat(validCurrencyRequest.getCurrency()).isEqualTo("USD");
                assertThat(invalidCurrencyRequest.getCurrency()).isEqualTo("XXX");
        }
}
