package com.aivle.backend.journey.conversation;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdeaAttachmentStateService {
    private final IdeaAttachmentRepository attachments;
    private final JobEventPublisher events;

    public IdeaAttachmentStateService(IdeaAttachmentRepository attachments, JobEventPublisher events) {
        this.attachments = attachments; this.events = events;
    }

    @Transactional
    public ProcessingInput start(Long projectId, Long conversationId, Long attachmentId, String jobId) {
        IdeaAttachment attachment = require(projectId, conversationId, attachmentId);
        if (attachment.getStatus() == IdeaAttachment.Status.UPLOADED) {
            attachment.startProcessing();
            events.publish(command(projectId, jobId, "ATTACHMENT_PARSING", "job.idea.attachment.parsing.started",
                JobEvent.Status.RUNNING, "job.idea.attachment.parsing.started", null));
        } else if (attachment.getStatus() != IdeaAttachment.Status.PROCESSING) {
            throw new IllegalStateException("attachment is not processable");
        }
        var file = attachment.getStoredFile();
        return new ProcessingInput(file.getStorageKey(), file.getOriginalFilename(), file.getExtension(),
            file.getMimeType(), file.getSizeBytes());
    }

    @Transactional
    public void complete(Long projectId, Long conversationId, Long attachmentId, String jobId, String hash) {
        IdeaAttachment attachment = require(projectId, conversationId, attachmentId);
        events.publish(command(projectId, jobId, "INFORMATION_EXTRACTION",
            "job.idea.information.extraction.started", JobEvent.Status.RUNNING,
            "job.idea.information.extraction.started", null));
        attachment.extracted(hash);
    }

    @Transactional
    public void fail(Long projectId, Long conversationId, Long attachmentId, String jobId, String code) {
        IdeaAttachment attachment = require(projectId, conversationId, attachmentId);
        if (attachment.getStatus() == IdeaAttachment.Status.UPLOADED
                || attachment.getStatus() == IdeaAttachment.Status.PROCESSING) attachment.fail(code);
        if (attachment.getStatus() == IdeaAttachment.Status.FAILED) events.publish(command(projectId, jobId,
            "ATTACHMENT_PARSING", "job.idea.attachment.parsing.failed", JobEvent.Status.FAILED,
            "job.idea.attachment.parsing.failed", code));
    }

    private IdeaAttachment require(Long projectId, Long conversationId, Long attachmentId) {
        return attachments.findByIdAndProjectIdAndConversationIdAndDeletedAtIsNull(attachmentId, projectId, conversationId)
            .orElseThrow();
    }
    private JobEventPublisher.Command command(Long projectId, String jobId, String stage, String type,
            JobEvent.Status status, String key, String code) {
        return new JobEventPublisher.Command(projectId, jobId, null, stage, type, status, key, Map.of(), code);
    }
    public record ProcessingInput(String storageKey, String originalFilename, String extension,
                                  String mimeType, long sizeBytes) { }
}
