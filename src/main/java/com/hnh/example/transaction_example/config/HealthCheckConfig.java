package com.hnh.example.transaction_example.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hnh.example.transaction_example.service.outbox.OutboxService;
import com.hnh.example.transaction_example.service.payment.PaymentService;

@Configuration
public class HealthCheckConfig {

    @Bean
    public HealthIndicator paymentServiceHealthIndicator(PaymentService paymentService) {
        return () -> {
            try {
                // Check if the service is working
                return Health.up()
                        .withDetail("service", "PaymentService")
                        .withDetail("timestamp", System.currentTimeMillis())
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("service", "PaymentService")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    @Bean
    public HealthIndicator outboxServiceHealthIndicator(OutboxService outboxService) {
        return () -> {
            try {
                Long unpublishedCount = outboxService.getUnpublishedEventCount();
                return Health.up()
                        .withDetail("service", "OutboxService")
                        .withDetail("unpublished_events", unpublishedCount)
                        .withDetail("timestamp", System.currentTimeMillis())
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("service", "OutboxService")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}
