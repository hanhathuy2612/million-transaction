package com.hnh.example.transaction_example.service.outbox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hnh.example.transaction_example.domain.OutboxEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC_NAME = "payments.events.v1";
    private static final int BATCH_SIZE = 100;

    /**
     * Scheduled job to relay outbox events to Kafka
     * Runs every 5 seconds to ensure low latency
     */
    @Async("outboxRelayExecutor")
    @Scheduled(fixedDelayString = "${outbox.relay.interval:5000}")
    public void relayEvents() {
        try {
            List<OutboxEvent> events = outboxService.getUnpublishedEvents(BATCH_SIZE);

            if (events.isEmpty()) {
                return;
            }

            log.debug("Processing {} unpublished outbox events", events.size());

            // Publish events to Kafka
            List<CompletableFuture<SendResult<String, String>>> futures = events.stream()
                    .map(this::publishToKafka)
                    .toList();

            // Wait for all publishes to complete
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            allOf.thenRun(() -> {
                // Mark events as published only after successful Kafka publish
                List<Long> eventIds = events.stream()
                        .map(OutboxEvent::getId)
                        .collect(Collectors.toList());

                outboxService.markEventsAsPublished(eventIds);
                log.info("Successfully relayed {} events to Kafka", events.size());
            }).exceptionally(throwable -> {
                log.error("Failed to relay events to Kafka", throwable);
                return null;
            });

        } catch (Exception e) {
            log.error("Error in outbox relay process", e);
        }
    }

    /**
     * Publish a single event to Kafka
     */
    private CompletableFuture<SendResult<String, String>> publishToKafka(OutboxEvent event) {
        // Use payment ID as a partition key for ordering
        String partitionKey = event.getAggregateId().toString();

        return kafkaTemplate.send(TOPIC_NAME, partitionKey, event.getPayload())
                .thenApply(result -> {
                    log.debug("Published event {} to Kafka partition {}",
                            event.getId(), result.getRecordMetadata().partition());
                    return result;
                })
                .exceptionally(throwable -> {
                    log.error("Failed to publish event {} to Kafka", event.getId(), throwable);
                    throw new RuntimeException("Kafka publish failed", throwable);
                });
    }

    /**
     * Manual trigger for event relay (useful for testing or emergency processing)
     */
    public void forceRelay() {
        log.info("Manual outbox relay triggered");
        relayEvents();
    }

    /**
     * Get current outbox lag for monitoring
     */
    public Long getOutboxLag() {
        return outboxService.getUnpublishedEventCount();
    }
}
