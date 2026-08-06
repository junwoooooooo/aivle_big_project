package com.aivle.backend.analysis.feasibility.repository;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityDimensionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeasibilityDimensionResultRepository
    extends JpaRepository<FeasibilityDimensionResult, Long> {
    List<FeasibilityDimensionResult>
        findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(Long assessmentId);
}
