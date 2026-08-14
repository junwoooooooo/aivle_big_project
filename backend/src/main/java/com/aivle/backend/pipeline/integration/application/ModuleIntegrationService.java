package com.aivle.backend.pipeline.integration.application;

import static com.aivle.backend.pipeline.integration.api.IntegrationApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.finance.domain.FinancialInputSnapshot;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.integration.domain.ModuleHandoff;
import com.aivle.backend.pipeline.integration.domain.ModuleRun;
import com.aivle.backend.pipeline.integration.domain.ModuleRunStatus;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.integration.repository.ModuleHandoffRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ModuleIntegrationService {
    public static final String MARKET_INPUT_CONTRACT = "market-analysis-seed-snapshot-v1";
    public static final String TECH_OPS_INPUT_CONTRACT = "tech-ops-input-snapshot-v1";
    public static final String FINANCIAL_INPUT_CONTRACT = "financial-input-snapshot-v1";
    private static final String DEFAULT_OPERATION = "START_MARKET_ANALYSIS";

    private final ProjectRepository projects;
    private final ConceptSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository marketSeedSnapshots;
    private final ModuleHandoffRepository handoffs;
    private final ModuleRunRepository runs;
    private final TechOpsInputSnapshotRepository techOpsSnapshots;
    private final FinancialInputSnapshotRepository financialSnapshots;
    private final MarketResearchService marketResearch;
    private final ObjectMapper mapper;

    @Transactional
    public HandoffResponse create(Long ownerId, Long projectId, CreateHandoffRequest request) {
        requireOwnedForUpdate(ownerId, projectId);
        ModuleType module = parseModule(request.module());

        String inputId;
        String inputHash;
        String inputJson;
        String inputContract;
        if (module == ModuleType.MARKET_ANALYSIS || module == ModuleType.BUSINESS_MODEL) {
            MarketAnalysisSeedSnapshot snapshot = currentMarketSeed(projectId);
            inputId = snapshot.getId();
            inputHash = snapshot.getSnapshotHash();
            inputContract = MARKET_INPUT_CONTRACT;
            inputJson = snapshot.getSnapshotJson();
        } else if (module == ModuleType.TECH_OPS) {
            TechOpsInputSnapshot snapshot = currentTechOpsSnapshot(projectId);
            inputId = snapshot.getId();
            inputHash = snapshot.getSnapshotHash();
            inputContract = TECH_OPS_INPUT_CONTRACT;
            inputJson = snapshot.getSnapshotJson();
        } else if (module == ModuleType.FINANCIAL_ANALYSIS) {
            FinancialInputSnapshot snapshot = currentFinancialSnapshot(ownerId, projectId);
            inputId = snapshot.getId();
            inputHash = snapshot.getSnapshotHash();
            inputContract = FINANCIAL_INPUT_CONTRACT;
            inputJson = snapshot.getSnapshotJson();
        } else {
            throw new BusinessException(ErrorCode.AI_OPERATIONS_NOT_AVAILABLE,
                "승인된 페르소나 입력 어댑터가 연결되지 않았습니다.");
        }

        if (request.inputSnapshotId() != null && !request.inputSnapshotId().isBlank()
            && !request.inputSnapshotId().equals(inputId)) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        }
        String operation = request.requestedOperation() == null || request.requestedOperation().isBlank()
            ? (module == ModuleType.MARKET_ANALYSIS ? DEFAULT_OPERATION : "START_" + module.name())
            : request.requestedOperation().strip();
        String key = HandoffIdempotencyKey.create(module, inputHash, operation);
        var existing = handoffs.findByIdempotencyKeyAndDeletedAtIsNull(key);
        if (existing.isPresent()) {
            return response(existing.get(), runs.findByHandoffIdAndProjectIdAndDeletedAtIsNull(
                existing.get().getId(), projectId).orElseThrow(), ownerId);
        }

        Instant requestedAt = Instant.now();
        String handoffId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        ModuleHandoff handoff = handoffs.save(ModuleHandoff.prepare(
            handoffId, projectId, module, inputContract, inputId, inputHash, inputJson, operation, key,
            "/api/v3/projects/" + projectId + "/module-runs/" + runId, ownerId, requestedAt));
        ModuleRun run = runs.save(ModuleRun.notConnected(runId, handoff));
        return response(handoff, run, ownerId);
    }

    @Transactional(readOnly = true)
    public ModuleRunListResponse list(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return new ModuleRunListResponse(runs.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .stream().map(run -> response(run, currentSnapshotId(ownerId, projectId, run.getModule()))).toList());
    }

    @Transactional(readOnly = true)
    public ModuleRunResponse get(Long ownerId, Long projectId, String runId) {
        requireOwned(ownerId, projectId);
        ModuleRun run = runs.findByIdAndProjectIdAndDeletedAtIsNull(runId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "Module Run을 찾을 수 없습니다."));
        return response(run, currentSnapshotId(ownerId, projectId, run.getModule()));
    }

    private HandoffResponse response(ModuleHandoff handoff, ModuleRun run, Long ownerId) {
        boolean marketSeed = handoff.getModule() == ModuleType.MARKET_ANALYSIS
            || handoff.getModule() == ModuleType.BUSINESS_MODEL;
        boolean techOps = handoff.getModule() == ModuleType.TECH_OPS;
        boolean finance = handoff.getModule() == ModuleType.FINANCIAL_ANALYSIS;
        String inputType = marketSeed ? "MARKET_ANALYSIS_SEED"
            : techOps ? "TECH_OPS_INPUT"
            : finance ? "FINANCIAL_INPUT" : "PERSONA_INPUT";
        return new HandoffResponse(
            "module-handoff-v2", handoff.getId(), handoff.getProjectId(), handoff.getModule().name(),
            handoff.getInputSnapshotId(), handoff.getInputSnapshotHash(), inputType, "2.0",
            handoff.getRequestedAt(), new CallbackView(handoff.getCallbackMode(), handoff.getCallbackReference()),
            handoff.getRequestedOperation(), handoff.getStatus(), mapper.readTree(handoff.getInputSnapshotJson()),
            response(run, currentSnapshotId(ownerId, handoff.getProjectId(), run.getModule())));
    }

    private ModuleRunResponse response(ModuleRun run, String currentSnapshotId) {
        boolean stale = currentSnapshotId != null && !currentSnapshotId.equals(run.getInputSnapshotId());
        String status = stale ? ModuleRunStatus.STALE.name() : run.getStatus().name();
        return new ModuleRunResponse(
            run.getId(), run.getHandoffId(), run.getModule().name(), run.getInputSnapshotId(),
            run.getInputSnapshotHash(), status, stale, run.isCancelRequested(), run.getExternalRunReference(),
            run.getStartedAt(), run.getCompletedAt(), run.getResultReference(), run.getResultHash(),
            run.getSafeErrorCode());
    }

    private String currentMarketSeedId(Long projectId) {
        var portfolio = marketSeedSnapshots
            .findFirstByProjectIdAndSourceTypeAndStaleAtIsNullAndDeletedAtIsNullOrderByFinalizedAtDesc(
                projectId, "CONCEPT_PORTFOLIO_V2");
        if (portfolio.isPresent()) return portfolio.get().getId();
        return selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .flatMap(selection -> marketSeedSnapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(
                selection.getId(), projectId))
            .map(MarketAnalysisSeedSnapshot::getId)
            .orElse(null);
    }

    private MarketAnalysisSeedSnapshot currentMarketSeed(Long projectId) {
        var portfolio = marketSeedSnapshots
            .findFirstByProjectIdAndSourceTypeAndStaleAtIsNullAndDeletedAtIsNullOrderByFinalizedAtDesc(
                projectId, "CONCEPT_PORTFOLIO_V2");
        if (portfolio.isPresent()) return portfolio.get();
        var selection = selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        return marketSeedSnapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
    }

    private String currentSnapshotId(Long ownerId, Long projectId, ModuleType module) {
        if (module == ModuleType.MARKET_ANALYSIS || module == ModuleType.BUSINESS_MODEL) {
            return currentMarketSeedId(projectId);
        }
        if (module == ModuleType.TECH_OPS) {
            String marketSeedId = currentMarketSeedId(projectId);
            return marketSeedId == null ? null : techOpsSnapshots
                .findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(marketSeedId, projectId)
                .map(TechOpsInputSnapshot::getId).orElse(null);
        }
        if (module == ModuleType.FINANCIAL_ANALYSIS) {
            CurrentFinanceSources sources = currentFinanceSourcesOrNull(ownerId, projectId);
            return sources == null ? null : financialSnapshots
                .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByFinalizedAtAsc(
                    projectId, sources.marketVersionId(), sources.businessModelVersionId())
                .map(FinancialInputSnapshot::getId).orElse(null);
        }
        return null;
    }

    private TechOpsInputSnapshot currentTechOpsSnapshot(Long projectId) {
        String marketSeedId = currentMarketSeedId(projectId);
        if (marketSeedId == null) {
            throw new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE);
        }
        return techOpsSnapshots.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
            marketSeedId, projectId).orElseThrow(() -> new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_NOT_READY));
    }

    private FinancialInputSnapshot currentFinancialSnapshot(Long ownerId, Long projectId) {
        CurrentFinanceSources sources = currentFinanceSourcesOrNull(ownerId, projectId);
        if (sources == null) throw new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY);
        return financialSnapshots
            .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByFinalizedAtAsc(
                projectId, sources.marketVersionId(), sources.businessModelVersionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY));
    }

    private CurrentFinanceSources currentFinanceSourcesOrNull(Long ownerId, Long projectId) {
        var market = marketResearch.current(ownerId, projectId, MarketResearchRun.Kind.FULL);
        var businessModel = marketResearch.current(ownerId, projectId, MarketResearchRun.Kind.BM);
        if (market.version() == null || market.stale() || businessModel.version() == null || businessModel.stale()) {
            return null;
        }
        return new CurrentFinanceSources(market.version().id(), businessModel.version().id());
    }

    private record CurrentFinanceSources(Long marketVersionId, Long businessModelVersionId) {}

    private ModuleType parseModule(String value) {
        try {
            return ModuleType.valueOf(value);
        } catch (RuntimeException invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 외부 Module입니다.");
        }
    }

    private void requireOwnedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId).filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
