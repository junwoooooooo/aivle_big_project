package com.aivle.backend.pipeline.techops.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptLegalRegulatoryReport;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptLegalRegulatoryReportRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchRunRepository;
import com.aivle.backend.pipeline.market.MarketResearchVersion;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TechOpsAdvisorySourceResolver {
    private final ProjectRepository projects;
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioConceptRepository concepts;
    private final ConceptLegalRegulatoryReportRepository legalReports;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final MarketResearchRunRepository marketRuns;
    private final MarketResearchVersionRepository marketVersions;
    private final TechOpsInputSnapshotRepository techOpsSnapshots;

    public Sources resolve(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> unavailable("current Concept Portfolio 선택이 필요합니다."));
        ConceptPortfolioConcept concept = concepts.findByIdAndProjectIdAndDeletedAtIsNull(
            selection.getConceptId(), projectId).filter(value -> value.getRun().getId().equals(selection.getRunId()))
            .orElseThrow(() -> unavailable("선택한 Concept 계보가 일치하지 않습니다."));
        MarketAnalysisSeedSnapshot seed = seeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value -> projectId.equals(value.getProjectId())
                && selection.getConceptId().equals(value.getPortfolioConceptId()))
            .orElseThrow(() -> unavailable("current Market Seed가 필요합니다."));
        ConceptLegalRegulatoryReport legal = seed.getLegalReportId() == null ? null
            : legalReports.findById(seed.getLegalReportId())
                .filter(value -> projectId.equals(value.getProjectId())
                    && selection.getId().equals(value.getSelectionId())
                    && selection.getConceptId().equals(value.getConceptId())
                    && "CURRENT".equals(value.getStatus())
                    && value.getDeletedAt() == null).orElse(null);

        MarketResearchRun marketRun = marketRuns
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, MarketResearchRun.Kind.FULL)
            .filter(value -> value.getState() == MarketResearchRun.State.SUCCEEDED
                && seed.getId().equals(value.getSourceMarketSeedSnapshotId())
                && selection.getId().equals(value.getSourcePortfolioSelectionId()))
            .orElseThrow(() -> unavailable("current Market FULL 결과가 필요합니다."));
        MarketResearchVersion market = marketVersions.findBySourceRunIdAndDeletedAtIsNull(marketRun.getId())
            .filter(value -> value.getKind() == MarketResearchRun.Kind.FULL
                && projectId.equals(value.getProject().getId()))
            .orElseThrow(() -> unavailable("current Market FULL 버전이 필요합니다."));
        MarketResearchRun bmRun = marketRuns
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, MarketResearchRun.Kind.BM)
            .filter(value -> value.getState() == MarketResearchRun.State.SUCCEEDED
                && market.getId().equals(value.getSourceMarketVersionId()))
            .orElseThrow(() -> unavailable("current Business Model 결과가 필요합니다."));
        MarketResearchVersion bm = marketVersions.findBySourceRunIdAndDeletedAtIsNull(bmRun.getId())
            .filter(value -> value.getKind() == MarketResearchRun.Kind.BM
                && projectId.equals(value.getProject().getId())
                && market.getId().equals(value.getSourceRun().getSourceMarketVersionId()))
            .orElseThrow(() -> unavailable("Market/BM 계보가 일치하지 않습니다."));
        TechOpsInputSnapshot snapshot = techOpsSnapshots
            .findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(seed.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_NOT_READY));
        return new Sources(selection, concept, legal, seed, market, bm, snapshot);
    }

    public boolean matches(Sources source, String snapshotId, String seedId, Long marketId,
            Long bmId, Long selectionId, String conceptId, String conceptHash) {
        return source.snapshot().getId().equals(snapshotId) && source.seed().getId().equals(seedId)
            && source.market().getId().equals(marketId) && source.businessModel().getId().equals(bmId)
            && source.selection().getId().equals(selectionId) && source.concept().getId().equals(conceptId)
            && source.selection().getSelectedConceptHash().equals(conceptHash);
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_NOT_READY, message);
    }

    public record Sources(ConceptPortfolioSelection selection, ConceptPortfolioConcept concept,
        ConceptLegalRegulatoryReport legal, MarketAnalysisSeedSnapshot seed,
        MarketResearchVersion market, MarketResearchVersion businessModel, TechOpsInputSnapshot snapshot) {}
}
