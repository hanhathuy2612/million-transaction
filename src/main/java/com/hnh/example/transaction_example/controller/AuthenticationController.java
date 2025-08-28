package com.hnh.example.transaction_example.controller;

import com.hnh.example.transaction_example.dto.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hnh.example.transaction_example.dto.LoginRequest;
import com.hnh.example.transaction_example.dto.RegisterRequest;
import com.hnh.example.transaction_example.dto.TokenResponse;
import com.hnh.example.transaction_example.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication (Login, Register,...)", description = "User authentication and registration API")
public class AuthenticationController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        HttpHeaders httpHeaders = new HttpHeaders();
        TokenResponse token = authService.login(request);
        httpHeaders.setBearerAuth(token.getToken());
        return ResponseEntity.ok()
                .headers(httpHeaders)
                .body(token);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
}
