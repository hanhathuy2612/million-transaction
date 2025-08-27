package com.hnh.example.transaction_example.repository;

import com.hnh.example.transaction_example.domain.PaymentLedger;
import com.hnh.example.transaction_example.domain.PaymentLedger.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentLedgerRepository extends JpaRepository<PaymentLedger, Long> {

    // Find all ledger entries for a payment
    List<PaymentLedger> findByPaymentIdOrderByOccurredAtAsc(UUID paymentId);

    // Find the latest ledger entry for a payment (for balance calculation)
    Optional<PaymentLedger> findTopByPaymentIdOrderByOccurredAtDesc(UUID paymentId);

    // Find ledger entries by type
    List<PaymentLedger> findByPaymentIdAndEntryType(UUID paymentId, EntryType entryType);

    // Get current balance for a payment
    @Query("SELECT COALESCE(MAX(pl.balanceAfter), 0) FROM PaymentLedger pl WHERE pl.paymentId = :paymentId")
    BigDecimal getCurrentBalance(@Param("paymentId") UUID paymentId);

    // Find entries within date range
    @Query("SELECT pl FROM PaymentLedger pl WHERE pl.paymentId = :paymentId AND pl.occurredAt BETWEEN :startDate AND :endDate ORDER BY pl.occurredAt ASC")
    List<PaymentLedger> findByPaymentIdAndDateRange(
            @Param("paymentId") UUID paymentId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Calculate total amount by entry type
    @Query("SELECT COALESCE(SUM(pl.deltaAmount), 0) FROM PaymentLedger pl WHERE pl.paymentId = :paymentId AND pl.entryType = :entryType")
    BigDecimal getTotalAmountByType(@Param("paymentId") UUID paymentId, @Param("entryType") EntryType entryType);

    // Find entries for reconciliation (within date range and specific types)
    @Query("SELECT pl FROM PaymentLedger pl WHERE pl.occurredAt BETWEEN :startDate AND :endDate AND pl.entryType IN :entryTypes ORDER BY pl.occurredAt ASC")
    List<PaymentLedger> findForReconciliation(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("entryTypes") List<EntryType> entryTypes);

    // Count entries by type for metrics
    @Query("SELECT COUNT(pl) FROM PaymentLedger pl WHERE pl.entryType = :entryType AND pl.occurredAt >= :since")
    Long countByEntryTypeSince(@Param("entryType") EntryType entryType, @Param("since") LocalDateTime since);
}
