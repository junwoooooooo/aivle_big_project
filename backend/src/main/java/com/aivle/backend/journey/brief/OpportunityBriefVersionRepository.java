package com.aivle.backend.journey.brief;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityBriefVersionRepository extends JpaRepository<OpportunityBriefVersion, Long> {
    @EntityGraph(attributePaths = {"project", "conversation", "basedOnVersion"})
    Optional<OpportunityBriefVersion> findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);

    @EntityGraph(attributePaths = {"project", "conversation", "basedOnVersion"})
    Optional<OpportunityBriefVersion> findTopByProjectIdAndConversationIdAndDeletedAtIsNullOrderByVersionNumberDesc(
        Long projectId, Long conversationId);

    @EntityGraph(attributePaths = {"project", "conversation", "basedOnVersion"})
    Optional<OpportunityBriefVersion> findTopByProjectIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
        Long projectId, OpportunityBriefVersion.State state);

    @EntityGraph(attributePaths = {"project", "conversation", "basedOnVersion"})
    Optional<OpportunityBriefVersion> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
    Optional<OpportunityBriefVersion> findByTaskRunIdAndDeletedAtIsNull(String taskRunId);
}
