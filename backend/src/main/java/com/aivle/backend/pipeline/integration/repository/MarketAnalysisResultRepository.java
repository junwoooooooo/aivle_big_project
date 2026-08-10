package com.aivle.backend.pipeline.integration.repository;

import com.aivle.backend.pipeline.integration.domain.MarketAnalysisResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketAnalysisResultRepository extends JpaRepository<MarketAnalysisResult, String> {
    Optional<MarketAnalysisResult> findFirstByProjectIdAndDeletedAtIsNullOrderByCompletedAtDesc(Long projectId);
}
