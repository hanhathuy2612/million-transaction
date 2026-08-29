package com.hnh.example.transaction_example.repository;

import com.hnh.example.transaction_example.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    // Find by merchant and key for idempotency check
    Optional<IdempotencyKey> findByMerchantIdAndKey(String merchantId, String key);

    // Cleanup expired keys (housekeeping job)
    @Modifying
    @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :cutoffDate")
    int deleteExpiredKeys(@Param("cutoffDate") LocalDateTime cutoffDate);
}
