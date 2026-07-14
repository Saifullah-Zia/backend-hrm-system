package com.hrm.system.controller;

import com.hrm.system.dto.ProbationDto;
import com.hrm.system.dto.UserDTO;
import com.hrm.system.model.User;
import com.hrm.system.service.ProbationService;
import com.hrm.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProbationService probationService;

    // ─── Create user ──────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserDTO> createUser(@RequestBody User user) {
        if (user.getPassword() == null) {
            throw new RuntimeException("Password is null");
        }
        return new ResponseEntity<>(userService.createUser(user), HttpStatus.CREATED);
    }

    // ─── Get all users ────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ─── Get user by ID ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // ─── Get user by email ────────────────────────────────────────────────
    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    // ─── Update user ──────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    // ─── Delete user ──────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User deleted successfully");
        return ResponseEntity.ok(response);
    }

    // ─── Change password ──────────────────────────────────────────────────
    @PutMapping("/{id}/change-password")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Long id, @RequestBody Map<String, String> passwordRequest) {
        userService.changePassword(id, passwordRequest.get("oldPassword"),
                passwordRequest.get("newPassword"));
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password changed successfully");
        return ResponseEntity.ok(response);
    }

    // ─── Get all users currently on probation ─────────────────────────────
    @GetMapping("/probation/active")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getUsersOnProbation() {
        return ResponseEntity.ok(userService.getUsersOnProbation());
    }

    // ─── Get users whose probation ended, pending HR confirmation ─────────
    @GetMapping("/probation/pending-confirmation")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getUsersPendingConfirmation() {
        return ResponseEntity.ok(userService.getUsersPendingProbationConfirmation());
    }

    // ─── HR confirms probation for an employee ────────────────────────────
    @PutMapping("/{userId}/probation/confirm")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ProbationDto.Response> confirmProbation(
            @PathVariable Long userId,
            @RequestParam Long confirmedByAdminId) {
        return ResponseEntity.ok(probationService.confirmProbation(userId, confirmedByAdminId));
    }

    // ─── Toggle web check-in access per employee ──────────────────────────
    @PatchMapping("/{id}/web-checkin-access")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<UserDTO> setWebCheckInAccess(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Boolean> body) {
        Boolean allowed = body.get("allowed");
        if (allowed == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userService.updateWebCheckInAccess(id, allowed));
    }

}