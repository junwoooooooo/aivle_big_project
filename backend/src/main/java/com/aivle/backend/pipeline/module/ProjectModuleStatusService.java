package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioRunRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.integration.domain.ModuleRun;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
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
    private final ConceptPortfolioRunRepository conceptPortfolioRunRepository;
    private final ConceptPortfolioSelectionRepository conceptPortfolioSelectionRepository;
    private final ConceptSelectionRepository selectionRepository;
    private final MarketAnalysisSeedSnapshotRepository marketSeedSnapshotRepository;
    private final ModuleRunRepository moduleRunRepository;
    private final MarketingContentRepository marketingRepository;
    private final MarketingSourceSnapshotRepository marketingSourceRepository;
    private final TechOpsInputPreparationRepository techOpsPreparationRepository;
    private final TechOpsInputSnapshotRepository techOpsSnapshotRepository;
    private final FinancialInputPreparationRepository financialPreparationRepository;
    private final FinancialInputSnapshotRepository financialSnapshotRepository;

    public List<ProjectModuleStatusResponse> findAll(Long userId, Long projectId) {
        projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        IdeaBrief brief = ideaBriefRepository.findCurrentOwned(userId, projectId).orElse(null);
        ConceptPortfolioRun conceptRun = conceptPortfolioRunRepository.findCurrentOwned(userId, projectId).orElse(null);
        long eligibleCount = conceptRun == null ? 0 : conceptRun.getProducedConceptCount();
        ConceptPortfolioSelection portfolioSelection = conceptPortfolioSelectionRepository
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        var legacySelection = portfolioSelection == null
            ? selectionRepository.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId).orElse(null)
            : null;
        MarketAnalysisSeedSnapshot selectedSnapshot = portfolioSelection != null
            ? marketSeedSnapshotRepository
                .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(portfolioSelection.getId()).orElse(null)
            : legacySelection == null ? null
                : marketSeedSnapshotRepository.findBySelectionIdAndProjectIdAndDeletedAtIsNull(
                    legacySelection.getId(), projectId).orElse(null);
        ModuleRun marketRun = latestRun(projectId, ModuleType.MARKET_ANALYSIS);
        ModuleRun businessRun = latestRun(projectId, ModuleType.BUSINESS_MODEL);
        ModuleRun techOpsRun = latestRun(projectId, ModuleType.TECH_OPS);
        ModuleRun financialRun = latestRun(projectId, ModuleType.FINANCIAL_ANALYSIS);
        MarketingContent marketing = marketingRepository.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).orElse(null);
        var marketingSource = selectedSnapshot == null ? null
            : marketingSourceRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        var techOpsPreparation = selectedSnapshot == null ? null
            : techOpsPreparationRepository.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(
                projectId, selectedSnapshot.getId()).orElse(null);
        var techOpsSnapshot = selectedSnapshot == null ? null
            : techOpsSnapshotRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        var financialPreparation = techOpsSnapshot == null ? null
            : financialPreparationRepository.findByProjectIdAndSourceTechOpsSnapshotIdAndDeletedAtIsNull(
                projectId, techOpsSnapshot.getId()).orElse(null);
        var financialSnapshot = techOpsSnapshot == null ? null
            : financialSnapshotRepository.findBySourceTechOpsSnapshotIdAndProjectIdAndDeletedAtIsNull(
                techOpsSnapshot.getId(), projectId).orElse(null);

        String confirmedBriefId = brief == null ? null : brief.getConfirmedSnapshotId();
        PipelineModuleStatus conceptStatus = conceptStatus(conceptRun, portfolioSelection, confirmedBriefId);
        PipelineModuleStatus marketStatus = selectedSnapshot == null
            ? PipelineModuleStatus.NOT_READY
            : externalStatus(marketRun, selectedSnapshot.getId());
        PipelineModuleStatus businessModelStatus = selectedSnapshot == null
            ? PipelineModuleStatus.NOT_READY
            : externalStatus(businessRun, selectedSnapshot.getId());
        PipelineModuleStatus marketingStatus = marketingStatus(marketing, marketingSource == null ? null : marketingSource.getId());
        PipelineModuleStatus techOpsStatus = selectedSnapshot == null ? PipelineModuleStatus.NOT_READY
            : techOpsPreparation == null ? PipelineModuleStatus.READY
            : techOpsSnapshot == null ? PipelineModuleStatus.NEEDS_INPUT
            : externalStatus(techOpsRun, techOpsSnapshot.getId());
        PipelineModuleStatus financialStatus = techOpsSnapshot == null ? PipelineModuleStatus.NOT_READY
            : financialPreparation == null ? PipelineModuleStatus.READY
            : financialSnapshot == null ? PipelineModuleStatus.NEEDS_INPUT
            : externalStatus(financialRun, financialSnapshot.getId());

        return List.of(
            response(projectId, PipelineModuleType.IDEA, ideaStatus(brief),
                brief == null || brief.getOverviewText() == null || brief.getOverviewText().isBlank() ? List.of("ideaOverview") : List.of(),
                new NextAction("아이디어 정리", "/idea"), null,
                brief == null ? null : brief.getActiveTaskRunId(), null, confirmedBriefId, null,
                brief == null ? null : brief.getUpdatedAt()),
            response(projectId, PipelineModuleType.CONCEPT_PORTFOLIO, conceptStatus,
                confirmedBriefId == null ? List.of("ideaBriefSnapshotId") : List.of(),
                new NextAction("사업안 검토", "/concepts"),
                conceptRun == null ? null : conceptRun.getId(),
                portfolioSelection != null && portfolioSelection.getActiveTaskRunId() != null
                    ? portfolioSelection.getActiveTaskRunId()
                    : conceptRun == null ? null : conceptRun.getActiveTaskRunId(),
                conceptRun == null ? null : conceptRun.getSourceIdeaBrief().getId(), confirmedBriefId, eligibleCount,
                conceptRun == null ? null : conceptRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKET_ANALYSIS, marketStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId") : List.of("marketAnalysisConnection"),
                new NextAction("시장분석 상태 확인", "/market"), marketRun == null ? null : marketRun.getId(), null,
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                marketRun == null ? null : marketRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.BUSINESS_MODEL, businessModelStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId")
                    : businessRun == null ? List.of("businessModelModuleConnection") : List.of(),
                new NextAction("BM 분석 상태 확인", "/business-model"),
                businessRun == null ? null : businessRun.getId(), null,
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                businessRun == null ? null : businessRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.TECH_OPS, techOpsStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId")
                    : techOpsSnapshot == null ? List.of("techOpsRequiredFacts", "techOpsRequiredDecisions")
                    : techOpsRun == null ? List.of("techOpsModuleConnection") : List.of(),
                new NextAction("기술·운영 입력 준비", "/tech-ops"), techOpsRun == null ? null : techOpsRun.getId(), null,
                techOpsSnapshot == null ? null : techOpsSnapshot.getId(), null, null,
                techOpsRun == null ? techOpsPreparation == null ? null : techOpsPreparation.getUpdatedAt() : techOpsRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.FINANCE, financialStatus,
                techOpsSnapshot == null ? List.of("techOpsInputSnapshotId")
                    : financialSnapshot == null ? List.of("financialRequiredInputs")
                    : financialRun == null ? List.of("financialModuleConnection") : List.of(),
                new NextAction("재무 입력 준비", "/finance"), financialRun == null ? null : financialRun.getId(), null,
                financialSnapshot == null ? null : financialSnapshot.getId(), null, null,
                financialRun == null ? financialPreparation == null ? null : financialPreparation.getUpdatedAt() : financialRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKETING, marketingStatus,
                marketingSource == null ? List.of("marketingSourceSnapshotId") : List.of(),
                new NextAction("마케팅 콘텐츠", "/marketing"), marketing == null ? null : marketing.getId(),
                marketing == null ? null : marketing.getTaskRunId(), marketingSource == null ? null : marketingSource.getId(),
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
            case SAFETY_BLOCKED -> PipelineModuleStatus.NEEDS_INPUT;
            case READY_FOR_REVIEW -> PipelineModuleStatus.READY;
            case CONFIRMED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
            case STALE -> PipelineModuleStatus.STALE;
        };
    }

    private PipelineModuleStatus conceptStatus(ConceptPortfolioRun run,
            ConceptPortfolioSelection selection, String currentBriefSnapshotId) {
        if (run == null) return currentBriefSnapshotId == null ? PipelineModuleStatus.NOT_READY : PipelineModuleStatus.READY;
        if (currentBriefSnapshotId != null && !currentBriefSnapshotId.equals(run.getSourceIdeaBrief().getId())) {
            return PipelineModuleStatus.STALE;
        }
        if (selection != null) {
            if (selection.getActiveTaskRunId() != null) return PipelineModuleStatus.RUNNING;
            return switch (selection.getStatus()) {
                case PENDING_HYPOTHESIS_CONFIRMATION, DELTA_LEGAL_FAILED -> PipelineModuleStatus.NEEDS_INPUT;
                case READY_FOR_MARKET -> PipelineModuleStatus.COMPLETED;
                case FAILED -> PipelineModuleStatus.FAILED;
                case STALE -> PipelineModuleStatus.STALE;
                default -> PipelineModuleStatus.READY;
            };
        }
        return switch (run.getProductStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case RESULTS_AVAILABLE, RESULTS_WITH_OPEN_INPUT -> PipelineModuleStatus.READY;
            case FAILED -> PipelineModuleStatus.FAILED;
            case STALE -> PipelineModuleStatus.STALE;
        };
    }

    private PipelineModuleStatus externalStatus(ModuleRun run, String currentSnapshotId) {
        if (run == null) return PipelineModuleStatus.NOT_CONNECTED;
        if (currentSnapshotId != null && !currentSnapshotId.equals(run.getInputSnapshotId())) return PipelineModuleStatus.STALE;
        return PipelineModuleStatus.valueOf(run.getStatus().name());
    }

    private PipelineModuleStatus marketingStatus(MarketingContent content, String marketingSourceSnapshotId) {
        if (marketingSourceSnapshotId == null) return PipelineModuleStatus.NOT_READY;
        if (content == null) return PipelineModuleStatus.READY;
        if (!marketingSourceSnapshotId.equals(content.getMarketingSourceSnapshotId())) return PipelineModuleStatus.STALE;
        return switch (content.getStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case COMPLETED, FINALIZED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
        };
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
