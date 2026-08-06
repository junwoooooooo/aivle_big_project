package com.aivle.backend.journey.brief;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.journey.conversation.IdeaConversation;
import com.aivle.backend.journey.conversation.IdeaConversationRepository;
import com.aivle.backend.journey.foundation.FoundationProjectAccess;
import com.aivle.backend.journey.foundation.SnapshotHasher;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.journey.conversation.IdeaAttachmentRepository;
import com.aivle.backend.journey.conversation.IdeaMessageRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class OpportunityBriefService {
    private final FoundationProjectAccess projectAccess;
    private final IdeaConversationRepository conversations;
    private final OpportunityBriefVersionRepository versions;
    private final OpportunityFieldValueRepository fields;
    private final SnapshotHasher hasher;
    private final ObjectMapper mapper;
    private final IdeaMessageRepository messages;
    private final IdeaAttachmentRepository attachments;

    public OpportunityBriefService(FoundationProjectAccess projectAccess,
            IdeaConversationRepository conversations, OpportunityBriefVersionRepository versions,
            OpportunityFieldValueRepository fields, SnapshotHasher hasher, ObjectMapper mapper,
            IdeaMessageRepository messages, IdeaAttachmentRepository attachments) {
        this.projectAccess = projectAccess;
        this.conversations = conversations;
        this.versions = versions;
        this.fields = fields;
        this.hasher = hasher;
        this.mapper = mapper;
        this.messages = messages;
        this.attachments = attachments;
    }

    @Transactional
    public OpportunityBriefVersion createDraft(Long ownerId, Long projectId, Long conversationId,
            Long basedOnVersionId, String snapshotJson, List<FieldInput> fieldInputs) {
        return createDraft(ownerId, projectId, conversationId, basedOnVersionId, snapshotJson, fieldInputs, null);
    }

    @Transactional
    public OpportunityBriefVersion createDraft(Long ownerId, Long projectId, Long conversationId,
            Long basedOnVersionId, String snapshotJson, List<FieldInput> fieldInputs, TaskRun taskRun) {
        Project project = projectAccess.requireOwnedForUpdate(ownerId, projectId);
        IdeaConversation conversation = conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        OpportunityBriefVersion basedOn = basedOnVersionId == null ? null
            : versions.findByIdAndProjectIdAndDeletedAtIsNull(basedOnVersionId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        int nextVersion = versions.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .map(value -> value.getVersionNumber() + 1).orElse(1);
        String snapshotHash = hasher.hash(snapshotJson);
        OpportunityBriefVersion version = versions.save(OpportunityBriefVersion.draft(
            project, conversation, basedOn, nextVersion, snapshotJson, snapshotHash));
        version.linkTaskRun(taskRun);
        persistFields(version, fieldInputs);
        return version;
    }

    @Transactional
    public OpportunityBriefVersion confirm(Long ownerId, Long projectId, Long versionId) {
        projectAccess.requireOwnedForUpdate(ownerId, projectId);
        OpportunityBriefVersion version = versions.findByIdAndProjectIdAndDeletedAtIsNull(versionId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        version.confirm(LocalDateTime.now());
        return version;
    }

    @Transactional(readOnly = true)
    public OpportunityBriefVersion currentConfirmed(Long ownerId, Long projectId) {
        projectAccess.requireOwned(ownerId, projectId);
        return versions.findTopByProjectIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
            projectId, OpportunityBriefVersion.State.CONFIRMED).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<OpportunityFieldValue> fields(Long ownerId, Long projectId, Long versionId) {
        projectAccess.requireOwned(ownerId, projectId);
        versions.findByIdAndProjectIdAndDeletedAtIsNull(versionId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return fields.findByBriefVersionIdAndDeletedAtIsNullOrderByFieldKey(versionId);
    }

    private void persistFields(OpportunityBriefVersion version, List<FieldInput> inputs) {
        List<FieldInput> safeInputs = inputs == null ? List.of() : inputs;
        HashSet<String> keys = new HashSet<>();
        for (FieldInput input : safeInputs) {
            if (!keys.add(input.fieldKey())) throw new IllegalArgumentException("duplicate opportunity field key");
            if (input.valueJson() != null) mapper.readTree(input.valueJson());
            var sourceMessage = input.sourceMessageId() == null ? null : messages.findById(input.sourceMessageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            var sourceAttachment = input.sourceAttachmentId() == null ? null : attachments.findById(input.sourceAttachmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            fields.save(OpportunityFieldValue.create(version, input.fieldKey(), input.valueJson(),
                input.decisionStatus(), input.sourceType(), input.sourceReference(), sourceMessage, sourceAttachment,
                input.confidence(), input.userConfirmed(), input.confirmedAt()));
        }
    }

    public record FieldInput(String fieldKey, String valueJson, FieldDecisionStatus decisionStatus,
                             FieldSourceType sourceType, String sourceReference, Long sourceMessageId,
                             Long sourceAttachmentId, Double confidence, boolean userConfirmed,
                             LocalDateTime confirmedAt) {
        public FieldInput(String fieldKey, String valueJson, FieldDecisionStatus decisionStatus,
                FieldSourceType sourceType, String sourceReference) {
            this(fieldKey, valueJson, decisionStatus, sourceType, sourceReference,
                null, null, null, false, null);
        }
    }
}
