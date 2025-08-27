package com.hnh.example.transaction_example.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hnh.example.transaction_example.domain.OutboxEvent;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.repository.OutboxEventRepository;
import com.hnh.example.transaction_example.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Create outbox event for payment authorization
     * Must be called within the same transaction as payment creation
     */
    @Transactional
    public void publishPaymentAuthorized(Payment payment) {
        Map<String, Object> payload = createPaymentEventPayload(payment, "authorized");
        OutboxEvent event = OutboxEvent.paymentAuthorized(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.authorized: {}", payment.getId());
    }

    /**
     * Create outbox event for payment capture
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPaymentCaptured(Payment payment, java.math.BigDecimal capturedAmount) {
        Map<String, Object> payload = createPaymentEventPayload(payment, "captured");
        payload.put("capturedAmount", capturedAmount);

        OutboxEvent event = OutboxEvent.paymentCaptured(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.captured: {}", payment.getId());
    }

    /**
     * Create outbox event for payment refund
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPaymentRefunded(Payment payment, java.math.BigDecimal refundedAmount) {
        Map<String, Object> payload = createPaymentEventPayload(payment, "refunded");
        payload.put("refundedAmount", refundedAmount);
        payload.put("totalRefundedAmount", payment.getRefundedAmount());

        OutboxEvent event = OutboxEvent.paymentRefunded(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.refunded: {}", payment.getId());
    }

    /**
     * Create outbox event for payment failure
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPaymentFailed(Payment payment, String failureReason) {
        Map<String, Object> payload = createPaymentEventPayload(payment, "failed");
        payload.put("failureReason", failureReason);

        OutboxEvent event = OutboxEvent.paymentFailed(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.failed: {}", payment.getId());
    }

    /**
     * Get unpublished events for relay processing
     */
    @Transactional(readOnly = true)
    public List<OutboxEvent> getUnpublishedEvents(int limit) {
        return outboxEventRepository.findUnpublishedEventsWithLimit(limit);
    }

    /**
     * Mark events as published after successful Kafka publishing
     */
    @Transactional
    public void markEventsAsPublished(List<Long> eventIds) {
        outboxEventRepository.markAsPublished(eventIds, LocalDateTime.now());
        log.debug("Marked {} events as published", eventIds.size());
    }

    /**
     * Get count of unpublished events for monitoring
     */
    @Transactional(readOnly = true)
    public Long getUnpublishedEventCount() {
        return outboxEventRepository.countUnpublishedEvents();
    }

    /**
     * Cleanup old published events (housekeeping)
     */
    @Transactional
    public void cleanupOldEvents(LocalDateTime cutoffDate) {
        outboxEventRepository.deleteOldPublishedEvents(cutoffDate);
        log.info("Cleaned up old outbox events before: {}", cutoffDate);
    }

    private Map<String, Object> createPaymentEventPayload(Payment payment, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId().toString());
        payload.put("merchantId", payment.getMerchantId());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("status", payment.getStatus().toString());
        payload.put("eventType", eventType);
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("referenceId", payment.getReferenceId() != null ? payment.getReferenceId() : "");
        payload.put("paymentMethodId", payment.getPaymentMethodId());
        return payload;
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return JsonUtil.toJson(payload);
        } catch (Exception e) {
            log.error("Error serializing event payload", e);
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }
}
