package com.aivle.backend.launchreadiness.repository;

import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalAnalysisReportRepository extends JpaRepository<ProfessionalAnalysisReport, String> {
    Optional<ProfessionalAnalysisReport> findFirstByProjectIdAndModuleTypeAndDeletedAtIsNullOrderByCompletedAtDesc(Long projectId, ProfessionalAnalysisReport.ModuleType moduleType);
}
