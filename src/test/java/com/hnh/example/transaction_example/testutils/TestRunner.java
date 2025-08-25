package com.hnh.example.transaction_example.testutils;

import com.hnh.example.transaction_example.util.JsonUtil;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration runner that provides test-specific beans and overrides
 */
@TestConfiguration
@Profile("test")
public class TestRunner {

    /**
     * Provides a test-specific object mapper using the shared JsonUtil
     */
    @Bean
    @Primary
    public com.fasterxml.jackson.databind.ObjectMapper testObjectMapper() {
        return com.hnh.example.transaction_example.util.JsonUtil.createCustomMapper();
    }

    /**
     * Provides a test-specific async task executor for controlled testing
     */
    @Bean
    @Primary
    public org.springframework.core.task.TaskExecutor testTaskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = 
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("test-async-");
        executor.initialize();
        return executor;
    }

    /**
     * Provides a test-specific RestTemplate with timeouts suitable for testing
     */
    @Bean
    @Primary
    public org.springframework.web.client.RestTemplate testRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new org.springframework.web.client.RestTemplate(factory);
    }

    /**
     * Test properties for webhook URLs (pointing to mock server)
     */
    @Bean
    @Primary
    public java.util.Map<String, String> testMerchantWebhookUrls() {
        return java.util.Map.of(
                "merchant_1", "http://localhost:8080/webhooks/payments",
                "merchant_2", "http://localhost:8080/webhooks/payments"
        );
    }

    /**
     * Test webhook secrets
     */
    @Bean
    @Primary
    public java.util.Map<String, String> testMerchantWebhookSecrets() {
        return java.util.Map.of(
                "merchant_1", "test_webhook_secret_1",
                "merchant_2", "test_webhook_secret_2"
        );
    }
}
