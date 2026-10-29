package com.hrm.system.dto;

import java.util.List;

public record OnlineUsersResponse(
        long onlineCount,
        long totalUsers,
        List<OnlineUserDTO> users
) {}