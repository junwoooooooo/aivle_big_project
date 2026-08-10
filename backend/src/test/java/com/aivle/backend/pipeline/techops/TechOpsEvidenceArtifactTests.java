package com.aivle.backend.pipeline.techops;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.api.TechOpsApiModels.EvidenceRequest;
import com.aivle.backend.pipeline.techops.application.*;
import com.aivle.backend.pipeline.techops.domain.TechOpsEvidenceReference;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.techops.repository.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TechOpsEvidenceArtifactTests {
    @Test
    void evidenceReferenceRequiresARealArtifactFromTheSameProject() {
        Harness h = new Harness();
        when(h.artifacts.findByIdAndProjectIdAndDeletedAtIsNull("artifact-1", 41L))
            .thenReturn(Optional.of(h.artifact));

        h.service.addEvidence(7L, 41L, new EvidenceRequest("QUOTE", "artifact-1", "공급사 견적"));

        var capture = org.mockito.ArgumentCaptor.forClass(TechOpsEvidenceReference.class);
        verify(h.evidence).save(capture.capture());
        assertThat(capture.getValue().getArtifactId()).isEqualTo("artifact-1");
        assertThat(capture.getValue().getArtifactRef()).isNull();
        assertThat(capture.getValue().getDisplayName()).isEqualTo("quote.pdf");
    }

    @Test
    void deletedOrForeignArtifactCannotBeNewlyReferenced() {
        Harness h = new Harness();

        assertThatThrownBy(() -> h.service.addEvidence(7L, 41L,
            new EvidenceRequest("BOM", "deleted-artifact", null)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ARTIFACT_NOT_FOUND));
        verify(h.evidence, never()).save(any());
    }

    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final ProjectRepository projects = mock(ProjectRepository.class);
        final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
        final MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        final TechOpsInputPreparationRepository preparations = mock(TechOpsInputPreparationRepository.class);
        final TechOpsEvidenceReferenceRepository evidence = mock(TechOpsEvidenceReferenceRepository.class);
        final ProjectEvidenceArtifactRepository artifacts = mock(ProjectEvidenceArtifactRepository.class);
        final TechOpsInputSnapshotRepository snapshots = mock(TechOpsInputSnapshotRepository.class);
        final TechOpsPreparationFactory preparationFactory = mock(TechOpsPreparationFactory.class);
        final TechOpsInputSnapshotFactory snapshotFactory = mock(TechOpsInputSnapshotFactory.class);
        final TechOpsReadiness readiness = mock(TechOpsReadiness.class);
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
        final JobEventPublisher events = mock(JobEventPublisher.class);
        final TechOpsService service = new TechOpsService(projects, selections, marketSeeds, preparations,
            evidence, artifacts, snapshots, preparationFactory, snapshotFactory, readiness, mapper,
            taskRuns, hasher, events);
        final ProjectEvidenceArtifact artifact = ProjectEvidenceArtifact.create("artifact-1", 41L,
            StorageType.LOCAL, "projects/41/evidence/a/uuid.pdf", "quote.pdf", "uuid.pdf",
            "application/pdf", 100L, "sha256:" + "a".repeat(64), 7L);

        Harness() {
            Project project = mock(Project.class); User owner = mock(User.class);
            when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
            when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
            ConceptSelection selection = mock(ConceptSelection.class); when(selection.getId()).thenReturn(9L);
            when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L))
                .thenReturn(Optional.of(selection));
            MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class); when(seed.getId()).thenReturn("seed-1");
            when(marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(9L, 41L)).thenReturn(Optional.of(seed));
            TechOpsInputPreparation preparation = TechOpsInputPreparation.create("prep-1", 41L, "seed-1",
                "sha256:" + "b".repeat(64), "{}", "{}", 7L);
            when(preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(41L, "seed-1"))
                .thenReturn(Optional.of(preparation));
            when(preparations.findLocked("prep-1", 41L)).thenReturn(Optional.of(preparation));
            when(snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull("prep-1", 41L)).thenReturn(Optional.empty());
            when(evidence.findAllByPreparationIdAndDeletedAtIsNullOrderByCreatedAtAsc("prep-1")).thenReturn(List.of());
            when(readiness.missing(any(), any())).thenReturn(List.of());
        }
    }
}
