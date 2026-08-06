package com.aivle.backend.analysis.legal.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public static LegalReviewQuestion open(LegalReview review, int order, String question, String reason) {
        LegalReviewQuestion value = new LegalReviewQuestion();
        value.legalReview = review;
        value.displayOrder = order;
        value.question = question;
        value.reason = reason;
        value.status = LegalQuestionStatus.OPEN;
        return value;
    }
}
