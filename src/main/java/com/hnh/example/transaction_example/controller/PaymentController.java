package com.hnh.example.transaction_example.controller;

import com.hnh.example.transaction_example.dto.CaptureRequest;
import com.hnh.example.transaction_example.dto.PaymentRequest;
import com.hnh.example.transaction_example.dto.PaymentResponse;
import com.hnh.example.transaction_example.dto.RefundRequest;
import com.hnh.example.transaction_example.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.hnh.example.transaction_example.constant.HeaderConstant.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Payments", description = "Payment processing API")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create a new payment", description = "Creates a new payment with idempotency support. Requires an Idempotency-Key header to prevent duplicate charges.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "402", description = "Payment authorization failed"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(description = "Merchant identifier", required = true) @RequestHeader(MERCHANT_ID) String merchantId,
            @Parameter(description = "Idempotency key to prevent duplicate charges", required = true) @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Parameter(description = "Payment creation request", required = true) @Valid @RequestBody PaymentRequest request) {

        log.info("Creating payment for merchant: {} with idempotency key: {}", merchantId, idempotencyKey);

        // Validate merchant ID matches request
        if (!merchantId.equals(request.getMerchantId())) {
            throw new IllegalArgumentException("Merchant ID in header must match request body");
        }

        return paymentService.createPayment(merchantId, idempotencyKey, request);
    }

    @Operation(summary = "Get payment by ID", description = "Retrieves a payment by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "Payment unique identifier", required = true) @PathVariable UUID paymentId) {

        log.debug("Retrieving payment: {}", paymentId);
        return paymentService.getPayment(paymentId);
    }

    @Operation(summary = "List payments for merchant", description = "Retrieves a paginated list of payments for the specified merchant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> listPayments(
            @RequestHeader(MERCHANT_ID) String merchantId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.debug("Listing payments for merchant: {} with pagination: {}", merchantId, pageable);
        Page<PaymentResponse> responsePage = paymentService.listPayments(merchantId, pageable);

        MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
        headersMap.add(X_TOTAL_COUNT, String.valueOf(responsePage.getTotalElements()));
        headersMap.add(X_TOTAL_PAGES, String.valueOf(responsePage.getTotalPages()));
        headersMap.add(X_CURRENT_PAGE, String.valueOf(responsePage.getNumber()));
        headersMap.add(X_PAGE_SIZE, String.valueOf(responsePage.getSize()));

        return ResponseEntity.ok().headers(HttpHeaders.readOnlyHttpHeaders(headersMap)).body(responsePage.getContent());
    }

    @Operation(summary = "Capture an authorized payment", description = "Captures funds from an authorized payment. Supports partial captures.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment captured successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid capture request"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "409", description = "Payment cannot be captured in current state")
    })
    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<PaymentResponse> capturePayment(
            @Parameter(description = "Payment unique identifier", required = true) @PathVariable UUID paymentId,
            @Parameter(description = "Idempotency key to prevent duplicate captures") @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Parameter(description = "Capture request details", required = true) @Valid @RequestBody CaptureRequest request) {

        log.info("Capturing payment: {} with amount: {}", paymentId, request.getAmount());
        return paymentService.capturePayment(paymentId, idempotencyKey, request);
    }

    @Operation(summary = "Refund a captured payment", description = "Refunds a captured payment. Supports partial refunds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment refunded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid refund request"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "409", description = "Payment cannot be refunded in current state")
    })
    @PostMapping("/{paymentId}/refunds")
    public ResponseEntity<PaymentResponse> refundPayment(
            @Parameter(description = "Payment unique identifier", required = true) @PathVariable UUID paymentId,
            @Parameter(description = "Idempotency key to prevent duplicate refunds", required = true) @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Parameter(description = "Refund request details", required = true) @Valid @RequestBody RefundRequest request) {

        log.info("Refunding payment: {} with amount: {}", paymentId, request.getAmount());
        return paymentService.refundPayment(paymentId, idempotencyKey, request);
    }
}
