package com.hnh.example.transaction_example.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private Boolean published = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    // Static factory methods for different event types
    public static OutboxEvent paymentAuthorized(UUID paymentId, String payload) {
        return OutboxEvent.builder()
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .eventType(EventType.PAYMENT_AUTHORIZED)
                .payload(payload)
                .build();
    }

    public static OutboxEvent paymentCaptured(UUID paymentId, String payload) {
        return OutboxEvent.builder()
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .eventType(EventType.PAYMENT_CAPTURED)
                .payload(payload)
                .build();
    }

    public static OutboxEvent paymentRefunded(UUID paymentId, String payload) {
        return OutboxEvent.builder()
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .eventType(EventType.PAYMENT_REFUNDED)
                .payload(payload)
                .build();
    }

    public static OutboxEvent paymentFailed(UUID paymentId, String payload) {
        return OutboxEvent.builder()
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .eventType(EventType.PAYMENT_FAILED)
                .payload(payload)
                .build();
    }

    public static OutboxEvent paymentPending(UUID paymentId, String payload) {
        return OutboxEvent.builder()
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .eventType(EventType.PAYMENT_PENDING)
                .payload(payload)
                .build();
    }

    public void markAsPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }

    @Getter
    public static enum EventType {
        PAYMENT_AUTHORIZED("payment.authorized"),
        PAYMENT_CAPTURED("payment.captured"),
        PAYMENT_REFUNDED("payment.refunded"),
        PAYMENT_FAILED("payment.failed"),
        PAYMENT_PENDING("payment.pending");

        private final String eventType;

        EventType(String eventType) {
            this.eventType = eventType;
        }
    }
}
