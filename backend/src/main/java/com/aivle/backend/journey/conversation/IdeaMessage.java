package com.aivle.backend.journey.conversation;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaMessage extends BaseEntity {
    public enum Role { USER, ASSISTANT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id", nullable = false) private IdeaConversation conversation;
    @Column(nullable = false) private int sequenceNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(length = 20) private String schemaVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private IdeaMessageContract.Type messageType;
    @Column(columnDefinition = "TEXT") private String payloadJson;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id") private TaskRun taskRun;

    public static IdeaMessage create(IdeaConversation conversation, int sequenceNumber, Role role, String content) {
        if (conversation.getStatus() != IdeaConversation.Status.ACTIVE) {
            throw new IllegalStateException("messages require an active conversation");
        }
        if (sequenceNumber <= 0) throw new IllegalArgumentException("message sequence must be positive");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("message content is required");
        IdeaMessage value = new IdeaMessage();
        value.project = conversation.getProject();
        value.conversation = conversation;
        value.sequenceNumber = sequenceNumber;
        value.role = role;
        value.content = content;
        value.messageType = IdeaMessageContract.Type.TEXT;
        return value;
    }

    public static IdeaMessage assistant(IdeaConversation conversation, int sequenceNumber,
            IdeaMessageContract.Envelope envelope, String payloadJson, String displayText, TaskRun taskRun) {
        IdeaMessage value = create(conversation, sequenceNumber, Role.ASSISTANT,
            displayText == null || displayText.isBlank() ? "메시지를 표시할 수 없습니다." : displayText);
        value.schemaVersion = envelope.schemaVersion();
        value.messageType = envelope.messageType();
        value.payloadJson = payloadJson;
        value.taskRun = taskRun;
        return value;
    }
}
