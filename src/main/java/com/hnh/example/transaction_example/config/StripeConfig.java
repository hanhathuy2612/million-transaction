package com.hnh.example.transaction_example.config;

import com.stripe.Stripe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for Stripe payment processor
 */
@Slf4j
@Configuration
public class StripeConfig {
    
    @Value("${stripe.secret-key}")
    private String stripeSecretKey;
    
    @PostConstruct
    public void initializeStripe() {
        if (stripeSecretKey != null && !stripeSecretKey.startsWith("sk_test_")) {
            log.warn("⚠️  WARNING: Using LIVE Stripe key! Make sure this is intentional.");
        } else if (stripeSecretKey != null && stripeSecretKey.startsWith("sk_test_")) {
            log.info("✅ Stripe initialized with TEST key");
        } else {
            log.warn("⚠️  Stripe secret key not configured. Set STRIPE_SECRET_KEY environment variable.");
        }
        
        Stripe.apiKey = stripeSecretKey;
    }
}

