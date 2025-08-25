package com.hnh.example.transaction_example.unit.dto;

import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment Request Validation Tests")
class PaymentRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Valid Request Tests")
    class ValidRequestTests {

        @Test
        @DisplayName("Should pass validation for valid payment request")
        void shouldPassValidationForValidPaymentRequest() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId("merchant_1")
                    .amount(BigDecimal.valueOf(100.00))
                    .currency("USD")
                    .paymentMethodId("pm_test_123")
                    .description("Test payment")
                    .referenceId("ref_123")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with minimum required fields")
        void shouldPassValidationWithMinimumRequiredFields() {
            // Arrange
            PaymentRequest request = PaymentRequest.builder()
                    .merchantId("merchant_1")
                    .amount(BigDecimal.valueOf(1.00))
                    .currency("USD")
                    .paymentMethodId("pm_123")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with optional fields")
        void shouldPassValidationWithOptionalFields() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId("merchant_1")
                    .amount(BigDecimal.valueOf(100.00))
                    .currency("USD")
                    .paymentMethodId("pm_test_123")
                    .description("Test payment with description")
                    .referenceId("ref_external_123")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Merchant ID Validation")
    class MerchantIdValidation {

        @Test
        @DisplayName("Should fail validation when merchant ID is null")
        void shouldFailValidationWhenMerchantIdIsNull() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId(null)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("merchantId");
            assertThat(violations.iterator().next().getMessage()).contains("must not be blank");
        }

        @Test
        @DisplayName("Should fail validation when merchant ID is empty")
        void shouldFailValidationWhenMerchantIdIsEmpty() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId("")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("merchantId");
        }

        @Test
        @DisplayName("Should fail validation when merchant ID is blank")
        void shouldFailValidationWhenMerchantIdIsBlank() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .merchantId("   ")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("merchantId");
        }
    }

    @Nested
    @DisplayName("Amount Validation")
    class AmountValidation {

        @Test
        @DisplayName("Should fail validation when amount is null")
        void shouldFailValidationWhenAmountIsNull() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .amount(null)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("amount");
            assertThat(violations.iterator().next().getMessage()).contains("must not be null");
        }

        @Test
        @DisplayName("Should fail validation when amount is zero")
        void shouldFailValidationWhenAmountIsZero() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .amount(BigDecimal.ZERO)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("amount");
            assertThat(violations.iterator().next().getMessage()).contains("must be greater than 0");
        }

        @Test
        @DisplayName("Should fail validation when amount is negative")
        void shouldFailValidationWhenAmountIsNegative() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .amount(BigDecimal.valueOf(-100.00))
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("amount");
            assertThat(violations.iterator().next().getMessage()).contains("must be greater than 0");
        }

        @Test
        @DisplayName("Should pass validation with minimum amount")
        void shouldPassValidationWithMinimumAmount() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .amount(BigDecimal.valueOf(0.01))
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with large amount")
        void shouldPassValidationWithLargeAmount() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .amount(BigDecimal.valueOf(999999.99))
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Currency Validation")
    class CurrencyValidation {

        @Test
        @DisplayName("Should fail validation when currency is null")
        void shouldFailValidationWhenCurrencyIsNull() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .currency(null)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("currency");
            assertThat(violations.iterator().next().getMessage()).contains("must not be blank");
        }

        @Test
        @DisplayName("Should fail validation when currency is empty")
        void shouldFailValidationWhenCurrencyIsEmpty() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .currency("")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("currency");
        }

        @Test
        @DisplayName("Should fail validation when currency is too long")
        void shouldFailValidationWhenCurrencyIsTooLong() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .currency("TOOLONG") // More than 3 characters
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("currency");
            assertThat(violations.iterator().next().getMessage()).contains("size must be between 3 and 3");
        }

        @Test
        @DisplayName("Should fail validation when currency is too short")
        void shouldFailValidationWhenCurrencyIsTooShort() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .currency("US") // Less than 3 characters
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("currency");
            assertThat(violations.iterator().next().getMessage()).contains("size must be between 3 and 3");
        }

        @Test
        @DisplayName("Should pass validation with valid currencies")
        void shouldPassValidationWithValidCurrencies() {
            // Arrange
            String[] validCurrencies = {"USD", "EUR", "GBP", "JPY", "CAD", "AUD"};

            for (String currency : validCurrencies) {
                PaymentRequest request = TestDataBuilder.paymentRequest()
                        .currency(currency)
                        .build();

                // Act
                Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

                // Assert
                assertThat(violations).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Payment Method ID Validation")
    class PaymentMethodIdValidation {

        @Test
        @DisplayName("Should fail validation when payment method ID is null")
        void shouldFailValidationWhenPaymentMethodIdIsNull() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .paymentMethodId(null)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("paymentMethodId");
            assertThat(violations.iterator().next().getMessage()).contains("must not be blank");
        }

        @Test
        @DisplayName("Should fail validation when payment method ID is empty")
        void shouldFailValidationWhenPaymentMethodIdIsEmpty() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .paymentMethodId("")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("paymentMethodId");
        }

        @Test
        @DisplayName("Should pass validation with valid payment method IDs")
        void shouldPassValidationWithValidPaymentMethodIds() {
            // Arrange
            String[] validPaymentMethodIds = {
                    "pm_123",
                    "card_456",
                    "paypal_789",
                    "stripe_pm_1234567890"
            };

            for (String paymentMethodId : validPaymentMethodIds) {
                PaymentRequest request = TestDataBuilder.paymentRequest()
                        .paymentMethodId(paymentMethodId)
                        .build();

                // Act
                Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

                // Assert
                assertThat(violations).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Optional Fields Validation")
    class OptionalFieldsValidation {

        @Test
        @DisplayName("Should pass validation when description is null")
        void shouldPassValidationWhenDescriptionIsNull() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .description(null)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation when reference ID is null")
        void shouldPassValidationWhenReferenceIdIsNull() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .referenceId(null)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with long description")
        void shouldPassValidationWithLongDescription() {
            // Arrange
            String longDescription = "This is a very long description that contains many characters and should still be valid as long as it doesn't exceed the maximum length limit set in the validation constraints";
            
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .description(longDescription)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with special characters in optional fields")
        void shouldPassValidationWithSpecialCharactersInOptionalFields() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .description("Payment for order #12345 - café & restaurant")
                    .referenceId("order-2023-12-01_#123")
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Multiple Field Validation Errors")
    class MultipleFieldValidationErrors {

        @Test
        @DisplayName("Should report multiple validation errors")
        void shouldReportMultipleValidationErrors() {
            // Arrange
            PaymentRequest request = PaymentRequest.builder()
                    .merchantId(null)
                    .amount(BigDecimal.valueOf(-100.00))
                    .currency("")
                    .paymentMethodId(null)
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(4);
            
            Set<String> violatedProperties = violations.stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(java.util.stream.Collectors.toSet());
            
            assertThat(violatedProperties).containsExactlyInAnyOrder(
                    "merchantId", "amount", "currency", "paymentMethodId"
            );
        }

        @Test
        @DisplayName("Should report all currency validation errors")
        void shouldReportAllCurrencyValidationErrors() {
            // Arrange
            PaymentRequest request = TestDataBuilder.paymentRequest()
                    .currency("") // Both blank and wrong size
                    .build();

            // Act
            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).hasSize(2); // Should have both "not blank" and "size" violations
            
            Set<String> violationMessages = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(java.util.stream.Collectors.toSet());
            
            assertThat(violationMessages).anyMatch(msg -> msg.contains("must not be blank"));
            assertThat(violationMessages).anyMatch(msg -> msg.contains("size must be between 3 and 3"));
        }
    }
}
