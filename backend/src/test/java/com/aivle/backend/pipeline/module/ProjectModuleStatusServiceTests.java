package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptSlotRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.planning.repository.FinalizedPlanningSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.repository.SelectedConceptSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectModuleStatusServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
    private final ConceptFactoryRunRepository conceptRuns = mock(ConceptFactoryRunRepository.class);
    private final ConceptSlotRepository slots = mock(ConceptSlotRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final SelectedConceptSnapshotRepository snapshots = mock(SelectedConceptSnapshotRepository.class);
    private final ModuleRunRepository runs = mock(ModuleRunRepository.class);
    private final FinalizedPlanningSnapshotRepository finalized = mock(FinalizedPlanningSnapshotRepository.class);
    private final MarketingContentRepository marketing = mock(MarketingContentRepository.class);
    private final ProjectModuleStatusService service = new ProjectModuleStatusService(
        projects, briefs, conceptRuns, slots, selections, snapshots, runs, finalized, marketing);

    @Test
    void derivesIdeaAndConceptFromCanonicalDomainsWithoutProjectDescription() {
        Project project = mock(Project.class);
        IdeaBrief brief = mock(IdeaBrief.class);
        ConceptFactoryRun run = mock(ConceptFactoryRun.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 7, 10, 0);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(briefs.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(brief));
        when(brief.getStatus()).thenReturn(IdeaBriefStatus.CONFIRMED);
        when(brief.getConfirmedSnapshotId()).thenReturn("brief-snapshot");
        when(brief.getUpdatedAt()).thenReturn(updatedAt);
        when(conceptRuns.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(run));
        when(run.getId()).thenReturn("run-1");
        when(run.getTaskRunId()).thenReturn("task-1");
        when(run.getSourceIdeaBriefSnapshotId()).thenReturn("brief-snapshot");
        when(run.getStatus()).thenReturn(ConceptFactoryRunStatus.GENERATING);
        when(slots.countByRunIdAndStatusAndDeletedAtIsNull("run-1", ConceptSlotStatus.ELIGIBLE)).thenReturn(3L);

        var modules = service.findAll(7L, 41L);

        assertThat(modules).extracting(ProjectModuleStatusResponse::module).containsExactly(
            PipelineModuleType.IDEA, PipelineModuleType.CONCEPT_FACTORY, PipelineModuleType.CONCEPT_SELECTION,
            PipelineModuleType.MARKET_ANALYSIS, PipelineModuleType.BUSINESS_PERSONA_TEST, PipelineModuleType.MARKETING);
        assertThat(modules.get(0).status()).isEqualTo(PipelineModuleStatus.COMPLETED);
        assertThat(modules.get(0).confirmedSnapshotId()).isEqualTo("brief-snapshot");
        assertThat(modules.get(1).status()).isEqualTo(PipelineModuleStatus.RUNNING);
        assertThat(modules.get(1).activeTaskRunId()).isEqualTo("task-1");
        assertThat(modules.get(1).activeJobId()).isEqualTo("task-1");
        assertThat(modules.get(1).eligibleCount()).isEqualTo(3L);
        verify(project, never()).getDescription();
    }

    @Test
    void returnsNeedsInputWhenNoIdeaBriefExists() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        var modules = service.findAll(7L, 41L);
        assertThat(modules.get(0).status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(modules.get(1).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(5).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
    }

    @Test
    void hidesProjectsNotOwnedByTheCurrentUser() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findAll(8L, 41L))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
