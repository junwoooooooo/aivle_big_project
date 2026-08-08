package com.aivle.backend.pipeline.marketseed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalStatus;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalAssessmentRepository;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalEvidenceLinkRepository;
import com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedSnapshotFactory;
import com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedSnapshotService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.repository.ConceptHypothesisDecisionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketAnalysisSeedSnapshotServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final ConceptHypothesisDecisionRepository decisions = mock(ConceptHypothesisDecisionRepository.class);
    private final ConceptRepository concepts = mock(ConceptRepository.class);
    private final IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
    private final IdeaBriefFieldRepository briefFields = mock(IdeaBriefFieldRepository.class);
    private final ConceptLegalAssessmentRepository assessments = mock(ConceptLegalAssessmentRepository.class);
    private final ConceptLegalEvidenceLinkRepository evidenceLinks = mock(ConceptLegalEvidenceLinkRepository.class);
    private final MarketAnalysisSeedSnapshotRepository snapshots = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final MarketAnalysisSeedSnapshotFactory factory = mock(MarketAnalysisSeedSnapshotFactory.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketAnalysisSeedSnapshotService service = new MarketAnalysisSeedSnapshotService(projects, selections,
        decisions, concepts, briefs, briefFields, assessments, evidenceLinks, snapshots, factory,
        new SnapshotHasher(mapper), mapper);
    private final ConceptSelection selection = mock(ConceptSelection.class);

    @BeforeEach
    void ownedCurrentSelection() {
        Project project = mock(Project.class); User owner = mock(User.class);
        when(owner.getId()).thenReturn(11L); when(project.getOwner()).thenReturn(owner);
        when(projects.findByIdForUpdate(7L)).thenReturn(Optional.of(project));
        when(selection.getId()).thenReturn(9L); when(selection.getConceptId()).thenReturn("concept-1");
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(7L)).thenReturn(Optional.of(selection));
    }

    @Test
    void repeatedFinalizationReturnsTheSameImmutableSnapshot() {
        var existing = MarketAnalysisSeedSnapshot.create("seed-1", 7L, 9L, "concept-1", "2.0",
            "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64), "{\"hash\":\"sha256:" + "b".repeat(64) + "\"}",
            11L, Instant.EPOCH);
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(9L, 7L)).thenReturn(Optional.of(existing));

        var response = service.finalizeSnapshot(11L, 7L);

        assertThat(response.snapshotId()).isEqualTo("seed-1");
        assertThat(response.createdAt()).isEqualTo(Instant.EPOCH);
        verify(concepts, never()).findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull("concept-1", 7L);
        verify(snapshots, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void incompleteHypothesisDecisionsBlockSnapshotCreation() {
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(9L, 7L)).thenReturn(Optional.empty());
        Concept concept = mock(Concept.class);
        when(concept.getLegalStatus()).thenReturn(ConceptLegalStatus.IMPLEMENTABLE);
        when(concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull("concept-1", 7L)).thenReturn(Optional.of(concept));
        when(decisions.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.finalizeSnapshot(11L, 7L))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
        verify(snapshots, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
