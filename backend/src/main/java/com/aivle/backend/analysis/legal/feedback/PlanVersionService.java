package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.analysis.legal.entity.LegalQuestionStatus;
import com.aivle.backend.analysis.legal.entity.LegalReviewQuestion;
import com.aivle.backend.analysis.legal.repository.LegalReviewQuestionRepository;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.entity.*;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.document.repository.StructuredPlanSectionRepository;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 기획서 새 버전 생성 경로 3종. 기존 버전의 섹션은 어떤 경우에도 변경하지 않는다 —
 * 수정은 항상 딥카피 + 교체로 새 버전을 만든다.
 */
@Service
@RequiredArgsConstructor
public class PlanVersionService {
    private final ProjectRepository projects;
    private final UserRepository users;
    private final StructuredPlanRepository plans;
    private final StructuredPlanSectionRepository sections;
    private final ReviewCycleRepository cycles;
    private final RevisionRequestRepository revisionRequests;
    private final RevisionSuggestionRepository suggestions;
    private final ConfirmedFactRepository facts;
    private final LegalReviewQuestionRepository questions;
    private final ReviewCycleService cycleService;
    private final DomainAuditService audit;
    private final Clock jobClock;

    public record PlanVersionCreated(
        Long newPlanId, Integer newVersionNumber, PlanOrigin origin, Long confirmedFactId
    ) {}

    public record SectionEdit(String code, String sourceText) {}

