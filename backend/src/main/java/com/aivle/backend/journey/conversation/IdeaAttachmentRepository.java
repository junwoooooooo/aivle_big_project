package com.aivle.backend.journey.conversation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaAttachmentRepository extends JpaRepository<IdeaAttachment, Long> {
    List<IdeaAttachment> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long conversationId);
    java.util.Optional<IdeaAttachment> findByIdAndProjectIdAndConversationIdAndDeletedAtIsNull(
        Long id, Long projectId, Long conversationId);
}
