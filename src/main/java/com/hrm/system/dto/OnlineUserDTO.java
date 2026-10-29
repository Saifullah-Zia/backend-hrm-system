package com.hrm.system.dto;

import java.time.LocalDateTime;

public record OnlineUserDTO(
        Long id,
        String name,
        String email,
        String role,
        LocalDateTime lastSeenAt
) {}