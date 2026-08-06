package com.aivle.backend.pipeline.idea.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaQuestion extends BaseEntity {
    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false)
    private IdeaBrief brief;

    @Column(length = 80)
    private String targetFieldKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdeaQuestionType questionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean answered;

    public static IdeaQuestion create(
        IdeaBrief brief,
        String targetFieldKey,
        IdeaQuestionType type,
        String prompt,
        String optionsJson,
        int displayOrder
    ) {
        brief.requireMutable();
        if (prompt == null || prompt.isBlank() || displayOrder < 0) {
            throw new IllegalArgumentException("question is invalid");
        }
        IdeaQuestion question = new IdeaQuestion();
        question.id = UUID.randomUUID().toString();
        question.brief = brief;
        question.targetFieldKey = targetFieldKey;
        question.questionType = type;
        question.prompt = prompt;
        question.optionsJson = optionsJson;
        question.displayOrder = displayOrder;
        return question;
    }

    public void markAnswered() {
        brief.requireMutable();
        this.answered = true;
    }
}
