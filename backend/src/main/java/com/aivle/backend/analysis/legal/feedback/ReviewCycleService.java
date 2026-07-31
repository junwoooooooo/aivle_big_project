package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.analysis.legal.entity.LegalQuestionStatus;
import com.aivle.backend.analysis.legal.entity.LegalReview;
import com.aivle.backend.analysis.legal.repository.LegalReviewQuestionRepository;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewCycleService {
    private final ReviewCycleRepository cycles;
    private final RevisionRequestRepository revisionRequests;
    private final LegalReviewQuestionRepository questions;

    /** 프로젝트당 활성(미발행) 사이클은 최대 1개. 없으면 생성하고, 계획이 바뀌었으면 이동한다. */
    public ReviewCycle ensureActiveCycle(Project project, StructuredPlan plan) {
        return cycles.findTopByProjectIdAndStatusNotAndDeletedAtIsNullOrderByIdDesc(
                project.getId(), ReviewCycleStatus.PUBLISHED)
            .map(cycle -> {
                if (!cycle.getCurrentPlan().getId().equals(plan.getId())) {
                    cycle.moveCurrentPlan(plan);
                }
                return cycle;
            })
            .orElseGet(() -> cycles.save(ReviewCycle.start(project, plan)));
    }

    /**
     * 수렴 판정: OPEN 수정 요청 0 && 미답변 질문 0 → CONVERGED.
     * 할 일(체크리스트) 완료 여부는 조건에 포함하지 않는다 (§2).
     *
     * @return true면 CONVERGED로 전이됨
     */
    public boolean recomputeState(ReviewCycle cycle, LegalReview latestReview) {
        long openRequests = revisionRequests
            .countByReviewCycleIdAndStatusAndResolvedInVersionIsNullAndDeletedAtIsNull(
                cycle.getId(), RevisionRequestStatus.OPEN);
        long openQuestions = questions
            .countByLegalReviewIdAndStatusAndResolvedInVersionIsNullAndDeletedAtIsNull(
                latestReview.getId(), LegalQuestionStatus.OPEN);
        boolean converged = openRequests == 0 && openQuestions == 0;
        cycle.settle(converged, latestReview.getId());
        return converged;
    }
}
