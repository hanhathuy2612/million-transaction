package com.hnh.example.transaction_example.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .tags(tags())
                .components(components());
    }

    private Info apiInfo() {
        return new Info()
                .title("Millions Transaction API")
                .description("""
                        A comprehensive payment processing API that handles payment authorization,
                        capture, refund, and idempotency for millions of transactions.

                        ## Features
                        - **Payment Processing**: Create, authorize, capture, and refund payments
                        - **Idempotency**: Prevent duplicate transactions with idempotency keys
                        - **Caching**: Redis-based caching for performance optimization
                        - **Event Streaming**: Kafka-based event publishing for payment lifecycle
                        - **Webhooks**: Real-time payment notifications to merchants
                        - **Analytics**: Payment analytics and reporting capabilities

                        ## Authentication
                        This API uses Bearer token authentication. Include your API key in the Authorization header:
                        ```
                        Authorization: Bearer your-api-key-here
                        ```

                        ## Rate Limiting
                        - 1000 requests per minute per merchant
                        - 10000 requests per hour per merchant

                        ## Idempotency
                        To prevent duplicate payments, include an idempotency key in your requests:
                        ```
                        X-Idempotency-Key: unique-key-here
                        ```

                        ## Webhooks
                        Configure webhook endpoints to receive real-time payment notifications.
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("API Support")
                        .email("support@millions-transaction.com")
                        .url("https://docs.millions-transaction.com"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> servers() {
        return List.of(
                new Server()
                        .url("http://localhost:8888")
                        .description("Local Development Server"),
                new Server()
                        .url("https://api.millions-transaction.com")
                        .description("Production Server"),
                new Server()
                        .url("https://staging-api.millions-transaction.com")
                        .description("Staging Server"));
    }

    private List<Tag> tags() {
        return List.of(
                new Tag()
                        .name("Payments")
                        .description("Payment processing operations including create, capture, and refund"),
                new Tag()
                        .name("Idempotency")
                        .description("Idempotency key management to prevent duplicate transactions"),
                new Tag()
                        .name("Analytics")
                        .description("Payment analytics and reporting endpoints"),
                new Tag()
                        .name("Webhooks")
                        .description("Webhook configuration and management"),
                new Tag()
                        .name("Health")
                        .description("Health check and monitoring endpoints"));
    }

    private Components components() {
        return new Components()
                .addSecuritySchemes("Bearer Authentication", createAPIKeyScheme())
                .addSecuritySchemes("Idempotency Key", createIdempotencyKeyScheme());
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer")
                .description("Enter your API key in the format: Bearer <your-api-key>");
    }

    private SecurityScheme createIdempotencyKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-Idempotency-Key")
                .description("Unique key to prevent duplicate transactions");
    }
}
