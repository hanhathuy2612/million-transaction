package com.hnh.example.transaction_example.service.payment;

import static com.hnh.example.transaction_example.constant.KafkaConstant.PAYMENT_EVENTS_GROUP_ID;
import static com.hnh.example.transaction_example.constant.KafkaConstant.PAYMENT_EVENTS_GROUP_ID_READ_MODEL;
import static com.hnh.example.transaction_example.constant.KafkaConstant.PAYMENT_EVENTS_TOPIC;
import static com.hnh.example.transaction_example.constant.PaymentEvent.EVENT_TYPE_AUTHORIZED;
import static com.hnh.example.transaction_example.constant.PaymentEvent.EVENT_TYPE_CAPTURED;
import static com.hnh.example.transaction_example.constant.PaymentEvent.EVENT_TYPE_FAILED;
import static com.hnh.example.transaction_example.constant.PaymentEvent.EVENT_TYPE_PENDING;
import static com.hnh.example.transaction_example.constant.PaymentEvent.EVENT_TYPE_REFUNDED;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnh.example.transaction_example.service.AnalyticsService;
import com.hnh.example.transaction_example.service.WebhookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;
    private final AnalyticsService analyticsService;

    /**
     * Consumer for payment events to trigger webhooks and analytics
     */
    @KafkaListener(topics = PAYMENT_EVENTS_TOPIC, groupId = PAYMENT_EVENTS_GROUP_ID, containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String paymentId,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Processing payment event: {} from partition: {} offset: {}", paymentId, partition, offset);

            JsonNode eventData = objectMapper.readTree(eventPayload);
            String eventType = eventData.get("eventType").asText();
            String merchantId = eventData.get("merchantId").asText();

            // Process webhooks asynchronously
            webhookService.sendWebhookAsync(merchantId, eventType, eventData);

            // Update analytics
            analyticsService.recordPaymentEvent(eventType, eventData);

            // Manual acknowledgment after successful processing
            acknowledgment.acknowledge();

            log.debug("Successfully processed payment event: {} type: {}", paymentId, eventType);

        } catch (Exception e) {
            log.error("Error processing payment event: {} from partition: {} offset: {}",
                    paymentId, partition, offset, e);

            // Don't acknowledge - let the error handler deal with retries
            // In production, you might want to send to DLT after max retries
        }
    }

    /**
     * Consumer for payment events to update read models
     */
    @KafkaListener(topics = PAYMENT_EVENTS_TOPIC, groupId = PAYMENT_EVENTS_GROUP_ID_READ_MODEL, containerFactory = "kafkaListenerContainerFactory")
    public void projectToReadModel(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String paymentId,
            Acknowledgment acknowledgment) {

        try {
            JsonNode eventData = objectMapper.readTree(eventPayload);
            String eventType = eventData.get("eventType").asText();

            // Update search indexes, dashboards, etc.
            switch (eventType) {
                case EVENT_TYPE_PENDING:
                    updateProcessingRequested(eventData);
                    break;
                case EVENT_TYPE_AUTHORIZED:
                    updatePaymentSearchIndex(eventData);
                    break;
                case EVENT_TYPE_CAPTURED:
                    updateRevenueProjections(eventData);
                    break;
                case EVENT_TYPE_REFUNDED:
                    updateRefundMetrics(eventData);
                    break;
                case EVENT_TYPE_FAILED:
                    updateFailureMetrics(eventData);
                    break;
                default:
                    log.warn("Unknown event type: {}", eventType);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error projecting to read model for payment: {}", paymentId, e);
        }
    }

    private void updateProcessingRequested(JsonNode eventData) {
        // Update processing requested metrics
        log.debug("Updating processing requested metrics for payment: {}", eventData.get("paymentId").asText());
    }

    private void updatePaymentSearchIndex(JsonNode eventData) {
        // Update Elasticsearch or other search engine
        log.debug("Updating search index for payment: {}", eventData.get("paymentId").asText());
        // Implementation would depend on your search engine
    }

    private void updateRevenueProjections(JsonNode eventData) {
        // Update revenue dashboards, reports
        log.debug("Updating revenue projections for captured payment: {}", eventData.get("paymentId").asText());
        // Could update time-series database, analytics warehouse, etc.
    }

    private void updateRefundMetrics(JsonNode eventData) {
        // Update refund tracking metrics
        log.debug("Updating refund metrics for payment: {}", eventData.get("paymentId").asText());
    }

    private void updateFailureMetrics(JsonNode eventData) {
        // Update failure rate metrics
        log.debug("Updating failure metrics for payment: {}", eventData.get("paymentId").asText());
    }
}
