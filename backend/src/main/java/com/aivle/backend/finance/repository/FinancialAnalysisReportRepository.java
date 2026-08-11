package com.aivle.backend.finance.repository;

import com.aivle.backend.finance.entity.FinancialAnalysisReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAnalysisReportRepository extends JpaRepository<FinancialAnalysisReport, String> {
    Optional<FinancialAnalysisReport> findFirstByProjectIdAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(Long projectId, String inputSnapshotId);
}
