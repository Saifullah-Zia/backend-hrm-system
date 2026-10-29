package com.hrm.system.service;

import com.hrm.system.dto.OnlineUserDTO;
import com.hrm.system.dto.OnlineUsersResponse;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PresenceService {

    private static final long THROTTLE_SECONDS = 30;
    private static final long ONLINE_WINDOW_SECONDS = 150; // 2.5 minutes

    private final UserRepository userRepository;

    public PresenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Stamps lastSeenAt; the query skips the write if stamped within the last 30s. */
    public void heartbeat(String email) {
        LocalDateTime now = LocalDateTime.now();
        userRepository.touchLastSeen(email, now, now.minusSeconds(THROTTLE_SECONDS));
    }

    public OnlineUsersResponse getOnlineUsers() {
        LocalDateTime since = LocalDateTime.now().minusSeconds(ONLINE_WINDOW_SECONDS);
        List<User> online = userRepository.findByLastSeenAtAfterOrderByLastSeenAtDesc(since);

        List<OnlineUserDTO> dtos = online.stream()
                .map(u -> new OnlineUserDTO(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        String.valueOf(u.getRole()),
                        u.getLastSeenAt()))
                .toList();

        return new OnlineUsersResponse(dtos.size(), userRepository.count(), dtos);
    }
}