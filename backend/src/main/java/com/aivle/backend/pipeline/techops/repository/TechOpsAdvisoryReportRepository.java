package com.aivle.backend.pipeline.techops.repository;

import com.aivle.backend.pipeline.techops.domain.TechOpsAdvisoryReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechOpsAdvisoryReportRepository extends JpaRepository<TechOpsAdvisoryReport, String> {
    Optional<TechOpsAdvisoryReport> findByTaskRunIdAndDeletedAtIsNull(String taskRunId);
    Optional<TechOpsAdvisoryReport> findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
}
