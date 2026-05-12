package com.hrm.system.controller;

import com.hrm.system.config.JwtUtil;
import com.hrm.system.dto.auth.LoginRequest;
import com.hrm.system.dto.auth.RegisterResponse;
import com.hrm.system.model.User;
import com.hrm.system.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("http://localhost:3000")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = authService.authenticate(request.getEmail(), request.getPassword());

        String accessToken  = jwtUtil.generateToken(
                user.getName(), user.getRole().name(), user.getId(), user.getEmail());

        String refreshToken = jwtUtil.generateRefreshToken(
                user.getName(), user.getRole().name(), user.getId(), user.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken",  accessToken);
        response.put("refreshToken", refreshToken);
        response.put("userId",       user.getId());
        response.put("role",         user.getRole().name());
        response.put("name",         user.getName());
        response.put("email",        user.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody User user) {
        return authService.register(user);
    }
}