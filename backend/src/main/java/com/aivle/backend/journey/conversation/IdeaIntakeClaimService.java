package com.aivle.backend.journey.conversation;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdeaIntakeClaimService {
    private final TaskRunService tasks;
    private final IdeaMessageRepository messages;
    private final IdeaAttachmentRepository attachments;
    private final ObjectMapper mapper;

    public IdeaIntakeClaimService(TaskRunService tasks, IdeaMessageRepository messages,
            IdeaAttachmentRepository attachments, ObjectMapper mapper) {
        this.tasks = tasks;
        this.messages = messages;
        this.attachments = attachments;
        this.mapper = mapper;
    }

    @Transactional
    public ClaimContext claimNext(TaskType type, String workerId, Duration lease, Duration timeout) {
        TaskRunService.Claim claim = tasks.claimNext(type, workerId, lease, timeout);
        return claim == null ? null : capture(claim);
    }

    @Transactional(readOnly = true)
    public ClaimContext capture(TaskRunService.Claim claim) {
        TaskRunWorkerContext task = tasks.workerContext(claim.taskRunId());
        if (task.taskType() == TaskType.IDEA_CONVERSATION_TURN) {
            Long sourceMessageId = parsePositive(task.subjectId(), "source message");
            IdeaMessage source = messages.findById(sourceMessageId).orElseThrow(
                () -> new IllegalArgumentException("source message is missing"));
            Long messageProjectId = source.getProject().getId();
            Long conversationId = source.getConversation().getId();
            if (!task.projectId().equals(messageProjectId)
                    || !task.projectId().equals(source.getConversation().getProject().getId())) {
                throw new IllegalArgumentException("conversation task project mismatch");
            }
            return new ClaimContext(claim, task, conversationId, sourceMessageId,
                null, null, null, null);
        }
        if (task.taskType() != TaskType.IDEA_ATTACHMENT_PARSE) {
            throw new IllegalArgumentException("unsupported idea intake task type");
        }
        JsonNode input = mapper.readTree(task.inputSnapshot());
        Long attachmentId = positive(input, "attachmentId");
        Long conversationId = positive(input, "conversationId");
        IdeaAttachment attachment = attachments
            .findByIdAndProjectIdAndConversationIdAndDeletedAtIsNull(
                attachmentId, task.projectId(), conversationId)
            .orElseThrow(() -> new IllegalArgumentException("attachment task scope mismatch"));
        boolean alreadyExtracted = attachment.getStatus() == IdeaAttachment.Status.EXTRACTED
            && input.path("contentChecksum").asText().equals(
                attachment.getStoredFile().getChecksumSha256());
        return new ClaimContext(claim, task, conversationId, null, attachmentId,
            alreadyExtracted, attachment.getExtractedTextHash(), input.path("contentChecksum").asText());
    }

    private Long positive(JsonNode root, String field) {
        if (!root.path(field).canConvertToLong() || root.path(field).asLong() <= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return root.path(field).asLong();
    }

    private Long parsePositive(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    public record ClaimContext(
        TaskRunService.Claim claim,
        TaskRunWorkerContext task,
        Long conversationId,
        Long sourceMessageId,
        Long attachmentId,
        Boolean attachmentAlreadyExtracted,
        String extractedTextHash,
        String contentChecksum) {
    }
}
