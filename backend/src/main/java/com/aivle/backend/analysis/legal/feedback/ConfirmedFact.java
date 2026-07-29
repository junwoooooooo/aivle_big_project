package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.document.entity.StructuredPlan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "confirmed_facts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmedFact extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_cycle_id", nullable = false) private ReviewCycle reviewCycle;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_in_plan_id", nullable = false) private StructuredPlan createdInPlan;
    @Column(name = "fact_key", nullable = false, length = 200) private String factKey;
    @Column(name = "fact_value", nullable = false, columnDefinition = "TEXT") private String factValue;
    @Column(length = 500) private String source;
    @Column(nullable = false) private LocalDateTime answeredAt;
    @Column(name = "from_question_id") private Long fromQuestionId;

    public static ConfirmedFact create(
        ReviewCycle cycle, StructuredPlan createdInPlan, String factKey, String factValue,
        String source, LocalDateTime answeredAt, Long fromQuestionId
    ) {
        ConfirmedFact fact = new ConfirmedFact();
        fact.reviewCycle = cycle;
        fact.createdInPlan = createdInPlan;
        fact.factKey = factKey;
        fact.factValue = factValue;
        fact.source = source;
        fact.answeredAt = answeredAt;
        fact.fromQuestionId = fromQuestionId;
        return fact;
    }
}
