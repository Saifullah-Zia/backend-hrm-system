package com.hrm.system.controller;

import com.hrm.system.dto.NoticeDto;
import com.hrm.system.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    // Send notice to employee
    @PostMapping("/send")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<NoticeDto> sendNotice(@Valid @RequestBody NoticeDto dto) {
        return ResponseEntity.ok(noticeService.sendNotice(dto));
    }

    // Get all notices (admin only)
    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<NoticeDto>> getAllNotices() {
        return ResponseEntity.ok(noticeService.getAllNotices());
    }

    // Get notices for a specific user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NoticeDto>> getNoticesByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(noticeService.getNoticesByUserId(userId));
    }
}
