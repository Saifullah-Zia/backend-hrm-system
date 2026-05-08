package com.hrm.system.service;

import com.hrm.system.dto.AnnouncementDto;
import com.hrm.system.model.Announcement;
import com.hrm.system.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    // ✅ Convert Entity → DTO
    private AnnouncementDto toDto(Announcement a) {
        AnnouncementDto dto = new AnnouncementDto();
        dto.setId(a.getId());
        dto.setTitle(a.getTitle());
        dto.setContent(a.getContent());
        dto.setActive(a.isActive());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUpdatedAt(a.getUpdatedAt());
        dto.setCreatedBy(a.getCreatedBy());
        dto.setUpdatedBy(a.getUpdatedBy());
        return dto;
    }

    // ✅ Now returns List<AnnouncementDto>
    public List<AnnouncementDto> getAllAnnouncement() {
        return announcementRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<AnnouncementDto> getActive() {
        return announcementRepository.findByActiveTrue()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AnnouncementDto create(AnnouncementDto dto) {
        Announcement a = new Announcement();
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setActive(true);
        return toDto(announcementRepository.save(a));
    }

    public AnnouncementDto update(Long id, AnnouncementDto dto) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setActive(dto.isActive());
        return toDto(announcementRepository.save(a));
    }

    public void delete(Long id) {
        if (!announcementRepository.existsById(id))
            throw new RuntimeException("Announcement not found");
        announcementRepository.deleteById(id);
    }
}