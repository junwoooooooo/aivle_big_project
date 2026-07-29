package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.analysis.legal.entity.LegalReview;
import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI가 제안한 기획서 수정 요청. 행은 절대 삭제하지 않는다 —
 * 해결은 resolvedInVersion 기록으로만 표현한다 (이력이 신뢰의 근거).
 */
@Entity
@Table(name = "revision_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionRequest extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_cycle_id", nullable = false) private ReviewCycle reviewCycle;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raised_in_review_id", nullable = false) private LegalReview raisedInReview;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 100) private LegalCategory category;
    @Enumerated(EnumType.STRING) @Column(name = "anchor_section_code", nullable = false, length = 80)
    private BusinessPlanSectionCode anchorSectionCode;
    @Column(name = "anchor_quote", nullable = false, columnDefinition = "TEXT") private String anchorQuote;
    @Column(columnDefinition = "TEXT") private String rationale;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RevisionRequestStatus status;
    @Column(name = "accepted_suggestion_id") private Long acceptedSuggestionId;
    private Integer resolvedInVersion;

    public static RevisionRequest open(
        ReviewCycle cycle, LegalReview review, LegalCategory category,
        BusinessPlanSectionCode anchorSectionCode, String anchorQuote, String rationale
    ) {
        RevisionRequest request = new RevisionRequest();
        request.reviewCycle = cycle;
        request.raisedInReview = review;
        request.category = category;
        request.anchorSectionCode = anchorSectionCode;
        request.anchorQuote = anchorQuote;
        request.rationale = rationale;
        request.status = RevisionRequestStatus.OPEN;
        return request;
    }

    public boolean isPending() {
        return status == RevisionRequestStatus.OPEN && resolvedInVersion == null;
    }

    public void accept(Long suggestionId) {
        if (status != RevisionRequestStatus.OPEN) {
            throw new IllegalStateException("only open revision requests can be accepted");
        }
        this.status = RevisionRequestStatus.ACCEPTED;
        this.acceptedSuggestionId = suggestionId;
    }

    public void dismiss() {
        if (status != RevisionRequestStatus.OPEN) {
            throw new IllegalStateException("only open revision requests can be dismissed");
        }
        this.status = RevisionRequestStatus.DISMISSED;
    }

    /** 재검토 결과 같은 키의 요청이 더는 방출되지 않을 때 — 삭제 대신 해결 기록. */
    public void markResolved(int planVersionNumber) {
        this.resolvedInVersion = planVersionNumber;
    }

    /** 재검토가 같은 키의 요청을 다시 방출했을 때(OPEN 한정) 내용을 최신으로 갱신. */
    public void refresh(String anchorQuote, String rationale) {
        if (status != RevisionRequestStatus.OPEN) {
            throw new IllegalStateException("only open revision requests can be refreshed");
        }
        this.anchorQuote = anchorQuote;
        this.rationale = rationale;
    }
}
