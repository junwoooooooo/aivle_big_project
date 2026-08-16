package com.aivle.backend.pipeline.finance.repository;

import com.aivle.backend.pipeline.finance.domain.FinancialInputSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialInputSnapshotRepository extends JpaRepository<FinancialInputSnapshot, String> {
    Optional<FinancialInputSnapshot> findByPreparationIdAndProjectIdAndDeletedAtIsNull(String preparationId, Long projectId);
    Optional<FinancialInputSnapshot> findBySourceTechOpsSnapshotIdAndProjectIdAndDeletedAtIsNull(String sourceId, Long projectId);
    Optional<FinancialInputSnapshot> findByProjectIdAndSourceTechOpsSnapshotIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNull(
        Long projectId, String techOpsId, Long marketVersionId, Long businessModelVersionId);
    Optional<FinancialInputSnapshot> findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByFinalizedAtAsc(
        Long projectId, Long marketVersionId, Long businessModelVersionId);
    Optional<FinancialInputSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(Long projectId);
    Optional<FinancialInputSnapshot> findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByFinalizedAtDesc(Long projectId, String sourceMode);
}
