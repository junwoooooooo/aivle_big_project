package com.aivle.backend.pipeline.market;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BmPlanPreparationRepository extends JpaRepository<BmPlanPreparation, String> {

    /** 프로젝트당 하나다 — 여러 벌을 두면 「어느 것으로 돌렸나」가 흐려진다. */
    Optional<BmPlanPreparation> findByProjectIdAndDeletedAtIsNull(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BmPlanPreparation p where p.projectId=:projectId and p.deletedAt is null")
    Optional<BmPlanPreparation> findByProjectIdForUpdate(@Param("projectId") Long projectId);
}
