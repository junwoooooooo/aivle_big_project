package com.aivle.backend.document.repository;
import com.aivle.backend.document.entity.DocumentVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findAllByDocumentIdOrderByVersionNumberDesc(Long documentId);

    @EntityGraph(attributePaths = {"storedFile", "document", "document.project", "document.project.owner"})
    Optional<DocumentVersion> findByIdAndDocumentIdAndDeletedAtIsNull(Long id, Long documentId);

    @Query("""
        select v
        from DocumentVersion v
        join fetch v.storedFile
        where v.document.id in :documentIds
          and v.versionNumber = v.document.currentVersion
          and v.deletedAt is null
        """)
    List<DocumentVersion> findCurrentVersions(@Param("documentIds") List<Long> documentIds);
}
