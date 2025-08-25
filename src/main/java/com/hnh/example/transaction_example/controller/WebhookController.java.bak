package com.hnh.example.transaction_example.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Webhook configuration and management endpoints")
public class WebhookController {

    @Operation(summary = "Configure webhook endpoint", description = "Configure a webhook endpoint to receive payment notifications. "
            +
            "The webhook will be called for payment events like authorization, capture, refund, and failure.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook configured successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WebhookConfigResponse.class), examples = @ExampleObject(name = "Webhook Configuration", value = """
                    {
                      "webhookId": "webhook_12345",
                      "url": "https://merchant.example.com/webhooks/payments",
                      "events": ["payment.authorized", "payment.captured", "payment.refunded"],
                      "status": "active",
                      "createdAt": "2024-01-15T10:30:00Z"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Invalid webhook configuration", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "Invalid URL", value = """
                    {
                      "timestamp": "2024-01-15T10:30:00Z",
                      "status": 400,
                      "error": "Bad Request",
                      "message": "Invalid webhook URL format",
                      "path": "/api/v1/webhooks"
                    }
                    """)))
    })
    @PostMapping
    public ResponseEntity<WebhookConfigResponse> configureWebhook(
            @Parameter(description = "Merchant identifier", required = true) @RequestHeader("X-Merchant-ID") String merchantId,

            @Parameter(description = "Webhook configuration", required = true) @RequestBody WebhookConfigRequest request) {

        log.info("Configuring webhook for merchant: {} with URL: {}", merchantId, request.getUrl());

        // Simulate webhook configuration
        WebhookConfigResponse response = new WebhookConfigResponse();
        response.setWebhookId("webhook_" + System.currentTimeMillis());
        response.setUrl(request.getUrl());
        response.setEvents(request.getEvents());
        response.setStatus("active");
        response.setCreatedAt(java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List webhook configurations", description = "Retrieve all webhook configurations for a merchant.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook configurations retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WebhookListResponse.class)))
    })
    @GetMapping
    public ResponseEntity<WebhookListResponse> listWebhooks(
            @Parameter(description = "Merchant identifier", required = true) @RequestHeader("X-Merchant-ID") String merchantId) {

        log.info("Listing webhooks for merchant: {}", merchantId);

        // Simulate webhook list
        WebhookListResponse response = new WebhookListResponse();
        response.setWebhooks(java.util.List.of(
                new WebhookConfigResponse("webhook_1", "https://merchant.example.com/webhooks/payments",
                        java.util.List.of("payment.authorized", "payment.captured"), "active"),
                new WebhookConfigResponse("webhook_2", "https://merchant.example.com/webhooks/refunds",
                        java.util.List.of("payment.refunded"), "active")));

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete webhook configuration", description = "Delete a webhook configuration by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Webhook deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Webhook not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{webhookId}")
    public ResponseEntity<Void> deleteWebhook(
            @Parameter(description = "Merchant identifier", required = true) @RequestHeader("X-Merchant-ID") String merchantId,

            @Parameter(description = "Webhook identifier", required = true) @PathVariable String webhookId) {

        log.info("Deleting webhook: {} for merchant: {}", webhookId, merchantId);

        // Simulate webhook deletion
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Test webhook endpoint", description = "Send a test webhook to verify the endpoint is working correctly.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test webhook sent successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TestWebhookResponse.class)))
    })
    @PostMapping("/{webhookId}/test")
    public ResponseEntity<TestWebhookResponse> testWebhook(
            @Parameter(description = "Merchant identifier", required = true) @RequestHeader("X-Merchant-ID") String merchantId,

            @Parameter(description = "Webhook identifier", required = true) @PathVariable String webhookId) {

        log.info("Testing webhook: {} for merchant: {}", webhookId, merchantId);

        // Simulate webhook test
        TestWebhookResponse response = new TestWebhookResponse();
        response.setSuccess(true);
        response.setMessage("Test webhook sent successfully");
        response.setTimestamp(java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    // Schema classes for OpenAPI documentation
    @Schema(description = "Webhook configuration request")
    public static class WebhookConfigRequest {
        @Schema(description = "Webhook URL", example = "https://merchant.example.com/webhooks/payments", required = true)
        private String url;

        @Schema(description = "Events to subscribe to", example = "[\"payment.authorized\", \"payment.captured\"]", required = true)
        private java.util.List<String> events;

        @Schema(description = "Secret for webhook signature verification", example = "webhook_secret_123")
        private String secret;

        // Getters and setters
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public java.util.List<String> getEvents() {
            return events;
        }

        public void setEvents(java.util.List<String> events) {
            this.events = events;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @Schema(description = "Webhook configuration response")
    public static class WebhookConfigResponse {
        @Schema(description = "Webhook identifier", example = "webhook_12345")
        private String webhookId;

        @Schema(description = "Webhook URL", example = "https://merchant.example.com/webhooks/payments")
        private String url;

        @Schema(description = "Subscribed events", example = "[\"payment.authorized\", \"payment.captured\"]")
        private java.util.List<String> events;

        @Schema(description = "Webhook status", example = "active", allowableValues = { "active", "inactive",
                "failed" })
        private String status;

        @Schema(description = "Creation timestamp", example = "2024-01-15T10:30:00Z")
        private String createdAt;

        // Constructors
        public WebhookConfigResponse() {
        }

        public WebhookConfigResponse(String webhookId, String url, java.util.List<String> events, String status) {
            this.webhookId = webhookId;
            this.url = url;
            this.events = events;
            this.status = status;
        }

        // Getters and setters
        public String getWebhookId() {
            return webhookId;
        }

        public void setWebhookId(String webhookId) {
            this.webhookId = webhookId;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public java.util.List<String> getEvents() {
            return events;
        }

        public void setEvents(java.util.List<String> events) {
            this.events = events;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    @Schema(description = "Webhook list response")
    public static class WebhookListResponse {
        @Schema(description = "List of webhook configurations")
        private java.util.List<WebhookConfigResponse> webhooks;

        public java.util.List<WebhookConfigResponse> getWebhooks() {
            return webhooks;
        }

        public void setWebhooks(java.util.List<WebhookConfigResponse> webhooks) {
            this.webhooks = webhooks;
        }
    }

    @Schema(description = "Test webhook response")
    public static class TestWebhookResponse {
        @Schema(description = "Test success status", example = "true")
        private Boolean success;

        @Schema(description = "Test result message", example = "Test webhook sent successfully")
        private String message;

        @Schema(description = "Test timestamp", example = "2024-01-15T10:30:00Z")
        private String timestamp;

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    @Schema(description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2024-01-15T10:30:00Z")
        private String timestamp;

        @Schema(description = "HTTP status code", example = "400")
        private Integer status;

        @Schema(description = "Error type", example = "Bad Request")
        private String error;

        @Schema(description = "Error message", example = "Invalid webhook URL format")
        private String message;

        @Schema(description = "Request path", example = "/api/v1/webhooks")
        private String path;
    }
}
