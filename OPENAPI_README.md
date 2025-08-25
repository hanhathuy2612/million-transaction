# OpenAPI Documentation for Millions Transaction API

This document explains how to use and configure OpenAPI (Swagger) documentation for the Millions Transaction API.

## 🚀 Quick Start

### 1. Access Swagger UI

Once your application is running, you can access the Swagger UI at:

- **Local Development**: http://localhost:8888/swagger-ui.html
- **API Documentation**: http://localhost:8888/api-docs

### 2. API Base URL

The API is available at:
- **Local Development**: http://localhost:8888/api/v1
- **Production**: https://api.millions-transaction.com/api/v1
- **Staging**: https://staging-api.millions-transaction.com/api/v1

## 📋 API Overview

The Millions Transaction API provides comprehensive payment processing capabilities:

### Core Features
- **Payment Processing**: Create, authorize, capture, and refund payments
- **Idempotency**: Prevent duplicate transactions with idempotency keys
- **Caching**: Redis-based caching for performance optimization
- **Event Streaming**: Kafka-based event publishing for payment lifecycle
- **Webhooks**: Real-time payment notifications to merchants
- **Analytics**: Payment analytics and reporting capabilities

## 🔐 Authentication

The API uses Bearer token authentication. Include your API key in the Authorization header:

```bash
Authorization: Bearer your-api-key-here
```

## 🔑 Idempotency

To prevent duplicate payments, include an idempotency key in your requests:

```bash
X-Idempotency-Key: unique-key-here
```

## 📊 Rate Limiting

- **1000 requests per minute** per merchant
- **10000 requests per hour** per merchant

## 🏷️ API Endpoints

### Payments
- `POST /api/v1/payments` - Create a new payment
- `GET /api/v1/payments/{paymentId}` - Get payment by ID
- `GET /api/v1/payments` - List payments for merchant
- `POST /api/v1/payments/{paymentId}/capture` - Capture an authorized payment
- `POST /api/v1/payments/{paymentId}/refunds` - Refund a captured payment

### Analytics
- `GET /api/v1/analytics/payments` - Get payment analytics
- `GET /api/v1/analytics/metrics` - Get payment metrics

### Webhooks
- `POST /api/v1/webhooks` - Configure webhook endpoint
- `GET /api/v1/webhooks` - List webhook configurations
- `DELETE /api/v1/webhooks/{webhookId}` - Delete webhook configuration
- `POST /api/v1/webhooks/{webhookId}/test` - Test webhook endpoint

### Health
- `GET /actuator/health` - Health check endpoint

## 🛠️ Configuration

### Application Properties

The OpenAPI configuration is defined in `application.yml`:

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
    doc-expansion: none
    disable-swagger-default-url: true
    display-request-duration: true
    filter: true
    deep-linking: true
    display-operation-id: false
    default-models-expand-depth: 1
    default-model-expand-depth: 1
    default-model-renderer: example
    show-common-extensions: true
    show-extensions: true
    try-it-out-enabled: true
    request-snippets-enabled: true
    response-snippets-enabled: true
    syntax-highlight:
      activated: true
      theme: agate
  packages-to-scan: com.hnh.example.transaction_example.controller
  paths-to-match: /api/**
```

### OpenAPI Configuration Class

The main OpenAPI configuration is in `OpenApiConfig.java`:

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .tags(tags())
                .components(components())
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"));
    }
}
```

## 📝 Usage Examples

### Creating a Payment

```bash
curl -X POST "http://localhost:8888/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -H "X-Merchant-ID: merchant_12345" \
  -H "Idempotency-Key: payment_12345" \
  -d '{
    "merchantId": "merchant_12345",
    "amount": 100.00,
    "currency": "USD",
    "paymentMethodId": "pm_1234567890",
    "description": "Test payment",
    "referenceId": "ref_12345"
  }'
```

### Getting Payment Analytics

```bash
curl -X GET "http://localhost:8888/api/v1/analytics/payments?fromDate=2024-01-01T00:00:00&toDate=2024-01-31T23:59:59" \
  -H "Authorization: Bearer your-api-key" \
  -H "X-Merchant-ID: merchant_12345"
```

### Configuring a Webhook

```bash
curl -X POST "http://localhost:8888/api/v1/webhooks" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -H "X-Merchant-ID: merchant_12345" \
  -d '{
    "url": "https://merchant.example.com/webhooks/payments",
    "events": ["payment.authorized", "payment.captured", "payment.refunded"],
    "secret": "webhook_secret_123"
  }'
```

## 🔧 Development

### Adding New Endpoints

When adding new endpoints, use the following OpenAPI annotations:

```java
@Operation(
    summary = "Brief description",
    description = "Detailed description of the endpoint"
)
@ApiResponses(value = {
    @ApiResponse(
        responseCode = "200",
        description = "Success response",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = YourResponseClass.class)
        )
    ),
    @ApiResponse(
        responseCode = "400",
        description = "Bad request",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class)
        )
    )
})
@PostMapping("/your-endpoint")
public ResponseEntity<YourResponseClass> yourMethod(
    @Parameter(description = "Parameter description", required = true)
    @RequestBody YourRequestClass request) {
    // Implementation
}
```

### Schema Documentation

For request/response models, use `@Schema` annotations:

```java
@Schema(description = "Payment request")
public class PaymentRequest {
    @Schema(description = "Payment amount", example = "100.00", required = true)
    private BigDecimal amount;
    
    @Schema(description = "Currency code", example = "USD", required = true)
    private String currency;
    
    // Getters and setters
}
```

## 🧪 Testing

### Using Swagger UI for Testing

1. Open http://localhost:8888/swagger-ui.html
2. Click on "Authorize" and enter your API key
3. Select an endpoint you want to test
4. Click "Try it out"
5. Fill in the required parameters
6. Click "Execute"

### Using curl for Testing

```bash
# Test health endpoint
curl http://localhost:8888/actuator/health

# Test API documentation
curl http://localhost:8888/api-docs
```

## 📚 Additional Resources

- [OpenAPI Specification](https://swagger.io/specification/)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [Swagger UI](https://swagger.io/tools/swagger-ui/)

## 🐛 Troubleshooting

### Common Issues

1. **Swagger UI not loading**: Check if the application is running on the correct port
2. **Authentication errors**: Verify your API key is correct
3. **CORS issues**: Ensure your frontend is configured to handle CORS
4. **Missing endpoints**: Check if the controller is in the correct package

### Debug Mode

Enable debug logging for OpenAPI:

```yaml
logging:
  level:
    org.springdoc: DEBUG
```

## 📄 License

This API documentation is licensed under the MIT License.
