package com.aivle.backend.pipeline.techops.repository;

import com.aivle.backend.pipeline.techops.domain.TechOpsEvidenceReference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechOpsEvidenceReferenceRepository extends JpaRepository<TechOpsEvidenceReference, String> {
    List<TechOpsEvidenceReference> findAllByPreparationIdAndDeletedAtIsNullOrderByCreatedAtAsc(String preparationId);
    Optional<TechOpsEvidenceReference> findByIdAndPreparationIdAndProjectIdAndDeletedAtIsNull(String id, String preparationId, Long projectId);
}
