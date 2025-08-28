package com.hnh.example.transaction_example.service.payment.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hnh.example.transaction_example.service.payment.PaymentProcessingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchPaymentProcessor {
    private final PaymentQueueService queueService;
    private final PaymentProcessingService processingService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Value("${app.payment.processing.batch-size:10}")
    private int batchSize;

    @Value("${app.payment.processing.max-retries:3}")
    private int maxRetries;

    @Value("${app.payment.processing.retry-delay:1000}")
    private final long retryDelay = 1000;

    /**
     * Process payments in batches every 1 second
     */
    @Scheduled(fixedDelay = retryDelay)
    public void processBatch() {
        long queueSize = queueService.getQueueSize();
        if (queueSize == 0)
            return;

        int batchSize = Math.min(this.batchSize, (int) queueSize);
        List<PaymentQueueItem> batch = new ArrayList<>();

        // Dequeue batch
        for (int i = 0; i < batchSize; i++) {
            PaymentQueueItem item = queueService.dequeuePayment();
            if (item != null) {
                batch.add(item);
            }
        }

        if (!batch.isEmpty()) {
            log.info("Processing batch of {} payments", batch.size());
            processBatchAsync(batch);
        }
    }

    /**
     * Process batch asynchronously
     */
    private void processBatchAsync(List<PaymentQueueItem> batch) {
        List<CompletableFuture<Void>> futures = batch.stream()
                .map(item -> CompletableFuture.runAsync(() -> {
                    try {
                        queueService.markProcessing(item.getPaymentId());
                        processingService.processPaymentAsync(item.getPaymentId());
                        queueService.markComplete(item.getPaymentId());
                    } catch (Exception e) {
                        log.error("Error processing payment {}", item.getPaymentId(), e);
                        handleFailure(item, e);
                    }
                }, executorService))
                .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(throwable -> {
                    log.error("Batch processing error", throwable);
                    return null;
                });
    }

    private void handleFailure(PaymentQueueItem item, Exception e) {
        // Retry logic or DLQ (Dead Letter Queue)
        if (item.getRetryCount() < maxRetries) {
            item.setRetryCount(item.getRetryCount() + 1);
            item.setErrorMessage(e.getMessage());
            queueService.enqueuePayment(item.getPaymentId(), item.getRequest());
        } else {
            log.error("Payment {} failed after 3 retries", item.getPaymentId());
        }
        queueService.markComplete(item.getPaymentId());
    }
}
