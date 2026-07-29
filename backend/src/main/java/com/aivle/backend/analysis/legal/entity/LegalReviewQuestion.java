package com.aivle.backend.analysis.legal.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "legal_review_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalReviewQuestion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_review_id", nullable = false) private LegalReview legalReview;
    @Column(nullable = false) private Integer displayOrder;
    @Column(nullable = false, columnDefinition = "TEXT") private String question;
    @Column(columnDefinition = "TEXT") private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LegalQuestionStatus status;
    @Column(columnDefinition = "TEXT") private String answerText;
    private LocalDateTime answeredAt;
    @Column(name = "confirmed_fact_id") private Long confirmedFactId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carried_from_question_id") private LegalReviewQuestion carriedFromQuestion;
    @Column(columnDefinition = "TEXT") private String categoriesJson;
    private Integer resolvedInVersion;

    public static LegalReviewQuestion open(LegalReview review, int order, String question, String reason) {
        return open(review, order, question, reason, null);
    }

    public static LegalReviewQuestion open(
        LegalReview review, int order, String question, String reason, String categoriesJson
    ) {
        LegalReviewQuestion value = new LegalReviewQuestion();
        value.legalReview = review;
        value.displayOrder = order;
        value.question = question;
        value.reason = reason;
        value.status = LegalQuestionStatus.OPEN;
        value.categoriesJson = categoriesJson;
        return value;
    }

    /** 증분 재검토에서 승계 범주의 미답변 질문을 새 리뷰로 복사한다. */
    public static LegalReviewQuestion carriedFrom(LegalReviewQuestion parent, LegalReview newReview, int order) {
        LegalReviewQuestion value = new LegalReviewQuestion();
        value.legalReview = newReview;
        value.displayOrder = order;
        value.question = parent.question;
        value.reason = parent.reason;
        value.status = parent.status;
        value.categoriesJson = parent.categoriesJson;
        value.carriedFromQuestion = parent;
        return value;
    }

    public void answer(String answerText, Long confirmedFactId, LocalDateTime now) {
        if (status != LegalQuestionStatus.OPEN) {
            throw new IllegalStateException("only open questions can be answered");
        }
        this.status = LegalQuestionStatus.ANSWERED;
        this.answerText = answerText;
        this.confirmedFactId = confirmedFactId;
        this.answeredAt = now;
    }

    public void markResolved(int planVersionNumber) {
        this.resolvedInVersion = planVersionNumber;
    }
}
