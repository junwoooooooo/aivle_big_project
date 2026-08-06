package com.aivle.backend.journey.conversation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaMessageRepository extends JpaRepository<IdeaMessage, Long> {
    List<IdeaMessage> findByConversationIdAndDeletedAtIsNullOrderBySequenceNumber(Long conversationId);
    Optional<IdeaMessage> findTopByConversationIdAndDeletedAtIsNullOrderBySequenceNumberDesc(Long conversationId);
    Optional<IdeaMessage> findByTaskRunIdAndDeletedAtIsNull(String taskRunId);
}
