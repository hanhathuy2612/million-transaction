package com.hnh.example.transaction_example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for caching idempotency response data in Redis
 * This avoids caching ResponseEntity directly which can cause issues
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyCacheDto implements Serializable {
    
    private String requestHash;
    private Integer statusCode;
    private String responseBody;
    private String headers;
    private Long timestamp;
    
    public IdempotencyCacheDto(String requestHash, Integer statusCode, String responseBody, String headers) {
        this.requestHash = requestHash;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.headers = headers;
        this.timestamp = System.currentTimeMillis();
    }
}
