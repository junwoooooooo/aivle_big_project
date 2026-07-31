package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.analysis.legal.entity.LegalReview;
import com.aivle.backend.analysis.legal.repository.LegalFindingRepository;
import com.aivle.backend.analysis.legal.repository.LegalReviewRepository;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 정식 보고서 발행. CONVERGED에서만 가능하며, 미완료 할 일은 발행을 막지 않고
 * "이행 예정 사항"으로 스냅샷에 수록된다. 발행 시점에 프로젝트가 FEASIBILITY로 전이한다.
 */
@Service
@RequiredArgsConstructor
public class PublicationService {
    private final ProjectRepository projects;
    private final UserRepository users;
    private final ReviewCycleRepository cycles;
    private final PublicationRepository publications;
    private final RevisionRequestRepository revisionRequests;
    private final RevisionSuggestionRepository suggestions;
    private final ConfirmedFactRepository facts;
    private final LegalReviewRepository reviews;
    private final LegalFindingRepository findings;
    private final DomainAuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;

    public record Published(Long publicationId, Integer finalVersionNumber, LocalDateTime publishedAt) {}

    @Transactional
    public Published publish(Long userId, Long projectId, Long cycleId, List<String> completedActions) {
        Project project = projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        ReviewCycle cycle = cycles.findByIdAndProjectIdAndDeletedAtIsNull(cycleId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_CYCLE_NOT_FOUND));
        if (cycle.getStatus() != ReviewCycleStatus.CONVERGED) {
            throw new BusinessException(ErrorCode.REVIEW_CYCLE_NOT_CONVERGED);
        }
        User publisher = users.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        StructuredPlan finalPlan = cycle.getCurrentPlan();
        LocalDateTime now = LocalDateTime.now(jobClock);
        String snapshot = buildSnapshot(cycle, finalPlan, completedActions);

        Publication publication = publications.save(Publication.create(
            project, cycle, finalPlan, snapshot, publisher, now));
        cycle.publish();
        project.enterFeasibility();

        audit.record(userId, projectId, AuditEventType.REPORT_PUBLISHED,
            "Publication", publication.getId(), null,
            Map.of("publicationId", publication.getId().toString(),
                "reviewCycleId", cycle.getId().toString(),
                "planVersion", finalPlan.getVersionNumber().toString()));
        return new Published(publication.getId(), finalPlan.getVersionNumber(), now);
    }

    private String buildSnapshot(
        ReviewCycle cycle, StructuredPlan finalPlan, List<String> completedActions
    ) {
        // 버전 이력: 최종 버전에서 부모 체인을 따라 올라가며 수집 (v1 → vN 오름차순)
        List<Map<String, Object>> versions = new ArrayList<>();
        for (StructuredPlan plan = finalPlan; plan != null; plan = plan.getParentPlan()) {
            versions.add(Map.of(
                "versionNo", plan.getVersionNumber(),
                "origin", plan.getOrigin().name(),
                "createdAt", String.valueOf(plan.getCreatedAt())));
        }
        Collections.reverse(versions);

        // 해결 이력: RESOLVED 기록이 있는 수정 요청 (행은 삭제되지 않으므로 전부 남아 있다)
        List<Map<String, Object>> resolutions = new ArrayList<>();
        for (RevisionRequest request :
            revisionRequests.findByReviewCycleIdAndDeletedAtIsNullOrderById(cycle.getId())) {
            if (request.getResolvedInVersion() == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("requestId", request.getId());
            entry.put("category", request.getCategory().name());
            entry.put("anchorSectionCode", request.getAnchorSectionCode().name());
            entry.put("resolvedInVersion", request.getResolvedInVersion());
            entry.put("status", request.getStatus().name());
            if (request.getAcceptedSuggestionId() != null) {
                suggestions.findById(request.getAcceptedSuggestionId())
                    .ifPresent(s -> entry.put("acceptedSuggestionLabel", s.getLabel()));
            }
            resolutions.add(entry);
        }

        List<Map<String, Object>> answeredQuestions = new ArrayList<>();
        for (ConfirmedFact fact :
            facts.findByReviewCycleIdAndDeletedAtIsNullOrderByAnsweredAt(cycle.getId())) {
            answeredQuestions.add(Map.of(
                "factKey", fact.getFactKey(),
                "factValue", fact.getFactValue(),
                "source", String.valueOf(fact.getSource()),
                "answeredAt", String.valueOf(fact.getAnsweredAt())));
        }

        // 미완료 할 일 = 최신 리뷰의 즉시 할 일 − 완료 처리된 액션 (발행을 막지 않는다)
        Set<String> pendingTodos = new LinkedHashSet<>();
        LegalReview latestReview = cycle.getLatestReviewId() == null ? null
            : reviews.findById(cycle.getLatestReviewId()).orElse(null);
        if (latestReview != null) {
            pendingTodos.addAll(ReviewDiffService.nowActions(
                findings.findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(latestReview.getId())));
            if (completedActions != null) {
                completedActions.forEach(pendingTodos::remove);
            }
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("finalVersionNumber", finalPlan.getVersionNumber());
        snapshot.put("versions", versions);
        snapshot.put("resolutions", resolutions);
        snapshot.put("answeredQuestions", answeredQuestions);
        snapshot.put("pendingTodos", List.copyOf(pendingTodos));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("publication snapshot serialization failed", exception);
        }
    }
}
