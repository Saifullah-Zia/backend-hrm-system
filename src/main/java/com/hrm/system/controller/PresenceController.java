package com.hrm.system.controller;

import com.hrm.system.dto.OnlineUsersResponse;
import com.hrm.system.service.PresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    // Any logged-in user (principal username is the user's email)
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(Authentication authentication) {
        presenceService.heartbeat(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // SUPERADMIN only
    @GetMapping("/online")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public OnlineUsersResponse online() {
        return presenceService.getOnlineUsers();
    }
}