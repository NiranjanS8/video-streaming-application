package com.stream_app.services;

import com.stream_app.dto.auth.AuthResponse;
import com.stream_app.dto.auth.LoginRequest;
import com.stream_app.dto.auth.RegisterRequest;

public interface AuthService {
    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}

