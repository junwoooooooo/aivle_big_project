package com.aivle.backend.pipeline.integration.repository;

import com.aivle.backend.pipeline.integration.domain.PlanningChangeProposal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningChangeProposalRepository extends JpaRepository<PlanningChangeProposal, String> {
    List<PlanningChangeProposal> findAllByModuleRunIdAndDeletedAtIsNullOrderByCreatedAtAsc(String moduleRunId);
    Optional<PlanningChangeProposal> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
}
