package com.hrm.system.controller;

import org.springframework.beans.factory.annotation.Value;
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

    // Was a hardcoded relative path ("uploads/leaves/"), which lives inside the
    // container's own filesystem. Railway containers are ephemeral — every
    // redeploy, restart, or new instance wipes that disk, so previously
    // uploaded files silently disappear while the DB still has the fileUrl.
    // That's why HR got a 404 on a file that was uploaded successfully.
    //
    // Fix: point this at a Railway Volume, which is a persistent disk mounted
    // into the container at a fixed path (survives restarts/redeploys).
    // Configurable via env var so local dev without a volume still works,
    // falling back to the old relative path.
    @Value("${app.upload.leaves-dir:${LEAVES_UPLOAD_DIR:uploads/leaves/}}")
    private String uploadDir;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
        }

        // Ensure directory exists
        Path uploadPath = Paths.get(uploadDir);
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
        // Guard against path traversal (e.g. "../../etc/passwd") — filename
        // should always be our own UUID + extension, never contain a
        // separator, but don't trust the path variable blindly.
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(uploadDir).resolve(fileName);
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