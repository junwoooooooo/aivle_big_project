package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.entity.PlanOrigin;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackQueryService {
    private final ProjectRepository projects;
    private final StructuredPlanRepository plans;
    private final ReviewCycleRepository cycles;
    private final PublicationRepository publications;

    public record ActiveCycleResponse(
        Long cycleId, ReviewCycleStatus status, Long currentPlanId,
        Integer currentVersionNumber, Long latestReviewId, boolean canPublish
    ) {}

    public record PlanVersionItem(
        Long planId, Integer versionNumber, PlanOrigin origin, LocalDateTime createdAt
    ) {}

    public record PublicationResponse(
        Long publicationId, Long reviewCycleId, Long finalPlanId,
        Integer finalVersionNumber, LocalDateTime publishedAt, String snapshotJson
    ) {}

    public ActiveCycleResponse activeCycle(Long userId, Long projectId) {
        requireOwnedProject(userId, projectId);
        ReviewCycle cycle = cycles
            .findTopByProjectIdAndStatusNotAndDeletedAtIsNullOrderByIdDesc(
                projectId, ReviewCycleStatus.PUBLISHED)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_CYCLE_NOT_FOUND));
        return new ActiveCycleResponse(
            cycle.getId(), cycle.getStatus(),
            cycle.getCurrentPlan().getId(), cycle.getCurrentPlan().getVersionNumber(),
            cycle.getLatestReviewId(),
            cycle.getStatus() == ReviewCycleStatus.CONVERGED);
    }

    public List<PlanVersionItem> planVersions(Long userId, Long projectId) {
        requireOwnedProject(userId, projectId);
        return plans.findAllByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .stream()
            .map(plan -> new PlanVersionItem(
                plan.getId(), plan.getVersionNumber(), plan.getOrigin(), plan.getCreatedAt()))
            .toList();
    }

    public PublicationResponse latestPublication(Long userId, Long projectId) {
        requireOwnedProject(userId, projectId);
        Publication publication = publications
            .findTopByProjectIdAndDeletedAtIsNullOrderByPublishedAtDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new PublicationResponse(
            publication.getId(), publication.getReviewCycle().getId(),
            publication.getFinalPlan().getId(), publication.getFinalVersionNumber(),
            publication.getPublishedAt(), publication.getSnapshotJson());
    }

    private void requireOwnedProject(Long userId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
