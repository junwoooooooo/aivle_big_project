package com.aivle.backend.analysis.financial.repository;

import com.aivle.backend.analysis.financial.entity.FinancialAnalysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAnalysisRepository extends JpaRepository<FinancialAnalysis, Long> {
    List<FinancialAnalysis> findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long projectId);
    Optional<FinancialAnalysis> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
    Optional<FinancialAnalysis> findTopByProjectIdAndDeletedAtIsNullAndStatusOrderByCompletedAtDescIdDesc(
        Long projectId, com.aivle.backend.analysis.financial.entity.FinancialStatus status);
}
