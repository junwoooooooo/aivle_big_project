package com.aivle.backend.pipeline.idea.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaAnswer extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false)
    private IdeaBrief brief;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private IdeaQuestion question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerJson;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    public static IdeaAnswer create(
        IdeaBrief brief,
        IdeaQuestion question,
        String answerJson,
        String idempotencyKey
    ) {
        brief.requireMutable();
        if (!question.getBrief().getId().equals(brief.getId())) {
            throw new IllegalArgumentException("question does not belong to idea brief");
        }
        if (answerJson == null || answerJson.isBlank()) {
            throw new IllegalArgumentException("answer is required");
        }
        IdeaAnswer answer = new IdeaAnswer();
        answer.brief = brief;
        answer.question = question;
        answer.answerJson = answerJson;
        answer.idempotencyKey = idempotencyKey;
        return answer;
    }
}
