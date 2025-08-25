package com.hnh.example.transaction_example.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hnh.example.transaction_example.service.AnalyticsService;

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
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "Payment analytics and reporting endpoints")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Get payment analytics", description = "Retrieves comprehensive payment analytics for a merchant within a specified date range. "
            +
            "Includes total volume, success rates, status breakdown, and average transaction amounts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnalyticsResponse.class), examples = @ExampleObject(name = "Sample Analytics", value = """
                    {
                      "totalVolume": 15000.00,
                      "capturedVolume": 14750.00,
                      "totalCount": 150,
                      "capturedCount": 145,
                      "failedCount": 5,
                      "successRate": 96.67,
                      "statusBreakdown": {
                        "AUTHORIZED": 10,
                        "CAPTURED": 145,
                        "FAILED": 5
                      },
                      "averageTransactionAmount": 100.00
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Invalid date range or merchant ID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "Invalid Date Range", value = """
                    {
                      "timestamp": "2024-01-15T10:30:00Z",
                      "status": 400,
                      "error": "Bad Request",
                      "message": "Date range cannot exceed 90 days",
                      "path": "/api/v1/analytics/payments"
                    }
                    """))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing API key"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions for this merchant"),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> getPaymentAnalytics(
            @Parameter(description = "Merchant identifier", required = true, example = "merchant_12345") @RequestHeader("X-Merchant-ID") String merchantId,

            @Parameter(description = "Start date for analytics (ISO format)", required = true, example = "2024-01-01T00:00:00") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,

            @Parameter(description = "End date for analytics (ISO format)", required = true, example = "2024-01-31T23:59:59") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {

        log.info("Retrieving analytics for merchant: {} from {} to {}", merchantId, fromDate, toDate);

        // Validate date range
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("From date cannot be after to date");
        }

        if (fromDate.plusDays(90).isBefore(toDate)) {
            throw new IllegalArgumentException("Date range cannot exceed 90 days");
        }

        Map<String, Object> analytics = analyticsService.getPaymentAnalytics(merchantId, fromDate, toDate);
        return ResponseEntity.ok(analytics);
    }

    @Operation(summary = "Get payment metrics", description = "Retrieves detailed payment metrics including conversion rates, refund rates, and failure rates.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentMetricsResponse.class)))
    })
    @GetMapping("/metrics")
    public ResponseEntity<AnalyticsService.PaymentMetrics> getPaymentMetrics(
            @Parameter(description = "Merchant identifier", required = true) @RequestHeader("X-Merchant-ID") String merchantId,

            @Parameter(description = "Start date for metrics", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @Parameter(description = "End date for metrics", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        log.info("Retrieving metrics for merchant: {} from {} to {}", merchantId, from, to);

        AnalyticsService.PaymentMetrics metrics = analyticsService.getPaymentMetrics(merchantId, from, to);
        return ResponseEntity.ok(metrics);
    }

    // Schema classes for OpenAPI documentation
    @Schema(description = "Payment analytics response")
    public static class AnalyticsResponse {
        @Schema(description = "Total payment volume", example = "15000.00")
        private Double totalVolume;

        @Schema(description = "Captured payment volume", example = "14750.00")
        private Double capturedVolume;

        @Schema(description = "Total number of payments", example = "150")
        private Long totalCount;

        @Schema(description = "Number of captured payments", example = "145")
        private Long capturedCount;

        @Schema(description = "Number of failed payments", example = "5")
        private Long failedCount;

        @Schema(description = "Success rate percentage", example = "96.67")
        private Double successRate;

        @Schema(description = "Breakdown by payment status")
        private Map<String, Long> statusBreakdown;

        @Schema(description = "Average transaction amount", example = "100.00")
        private Double averageTransactionAmount;
    }

    @Schema(description = "Payment metrics response")
    public static class PaymentMetricsResponse {
        @Schema(description = "Merchant identifier", example = "merchant_12345")
        private String merchantId;

        @Schema(description = "Period start date")
        private LocalDateTime periodStart;

        @Schema(description = "Period end date")
        private LocalDateTime periodEnd;

        @Schema(description = "Total authorizations", example = "150")
        private Long totalAuthorizations;

        @Schema(description = "Total captures", example = "145")
        private Long totalCaptures;

        @Schema(description = "Total refunds", example = "5")
        private Long totalRefunds;

        @Schema(description = "Total failures", example = "10")
        private Long totalFailures;

        @Schema(description = "Conversion rate percentage", example = "96.67")
        private Double conversionRate;

        @Schema(description = "Refund rate percentage", example = "3.45")
        private Double refundRate;

        @Schema(description = "Failure rate percentage", example = "6.25")
        private Double failureRate;
    }

    @Schema(description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2024-01-15T10:30:00Z")
        private String timestamp;

        @Schema(description = "HTTP status code", example = "400")
        private Integer status;

        @Schema(description = "Error type", example = "Bad Request")
        private String error;

        @Schema(description = "Error message", example = "Date range cannot exceed 90 days")
        private String message;

        @Schema(description = "Request path", example = "/api/v1/analytics/payments")
        private String path;
    }
}
