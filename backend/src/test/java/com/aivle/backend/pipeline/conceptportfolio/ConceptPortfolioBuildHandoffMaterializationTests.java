package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ConceptPortfolioBuildHandoffMaterializationTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void pythonCompatibleMarketSeedHashMaterializesTheCurrentV2Seed() {
        ObjectMapper mapper = new ObjectMapper();
        ConceptPortfolioSelectionRepository selections = mock(ConceptPortfolioSelectionRepository.class);
        ConceptPortfolioHypothesisDecisionRepository hypotheses = mock(ConceptPortfolioHypothesisDecisionRepository.class);
        ConceptPortfolioDeltaLegalReviewRepository deltas = mock(ConceptPortfolioDeltaLegalReviewRepository.class);
        ConceptLegalRegulatoryReportRepository reports = mock(ConceptLegalRegulatoryReportRepository.class);
        MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        ConceptPortfolioSelectionService selectionService = mock(ConceptPortfolioSelectionService.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        ConceptPortfolioJsonHasher hasher = new ConceptPortfolioJsonHasher(mapper);
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);
        var service = new ConceptPortfolioSelectionMaterializationService(selections, hypotheses, deltas,
            reports, marketSeeds, selectionService, hasher, taskRuns, mapper, clock);

        ConceptPortfolioSelection selection = ConceptPortfolioSelection.create(42L, "run", "concept-1",
            "candidate-1", HASH, HASH, "명시적 사용자 선택", HASH, "selection-key", 7L, clock.instant());
        ReflectionTestUtils.setField(selection, "id", 17L);
        ReflectionTestUtils.setField(selection, "status", ConceptPortfolioSelectionStatus.LEGAL_REPORT_READY);
        selection.attachTask("task-1", "BUILD_HANDOFF");
        when(selections.findLocked(17L)).thenReturn(Optional.of(selection));
        ConceptLegalRegulatoryReport report = mock(ConceptLegalRegulatoryReport.class);
        when(report.getId()).thenReturn("legal-report-1");
        when(reports.findBySelectionIdAndStatusAndDeletedAtIsNull(17L, "CURRENT")).thenReturn(Optional.of(report));

        var market = mapper.readTree("""
            {"contract":"market-analysis-seed-snapshot-v1","schemaVersion":"2.0",
             "snapshotId":"market-seed-1","sourceSnapshotHash":"%s",
             "selectedConcept":{"conceptId":"concept-1"},
             "confirmedHypotheses":{"PRE_MARKET_SOM":{"amount":240000000.0,"currency":"KRW"}},
             "label":"e\u0301"}
            """.formatted(HASH));
        String marketHash = hasher.productionCompatibleHash(market);
        var result = mapper.createObjectNode();
        result.put("contract", "concept-portfolio-v2-selection-action-result-v1");
        result.put("schemaVersion", "1.0");
        result.put("action", "BUILD_HANDOFF");
        result.put("marketSeedSnapshotHash", marketHash);
        var handoff = result.putObject("handoff");
        handoff.put("compatibility", "PASS");
        handoff.set("marketAnalysisSeedSnapshot", market);
        ExecutionResponse response = new ExecutionResponse("internal-ai-execution-v1",
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION.name(), "1.0", "task-1", "attempt-1",
            "corr-1", HASH, "1.0", result, null, null, null);
        TaskRunWorkerContext context = new TaskRunWorkerContext("task-1", 42L, 7L,
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION, "CONCEPT_PORTFOLIO_SELECTION", "17",
            "{\"action\":\"BUILD_HANDOFF\",\"expectedHypothesisRevision\":0}", HASH,
            "handoff-key", "corr-1", "internal-ai-execution-v1", "1.0", "ko-KR", 1, 3);

        assertThat(service.complete(new TaskRunService.Claim("task-1", "attempt-1", "claim"), context, response))
            .isEqualTo("BUILD_HANDOFF");
        ArgumentCaptor<MarketAnalysisSeedSnapshot> saved = ArgumentCaptor.forClass(MarketAnalysisSeedSnapshot.class);
        verify(marketSeeds).save(saved.capture());
        assertThat(saved.getValue().getSourceType()).isEqualTo("CONCEPT_PORTFOLIO_V2");
        assertThat(saved.getValue().getPortfolioSelectionId()).isEqualTo(17L);
        assertThat(saved.getValue().getSnapshotHash()).isEqualTo(marketHash);
        assertThat(mapper.readTree(saved.getValue().getSnapshotJson()).path("confirmedHypotheses")
            .path("PRE_MARKET_SOM").path("amount").asDouble()).isEqualTo(240000000.0);
        assertThat(selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        verify(taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("claim"), anyString(), eq(HASH), eq("1.0"));
    }
}
