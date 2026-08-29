package com.hnh.example.transaction_example.domain;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = { "merchant_id",
        "idempotency_key" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class IdempotencyKey extends AbstractAuditingEntity {

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

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "operation_type", length = 50)
    private String operationType;

    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private Integer requestCount = 1;

    // Default constructor for creating new idempotency keys
    public static IdempotencyKey create(String merchantId, String key, String requestHash, Integer responseCode,
            String responseBody) {
        return create(merchantId, key, requestHash, responseCode, responseBody, Duration.ofHours(24));
    }

    // Constructor with custom TTL
    public static IdempotencyKey create(String merchantId, String key, String requestHash, Integer responseCode,
            String responseBody, Duration ttl) {
        return IdempotencyKey.builder().merchantId(merchantId).key(key).requestHash(requestHash)
                .responseCode(responseCode).responseBody(responseBody).expiresAt(LocalDateTime.now().plus(ttl))
                .operationType(extractOperationType(key)).requestCount(1).build();
    }

    // Constructor with operation type
    public static IdempotencyKey create(String merchantId, String key, String requestHash, Integer responseCode,
            String responseBody, Duration ttl, String operationType) {
        return IdempotencyKey.builder().merchantId(merchantId).key(key).requestHash(requestHash)
                .responseCode(responseCode).responseBody(responseBody).expiresAt(LocalDateTime.now().plus(ttl))
                .operationType(operationType).requestCount(1).build();
    }

    /**
     * Extract operation type from idempotency key Format: {merchant_id}_{operation}_{timestamp}_{random}
     */
    private static String extractOperationType(String key) {
        if (key == null || key.isEmpty()) {
            return "UNKNOWN";
        }

        String[] parts = key.split("_");
        if (parts.length >= 2) {
            return parts[1].toUpperCase();
        }
        return "UNKNOWN";
    }

    /**
     * Check if the key is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the key expires within the given duration
     */
    public boolean expiresWithin(Duration duration) {
        return LocalDateTime.now().plus(duration).isAfter(expiresAt);
    }

    /**
     * Get remaining time until expiration
     */
    public Duration getRemainingTime() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }

    /**
     * Check if request hash matches
     */
    public boolean matchesRequest(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    /**
     * Increment request count (for monitoring)
     */
    public void incrementRequestCount() {
        this.requestCount++;
    }

    /**
     * Get time until expiration in human readable format
     */
    public String getTimeUntilExpiration() {
        Duration remaining = getRemainingTime();
        if (remaining.isZero()) {
            return "EXPIRED";
        }

        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        else {
            return String.format("%dm", minutes);
        }
    }

    /**
     * Check if this is a high-value operation based on operation type
     */
    public boolean isHighValueOperation() {
        return "CREATE_PAYMENT".equals(operationType) || "CAPTURE_PAYMENT".equals(operationType);
    }

    /**
     * Get recommended TTL based on operation type
     */
    public static Duration getRecommendedTTL(String operationType) {
        if (operationType == null) {
            return Duration.ofHours(24);
        }

        return switch (operationType.toUpperCase()) {
        case "CREATE_PAYMENT" -> Duration.ofDays(7);
        case "CAPTURE_PAYMENT" -> Duration.ofDays(3);
        case "REFUND_PAYMENT" -> Duration.ofDays(30);
        case "VOID_PAYMENT" -> Duration.ofDays(1);
        default -> Duration.ofHours(24);
        };
    }
}
