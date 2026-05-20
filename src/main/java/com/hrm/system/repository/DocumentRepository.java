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

    @Query("SELECT d FROM Document d JOIN FETCH d.employeeProfile ep LEFT JOIN FETCH d.uploadedBy " +
            "WHERE ep.id = :employeeId AND d.status != :status")
    List<Document> findByEmployeeProfileIdAndStatusNot(@Param("employeeId") Long employeeId,
                                                       @Param("status") DocumentStatus status);

    @Query("SELECT d FROM Document d JOIN FETCH d.employeeProfile ep LEFT JOIN FETCH d.uploadedBy " +
            "WHERE ep.id = :employeeId AND d.documentType = :documentType AND d.status != :status")
    List<Document> findByEmployeeProfileIdAndDocumentTypeAndStatusNot(@Param("employeeId") Long employeeId,
                                                                      @Param("documentType") DocumentType documentType,
                                                                      @Param("status") DocumentStatus status);

    List<Document> findByDocumentTypeAndStatusNot(DocumentType documentType, DocumentStatus status);

    @Query("SELECT d FROM Document d JOIN FETCH d.employeeProfile LEFT JOIN FETCH d.uploadedBy " +
            "WHERE d.id = :id AND d.status != :status")
    Optional<Document> findByIdAndStatusNot(@Param("id") Long id, @Param("status") DocumentStatus status);

    @Query("SELECT d FROM Document d JOIN FETCH d.employeeProfile LEFT JOIN FETCH d.uploadedBy " +
            "WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :threshold AND d.status = 'ACTIVE'")
    List<Document> findDocumentsExpiringSoon(@Param("threshold") LocalDateTime threshold);

    long countByEmployeeProfile_IdAndStatusNot(Long employeeId, DocumentStatus status);

    @Query("SELECT d FROM Document d JOIN FETCH d.employeeProfile LEFT JOIN FETCH d.uploadedBy " +
            "WHERE d.status != 'DELETED' AND LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Document> searchByTitle(@Param("keyword") String keyword);

    @Query("SELECT d FROM Document d JOIN FETCH d.employeeProfile LEFT JOIN FETCH d.uploadedBy")
    List<Document> findAllWithAssociations();
}