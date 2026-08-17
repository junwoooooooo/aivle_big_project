package com.aivle.backend.pipeline.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessConceptSourceResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LaunchReadinessConceptSourceResolverTests {
    @Test
    void resolvesCurrentSelectedConceptWithoutMarketSeedOrBusinessModelDependencies() {
        ConceptPortfolioSelectionRepository selections = mock(ConceptPortfolioSelectionRepository.class);
        ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        ConceptPortfolioConcept concept = mock(ConceptPortfolioConcept.class);
        ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L))
            .thenReturn(Optional.of(selection));
        when(selection.getId()).thenReturn(8L);
        when(selection.getConceptId()).thenReturn("concept-1");
        when(selection.getRunId()).thenReturn("run-1");
        when(selection.getCandidateId()).thenReturn("candidate-1");
        when(selection.getSelectedConceptHash()).thenReturn(hash('a'));
        when(selection.getHypothesisRevision()).thenReturn(3);
        when(concepts.findByIdAndProjectIdAndDeletedAtIsNull("concept-1", 41L))
            .thenReturn(Optional.of(concept));
        when(concept.getRun()).thenReturn(run);
        when(run.getId()).thenReturn("run-1");
        when(concept.getId()).thenReturn("concept-1");
        when(concept.getCandidateId()).thenReturn("candidate-1");
        when(concept.getCanonicalHash()).thenReturn(hash('a'));
        when(concept.getConceptName()).thenReturn("선택 사업안");
        when(concept.getCandidateSnapshotJson()).thenReturn(
            "{\"candidate\":{\"conceptName\":\"선택 사업안\",\"targetUsers\":[\"운영팀\"]}}");
        var resolver = new LaunchReadinessConceptSourceResolver(selections, concepts, new ObjectMapper());

        var source = resolver.require(41L, "선택 사업안이 필요합니다.");
        var binding = resolver.binding(source);

        assertThat(source.currentConcept().path("conceptName").asText()).isEqualTo("선택 사업안");
        assertThat(binding.selectionId()).isEqualTo(8L);
        assertThat(binding.selectionRevision()).isEqualTo(3);
        assertThat(binding.conceptId()).isEqualTo("concept-1");
        assertThat(binding.selectedConceptHash()).isEqualTo(hash('a'));
    }

    private static String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
