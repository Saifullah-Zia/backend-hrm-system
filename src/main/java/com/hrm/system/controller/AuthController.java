package com.hrm.system.controller;

import com.hrm.system.dto.auth.LoginRequest;
import com.hrm.system.dto.auth.RegisterResponse;
import com.hrm.system.model.User;
import com.hrm.system.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public RegisterResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody User user) {
        return authService.register(user);
    }
}
