package com.aivle.backend.pipeline.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.integration.application.ModuleIntegrationService;
import com.aivle.backend.pipeline.integration.repository.ModuleHandoffRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ModuleIntegrationV2MarketSeedTests {
    @Test
    void currentMarketInputPrefersV2SeedWithoutLegacySelectionConversion() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ConceptSelectionRepository legacySelections = mock(ConceptSelectionRepository.class);
        MarketAnalysisSeedSnapshotRepository seeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        ModuleIntegrationService service = new ModuleIntegrationService(projects, legacySelections, seeds,
            mock(ModuleHandoffRepository.class), mock(ModuleRunRepository.class),
            mock(TechOpsInputSnapshotRepository.class), mock(FinancialInputSnapshotRepository.class),
            mock(com.aivle.backend.pipeline.market.MarketResearchService.class),
            new ObjectMapper());
        MarketAnalysisSeedSnapshot v2 = MarketAnalysisSeedSnapshot.createPortfolio("seed-v2", 41L, 17L,
            "concept-v2", "report-v2", "2.0", hash('a'), hash('b'), "{}", 7L,
            Instant.parse("2026-08-11T00:00:00Z"));
        when(seeds.findFirstByProjectIdAndSourceTypeAndStaleAtIsNullAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, "CONCEPT_PORTFOLIO_V2")).thenReturn(Optional.of(v2));

        MarketAnalysisSeedSnapshot result = ReflectionTestUtils.invokeMethod(service, "currentMarketSeed", 41L);

        assertThat(result).isSameAs(v2);
        verify(legacySelections, never()).findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L);
    }

    private String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
