package com.hrm.system.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaves")
public class LeaveAttachmentController {

    private static final String UPLOAD_DIR = "uploads/leaves/";

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
        }

        // Ensure directory exists
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String savedFileName = UUID.randomUUID() + extension;
        Path savedPath = uploadPath.resolve(savedFileName).toAbsolutePath();

        Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

        String fileUrl = "/api/leaves/files/" + savedFileName;

        return ResponseEntity.ok(Map.of(
                "fileUrl", fileUrl,
                "fileName", originalName
        ));
    }

    @GetMapping("/files/{fileName}")
    public ResponseEntity<byte[]> serveFile(@PathVariable String fileName) throws IOException {
        Path filePath = Paths.get(UPLOAD_DIR).resolve(fileName);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = Files.readAllBytes(filePath);
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) mimeType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .body(data);
    }
}
