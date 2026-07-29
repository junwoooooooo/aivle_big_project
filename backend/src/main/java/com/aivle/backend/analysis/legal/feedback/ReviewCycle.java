package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "review_cycles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCycle extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_plan_id", nullable = false) private StructuredPlan currentPlan;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReviewCycleStatus status;
    @Column(name = "latest_review_id") private Long latestReviewId;

    public static ReviewCycle start(Project project, StructuredPlan plan) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.project = project;
        cycle.currentPlan = plan;
        cycle.status = ReviewCycleStatus.DRAFT;
        return cycle;
    }

    public void beginReview(Long reviewIdOrNull) {
        this.status = ReviewCycleStatus.REVIEWING;
        if (reviewIdOrNull != null) {
            this.latestReviewId = reviewIdOrNull;
        }
    }

    public void moveCurrentPlan(StructuredPlan newPlan) {
        if (status == ReviewCycleStatus.PUBLISHED) {
            throw new IllegalStateException("published cycles are immutable; start a new cycle");
        }
        this.currentPlan = newPlan;
        // 수렴 후의 편집·답변은 수렴 판정을 무효화한다
        if (status == ReviewCycleStatus.CONVERGED) {
            this.status = ReviewCycleStatus.NEEDS_ACTION;
        }
    }

    public void settle(boolean converged, Long latestReviewId) {
        if (status == ReviewCycleStatus.PUBLISHED) {
            throw new IllegalStateException("published cycles cannot be re-settled");
        }
        this.latestReviewId = latestReviewId;
        this.status = converged ? ReviewCycleStatus.CONVERGED : ReviewCycleStatus.NEEDS_ACTION;
    }

    public void publish() {
        if (status != ReviewCycleStatus.CONVERGED) {
            throw new IllegalStateException("only converged cycles can be published");
        }
        this.status = ReviewCycleStatus.PUBLISHED;
    }
}
