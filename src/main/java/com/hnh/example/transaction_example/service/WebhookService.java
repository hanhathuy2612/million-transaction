package com.hnh.example.transaction_example.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.hnh.example.transaction_example.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final RestTemplate restTemplate = new RestTemplate();

    // In production, these would come from merchant configuration
    private static final Map<String, String> MERCHANT_WEBHOOK_URLS = Map.of(
            "merchant_1", "https://merchant1.example.com/webhooks/payments",
            "merchant_2", "https://merchant2.example.com/webhooks/payments");

    private static final Map<String, String> MERCHANT_WEBHOOK_SECRETS = Map.of(
            "merchant_1", "webhook_secret_1",
            "merchant_2", "webhook_secret_2");

    /**
     * Send webhook asynchronously with retry logic
     */
    @Async
    public void sendWebhookAsync(String merchantId, String eventType, JsonNode eventData) {
        try {
            sendWebhook(merchantId, eventType, eventData);
        } catch (Exception e) {
            log.error("Failed to send webhook for merchant: {} event: {}", merchantId, eventType, e);
        }
    }

    /**
     * Send webhook with HMAC signature
     */
    private void sendWebhook(String merchantId, String eventType, JsonNode eventData) {
        String webhookUrl = MERCHANT_WEBHOOK_URLS.get(merchantId);
        String secret = MERCHANT_WEBHOOK_SECRETS.get(merchantId);

        if (webhookUrl == null) {
            log.debug("No webhook URL configured for merchant: {}", merchantId);
            return;
        }

        try {
            // Create webhook payload
            WebhookPayload payload = WebhookPayload.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .eventType(eventType)
                    .data(eventData)
                    .timestamp(java.time.Instant.now())
                    .build();

            String payloadJson = JsonUtil.toJson(payload);

            // Create HMAC signature
            String signature = createHmacSignature(payloadJson, secret);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Signature", "sha256=" + signature);
            headers.set("X-Webhook-Event-Type", eventType);
            headers.set("User-Agent", "PaymentService/1.0");

            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);

            // Send webhook with retry logic
            sendWithRetry(webhookUrl, request, 3);

            log.info("Webhook sent successfully to merchant: {} for event: {}", merchantId, eventType);

        } catch (Exception e) {
            log.error("Error sending webhook to merchant: {} for event: {}", merchantId, eventType, e);
            // In production, you would queue this for retry or send to DLT
        }
    }

    private void sendWithRetry(String url, HttpEntity<String> request, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                var response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    return; // Success
                }

                log.warn("Webhook returned non-2xx status: {} for URL: {}", response.getStatusCode(), url);

            } catch (Exception e) {
                lastException = e;
                log.warn("Webhook attempt {} failed for URL: {}", attempts + 1, url, e);
            }

            attempts++;

            if (attempts < maxRetries) {
                try {
                    // Exponential backoff
                    Thread.sleep(1000L * (long) Math.pow(2, attempts));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("All webhook attempts failed for URL: {}", url, lastException);
    }

    private String createHmacSignature(String payload, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class WebhookPayload {
        private String id;
        private String eventType;
        private JsonNode data;
        private java.time.Instant timestamp;
    }
}
