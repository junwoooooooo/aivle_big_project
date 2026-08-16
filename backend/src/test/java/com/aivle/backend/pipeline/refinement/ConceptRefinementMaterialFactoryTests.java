package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptLegalRegulatoryReportRepository;
import com.aivle.backend.pipeline.market.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptRefinementMaterialFactoryTests {
    @Test
    void materialUsesExactSessionMarketAndBmVersionsAndPrioritizesCitedEvidence() {
        ObjectMapper mapper = new ObjectMapper();
        MarketResearchVersionRepository versions = mock(MarketResearchVersionRepository.class);
        ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
        ConceptLegalRegulatoryReportRepository reports = mock(ConceptLegalRegulatoryReportRepository.class);
        ConceptRefinementMaterialFactory factory = new ConceptRefinementMaterialFactory(
            versions, concepts, reports, mapper);
        CompletedSource source = new CompletedSource("session-1", 91L, 92L, "seed-1", 31L, 4, 3,
            "sha256:" + "a".repeat(64));
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(selection.getId()).thenReturn(31L);
        when(selection.getConceptId()).thenReturn("concept-1");
        when(selection.getHypothesisRevision()).thenReturn(4);
        MarketResearchVersion market = mock(MarketResearchVersion.class);
        MarketResearchVersion bm = mock(MarketResearchVersion.class);
        when(market.getResultJson()).thenReturn("""
            {"evidence":[{"id":"E-2","value":2},{"id":"E-1","value":1}]}
            """);
        when(bm.getResultJson()).thenReturn("""
            {"canvas":{"cells":[{"marketEvidenceIds":["E-1"]}]},
             "bm":{"weaknesses":["채널 확인 필요"],"risks":[]}}
            """);
        when(versions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(91L, 41L, MarketResearchRun.Kind.FULL))
            .thenReturn(Optional.of(market));
        when(versions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(92L, 41L, MarketResearchRun.Kind.BM))
            .thenReturn(Optional.of(bm));
        ConceptPortfolioConcept concept = mock(ConceptPortfolioConcept.class);
        when(concept.getCandidateSnapshotJson()).thenReturn("""
            {"candidateId":"candidate-1","candidate":{"price":"10,000원"}}
            """);
        when(concept.getLegalReviewJson()).thenReturn("{" + "\"findings\":[]}");
        when(concepts.findByIdAndProjectIdAndDeletedAtIsNull("concept-1", 41L))
            .thenReturn(Optional.of(concept));
        when(reports.findBySelectionIdAndStatusAndDeletedAtIsNull(31L, "CURRENT"))
            .thenReturn(Optional.empty());

        var input = factory.input(41L, selection, source, 1);

        var material = input.path("refinementMaterial");
        assertThat(material.path("sourceBinding").path("marketVersionId").asLong()).isEqualTo(91L);
        assertThat(material.path("sourceBinding").path("bmVersionId").asLong()).isEqualTo(92L);
        assertThat(material.path("marketEvidence").path(0).path("id").asText()).isEqualTo("E-1");
        assertThat(material.path("canvas").path("cells")).hasSize(1);
        verify(versions).findByIdAndProjectIdAndKindAndDeletedAtIsNull(91L, 41L, MarketResearchRun.Kind.FULL);
        verify(versions).findByIdAndProjectIdAndKindAndDeletedAtIsNull(92L, 41L, MarketResearchRun.Kind.BM);
        verify(versions, never()).findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(anyLong(), any());
    }
}
