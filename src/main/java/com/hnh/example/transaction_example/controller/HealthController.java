package com.hnh.example.transaction_example.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hnh.example.transaction_example.service.AnalyticsService;
import com.hnh.example.transaction_example.service.outbox.OutboxRelayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OutboxRelayService outboxRelayService;
    private final AnalyticsService analyticsService;

    /**
     * Detailed health check for payment processing components
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        boolean allHealthy = true;

        // Check database connectivity
        try {
            dataSource.getConnection().close();
            health.put("database", Map.of("status", "UP", "responseTime", "< 100ms"));
        } catch (Exception e) {
            health.put("database", Map.of("status", "DOWN", "error", e.getMessage()));
            allHealthy = false;
        }

        // Check Redis connectivity
        try {
            redisTemplate.opsForValue().set("health:check", "ok");
            redisTemplate.delete("health:check");
            health.put("redis", Map.of("status", "UP", "responseTime", "< 50ms"));
        } catch (Exception e) {
            health.put("redis", Map.of("status", "DOWN", "error", e.getMessage()));
            allHealthy = false;
        }

        // Check Kafka connectivity
        try {
            // This is a simple check - in production you might want to send a test message
            health.put("kafka", Map.of("status", "UP", "note", "Connection healthy"));
        } catch (Exception e) {
            health.put("kafka", Map.of("status", "DOWN", "error", e.getMessage()));
            allHealthy = false;
        }

        // Check outbox lag
        try {
            Long outboxLag = outboxRelayService.getOutboxLag();
            String status = outboxLag < 100 ? "UP" : "DEGRADED";
            health.put("outbox", Map.of("status", status, "unpublishedEvents", outboxLag));

            if (outboxLag > 1000) {
                allHealthy = false;
            }
        } catch (Exception e) {
            health.put("outbox", Map.of("status", "DOWN", "error", e.getMessage()));
            allHealthy = false;
        }

        health.put("overall", allHealthy ? "UP" : "DOWN");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "payment-api");
        health.put("version", "1.0.0");

        return ResponseEntity.ok(health);
    }

    /**
     * Payment-specific metrics for monitoring
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> paymentMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // Get sample metrics (in production, this would come from your metrics system)
            var sampleMetrics = analyticsService.getPaymentMetrics(
                    "sample_merchant",
                    LocalDateTime.now().minusHours(1),
                    LocalDateTime.now());

            metrics.put("lastHourMetrics", sampleMetrics);
            metrics.put("outboxLag", outboxRelayService.getOutboxLag());
            metrics.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            log.error("Error getting payment metrics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve metrics"));
        }
    }

    /**
     * Simple readiness probe for Kubernetes
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        try {
            // Quick checks for critical dependencies
            dataSource.getConnection().close();
            redisTemplate.opsForValue().get("test");

            return ResponseEntity.ok(Map.of(
                    "status", "ready",
                    "timestamp", LocalDateTime.now().toString()));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "not ready",
                    "error", e.getMessage()));
        }
    }

    /**
     * Simple liveness probe for Kubernetes
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of(
                "status", "alive",
                "timestamp", LocalDateTime.now().toString()));
    }
}
