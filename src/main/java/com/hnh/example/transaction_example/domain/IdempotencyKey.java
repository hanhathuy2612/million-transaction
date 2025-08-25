package com.hnh.example.transaction_example.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = { "merchant_id",
        "idempotency_key" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "idempotency_key", nullable = false)
    private String key;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_code", nullable = false)
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "JSON")
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Constructor for creating new idempotency keys
    public static IdempotencyKey create(String merchantId, String key, String requestHash,
            Integer responseCode, String responseBody) {
        return IdempotencyKey.builder()
                .merchantId(merchantId)
                .key(key)
                .requestHash(requestHash)
                .responseCode(responseCode)
                .responseBody(responseBody)
                .expiresAt(LocalDateTime.now().plusHours(24)) // 24 hour TTL
                .build();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean matchesRequest(String requestHash) {
        return this.requestHash.equals(requestHash);
    }
}
