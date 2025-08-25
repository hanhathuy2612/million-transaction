package com.hnh.example.transaction_example.integration.repository;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.repository.PaymentRepository;
import com.hnh.example.transaction_example.testutils.TestContainerConfig;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
@DisplayName("Payment Repository Tests")
class PaymentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PaymentRepository paymentRepository;

    private String merchantId;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        merchantId = "merchant_1";
        testPayment = TestDataBuilder.payment()
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .status(Payment.PaymentStatus.AUTHORIZED)
                .build();
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save payment successfully")
        void shouldSavePaymentSuccessfully() {
            // Act
            Payment savedPayment = paymentRepository.save(testPayment);

            // Assert
            assertThat(savedPayment).isNotNull();
            assertThat(savedPayment.getId()).isNotNull();
            assertThat(savedPayment.getMerchantId()).isEqualTo(merchantId);
            assertThat(savedPayment.getAmount()).isEqualTo(BigDecimal.valueOf(100.00));
            assertThat(savedPayment.getCreatedAt()).isNotNull();
            assertThat(savedPayment.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should find payment by ID")
        void shouldFindPaymentById() {
            // Arrange
            Payment savedPayment = entityManager.persistAndFlush(testPayment);
            entityManager.clear();

            // Act
            Optional<Payment> foundPayment = paymentRepository.findById(savedPayment.getId());

            // Assert
            assertThat(foundPayment).isPresent();
            assertThat(foundPayment.get().getId()).isEqualTo(savedPayment.getId());
            assertThat(foundPayment.get().getMerchantId()).isEqualTo(merchantId);
        }

        @Test
        @DisplayName("Should return empty when payment not found")
        void shouldReturnEmptyWhenPaymentNotFound() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();

            // Act
            Optional<Payment> foundPayment = paymentRepository.findById(nonExistentId);

            // Assert
            assertThat(foundPayment).isEmpty();
        }

        @Test
        @DisplayName("Should update payment successfully")
        void shouldUpdatePaymentSuccessfully() {
            // Arrange
            Payment savedPayment = entityManager.persistAndFlush(testPayment);
            entityManager.clear();

            // Act
            savedPayment.setStatus(Payment.PaymentStatus.CAPTURED);
            savedPayment.setCapturedAmount(BigDecimal.valueOf(100.00));
            Payment updatedPayment = paymentRepository.save(savedPayment);

            // Assert
            assertThat(updatedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.CAPTURED);
            assertThat(updatedPayment.getCapturedAmount()).isEqualTo(BigDecimal.valueOf(100.00));
            assertThat(updatedPayment.getUpdatedAt()).isAfter(updatedPayment.getCreatedAt());
        }

        @Test
        @DisplayName("Should delete payment successfully")
        void shouldDeletePaymentSuccessfully() {
            // Arrange
            Payment savedPayment = entityManager.persistAndFlush(testPayment);
            UUID paymentId = savedPayment.getId();
            entityManager.clear();

            // Act
            paymentRepository.deleteById(paymentId);

            // Assert
            Optional<Payment> deletedPayment = paymentRepository.findById(paymentId);
            assertThat(deletedPayment).isEmpty();
        }
    }

    @Nested
    @DisplayName("Query by Merchant ID")
    class QueryByMerchantId {

        @Test
        @DisplayName("Should find payments by merchant ID")
        void shouldFindPaymentsByMerchantId() {
            // Arrange
            Payment payment1 = TestDataBuilder.payment().merchantId(merchantId).build();
            Payment payment2 = TestDataBuilder.payment().merchantId(merchantId).build();
            Payment payment3 = TestDataBuilder.payment().merchantId("other_merchant").build();

            entityManager.persist(payment1);
            entityManager.persist(payment2);
            entityManager.persist(payment3);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<Payment> payments = paymentRepository.findByMerchantId(merchantId, pageable);

            // Assert
            assertThat(payments.getContent()).hasSize(2);
            assertThat(payments.getContent())
                    .allMatch(payment -> payment.getMerchantId().equals(merchantId));
        }

        @Test
        @DisplayName("Should return empty page when no payments for merchant")
        void shouldReturnEmptyPageWhenNoPaymentsForMerchant() {
            // Arrange
            String nonExistentMerchant = "non_existent_merchant";
            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<Payment> payments = paymentRepository.findByMerchantId(nonExistentMerchant, pageable);

            // Assert
            assertThat(payments.getContent()).isEmpty();
            assertThat(payments.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("Should handle pagination correctly")
        void shouldHandlePaginationCorrectly() {
            // Arrange
            for (int i = 0; i < 15; i++) {
                Payment payment = TestDataBuilder.payment()
                        .merchantId(merchantId)
                        .amount(BigDecimal.valueOf(100.00 + i))
                        .build();
                entityManager.persist(payment);
            }
            entityManager.flush();

            Pageable firstPage = PageRequest.of(0, 5);
            Pageable secondPage = PageRequest.of(1, 5);

            // Act
            Page<Payment> firstPageResults = paymentRepository.findByMerchantId(merchantId, firstPage);
            Page<Payment> secondPageResults = paymentRepository.findByMerchantId(merchantId, secondPage);

            // Assert
            assertThat(firstPageResults.getContent()).hasSize(5);
            assertThat(secondPageResults.getContent()).hasSize(5);
            assertThat(firstPageResults.getTotalElements()).isEqualTo(15);
            assertThat(firstPageResults.getTotalPages()).isEqualTo(3);
            assertThat(secondPageResults.getTotalElements()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Query by Date Range")
    class QueryByDateRange {

        @Test
        @DisplayName("Should find payments by merchant and date range")
        void shouldFindPaymentsByMerchantAndDateRange() {
            // Arrange
            LocalDateTime fromDate = LocalDateTime.now().minusDays(7);
            LocalDateTime toDate = LocalDateTime.now().plusDays(1);

            Payment recentPayment = TestDataBuilder.payment()
                    .merchantId(merchantId)
                    .build();

            Payment oldPayment = TestDataBuilder.payment()
                    .merchantId(merchantId)
                    .build();

            entityManager.persist(recentPayment);
            entityManager.persist(oldPayment);
            entityManager.flush();

            // Simulate old payment by directly updating the created date
            entityManager.getEntityManager()
                    .createQuery("UPDATE Payment p SET p.createdAt = :oldDate WHERE p.id = :id")
                    .setParameter("oldDate", LocalDateTime.now().minusDays(30))
                    .setParameter("id", oldPayment.getId())
                    .executeUpdate();

            // Act
            List<Payment> payments = paymentRepository.findByMerchantIdAndCreatedAtBetween(
                    merchantId, fromDate, toDate);

            // Assert
            assertThat(payments).hasSize(1);
            assertThat(payments.get(0).getId()).isEqualTo(recentPayment.getId());
        }

        @Test
        @DisplayName("Should return empty list when no payments in date range")
        void shouldReturnEmptyListWhenNoPaymentsInDateRange() {
            // Arrange
            LocalDateTime futureFromDate = LocalDateTime.now().plusDays(10);
            LocalDateTime futureToDate = LocalDateTime.now().plusDays(20);

            entityManager.persist(testPayment);
            entityManager.flush();

            // Act
            List<Payment> payments = paymentRepository.findByMerchantIdAndCreatedAtBetween(
                    merchantId, futureFromDate, futureToDate);

            // Assert
            assertThat(payments).isEmpty();
        }

        @Test
        @DisplayName("Should handle same day date range")
        void shouldHandleSameDayDateRange() {
            // Arrange
            LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            LocalDateTime dayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

            entityManager.persist(testPayment);
            entityManager.flush();

            // Act
            List<Payment> payments = paymentRepository.findByMerchantIdAndCreatedAtBetween(
                    merchantId, dayStart, dayEnd);

            // Assert
            assertThat(payments).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Query by Status")
    class QueryByStatus {

        @Test
        @DisplayName("Should find payments by status")
        void shouldFindPaymentsByStatus() {
            // Arrange
            Payment authorizedPayment = TestDataBuilder.payment()
                    .merchantId(merchantId)
                    .status(Payment.PaymentStatus.AUTHORIZED)
                    .build();

            Payment capturedPayment = TestDataBuilder.payment()
                    .merchantId(merchantId)
                    .status(Payment.PaymentStatus.CAPTURED)
                    .build();

            Payment failedPayment = TestDataBuilder.payment()
                    .merchantId(merchantId)
                    .status(Payment.PaymentStatus.FAILED)
                    .build();

            entityManager.persist(authorizedPayment);
            entityManager.persist(capturedPayment);
            entityManager.persist(failedPayment);
            entityManager.flush();

            // Act
            // Note: findByStatus method doesn't exist in PaymentRepository
            // Using available methods instead
            List<Payment> allPayments = paymentRepository.findAll();
            List<Payment> authorizedPayments = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.AUTHORIZED)
                    .toList();
            List<Payment> capturedPayments = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.CAPTURED)
                    .toList();

            // Assert
            assertThat(authorizedPayments).hasSize(1);
            assertThat(authorizedPayments.get(0).getStatus()).isEqualTo(Payment.PaymentStatus.AUTHORIZED);

            assertThat(capturedPayments).hasSize(1);
            assertThat(capturedPayments.get(0).getStatus()).isEqualTo(Payment.PaymentStatus.CAPTURED);
        }

        @Test
        @DisplayName("Should return empty list when no payments with status")
        void shouldReturnEmptyListWhenNoPaymentsWithStatus() {
            // Arrange
            entityManager.persist(testPayment); // AUTHORIZED status
            entityManager.flush();

            // Act
            List<Payment> allPayments = paymentRepository.findAll();
            List<Payment> refundedPayments = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.REFUNDED)
                    .toList();

            // Assert
            assertThat(refundedPayments).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validation and Constraints")
    class ValidationAndConstraints {

        @Test
        @DisplayName("Should enforce non-null constraints")
        void shouldEnforceNonNullConstraints() {
            // Arrange
            Payment invalidPayment = new Payment();
            invalidPayment.setMerchantId(null); // This should fail

            // Act & Assert
            assertThatThrownBy(() -> {
                entityManager.persistAndFlush(invalidPayment);
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should enforce currency length constraint")
        void shouldEnforceCurrencyLengthConstraint() {
            // Arrange
            Payment invalidPayment = TestDataBuilder.payment()
                    .currency("INVALID_LONG_CURRENCY") // Too long
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> {
                entityManager.persistAndFlush(invalidPayment);
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should handle decimal precision correctly")
        void shouldHandleDecimalPrecisionCorrectly() {
            // Arrange
            Payment precisePayment = TestDataBuilder.payment()
                    .amount(new BigDecimal("999999999999999.99"))
                    .capturedAmount(new BigDecimal("999999999999999.99"))
                    .refundedAmount(new BigDecimal("999999999999999.99"))
                    .build();

            // Act
            Payment savedPayment = entityManager.persistAndFlush(precisePayment);

            // Assert
            assertThat(savedPayment.getAmount()).isEqualTo(new BigDecimal("999999999999999.99"));
            assertThat(savedPayment.getCapturedAmount()).isEqualTo(new BigDecimal("999999999999999.99"));
            assertThat(savedPayment.getRefundedAmount()).isEqualTo(new BigDecimal("999999999999999.99"));
        }
    }

    @Nested
    @DisplayName("Timestamp Management")
    class TimestampManagement {

        @Test
        @DisplayName("Should auto-populate created and updated timestamps")
        void shouldAutoPopulateTimestamps() {
            // Arrange
            LocalDateTime beforeSave = LocalDateTime.now().minusSeconds(1);

            // Act
            Payment savedPayment = entityManager.persistAndFlush(testPayment);

            // Assert
            LocalDateTime afterSave = LocalDateTime.now().plusSeconds(1);
            assertThat(savedPayment.getCreatedAt()).isBetween(beforeSave, afterSave);
            assertThat(savedPayment.getUpdatedAt()).isBetween(beforeSave, afterSave);
        }

        @Test
        @DisplayName("Should update only updated timestamp on modification")
        void shouldUpdateOnlyUpdatedTimestampOnModification() throws InterruptedException {
            // Arrange
            Payment savedPayment = entityManager.persistAndFlush(testPayment);
            LocalDateTime originalCreatedAt = savedPayment.getCreatedAt();
            LocalDateTime originalUpdatedAt = savedPayment.getUpdatedAt();

            entityManager.clear();
            Thread.sleep(1000); // Ensure time difference

            // Act
            Payment foundPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
            foundPayment.setStatus(Payment.PaymentStatus.CAPTURED);
            Payment updatedPayment = entityManager.persistAndFlush(foundPayment);

            // Assert
            assertThat(updatedPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(updatedPayment.getUpdatedAt()).isAfter(originalUpdatedAt);
        }
    }
}
