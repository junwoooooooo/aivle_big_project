package com.aivle.backend.pipeline.planning.repository;
import com.aivle.backend.pipeline.planning.domain.PlanningChangeDecision;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanningChangeDecisionRepository extends JpaRepository<PlanningChangeDecision, Long> {
    Optional<PlanningChangeDecision> findByProposalIdAndProjectIdAndDeletedAtIsNull(String proposalId, Long projectId);
    List<PlanningChangeDecision> findAllByProjectIdAndDeletedAtIsNullOrderByDecidedAtAsc(Long projectId);
}
