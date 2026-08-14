package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.finance.repository.FinancialAnalysisReportRepository;
import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport.ModuleType;
import com.aivle.backend.launchreadiness.repository.ProfessionalAnalysisReportRepository;
import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchRunRepository;
import com.aivle.backend.journey.TwinSurveyRun;
import com.aivle.backend.journey.TwinSurveyRunRepository;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioRunRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final MarketingContentRepository marketingRepository;
    private final MarketingSourceSnapshotRepository marketingSourceRepository;
    private final ProfessionalAnalysisReportRepository professionalAnalysisReportRepository;
    private final FinancialInputPreparationRepository financialPreparationRepository;
    private final FinancialInputSnapshotRepository financialSnapshotRepository;
    private final FinancialAnalysisReportRepository financialAnalysisReportRepository;
    private final MarketResearchRunRepository marketResearchRunRepository;
    private final TwinSurveyRunRepository twinSurveyRunRepository;

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
        TwinSurveyRun twinRun = twinSurveyRunRepository
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        MarketResearchRun marketRun = latestResearchRun(projectId, MarketResearchRun.Kind.FULL);
        MarketResearchRun businessRun = latestResearchRun(projectId, MarketResearchRun.Kind.BM);
        MarketingContent marketing = marketingRepository.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).orElse(null);
        var marketingSource = selectedSnapshot == null ? null
            : marketingSourceRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        var financialPreparation = financialPreparationRepository
            .findFirstByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId).orElse(null);
        var financialSnapshot = financialPreparation == null ? null
            : financialSnapshotRepository.findByPreparationIdAndProjectIdAndDeletedAtIsNull(
                financialPreparation.getId(), projectId).orElse(null);

        String confirmedBriefId = brief == null ? null : brief.getConfirmedSnapshotId();
        PipelineModuleStatus conceptStatus = conceptStatus(conceptRun, portfolioSelection, confirmedBriefId);
        // 시장조사·BM 은 외부 모듈 핸드오프가 아니라 자체 엔진(MARKET_RESEARCH TaskRun)이 돈다.
        // ⚠ 실행이 있으면 **Seed 확정 여부와 무관하게** 그 실행 상태를 보여준다. 견본 컨셉으로도
        //   돌 수 있어서, Seed 로 막아 두면 다 끝난 모듈이 「준비 전」으로 보이는 거짓말이 된다.
        PipelineModuleStatus marketStatus = researchOrGate(marketRun, selectedSnapshot);
        PipelineModuleStatus businessModelStatus = researchOrGate(businessRun, selectedSnapshot);
        PipelineModuleStatus marketingStatus = marketingStatus(marketing, marketingSource == null ? null : marketingSource.getId());
        boolean professionalReportCompleted = professionalAnalysisReportRepository
            .findFirstByProjectIdAndModuleTypeAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, ModuleType.TECHNOLOGY).isPresent()
            || professionalAnalysisReportRepository
                .findFirstByProjectIdAndModuleTypeAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, ModuleType.OPERATIONS).isPresent();
        boolean financialReportCompleted = financialAnalysisReportRepository
            .findFirstByProjectIdAndDeletedAtIsNullOrderByCompletedAtDesc(projectId).isPresent();
        PipelineModuleStatus launchReadinessStatus = professionalReportCompleted || financialReportCompleted
            ? PipelineModuleStatus.COMPLETED : PipelineModuleStatus.READY;

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
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId") : List.of(),
                new NextAction("시장조사 실행", "/market"),
                marketRun == null ? null : String.valueOf(marketRun.getId()),
                marketRun == null ? null : marketRun.getTaskRun().getId(),
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                marketRun == null ? null : marketRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.BUSINESS_MODEL, businessModelStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId") : List.of(),
                new NextAction("BM 캔버스 생성", "/business-model"),
                businessRun == null ? null : String.valueOf(businessRun.getId()),
                businessRun == null ? null : businessRun.getTaskRun().getId(),
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                businessRun == null ? null : businessRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.TECH_OPS, launchReadinessStatus, List.of(),
                new NextAction("출시 준비 분석", "/tech-ops"), null, null, null, null, null, null),
            response(projectId, PipelineModuleType.FINANCE, launchReadinessStatus, List.of(),
                new NextAction("출시 준비 분석", "/tech-ops"), null, null,
                financialSnapshot == null ? null : financialSnapshot.getId(), null, null, null),
            // ⚠ 이 게이트는 **새로 만든 것**이다. 재무와 마케팅은 원래 데이터로 이어져 있지 않았다
            //   (마케팅 게이트는 selectedSnapshot 기반). 트윈 조사는 재무 다음에 서므로
            //   앞 단계의 확정물인 financialSnapshotId 를 요구한다.
            //   ⚠ **컨셉도 같이 본다.** 자극 초안이 마켓 시드 스냅샷에서 나오므로 재무만 있고
            //   컨셉이 없으면 READY 라고 말해 놓고 초안을 만들지 못한다.
            //   requiredInputs 는 **없는 것부터** 센다 — 앞 단계를 먼저 가리켜야 길이 된다.
            //   시장조사와 같은 규칙으로, **실행이 있으면 게이트와 무관하게 그 상태를 보여준다** —
            //   막아 두면 다 끝난 모듈이 「준비 전」으로 보이는 거짓말이 된다.
            response(projectId, PipelineModuleType.PANEL_SURVEY,
                twinOrGate(twinRun, selectedSnapshot != null),
                twinRequiredInputs(selectedSnapshot != null),
                new NextAction("패널 트윈 조사", "/panel-survey"),
                twinRun == null ? null : String.valueOf(twinRun.getId()),
                twinRun == null ? null : twinRun.getTaskRun().getId(),
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                twinRun == null ? null : twinRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKETING, marketingStatus,
                marketingSource == null ? List.of("marketingSourceSnapshotId") : List.of(),
                new NextAction("마케팅 콘텐츠", "/marketing"), marketing == null ? null : marketing.getId(),
                marketing == null ? null : marketing.getTaskRunId(), marketingSource == null ? null : marketingSource.getId(),
                null, null, marketing == null ? null : marketing.getUpdatedAt())
        );
    }

    private MarketResearchRun latestResearchRun(Long projectId, MarketResearchRun.Kind kind) {
        return marketResearchRunRepository
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, kind).orElse(null);
    }

    private PipelineModuleStatus researchOrGate(MarketResearchRun run, MarketAnalysisSeedSnapshot seed) {
        if (run != null) return researchStatus(run);
        return seed == null ? PipelineModuleStatus.NOT_READY : PipelineModuleStatus.READY;
    }

    private PipelineModuleStatus twinOrGate(TwinSurveyRun run, boolean inputsReady) {
        if (run != null) return switch (run.getState()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
        };
        return inputsReady ? PipelineModuleStatus.READY : PipelineModuleStatus.NOT_READY;
    }

    /** 빠진 것을 여정 순서대로 센다 — 컨셉이 재무보다 앞이라 먼저 나온다. */
    private List<String> twinRequiredInputs(boolean conceptReady) {
        List<String> missing = new ArrayList<>();
        if (!conceptReady) missing.add("marketAnalysisSeedSnapshotId");
        return missing;
    }

    private PipelineModuleStatus researchStatus(MarketResearchRun run) {
        return switch (run.getState()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
        };
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
