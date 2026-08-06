package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptSlotRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.integration.domain.ModuleRun;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.planning.repository.FinalizedPlanningSnapshotRepository;
import com.aivle.backend.pipeline.selection.domain.SelectedConceptSnapshot;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.repository.SelectedConceptSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectModuleStatusService {
    private final ProjectRepository projectRepository;
    private final IdeaBriefRepository ideaBriefRepository;
    private final ConceptFactoryRunRepository conceptRunRepository;
    private final ConceptSlotRepository conceptSlotRepository;
    private final ConceptSelectionRepository selectionRepository;
    private final SelectedConceptSnapshotRepository selectionSnapshotRepository;
    private final ModuleRunRepository moduleRunRepository;
    private final FinalizedPlanningSnapshotRepository finalizedRepository;
    private final MarketingContentRepository marketingRepository;

    public List<ProjectModuleStatusResponse> findAll(Long userId, Long projectId) {
        projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        IdeaBrief brief = ideaBriefRepository.findCurrentOwned(userId, projectId).orElse(null);
        ConceptFactoryRun conceptRun = conceptRunRepository.findCurrentOwned(userId, projectId).orElse(null);
        long eligibleCount = conceptRun == null ? 0
            : conceptSlotRepository.countByRunIdAndStatusAndDeletedAtIsNull(conceptRun.getId(), ConceptSlotStatus.ELIGIBLE);
        var selection = selectionRepository.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId).orElse(null);
        SelectedConceptSnapshot selectedSnapshot = selection == null ? null
            : selectionSnapshotRepository.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId).orElse(null);
        ModuleRun marketRun = latestRun(projectId, ModuleType.MARKET_ANALYSIS);
        var finalized = finalizedRepository.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId).orElse(null);
        ModuleRun businessRun = latestRun(projectId, ModuleType.BUSINESS_FINANCIAL);
        ModuleRun personaRun = latestRun(projectId, ModuleType.PERSONA_RESPONSE_TEST);
        MarketingContent marketing = marketingRepository.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).orElse(null);

        String confirmedBriefId = brief == null ? null : brief.getConfirmedSnapshotId();
        PipelineModuleStatus conceptStatus = conceptStatus(conceptRun, confirmedBriefId);
        PipelineModuleStatus marketStatus = externalStatus(marketRun, selectedSnapshot == null ? null : selectedSnapshot.getId());
        PipelineModuleStatus businessPersonaStatus = finalized == null
            ? PipelineModuleStatus.NOT_CONNECTED
            : combinedExternalStatus(finalized.getId(), businessRun, personaRun);
        PipelineModuleStatus marketingStatus = marketingStatus(marketing, finalized == null ? null : finalized.getId());

        return List.of(
            response(projectId, PipelineModuleType.IDEA, ideaStatus(brief),
                brief == null || brief.getOverviewText() == null || brief.getOverviewText().isBlank() ? List.of("ideaOverview") : List.of(),
                new NextAction("아이디어 정리", "/idea"), null,
                brief == null ? null : brief.getActiveTaskRunId(), null, confirmedBriefId, null,
                brief == null ? null : brief.getUpdatedAt()),
            response(projectId, PipelineModuleType.CONCEPT_FACTORY, conceptStatus,
                confirmedBriefId == null ? List.of("ideaBriefSnapshotId") : List.of(),
                new NextAction("컨셉 생성·법률검토", "/concepts"),
                conceptRun == null ? null : conceptRun.getId(), conceptRun == null ? null : conceptRun.getTaskRunId(),
                conceptRun == null ? null : conceptRun.getSourceIdeaBriefSnapshotId(), confirmedBriefId, eligibleCount,
                conceptRun == null ? null : conceptRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.CONCEPT_SELECTION,
                selection == null ? PipelineModuleStatus.NOT_READY
                    : selectedSnapshot == null ? PipelineModuleStatus.FAILED : PipelineModuleStatus.COMPLETED,
                selection == null ? List.of("eligibleConcepts") : List.of(),
                new NextAction("컨셉 비교·선택", "/concepts/compare"), null, null,
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                selection == null ? null : selection.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKET_ANALYSIS, marketStatus,
                selectedSnapshot == null ? List.of("selectedConceptSnapshotId") : List.of("marketAnalysisConnection"),
                new NextAction("시장분석 상태 확인", "/market"), marketRun == null ? null : marketRun.getId(), null,
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                marketRun == null ? null : marketRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.BUSINESS_PERSONA_TEST, businessPersonaStatus,
                finalized == null ? List.of("finalizedPlanningSnapshotId") : List.of("businessPersonaModuleConnection"),
                new NextAction("BM·재무·페르소나 응답 상태 확인", "/business-persona-test"),
                businessRun != null ? businessRun.getId() : personaRun == null ? null : personaRun.getId(), null,
                finalized == null ? null : finalized.getId(), null, null,
                newestUpdatedAt(businessRun, personaRun)),
            response(projectId, PipelineModuleType.MARKETING, marketingStatus,
                finalized == null ? List.of("finalizedPlanningSnapshotId") : List.of(),
                new NextAction("마케팅 콘텐츠", "/marketing"), marketing == null ? null : marketing.getId(),
                marketing == null ? null : marketing.getTaskRunId(), finalized == null ? null : finalized.getId(),
                null, null, marketing == null ? null : marketing.getUpdatedAt())
        );
    }

    private ModuleRun latestRun(Long projectId, ModuleType type) {
        return moduleRunRepository.findFirstByProjectIdAndModuleAndDeletedAtIsNullOrderByCreatedAtDesc(projectId, type).orElse(null);
    }

    private PipelineModuleStatus ideaStatus(IdeaBrief brief) {
        if (brief == null) return PipelineModuleStatus.NEEDS_INPUT;
        return switch (brief.getStatus()) {
            case DRAFT -> brief.getOverviewText() == null || brief.getOverviewText().isBlank()
                ? PipelineModuleStatus.NEEDS_INPUT : PipelineModuleStatus.READY;
            case DERIVING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case READY_FOR_REVIEW -> PipelineModuleStatus.READY;
            case CONFIRMED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
            case STALE -> PipelineModuleStatus.STALE;
        };
    }

    private PipelineModuleStatus conceptStatus(ConceptFactoryRun run, String currentBriefSnapshotId) {
        if (run == null) return currentBriefSnapshotId == null ? PipelineModuleStatus.NOT_READY : PipelineModuleStatus.READY;
        if (currentBriefSnapshotId != null && !currentBriefSnapshotId.equals(run.getSourceIdeaBriefSnapshotId())) {
            return PipelineModuleStatus.STALE;
        }
        return switch (run.getStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case GENERATING, VALIDATING, REPLACING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case COMPLETED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
            case STALE -> PipelineModuleStatus.STALE;
        };
    }

    private PipelineModuleStatus externalStatus(ModuleRun run, String currentSnapshotId) {
        if (run == null) return PipelineModuleStatus.NOT_CONNECTED;
        if (currentSnapshotId != null && !currentSnapshotId.equals(run.getInputSnapshotId())) return PipelineModuleStatus.STALE;
        return PipelineModuleStatus.valueOf(run.getStatus().name());
    }

    private PipelineModuleStatus combinedExternalStatus(String currentId, ModuleRun business, ModuleRun persona) {
        if (business == null && persona == null) return PipelineModuleStatus.NOT_CONNECTED;
        if ((business != null && !currentId.equals(business.getInputSnapshotId()))
            || (persona != null && !currentId.equals(persona.getInputSnapshotId()))) return PipelineModuleStatus.STALE;
        if (hasStatus(business, "FAILED") || hasStatus(persona, "FAILED")) return PipelineModuleStatus.FAILED;
        if (hasStatus(business, "NEEDS_INPUT") || hasStatus(persona, "NEEDS_INPUT")) return PipelineModuleStatus.NEEDS_INPUT;
        if (hasStatus(business, "RUNNING") || hasStatus(persona, "RUNNING")) return PipelineModuleStatus.RUNNING;
        if (hasStatus(business, "QUEUED") || hasStatus(persona, "QUEUED")) return PipelineModuleStatus.QUEUED;
        if (hasStatus(business, "COMPLETED") && hasStatus(persona, "COMPLETED")) return PipelineModuleStatus.COMPLETED;
        if (hasStatus(business, "READY") || hasStatus(persona, "READY")) return PipelineModuleStatus.READY;
        return PipelineModuleStatus.NOT_CONNECTED;
    }

    private boolean hasStatus(ModuleRun run, String status) {
        return run != null && run.getStatus().name().equals(status);
    }

    private PipelineModuleStatus marketingStatus(MarketingContent content, String finalizedSnapshotId) {
        if (finalizedSnapshotId == null) return PipelineModuleStatus.NOT_READY;
        if (content == null) return PipelineModuleStatus.READY;
        if (!finalizedSnapshotId.equals(content.getPlanningSnapshotId())) return PipelineModuleStatus.STALE;
        return switch (content.getStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case COMPLETED, FINALIZED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
        };
    }

    private LocalDateTime newestUpdatedAt(ModuleRun left, ModuleRun right) {
        if (left == null) return right == null ? null : right.getUpdatedAt();
        if (right == null) return left.getUpdatedAt();
        return left.getUpdatedAt().isAfter(right.getUpdatedAt()) ? left.getUpdatedAt() : right.getUpdatedAt();
    }

    private ProjectModuleStatusResponse response(Long projectId, PipelineModuleType module,
            PipelineModuleStatus status, List<String> requiredInputs, NextAction nextAction,
            String activeRunId, String activeTaskRunId, String sourceSnapshotId,
            String confirmedSnapshotId, Long eligibleCount, LocalDateTime updatedAt) {
        return new ProjectModuleStatusResponse(projectId, module, status, status.getLabelKey(),
            List.copyOf(requiredInputs), nextAction, activeRunId, activeTaskRunId, activeTaskRunId,
            sourceSnapshotId, confirmedSnapshotId, eligibleCount, updatedAt);
    }
}
