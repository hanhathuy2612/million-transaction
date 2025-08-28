package com.hnh.example.transaction_example.repository;

import com.hnh.example.transaction_example.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
    // Find payments by merchant
    Page<Payment> findByMerchantId(String merchantId, Pageable pageable);
    
    // Find payments created within date range
    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId AND p.createdDate BETWEEN :startDate AND :endDate")
    List<Payment> findByMerchantIdAndCreatedDateBetween(
            @Param("merchantId") String merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
