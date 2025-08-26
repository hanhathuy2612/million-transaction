package com.hnh.example.transaction_example.testutils;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnh.example.transaction_example.util.JsonUtil;

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
    public ObjectMapper testObjectMapper() {
        return JsonUtil.createCustomMapper();
    }

    /**
     * Provides a test-specific async task executor for controlled testing
     */
    @Bean
    @Primary
    public TaskExecutor testTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
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
    public RestTemplate testRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    /**
     * Test properties for webhook URLs (pointing to mock server)
     */
    @Bean
    @Primary
    public java.util.Map<String, String> testMerchantWebhookUrls() {
        return java.util.Map.of(
                "merchant_1", "http://localhost:8888/webhooks/payments",
                "merchant_2", "http://localhost:8888/webhooks/payments");
    }

    /**
     * Test webhook secrets
     */
    @Bean
    @Primary
    public java.util.Map<String, String> testMerchantWebhookSecrets() {
        return java.util.Map.of(
                "merchant_1", "test_webhook_secret_1",
                "merchant_2", "test_webhook_secret_2");
    }
}
