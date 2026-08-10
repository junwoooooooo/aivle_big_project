package com.aivle.backend.pipeline.artifact.repository;

import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectEvidenceArtifactRepository extends JpaRepository<ProjectEvidenceArtifact, String> {
    Optional<ProjectEvidenceArtifact> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
    List<ProjectEvidenceArtifact> findAllByIdInAndProjectIdAndDeletedAtIsNull(List<String> ids, Long projectId);
}
