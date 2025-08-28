package com.hnh.example.transaction_example.service.outbox;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hnh.example.transaction_example.domain.OutboxEvent;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.repository.OutboxEventRepository;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentCapturedPayload;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentEventPayload;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentFailedPayload;
import com.hnh.example.transaction_example.service.outbox.payload.PaymentRefundedPayload;
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
        PaymentEventPayload payload = OutboxPayloadUtil.createPaymentEventPayload(payment,
                OutboxEvent.EventType.PAYMENT_AUTHORIZED);
        OutboxEvent event = OutboxEvent.paymentAuthorized(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.authorized: {}", payment.getId());
    }

    /**
     * Create outbox event for payment capture
     */
    @Transactional
    public void publishPaymentCaptured(Payment payment, BigDecimal capturedAmount) {
        PaymentCapturedPayload payload = OutboxPayloadUtil.createCapturedPayload(payment, capturedAmount);

        OutboxEvent event = OutboxEvent.paymentCaptured(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.captured: {}", payment.getId());
    }

    /**
     * Create outbox event for payment refund
     */
    @Transactional
    public void publishPaymentRefunded(Payment payment, BigDecimal refundedAmount) {
        PaymentRefundedPayload payload = OutboxPayloadUtil.createRefundedPayload(payment, refundedAmount);

        OutboxEvent event = OutboxEvent.paymentRefunded(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.refunded: {}", payment.getId());
    }

    /**
     * Create outbox event for payment failure
     */
    @Transactional
    public void publishPaymentFailed(Payment payment, String failureReason) {
        PaymentFailedPayload payload = OutboxPayloadUtil.createFailedPayload(payment, failureReason);

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

    /**
     * Create outbox event for payment processing request
     * Must be called within the same transaction as payment creation
     */
    @Transactional
    public void publishPendingPayment(Payment payment) {
        PaymentEventPayload payload = OutboxPayloadUtil.createPaymentEventPayload(payment,
                OutboxEvent.EventType.PAYMENT_PENDING);
        OutboxEvent event = OutboxEvent.paymentPending(payment.getId(), serializePayload(payload));
        outboxEventRepository.save(event);
        log.debug("Created outbox event for payment.created: {}", payment.getId());
    }

    private String serializePayload(PaymentEventPayload payload) {
        try {
            return JsonUtil.toJson(payload);
        } catch (Exception e) {
            log.error("Error serializing event payload", e);
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }
}
