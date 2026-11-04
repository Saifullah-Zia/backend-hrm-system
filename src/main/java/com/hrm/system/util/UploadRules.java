package com.hrm.system.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

/** File types HR uploads may have, and safe headers for serving them back. */
public final class UploadRules {

    private static final Map<String, MediaType> ALLOWED = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "webp", MediaType.parseMediaType("image/webp"),
            "doc", MediaType.parseMediaType("application/msword"),
            "docx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "xls", MediaType.parseMediaType("application/vnd.ms-excel"),
            "xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private UploadRules() {}

    /** Rejects empty files and types outside the list, and returns the lowercase extension. */
    public static String requireAllowedExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED.containsKey(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File type not allowed. Use PDF, JPG, PNG, WEBP, Word or Excel.");
        }
        return extension;
    }

    /** Drops any directory part and every character that is unsafe in a file name. */
    public static String safeFileName(String originalName) {
        String name = originalName == null ? "file" : originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._ -]", "_");
        name = name.replaceAll("\\.{2,}", ".").trim();
        return name.isEmpty() ? "file" : name;
    }

    /** Types outside the list are always downloaded, never rendered on the API's origin. */
    public static ResponseEntity.BodyBuilder fileResponse(String fileName) {
        MediaType type = ALLOWED.getOrDefault(extensionOf(fileName), MediaType.APPLICATION_OCTET_STREAM);
        boolean inline = type.equals(MediaType.APPLICATION_PDF) || "image".equals(type.getType());
        return ResponseEntity.ok()
                .contentType(type)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? "inline" : "attachment") + "; filename=\"" + safeFileName(fileName) + "\"");
    }

    private static String extensionOf(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
