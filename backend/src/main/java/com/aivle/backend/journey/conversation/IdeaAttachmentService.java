package com.aivle.backend.journey.conversation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.file.storage.FileStorage;
import com.aivle.backend.journey.foundation.FoundationProjectAccess;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdeaAttachmentService {
    public static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final FoundationProjectAccess projectAccess;
    private final IdeaConversationRepository conversations;
    private final IdeaMessageRepository messages;
    private final IdeaAttachmentRepository attachments;
    private final StoredFileRepository storedFiles;
    private final FileStorage storage;
    private final JobEventPublisher events;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final ObjectMapper mapper;

    public IdeaAttachmentService(FoundationProjectAccess projectAccess, IdeaConversationRepository conversations,
            IdeaMessageRepository messages, IdeaAttachmentRepository attachments, StoredFileRepository storedFiles,
            FileStorage storage, JobEventPublisher events, TaskRunService taskRuns,
            CanonicalInputHasher hasher, ObjectMapper mapper) {
        this.projectAccess = projectAccess; this.conversations = conversations; this.messages = messages;
        this.attachments = attachments; this.storedFiles = storedFiles; this.storage = storage;
        this.events = events; this.taskRuns = taskRuns; this.hasher = hasher; this.mapper = mapper;
    }

    @Transactional
    public AttachmentView upload(Long ownerId, Long projectId, Long conversationId, Long messageId,
            MultipartFile file) {
        projectAccess.requireOwned(ownerId, projectId);
        IdeaConversation conversation = conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.FILE_REQUIRED);
        String original = safeName(file.getOriginalFilename());
        String extension = extension(original);
        validate(extension, file.getContentType(), file.getSize());
        IdeaMessage message = messageId == null ? null : messages.findById(messageId)
            .filter(value -> value.getConversation().getId().equals(conversationId))
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String storageKey = "idea-conversations/" + projectId + "/" + UUID.randomUUID() + "." + extension;
        try {
            var stored = storage.store(file.getInputStream(), file.getSize(), extension, storageKey);
            StoredFile metadata = storedFiles.save(StoredFile.available(stored.storageKey(), original,
                stored.storedFilename(), extension, mime(extension), stored.sizeBytes(), stored.checksumSha256()));
            IdeaAttachment attachment = attachments.save(IdeaAttachment.uploaded(conversation, message, metadata));
            var inputNode = mapper.createObjectNode();
            inputNode.put("attachmentId", attachment.getId()); inputNode.put("conversationId", conversationId);
            inputNode.put("contentChecksum", metadata.getChecksumSha256());
            String input = mapper.writeValueAsString(inputNode);
            String key = "idea-attachment-parse:" + attachment.getId() + ":" + metadata.getChecksumSha256();
            TaskRun run = taskRuns.create(ownerId, projectId, TaskType.IDEA_ATTACHMENT_PARSE,
                "IDEA_ATTACHMENT", attachment.getId().toString(), input,
                hasher.hash(TaskType.IDEA_ATTACHMENT_PARSE, "1.0", "ko-KR", input), key, key, 3);
            attachment.linkTaskRun(run);
            String jobId = run.getId();
            events.publish(new JobEventPublisher.Command(projectId, jobId, jobId, "ATTACHMENT_UPLOAD",
                "job.idea.attachment.received", JobEvent.Status.QUEUED, "job.idea.attachment.received",
                Map.of("attachmentId", attachment.getId(), "fileType", extension), null));
            return view(attachment, jobId);
        } catch (IOException failure) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> list(Long ownerId, Long projectId, Long conversationId) {
        projectAccess.requireOwned(ownerId, projectId);
        conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return attachments.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(conversationId)
            .stream().map(value -> view(value, value.getTaskRun() == null ? null : value.getTaskRun().getId())).toList();
    }

    private AttachmentView view(IdeaAttachment value, String jobId) {
        return new AttachmentView(value.getId(), value.getMessage() == null ? null : value.getMessage().getId(),
            value.getStoredFile().getOriginalFilename(), value.getStoredFile().getExtension(),
            value.getStoredFile().getSizeBytes(), value.getStatus().name(), value.getFailureCode(),
            value.getExtractedTextHash(), jobId, value.getCreatedAt());
    }
    private String safeName(String value) {
        String name = value == null ? "attachment" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255) throw new IllegalArgumentException("invalid attachment name");
        return name;
    }
    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private void validate(String extension, String contentType, long size) {
        if (!supports(extension, contentType)) throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        if (size <= 0 || size > MAX_BYTES) throw new IllegalArgumentException("attachment size is invalid");
    }
    public static boolean supports(String extension, String contentType) {
        if (!List.of("txt", "docx").contains(extension)) return false;
        String declared = contentType == null ? "" : contentType;
        if ("docx".equals(extension)) return declared.isBlank() || DOCX_MIME.equalsIgnoreCase(declared);
        return declared.isBlank() || declared.toLowerCase(Locale.ROOT).startsWith("text/plain");
    }
    private String mime(String extension) { return "docx".equals(extension) ? DOCX_MIME : "text/plain"; }
    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { task.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { task.run(); }
        });
    }

    public record AttachmentView(Long id, Long messageId, String filename, String fileType, long sizeBytes,
            String status, String failureCode, String extractedTextHash, String jobId,
            java.time.LocalDateTime createdAt) { }
}
