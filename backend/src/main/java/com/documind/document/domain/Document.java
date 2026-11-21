package com.documind.document.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="documents")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Document {
    @Id @UuidGenerator private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="uploaded_by",nullable=false) private UUID uploadedBy;
    @Column(name="original_filename",nullable=false) private String originalFilename;
    @Column(name="content_type",nullable=false) private String contentType;
    @Column(name="file_size",nullable=false) private long fileSize;
    @Column(name="object_key",nullable=false) private String objectKey;
    @Column(nullable=false,length=64) private String sha256;
    @Column(nullable=false) private String status;
    @CreationTimestamp
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="deleted_at") private Instant deletedAt;
}
