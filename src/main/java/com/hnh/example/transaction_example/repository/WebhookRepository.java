package com.hnh.example.transaction_example.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hnh.example.transaction_example.domain.Webhook;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    @Query("SELECT w FROM Webhook w WHERE w.merchantId = :merchantId")
    List<Webhook> findByMerchantId(@Param("merchantId") String merchantId);
}
