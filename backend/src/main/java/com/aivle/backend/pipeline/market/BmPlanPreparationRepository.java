package com.aivle.backend.pipeline.market;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BmPlanPreparationRepository extends JpaRepository<BmPlanPreparation, String> {

    /** 프로젝트당 하나다 — 여러 벌을 두면 「어느 것으로 돌렸나」가 흐려진다. */
    Optional<BmPlanPreparation> findByProjectIdAndDeletedAtIsNull(Long projectId);
}
