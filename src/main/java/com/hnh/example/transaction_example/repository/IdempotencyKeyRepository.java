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

    // Check if key exists (lighter query)
    boolean existsByMerchantIdAndKey(String merchantId, String key);

    // Cleanup expired keys (housekeeping job)
    @Modifying
    @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :cutoffDate")
    void deleteExpiredKeys(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Count active keys per merchant (for monitoring/rate limiting)
    @Query("SELECT COUNT(i) FROM IdempotencyKey i WHERE i.merchantId = :merchantId AND i.expiresAt > :now")
    Long countActiveKeysByMerchant(@Param("merchantId") String merchantId, @Param("now") LocalDateTime now);

    // Find recent keys for a merchant (debugging)
    @Query("SELECT i FROM IdempotencyKey i WHERE i.merchantId = :merchantId AND i.createdAt >= :since ORDER BY i.createdAt DESC")
    java.util.List<IdempotencyKey> findRecentKeysByMerchant(
            @Param("merchantId") String merchantId, 
            @Param("since") LocalDateTime since
    );
}
