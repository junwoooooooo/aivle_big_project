package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalGuardrailSetRepository extends JpaRepository<LegalGuardrailSet, Long> {
    @EntityGraph(attributePaths = {"legalPrecheckVersion", "sourceRun"})
    Optional<LegalGuardrailSet> findByLegalPrecheckVersionIdAndDeletedAtIsNull(Long versionId);
    long countByProjectIdAndDeletedAtIsNull(Long projectId);
}
