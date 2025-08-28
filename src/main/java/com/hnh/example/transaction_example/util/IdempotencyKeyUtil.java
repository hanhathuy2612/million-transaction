package com.hnh.example.transaction_example.util;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyUtil {

    public static String generateKey(String merchantId, String operation) {
        return String.format("%s_%s_%d_%s",
                merchantId,
                operation,
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    public static boolean isValidFormat(String key) {
        String pattern = "^[a-zA-Z0-9]+_[a-zA-Z]+_\\d+_[a-f0-9]{8}$";
        return key.matches(pattern);
    }
}
