package com.hrm.system.model;

import com.hrm.system.enumm.DocumentStatus;
import com.hrm.system.enumm.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private EmployeeProfile employeeProfile;

    @Column(nullable = false)
    private String title;               // e.g. "Employment Contract 2024"

    @Column(nullable = false)
    private String fileName;            // original file name

    @Column(nullable = false)
    private String filePath;            // stored path / S3 key

    @Column(nullable = false)
    private String fileType;            // MIME type e.g. "application/pdf"

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;  // CONTRACT, CERTIFICATE, ID_PROOF …

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.ACTIVE;

    private String description;         // optional notes

    private LocalDateTime expiryDate;   // for documents that expire (e.g. visa)

    // Who uploaded the document
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
