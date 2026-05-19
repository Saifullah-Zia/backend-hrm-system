package com.hrm.system.repository;

import com.hrm.system.enumm.DocumentStatus;
import com.hrm.system.enumm.DocumentType;
import com.hrm.system.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    // All documents for a specific employee
    List<Document> findByEmployeeProfileIdAndStatusNot(Long employeeId, DocumentStatus status);

    // Filter by employee + document type
    List<Document> findByEmployeeProfileIdAndDocumentTypeAndStatusNot(Long employeeId, DocumentType documentType, DocumentStatus status);

    // All documents of a certain type across all employees
    List<Document> findByDocumentTypeAndStatusNot(DocumentType documentType, DocumentStatus status);

    // Find by id but exclude deleted
    Optional<Document> findByIdAndStatusNot(Long id, DocumentStatus status);

    // Documents expiring before a given date (for expiry alerts)
    @Query("SELECT d FROM Document d WHERE d.expiryDate IS NOT NULL " +
            "AND d.expiryDate <= :threshold AND d.status = 'ACTIVE'")
    List<Document> findDocumentsExpiringSoon(@Param("threshold") LocalDateTime threshold);

    // Count documents per employee
    long countByEmployeeProfile_IdAndStatusNot(Long employeeId, DocumentStatus status);

    // Search by title keyword
    @Query("SELECT d FROM Document d WHERE d.status != 'DELETED' " +
            "AND LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Document> searchByTitle(@Param("keyword") String keyword);
}