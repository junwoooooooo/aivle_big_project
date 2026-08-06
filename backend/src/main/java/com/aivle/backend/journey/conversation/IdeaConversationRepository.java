package com.aivle.backend.journey.conversation;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaConversationRepository extends JpaRepository<IdeaConversation, Long> {
    @EntityGraph(attributePaths = {"project", "source"})
    Optional<IdeaConversation> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);

    @EntityGraph(attributePaths = {"project", "source"})
    Optional<IdeaConversation> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
}
