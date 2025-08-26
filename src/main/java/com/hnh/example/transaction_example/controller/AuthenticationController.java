package com.hnh.example.transaction_example.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hnh.example.transaction_example.dto.LoginRequest;
import com.hnh.example.transaction_example.dto.RegisterRequest;
import com.hnh.example.transaction_example.dto.TokenResponse;
import com.hnh.example.transaction_example.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        HttpHeaders httpHeaders = new HttpHeaders();
        TokenResponse token = userService.login(request);
        httpHeaders.setBearerAuth(token.getToken());
        return ResponseEntity.ok()
                .headers(httpHeaders)
                .body(token);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        userService.register(request);
        return ResponseEntity.ok("Register successful");
    }
}
