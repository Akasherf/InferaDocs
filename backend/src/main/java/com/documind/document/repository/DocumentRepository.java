package com.documind.document.repository;
import com.documind.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DocumentRepository extends JpaRepository<Document,UUID> {
 List<Document> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId,String status);
 Optional<Document> findByIdAndOrganizationIdAndStatus(UUID id,UUID organizationId,String status);
}
