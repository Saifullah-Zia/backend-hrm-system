package com.hrm.system.controller;

import com.hrm.system.dto.DocumentDto;
import com.hrm.system.enumm.DocumentType;
import com.hrm.system.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor

    public class DocumentController {

        private final DocumentService documentService;

        // ─────────────────────────────────────────────────────
        // POST /api/documents/upload
        // Upload a new document (multipart form)
        // ─────────────────────────────────────────────────────
        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
        public ResponseEntity<DocumentDto.Response> uploadDocument(
                @RequestPart("data")  DocumentDto.Request request,
                @RequestPart("file") MultipartFile file,
                @RequestParam("uploadedBy") Long uploadedByUserId) throws IOException {

            return ResponseEntity.ok(
                    documentService.uploadDocument(request, file, uploadedByUserId));
        }

        // ─────────────────────────────────────────────────────
        // GET /api/documents/employee/{employeeId}
        // Get all documents for an employee
        // ─────────────────────────────────────────────────────
        @GetMapping("/employee/{employeeId}")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
        public ResponseEntity<List<DocumentDto.Response>> getByEmployee(
                @PathVariable Long employeeId) {

            return ResponseEntity.ok(documentService.getDocumentsByEmployee(employeeId));
        }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<DocumentDto.Response>> getAll() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

        // ─────────────────────────────────────────────────────
        // GET /api/documents/employee/{employeeId}/type/{type}
        // Filter documents by type
        // ─────────────────────────────────────────────────────
        @GetMapping("/employee/{employeeId}/type/{type}")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
        public ResponseEntity<List<DocumentDto.Response>> getByType(
                @PathVariable Long employeeId,
                @PathVariable DocumentType type) {

            return ResponseEntity.ok(documentService.getDocumentsByType(employeeId, type));
        }

        // ─────────────────────────────────────────────────────
        // GET /api/documents/{id}
        // Get document metadata by id
        // ─────────────────────────────────────────────────────
        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
        public ResponseEntity<DocumentDto.Response> getById(@PathVariable Long id) {
            return ResponseEntity.ok(documentService.getDocumentById(id));
        }

        // ─────────────────────────────────────────────────────
        // GET /api/documents/{id}/download
        // Stream / download the actual file
        // ─────────────────────────────────────────────────────
        @GetMapping("/{id}/download")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
        public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) throws IOException {
            Path filePath = documentService.getDocumentFilePath(id);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filePath.getFileName() + "\"")
                    .body(resource);
        }

        // ─────────────────────────────────────────────────────
        // PUT /api/documents/{id}
        // Update document metadata
        // ─────────────────────────────────────────────────────
        @PutMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
        public ResponseEntity<DocumentDto.Response> updateDocument(
                @PathVariable Long id,
                @RequestBody DocumentDto.UpdateRequest request) {

            return ResponseEntity.ok(documentService.updateDocument(id, request));
        }

        // ─────────────────────────────────────────────────────
        // DELETE /api/documents/{id}
        // Soft-delete a document
        // ─────────────────────────────────────────────────────
        @DeleteMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
        public ResponseEntity<String> deleteDocument(@PathVariable Long id) {
            documentService.deleteDocument(id);
            return ResponseEntity.ok("Document deleted successfully");
        }

        // ─────────────────────────────────────────────────────
        // GET /api/documents/search?keyword=contract
        // Search documents by title
        // ─────────────────────────────────────────────────────
        @GetMapping("/search")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
        public ResponseEntity<List<DocumentDto.Response>> search(
                @RequestParam String keyword) {

            return ResponseEntity.ok(documentService.searchDocuments(keyword));
        }

        // ─────────────────────────────────────────────────────
        // GET /api/documents/expiring?days=30
        // Documents expiring within N days
        // ─────────────────────────────────────────────────────
        @GetMapping("/expiring")
        @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
        public ResponseEntity<List<DocumentDto.Response>> getExpiringSoon(
                @RequestParam(defaultValue = "30") int days) {

            return ResponseEntity.ok(documentService.getExpiringSoonDocuments(days));
        }

}
