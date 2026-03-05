package com.stream_app.controllers;

import com.stream_app.dto.auth.AuthResponse;
import com.stream_app.dto.auth.LoginRequest;
import com.stream_app.dto.auth.RegisterRequest;
import com.stream_app.playload.CustomMessage;
import com.stream_app.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<CustomMessage> register(@RequestBody RegisterRequest request) {
        try {
            authService.register(request);
            return ResponseEntity.ok(new CustomMessage("User registered successfully", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new CustomMessage(ex.getMessage(), false));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}

