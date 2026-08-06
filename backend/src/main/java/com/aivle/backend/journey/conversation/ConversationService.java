package com.aivle.backend.journey.conversation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.journey.IdeaSource;
import com.aivle.backend.journey.IdeaSourceRepository;
import com.aivle.backend.journey.foundation.FoundationProjectAccess;
import com.aivle.backend.project.entity.Project;
import java.time.LocalDateTime;
import java.util.List;
import com.aivle.backend.taskrun.domain.TaskRun;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    private final FoundationProjectAccess projectAccess;
    private final IdeaSourceRepository sources;
    private final IdeaConversationRepository conversations;
    private final IdeaMessageRepository messages;
    private final ObjectMapper mapper;

    public ConversationService(FoundationProjectAccess projectAccess, IdeaSourceRepository sources,
            IdeaConversationRepository conversations, IdeaMessageRepository messages, ObjectMapper mapper) {
        this.projectAccess = projectAccess;
        this.sources = sources;
        this.conversations = conversations;
        this.messages = messages;
        this.mapper = mapper;
    }

    @Transactional
    public IdeaConversation create(Long ownerId, Long projectId, Long sourceId) {
        Project project = projectAccess.requireOwned(ownerId, projectId);
        IdeaSource source = sourceId == null ? null : sources.findByIdAndProjectIdAndDeletedAtIsNull(sourceId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return conversations.save(IdeaConversation.active(project, source));
    }

    @Transactional(readOnly = true)
    public IdeaConversation current(Long ownerId, Long projectId) {
        projectAccess.requireOwned(ownerId, projectId);
        return conversations.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
    }

    @Transactional
    public IdeaMessage appendMessage(Long ownerId, Long projectId, Long conversationId,
            IdeaMessage.Role role, String content) {
        projectAccess.requireOwnedForUpdate(ownerId, projectId);
        IdeaConversation conversation = conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        int nextSequence = messages.findTopByConversationIdAndDeletedAtIsNullOrderBySequenceNumberDesc(conversationId)
            .map(value -> value.getSequenceNumber() + 1).orElse(1);
        if (role == IdeaMessage.Role.USER) return messages.save(IdeaMessage.create(conversation, nextSequence, role, content));
        IdeaMessageContract.Envelope envelope = IdeaMessageContract.assistant(mapper,
            IdeaMessageContract.Type.TEXT, content, List.of(), List.of(), null);
        return messages.save(IdeaMessage.assistant(conversation, nextSequence, envelope,
            IdeaMessageContract.serializePayload(mapper, envelope), content, null));
    }

    @Transactional
    public IdeaMessage appendAssistant(Long ownerId, Long projectId, Long conversationId,
            IdeaMessageContract.Envelope envelope, TaskRun taskRun) {
        projectAccess.requireOwnedForUpdate(ownerId, projectId);
        IdeaConversation conversation = conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (taskRun != null && !taskRun.getProject().getId().equals(projectId)) throw new IllegalArgumentException("task project mismatch");
        int nextSequence = messages.findTopByConversationIdAndDeletedAtIsNullOrderBySequenceNumberDesc(conversationId)
            .map(value -> value.getSequenceNumber() + 1).orElse(1);
        String display = envelope.payload().path("text").asText("메시지를 표시할 수 없습니다.");
        return messages.save(IdeaMessage.assistant(conversation, nextSequence, envelope,
            IdeaMessageContract.serializePayload(mapper, envelope), display, taskRun));
    }

    @Transactional(readOnly = true)
    public List<IdeaMessage> messages(Long ownerId, Long projectId, Long conversationId) {
        projectAccess.requireOwned(ownerId, projectId);
        conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return messages.findByConversationIdAndDeletedAtIsNullOrderBySequenceNumber(conversationId);
    }

    @Transactional
    public IdeaConversation close(Long ownerId, Long projectId, Long conversationId) {
        projectAccess.requireOwnedForUpdate(ownerId, projectId);
        IdeaConversation conversation = conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        conversation.close(LocalDateTime.now());
        return conversation;
    }
}
