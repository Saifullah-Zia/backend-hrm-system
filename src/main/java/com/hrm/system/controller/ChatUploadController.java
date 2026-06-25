package com.hrm.system.controller;

import com.hrm.system.dto.ChatMessageDTO;
import com.hrm.system.enumm.MessageType;
import com.hrm.system.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatUploadController {

    private final ChatMessageService chatMessageService;

    private static final String UPLOAD_DIR = "uploads/chat/";

    @PostMapping(value = "/upload/{conversationId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadChatFile(
            @PathVariable UUID conversationId,
            @RequestParam("file") MultipartFile file,
            Principal principal) throws IOException {

        // Determine type
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        MessageType messageType = contentType.startsWith("image/") ? MessageType.IMAGE : MessageType.FILE;

        // Save file
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String savedFileName = UUID.randomUUID() + extension;
        Path savedPath = uploadPath.resolve(savedFileName);
        file.transferTo(savedPath.toFile());

        String fileUrl = "/api/chat/files/" + savedFileName;
        String displayName = originalName;

        // Save message record
        com.hrm.system.dto.ChatMessageRequest req = new com.hrm.system.dto.ChatMessageRequest(
                displayName, messageType, fileUrl
        );
        ChatMessageDTO saved = chatMessageService.save(conversationId, principal.getName(), req);

        return ResponseEntity.ok(Map.of(
                "fileUrl", fileUrl,
                "fileName", displayName,
                "messageId", saved.getId().toString(),
                "type", messageType.name()
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
