package com.aivle.backend.journey.conversation;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaAttachment extends BaseEntity {
    public enum Status { UPLOADED, PROCESSING, EXTRACTED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id", nullable = false) private IdeaConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "message_id") private IdeaMessage message;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "stored_file_id", nullable = false) private StoredFile storedFile;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 80) private String failureCode;
    @Column(length = 71) private String extractedTextHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;

    public static IdeaAttachment uploaded(IdeaConversation conversation, IdeaMessage message, StoredFile storedFile) {
        if (message != null && !message.getConversation().getId().equals(conversation.getId())) {
            throw new IllegalArgumentException("attachment message must belong to conversation");
        }
        IdeaAttachment value = new IdeaAttachment();
        value.project = conversation.getProject();
        value.conversation = conversation;
        value.message = message;
        value.storedFile = storedFile;
        value.status = Status.UPLOADED;
        return value;
    }

    public void startProcessing() {
        requireStatus(Status.UPLOADED);
        status = Status.PROCESSING;
    }

    public void linkTaskRun(TaskRun run) {
        if (run == null || !run.getProject().getId().equals(project.getId())) throw new IllegalArgumentException("task project mismatch");
        if (taskRun != null && !taskRun.getId().equals(run.getId())) throw new IllegalStateException("attachment task link is immutable");
        taskRun = run;
    }

    public void extracted(String textHash) {
        requireStatus(Status.PROCESSING);
        if (textHash == null || !textHash.startsWith("sha256:")) {
            throw new IllegalArgumentException("canonical extracted text hash is required");
        }
        status = Status.EXTRACTED;
        extractedTextHash = textHash;
        failureCode = null;
    }

    public void fail(String code) {
        if (status != Status.UPLOADED && status != Status.PROCESSING) {
            throw new IllegalStateException("attachment cannot fail from current state");
        }
        if (code == null || code.isBlank()) throw new IllegalArgumentException("failure code is required");
        status = Status.FAILED;
        failureCode = code;
    }

    private void requireStatus(Status expected) {
        if (status != expected) throw new IllegalStateException("invalid attachment state transition");
    }
}
