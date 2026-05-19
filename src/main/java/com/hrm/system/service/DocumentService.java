package com.hrm.system.service;

import com.hrm.system.dto.DocumentDto;
import com.hrm.system.enumm.DocumentStatus;
import com.hrm.system.enumm.DocumentType;
import com.hrm.system.exception.BadRequestException;
import com.hrm.system.exception.ResourceNotFoundException;
import com.hrm.system.model.Document;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.User;
import com.hrm.system.repository.DocumentRepository;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final UserRepository userRepository;

    private static final String STORAGE_DIR = "uploads/documents/";

    // ─────────────────────────────────────────────────────
    // UPLOAD a new document
    // ─────────────────────────────────────────────────────
    public DocumentDto.Response uploadDocument(
            DocumentDto.Request request,
            MultipartFile file,
            Long uploadedByUserId) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        EmployeeProfile employee = employeeProfileRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + request.getEmployeeId()));

        User uploader = userRepository.findById(uploadedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + uploadedByUserId));

        String fileName  = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String directory = STORAGE_DIR + request.getEmployeeId() + "/";
        Path filePath    = Paths.get(directory, fileName);
        Files.createDirectories(filePath.getParent());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Document document = Document.builder()
                .employeeProfile(employee)
                .title(request.getTitle())
                .fileName(file.getOriginalFilename())
                .filePath(filePath.toString())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .documentType(request.getDocumentType())
                .description(request.getDescription())
                .expiryDate(request.getExpiryDate())
                .uploadedBy(uploader)
                .status(DocumentStatus.ACTIVE)
                .build();

        return mapToResponse(documentRepository.save(document));
    }

    // ─────────────────────────────────────────────────────
    // GET all documents for an employee
    // ─────────────────────────────────────────────────────
    public List<DocumentDto.Response> getDocumentsByEmployee(Long employeeId) {
        return documentRepository
                .findByEmployeeProfileIdAndStatusNot(employeeId, DocumentStatus.DELETED)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET documents filtered by type
    // ─────────────────────────────────────────────────────
    public List<DocumentDto.Response> getDocumentsByType(Long employeeId, DocumentType type) {
        return documentRepository
                .findByEmployeeProfileIdAndDocumentTypeAndStatusNot(employeeId, type, DocumentStatus.DELETED)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET single document
    // ─────────────────────────────────────────────────────
    public DocumentDto.Response getDocumentById(Long id) {
        return mapToResponse(findActiveDocument(id));
    }

    // ─────────────────────────────────────────────────────
    // DOWNLOAD — return file path for streaming
    // ─────────────────────────────────────────────────────
    public Path getDocumentFilePath(Long id) {
        Document document = findActiveDocument(id);
        return Paths.get(document.getFilePath());
    }

    // ─────────────────────────────────────────────────────
    // UPDATE document metadata
    // ─────────────────────────────────────────────────────
    public DocumentDto.Response updateDocument(Long id, DocumentDto.UpdateRequest request) {
        Document document = findActiveDocument(id);

        if (request.getTitle()        != null) document.setTitle(request.getTitle());
        if (request.getDocumentType() != null) document.setDocumentType(request.getDocumentType());
        if (request.getDescription()  != null) document.setDescription(request.getDescription());
        if (request.getExpiryDate()   != null) document.setExpiryDate(request.getExpiryDate());
        if (request.getStatus()       != null) document.setStatus(request.getStatus());

        return mapToResponse(documentRepository.save(document));
    }

    // ─────────────────────────────────────────────────────
    // SOFT DELETE
    // ─────────────────────────────────────────────────────
    public void deleteDocument(Long id) {
        Document document = findActiveDocument(id);
        document.setStatus(DocumentStatus.DELETED);
        documentRepository.save(document);
    }

    // ─────────────────────────────────────────────────────
    // SEARCH by title keyword
    // ─────────────────────────────────────────────────────
    public List<DocumentDto.Response> searchDocuments(String keyword) {
        return documentRepository.searchByTitle(keyword)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // EXPIRY ALERT — documents expiring within N days
    // ─────────────────────────────────────────────────────
    public List<DocumentDto.Response> getExpiringSoonDocuments(int daysAhead) {
        LocalDateTime threshold = LocalDateTime.now().plusDays(daysAhead);
        return documentRepository.findDocumentsExpiringSoon(threshold)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // HELPER — find non-deleted document or throw
    // ─────────────────────────────────────────────────────
    private Document findActiveDocument(Long id) {
        return documentRepository.findByIdAndStatusNot(id, DocumentStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found with id: " + id));
    }

    // ─────────────────────────────────────────────────────
    // MAPPER — Entity → Response DTO
    // ─────────────────────────────────────────────────────
    private DocumentDto.Response mapToResponse(Document doc) {
        boolean expired = doc.getExpiryDate() != null
                && doc.getExpiryDate().isBefore(LocalDateTime.now());

        EmployeeProfile emp = doc.getEmployeeProfile();
        String employeeName = emp.getFirstName() + " " + emp.getLastName();

        return DocumentDto.Response.builder()
                .id(doc.getId())
                .employeeId(emp.getId())
                .employeeName(employeeName)
                .title(doc.getTitle())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .fileSizeFormatted(formatFileSize(doc.getFileSize()))
                .documentType(doc.getDocumentType())
                .status(doc.getStatus())
                .description(doc.getDescription())
                .expiryDate(doc.getExpiryDate())
                .isExpired(expired)
                .uploadedByName(doc.getUploadedBy() != null
                        ? doc.getUploadedBy().getName() : "System")
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    // ─────────────────────────────────────────────────────
    // HELPER — format bytes to human-readable size
    // ─────────────────────────────────────────────────────
    private String formatFileSize(Long bytes) {
        if (bytes == null)       return "Unknown";
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}