package com.hnh.example.transaction_example.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WebhookNotFoundException extends RuntimeException {
    public WebhookNotFoundException(Long id) {
        super("Webhook not found with id: " + id);
    }
}
