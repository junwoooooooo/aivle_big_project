package com.aivle.backend.journey.conversation;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.journey.IdeaSource;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaConversation extends BaseEntity {
    public enum Status { ACTIVE, CLOSED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_id") private IdeaSource source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    private LocalDateTime closedAt;

    public static IdeaConversation active(Project project, IdeaSource source) {
        IdeaConversation value = new IdeaConversation();
        value.project = project;
        value.source = source;
        value.status = Status.ACTIVE;
        return value;
    }

    public void close(LocalDateTime now) {
        if (status != Status.ACTIVE) throw new IllegalStateException("conversation is not active");
        status = Status.CLOSED;
        closedAt = now;
    }
}
