package com.aivle.backend.pipeline.marketing.strategy.repository;

import com.aivle.backend.pipeline.marketing.strategy.domain.MarketingStrategyReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingStrategyReportRepository
        extends JpaRepository<MarketingStrategyReport, String> {

    Optional<MarketingStrategyReport>
        findByIdAndProjectIdAndDeletedAtIsNull(
            String id,
            Long projectId
        );

    Optional<MarketingStrategyReport>
        findByTaskRunIdAndDeletedAtIsNull(
            String taskRunId
        );

    Optional<MarketingStrategyReport>
        findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long projectId
        );
}
