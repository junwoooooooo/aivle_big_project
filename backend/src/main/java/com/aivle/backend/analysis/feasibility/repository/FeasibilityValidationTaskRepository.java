package com.aivle.backend.analysis.feasibility.repository;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityValidationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeasibilityValidationTaskRepository
    extends JpaRepository<FeasibilityValidationTask, Long> {
    List<FeasibilityValidationTask>
        findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(Long assessmentId);
}
