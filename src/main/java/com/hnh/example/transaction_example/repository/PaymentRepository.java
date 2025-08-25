package com.hnh.example.transaction_example.repository;

import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.domain.Payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // Find payments by merchant
    Page<Payment> findByMerchantId(String merchantId, Pageable pageable);

    // Find by merchant and status
    Page<Payment> findByMerchantIdAndStatus(String merchantId, PaymentStatus status, Pageable pageable);

    // Find by reference ID (for external system correlation)
    Optional<Payment> findByMerchantIdAndReferenceId(String merchantId, String referenceId);

    // Find payments created within date range
    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByMerchantIdAndCreatedAtBetween(
            @Param("merchantId") String merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Find payments that need timeout processing
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt < :timeoutThreshold")
    List<Payment> findTimeoutCandidates(
            @Param("status") PaymentStatus status,
            @Param("timeoutThreshold") LocalDateTime timeoutThreshold
    );

    // Find payments by status for monitoring
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.createdAt >= :since")
    Long countByStatusSince(@Param("status") PaymentStatus status, @Param("since") LocalDateTime since);

    // Performance: find with minimal data for lists
    @Query("SELECT p.id, p.amount, p.currency, p.status, p.createdAt FROM Payment p WHERE p.merchantId = :merchantId")
    List<Object[]> findPaymentSummaryByMerchantId(@Param("merchantId") String merchantId);
}
