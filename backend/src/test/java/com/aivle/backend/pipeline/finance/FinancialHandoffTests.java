package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.finance.domain.FinancialInputSnapshot;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.integration.api.IntegrationApiModels.CreateHandoffRequest;
import com.aivle.backend.pipeline.integration.application.ModuleIntegrationService;
import com.aivle.backend.pipeline.integration.repository.ModuleHandoffRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinancialHandoffTests {
    @Test
    void handoffUsesOnlyTheImmutableFinancialInputSnapshot() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
        MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        ModuleHandoffRepository handoffs = mock(ModuleHandoffRepository.class);
        ModuleRunRepository runs = mock(ModuleRunRepository.class);
        TechOpsInputSnapshotRepository techSnapshots = mock(TechOpsInputSnapshotRepository.class);
        FinancialInputSnapshotRepository financialSnapshots = mock(FinancialInputSnapshotRepository.class);
        Project project = mock(Project.class); User owner = mock(User.class); ConceptSelection selection = mock(ConceptSelection.class);
        MarketAnalysisSeedSnapshot marketSeed = mock(MarketAnalysisSeedSnapshot.class);
        MarketResearchService marketResearch = mock(MarketResearchService.class);
        FinancialInputSnapshot snapshot = mock(FinancialInputSnapshot.class);
        when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
        when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project)); when(selection.getId()).thenReturn(13L);
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(marketSeed.getId()).thenReturn("market-seed-1");
        when(marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L)).thenReturn(Optional.of(marketSeed));
        when(marketResearch.current(7L, 41L, MarketResearchRun.Kind.FULL))
            .thenReturn(currentVersion(101L, "FULL"));
        when(marketResearch.current(7L, 41L, MarketResearchRun.Kind.BM))
            .thenReturn(currentVersion(201L, "BM"));
        when(snapshot.getId()).thenReturn("finance-1"); when(snapshot.getSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
        when(snapshot.getSnapshotJson()).thenReturn("{\"contract\":\"financial-input-snapshot-v1\"}");
        when(financialSnapshots
            .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByFinalizedAtAsc(
                41L, 101L, 201L))
            .thenReturn(Optional.of(snapshot));
        when(handoffs.findByIdempotencyKeyAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(handoffs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new ModuleIntegrationService(projects, selections, marketSeeds, handoffs, runs,
            techSnapshots, financialSnapshots, marketResearch, new ObjectMapper());

        var response = service.create(7L, 41L,
            new CreateHandoffRequest("FINANCIAL_ANALYSIS", "finance-1", "START_FINANCIAL_ANALYSIS"));

        assertThat(response.inputSnapshotType()).isEqualTo("FINANCIAL_INPUT");
        assertThat(response.inputSchemaVersion()).isEqualTo("2.0");
        assertThat(response.inputSnapshotId()).isEqualTo("finance-1");
        assertThat(response.input().path("contract").asText()).isEqualTo("financial-input-snapshot-v1");
        verifyNoInteractions(techSnapshots);

        var staleBm = currentVersion(201L, "BM");
        staleBm = new MarketResearchService.CurrentView(staleBm.run(), staleBm.version(), staleBm.source(), true);
        when(marketResearch.current(7L, 41L, MarketResearchRun.Kind.BM)).thenReturn(staleBm);
        assertThatThrownBy(() -> service.create(7L, 41L,
            new CreateHandoffRequest("FINANCIAL_ANALYSIS", "finance-1", "START_FINANCIAL_ANALYSIS")))
            .isInstanceOf(BusinessException.class);
    }

    private MarketResearchService.CurrentView currentVersion(Long id, String kind) {
        return new MarketResearchService.CurrentView(null,
            new MarketResearchService.VersionView(id, kind, 1, new ObjectMapper().createObjectNode(),
                0, 0, null, null, 0, 0, 0), null, false);
    }
}
