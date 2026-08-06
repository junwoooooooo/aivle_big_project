package com.aivle.backend.document.repository;
import com.aivle.backend.document.entity.ProjectDocument;
import com.aivle.backend.common.entity.DocumentStatus;
import com.aivle.backend.common.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    List<ProjectDocument> findAllByProjectIdAndStatus(Long projectId, DocumentStatus status);
    List<ProjectDocument> findAllByProjectIdAndDocumentTypeAndStatusAndDeletedAtIsNull(
        Long projectId,
        DocumentType documentType,
        DocumentStatus status
    );
    List<ProjectDocument> findAllByProjectIdAndStatusAndDeletedAtIsNull(
        Long projectId,
        DocumentStatus status
    );
}
