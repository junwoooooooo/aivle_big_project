package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_clarification_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaClarificationQuestion extends BaseEntity {
    public enum Requirement { REQUIRED_FOR_IDEA_ORIGIN, REQUIRED_FOR_LEGAL_PRECHECK }
    public enum Status { MISSING, USER_CONFIRMED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "origin_draft_version_id", nullable = false) private IdeaOriginVersion originDraftVersion;
    @Column(nullable = false, length = 160) private String targetField;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Requirement requirement;
    @Column(nullable = false, columnDefinition = "TEXT") private String question;
    @Column(nullable = false, columnDefinition = "TEXT") private String reason;
    @Column(columnDefinition = "TEXT") private String answer;
    @Column(length = 300) private String answerSource;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    private LocalDateTime answeredAt;

    public static IdeaClarificationQuestion create(Project project, IdeaOriginVersion draft, String targetField,
            Requirement requirement, String question, String reason) {
        IdeaClarificationQuestion value = new IdeaClarificationQuestion();
        value.project = project; value.originDraftVersion = draft; value.targetField = targetField;
        value.requirement = requirement; value.question = question; value.reason = reason;
        value.status = Status.MISSING;
        return value;
    }

    public void answer(String answer, String answerSource) {
        this.answer = answer; this.answerSource = answerSource; this.status = Status.USER_CONFIRMED;
        this.answeredAt = LocalDateTime.now();
    }
}
