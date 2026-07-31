package com.aivle.backend.analysis.feasibility.repository;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityGroupResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeasibilityGroupResultRepository
    extends JpaRepository<FeasibilityGroupResult, Long> {
    List<FeasibilityGroupResult>
        findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(Long assessmentId);
}
