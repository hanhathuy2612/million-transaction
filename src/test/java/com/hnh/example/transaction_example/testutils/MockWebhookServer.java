package com.hnh.example.transaction_example.testutils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple mock webhook server for testing webhook functionality
 */
public class MockWebhookServer {

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public MockWebhookServer() {
        // Simple constructor
    }

    public void start() {
        // No-op for simple mock
    }

    public void stop() {
        // No-op for simple mock
    }

    public void reset() {
        requestCount.set(0);
        successCount.set(0);
        failureCount.set(0);
    }

    public int getPort() {
        return 8080; // Mock port
    }

    public String getBaseUrl() {
        return "http://localhost:8080";
    }

    public void simulateSuccessfulWebhook() {
        requestCount.incrementAndGet();
        successCount.incrementAndGet();
    }

    public void simulateFailedWebhook() {
        requestCount.incrementAndGet();
        failureCount.incrementAndGet();
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
