package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.market.BmPlanPreparationService.PlanView;
import com.aivle.backend.pipeline.marketing.application.*;
import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketingSourceSnapshotServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CurrentConceptSourceResolver currentConcepts = mock(CurrentConceptSourceResolver.class);
    private final MarketingSourceSnapshotRepository sources = mock(MarketingSourceSnapshotRepository.class);
    private final ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
    private final MarketingSourceSnapshotFactory factory = mock(MarketingSourceSnapshotFactory.class);
    private final MarketingSourceSnapshotService service = new MarketingSourceSnapshotService(
        projects, currentConcepts, sources, concepts, factory, mapper);
    private final ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
    private final MarketAnalysisSeedSnapshot seed = MarketAnalysisSeedSnapshot.createPortfolio(
        "market-seed-v2", 1L, 77L, "portfolio-concept-1", "legal-1", "2.0",
        "sha256:" + "d".repeat(64), "sha256:" + "e".repeat(64), "{}", 7L, Instant.EPOCH);
    private final PlanView bm = new PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3);
    private CurrentConceptSourceResolver.Source current;

    @BeforeEach
    void currentOwnedProject() {
        Project project = mock(Project.class); User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
        when(projects.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(1L, 7L)).thenReturn(Optional.of(project));
        when(selection.getId()).thenReturn(77L);
        when(selection.getConceptId()).thenReturn("portfolio-concept-1");
        when(selection.getHypothesisRevision()).thenReturn(5);
        current = new CurrentConceptSourceResolver.Source(selection, seed, bm);
        when(currentConcepts.require(eq(1L), anyString())).thenReturn(current);
    }

    @Test
    void repeatedFinalizationReturnsTheSameExactLineageSnapshot() {
        MarketingSourceSnapshot existing = MarketingSourceSnapshot.createPortfolio("source-1", 1L,
            seed.getId(), 77L, "portfolio-concept-1", 5, 3, "2.1",
            "sha256:" + "c".repeat(64), "{}", 7L, Instant.EPOCH);
        when(sources.findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
            seed.getId(), 5, 3, 1L)).thenReturn(Optional.of(existing));

        var result = service.finalizeSnapshot(7L, 1L);

        assertThat(result.snapshotId()).isEqualTo("source-1");
        assertThat(result.sourceSelectionRevision()).isEqualTo(5);
        assertThat(result.sourceBmPlanRevision()).isEqualTo(3);
        verify(sources, never()).save(any());
    }

    @Test
    void missingCurrentConceptAuthorityIsRejected() {
        when(currentConcepts.require(eq(1L), anyString()))
            .thenThrow(new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        assertThatThrownBy(() -> service.finalizeSnapshot(7L, 1L))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MODULE_INPUT_STALE));
    }

    @Test
    void finalizedSourceBindsSeedSelectionAndBmRevisions() {
        ConceptPortfolioConcept concept = mock(ConceptPortfolioConcept.class);
        when(concepts.findByIdAndProjectIdAndDeletedAtIsNull("portfolio-concept-1", 1L))
            .thenReturn(Optional.of(concept));
        var body = mapper.createObjectNode();
        when(factory.create(anyString(), any(), eq(seed), eq(concept), eq(5), eq(bm)))
            .thenReturn(new MarketingSourceSnapshotFactory.BuiltSnapshot(body,
                "sha256:" + "f".repeat(64)));
        when(sources.findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
            seed.getId(), 5, 3, 1L)).thenReturn(Optional.empty());
        when(sources.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.finalizeSnapshot(7L, 1L);

        assertThat(result.marketAnalysisSeedSnapshotId()).isEqualTo(seed.getId());
        assertThat(result.selectionId()).isEqualTo(77L);
        assertThat(result.sourceSelectionRevision()).isEqualTo(5);
        assertThat(result.sourceBmPlanRevision()).isEqualTo(3);
    }
}
