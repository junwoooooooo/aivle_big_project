package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.repository.SelectedConceptSnapshotRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.planning.repository.FinalizedPlanningSnapshotRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectModuleStatusServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final SelectedConceptSnapshotRepository snapshots = mock(SelectedConceptSnapshotRepository.class);
    private final ModuleRunRepository runs = mock(ModuleRunRepository.class);
    private final FinalizedPlanningSnapshotRepository finalized = mock(FinalizedPlanningSnapshotRepository.class);
    private final ProjectModuleStatusService service = new ProjectModuleStatusService(projects, selections, snapshots, runs, finalized);

    @Test
    void returnsSafeDefaultsWithoutUsingProjectStage() {
        Project project = mock(Project.class);
        when(project.getDescription()).thenReturn("해결할 문제와 대상 고객");
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));

        var modules = service.findAll(7L, 41L);

        assertThat(modules).extracting(ProjectModuleStatusResponse::module).containsExactly(
            PipelineModuleType.IDEA,
            PipelineModuleType.CONCEPT_FACTORY,
            PipelineModuleType.CONCEPT_SELECTION,
            PipelineModuleType.MARKET_ANALYSIS,
            PipelineModuleType.BUSINESS_PERSONA_TEST,
            PipelineModuleType.MARKETING
        );
        assertThat(modules).extracting(ProjectModuleStatusResponse::status).containsExactly(
            PipelineModuleStatus.READY,
            PipelineModuleStatus.NOT_READY,
            PipelineModuleStatus.NOT_READY,
            PipelineModuleStatus.NOT_CONNECTED,
            PipelineModuleStatus.NOT_CONNECTED,
            PipelineModuleStatus.NOT_READY
        );
        assertThat(modules).allSatisfy(module -> {
            assertThat(module.projectId()).isEqualTo(41L);
            assertThat(module.statusLabelKey()).startsWith("module.status.");
            assertThat(module.activeRunId()).isNull();
            assertThat(module.sourceSnapshotId()).isNull();
            assertThat(module.updatedAt()).isNull();
        });
        verify(projects).findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L);
    }

    @Test
    void hidesProjectsNotOwnedByTheCurrentUser() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAll(8L, 41L))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
