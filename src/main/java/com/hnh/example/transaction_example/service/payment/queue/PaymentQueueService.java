package com.hnh.example.transaction_example.service.payment.queue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentQueueService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String PAYMENT_QUEUE = "payment:queue";
    private static final String PAYMENT_PROCESSING = "payment:processing";

    /**
     * Enqueue payment for processing
     */
    public void enqueuePayment(UUID paymentId, PaymentRequest request) {
        PaymentQueueItem item = PaymentQueueItem.builder()
                .paymentId(paymentId)
                .request(request)
                .enqueuedAt(System.currentTimeMillis())
                .retryCount(0)
                .build();

        redisTemplate.opsForList().leftPush(PAYMENT_QUEUE, JsonUtil.toJson(item));
        log.info("Payment {} enqueued for processing", paymentId);
    }

    /**
     * Dequeue payment for processing
     */
    public PaymentQueueItem dequeuePayment() {
        try {
            // Blocking pop with timeout
            Object item = redisTemplate.opsForList().rightPop(PAYMENT_QUEUE, 5, TimeUnit.SECONDS);
            if (item != null) {
                return JsonUtil.fromJson(item.toString(), PaymentQueueItem.class);
            }
        } catch (Exception e) {
            log.error("Error dequeuing payment", e);
        }
        return null;
    }

    /**
     * Mark payment as processing
     */
    public void markProcessing(UUID paymentId) {
        redisTemplate.opsForSet().add(PAYMENT_PROCESSING, paymentId.toString());
        redisTemplate.expire(PAYMENT_PROCESSING, 300, TimeUnit.SECONDS); // 5 min timeout
    }

    /**
     * Mark payment processing complete
     */
    public void markComplete(UUID paymentId) {
        redisTemplate.opsForSet().remove(PAYMENT_PROCESSING, paymentId.toString());
    }

    /**
     * Get queue size
     */
    public long getQueueSize() {
        Long size = redisTemplate.opsForList().size(PAYMENT_QUEUE);
        return size != null ? size : 0L;
    }
}