    /** 수정 승인: AI 수정안은 사용자가 명시적으로 승인한 경우에만 반영된다. 재검토는 시작하지 않는다. */
    @Transactional
    public PlanVersionCreated acceptSuggestion(
        Long userId, Long projectId, Long requestId, Long suggestionId
    ) {
        Project project = ownedProject(userId, projectId);
        RevisionRequest request = revisionRequests.findByIdAndDeletedAtIsNull(requestId)
            .filter(r -> r.getReviewCycle().getProject().getId().equals(projectId))
            .orElseThrow(() -> new BusinessException(ErrorCode.REVISION_REQUEST_NOT_FOUND));
        if (request.getStatus() != RevisionRequestStatus.OPEN) {
            throw new BusinessException(ErrorCode.REVISION_REQUEST_NOT_OPEN);
        }
        RevisionSuggestion suggestion = suggestions
            .findByRevisionRequestIdAndDeletedAtIsNullOrderByDisplayOrder(requestId).stream()
            .filter(s -> s.getId().equals(suggestionId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.REVISION_SUGGESTION_NOT_FOUND));

        ReviewCycle cycle = request.getReviewCycle();
        StructuredPlan current = cycle.getCurrentPlan();
        List<StructuredPlanSection> currentSections = sectionsOf(current.getId());
        StructuredPlanSection anchor = currentSections.stream()
            .filter(s -> s.getSectionCode() == request.getAnchorSectionCode())
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.REVISION_ANCHOR_MISMATCH));
        String sourceText = anchor.getSourceText();
        int index = sourceText == null ? -1 : sourceText.indexOf(request.getAnchorQuote());
        if (index < 0) {
            throw new BusinessException(ErrorCode.REVISION_ANCHOR_MISMATCH);
        }
        String replaced = sourceText.substring(0, index)
            + suggestion.getNewText()
            + sourceText.substring(index + request.getAnchorQuote().length());

        StructuredPlan newPlan = derive(current, PlanOrigin.REVISION_ACCEPT, userId);
        copySections(currentSections, newPlan, Map.of(request.getAnchorSectionCode(), replaced));
        request.accept(suggestionId);
        cycle.moveCurrentPlan(newPlan);

        audit.record(userId, projectId, AuditEventType.PLAN_REVISION_ACCEPTED,
            "StructuredPlan", newPlan.getId(), null,
            Map.of("planVersion", newPlan.getVersionNumber().toString(),
                "origin", PlanOrigin.REVISION_ACCEPT.name(),
                "revisionRequestId", request.getId().toString(),
                "suggestionId", suggestion.getId().toString()));
        return new PlanVersionCreated(
            newPlan.getId(), newPlan.getVersionNumber(), PlanOrigin.REVISION_ACCEPT, null);
    }

    @Transactional
    public void dismissRequest(Long userId, Long projectId, Long requestId) {
        ownedProject(userId, projectId);
        RevisionRequest request = revisionRequests.findByIdAndDeletedAtIsNull(requestId)
            .filter(r -> r.getReviewCycle().getProject().getId().equals(projectId))
            .orElseThrow(() -> new BusinessException(ErrorCode.REVISION_REQUEST_NOT_FOUND));
        if (request.getStatus() != RevisionRequestStatus.OPEN) {
            throw new BusinessException(ErrorCode.REVISION_REQUEST_NOT_OPEN);
        }
        request.dismiss();
        audit.record(userId, projectId, AuditEventType.PLAN_REVISION_DISMISSED,
            "RevisionRequest", request.getId(), null,
            Map.of("revisionRequestId", request.getId().toString()));
    }

    /** 질문 답변: 본문에는 삽입하지 않고 confirmedFacts로만 저장한다 (§4-3). */
    @Transactional
    public PlanVersionCreated answerQuestion(
        Long userId, Long projectId, Long questionId, String answer, String factKey, String source
    ) {
        ownedProject(userId, projectId);
        LegalReviewQuestion question = questions.findById(questionId)
            .filter(q -> !q.isDeleted()
                && q.getLegalReview().getProject().getId().equals(projectId))
            .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_QUESTION_NOT_FOUND));
        if (question.getStatus() != LegalQuestionStatus.OPEN) {
            throw new BusinessException(ErrorCode.LEGAL_QUESTION_NOT_OPEN);
        }
        Long cycleId = question.getLegalReview().getReviewCycleId();
        ReviewCycle cycle = (cycleId == null ? null
            : cycles.findById(cycleId).filter(c -> !c.isDeleted()).orElse(null));
        if (cycle == null) {
            throw new BusinessException(ErrorCode.REVIEW_CYCLE_NOT_FOUND);
        }

        StructuredPlan current = cycle.getCurrentPlan();
        StructuredPlan newPlan = derive(current, PlanOrigin.ANSWER, userId);
        // 섹션은 원문 그대로 복사 — 답변 문장은 본문에 들어가지 않는다
        copySections(sectionsOf(current.getId()), newPlan, Map.of());

        LocalDateTime now = LocalDateTime.now(jobClock);
        ConfirmedFact fact = facts.save(ConfirmedFact.create(
            cycle, newPlan, factKey, answer, source, now, question.getId()));
        question.answer(answer, fact.getId(), now);
        cycle.moveCurrentPlan(newPlan);

        audit.record(userId, projectId, AuditEventType.LEGAL_QUESTION_ANSWERED,
            "ConfirmedFact", fact.getId(), null,
            Map.of("questionId", question.getId().toString(),
                "factKey", factKey,
                "planVersion", newPlan.getVersionNumber().toString()));
        return new PlanVersionCreated(
            newPlan.getId(), newPlan.getVersionNumber(), PlanOrigin.ANSWER, fact.getId());
    }

    /** 사용자 직접 편집. 활성 사이클이 없으면(발행 직후 포함) 새 사이클이 DRAFT로 시작한다 (§2). */
    @Transactional
    public PlanVersionCreated userEdit(
        Long userId, Long projectId, Long planId, List<SectionEdit> edits
    ) {
        Project project = ownedProject(userId, projectId);
        StructuredPlan basePlan = plans.findByIdAndProjectIdAndDeletedAtIsNull(planId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STRUCTURED_PLAN_NOT_FOUND));

        var activeCycle = cycles.findTopByProjectIdAndStatusNotAndDeletedAtIsNullOrderByIdDesc(
            projectId, ReviewCycleStatus.PUBLISHED);
        StructuredPlan current = activeCycle.map(ReviewCycle::getCurrentPlan).orElse(basePlan);

        StructuredPlan newPlan = derive(current, PlanOrigin.USER_EDIT, userId);
        Map<BusinessPlanSectionCode, String> overrides = new java.util.EnumMap<>(BusinessPlanSectionCode.class);
        for (SectionEdit edit : edits) {
            try {
                overrides.put(BusinessPlanSectionCode.valueOf(edit.code()), edit.sourceText());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        copySections(sectionsOf(current.getId()), newPlan, overrides);

        if (activeCycle.isPresent()) {
            activeCycle.get().moveCurrentPlan(newPlan);
        } else {
            cycles.save(ReviewCycle.start(project, newPlan));
        }

        audit.record(userId, projectId, AuditEventType.PLAN_USER_EDITED,
            "StructuredPlan", newPlan.getId(), null,
            Map.of("planVersion", newPlan.getVersionNumber().toString(),
                "origin", PlanOrigin.USER_EDIT.name()));
        return new PlanVersionCreated(
            newPlan.getId(), newPlan.getVersionNumber(), PlanOrigin.USER_EDIT, null);
    }

    private Project ownedProject(Long userId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private StructuredPlan derive(StructuredPlan parent, PlanOrigin origin, Long userId) {
        User user = users.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        int nextVersion = plans
            .findTopByProjectIdOrderByVersionNumberDesc(parent.getProject().getId())
            .map(latest -> latest.getVersionNumber() + 1)
            .orElse(parent.getVersionNumber() + 1);
        return plans.save(StructuredPlan.deriveFrom(
            parent, origin, nextVersion, user, LocalDateTime.now(jobClock)));
    }

    private void copySections(
        List<StructuredPlanSection> source,
        StructuredPlan target,
        Map<BusinessPlanSectionCode, String> overrides
    ) {
        sections.saveAll(source.stream()
            .map(section -> StructuredPlanSection.copyOf(
                section, target, overrides.get(section.getSectionCode())))
            .toList());
    }

    private List<StructuredPlanSection> sectionsOf(Long planId) {
        return sections.findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(planId);
    }
}
