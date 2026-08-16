package com.aivle.backend.pipeline.finance.repository;

import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialInputPreparationRepository extends JpaRepository<FinancialInputPreparation, String> {
    Optional<FinancialInputPreparation> findByProjectIdAndSourceTechOpsSnapshotIdAndDeletedAtIsNull(Long projectId, String sourceId);
    Optional<FinancialInputPreparation> findByProjectIdAndSourceTechOpsSnapshotIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNull(
        Long projectId, String techOpsId, Long marketVersionId, Long businessModelVersionId);
    Optional<FinancialInputPreparation> findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(
        Long projectId, Long marketVersionId, Long businessModelVersionId);
    Optional<FinancialInputPreparation> findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
        Long projectId, String sourceMode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from FinancialInputPreparation value where value.id=:id and value.projectId=:projectId and value.deletedAt is null")
    Optional<FinancialInputPreparation> findLocked(@Param("id") String id, @Param("projectId") Long projectId);
}
