package com.hnh.example.transaction_example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/test")
@Tag(name = "Test", description = "Test endpoints")
public class TestController {

    @Operation(summary = "Test endpoint", description = "Simple test endpoint to verify OpenAPI is working")
    @GetMapping
    public String test() {
        return "OpenAPI is working!";
    }
}
