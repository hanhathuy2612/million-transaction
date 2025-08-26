package com.hnh.example.transaction_example.testutils;

import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Utility class for generating JWT tokens in tests
 */
public class JwtTestUtil {

    private static final String SECRET_KEY = "test-secret-key-for-testing-purposes-only";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    /**
     * Generate a valid JWT token for testing
     */
    public static String generateTestToken() {
        return generateTestToken("test-user", "ROLE_USER");
    }

    /**
     * Generate a valid JWT token with custom claims
     */
    public static String generateTestToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 86400000); // 24 hours

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("merchantId", "merchant_1")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(KEY)
                .compact();
    }

    /**
     * Generate an expired JWT token for testing
     */
    public static String generateExpiredToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() - 1000); // Expired 1 second ago

        return Jwts.builder()
                .subject("test-user")
                .claim("role", "ROLE_USER")
                .claim("merchantId", "merchant_1")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(KEY)
                .compact();
    }
}
