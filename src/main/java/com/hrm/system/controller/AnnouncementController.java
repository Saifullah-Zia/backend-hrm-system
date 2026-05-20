package com.hrm.system.controller;

import com.hrm.system.dto.AnnouncementDto;
import com.hrm.system.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    //get all Announcement
    @GetMapping
    public ResponseEntity<List<AnnouncementDto>> getAll() {
        return ResponseEntity.ok(announcementService.getAllAnnouncement());
    }

    //get all Active announcement
    @GetMapping("/active")
    public ResponseEntity<List<AnnouncementDto>> getAllActive() {
        return ResponseEntity.ok(announcementService.getActive());
    }

    //create announcement
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<AnnouncementDto> createAnnouncement(@Valid @RequestBody AnnouncementDto dto) {
        return ResponseEntity.ok(announcementService.create(dto));
    }

    //Update announcement
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<AnnouncementDto> updateAnnouncement(@PathVariable Long id, @Valid @RequestBody AnnouncementDto dto) {
        return ResponseEntity.ok(announcementService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<String> deleteAnnouncement(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.ok("Announcement Deleted Successfully");
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<AnnouncementDto>> getPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(announcementService.getPaged(pageable));
    }
}
