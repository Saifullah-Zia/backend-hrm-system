package com.hrm.system.service;

import com.hrm.system.config.JwtUtil;
import com.hrm.system.dto.auth.LoginRequest;
import com.hrm.system.dto.auth.RegisterResponse;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;  // ✅ changed
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId(),      // ✅ add
                user.getEmail()    // ✅ add
        );
        return new RegisterResponse("Login successful", token);
    }

    public RegisterResponse register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId(),      // ✅ add
                user.getEmail()    // ✅ add
        );
        return new RegisterResponse("Registration successful", token);
    }
}