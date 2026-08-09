package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.concept.api.ConceptFactoryApiModels.CreateRunRequest;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryRetryPolicy;
import com.aivle.backend.pipeline.concept.repository.*;
import com.aivle.backend.pipeline.idea.domain.*;
import com.aivle.backend.pipeline.idea.repository.*;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.legal.repository.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptFactoryJurisdictionGuardTests {
    @Test
    void lockedForeignRegionStopsBeforeSlotsTasksOrLegalProviderWorkAreCreated() {
        ConceptFactoryRunRepository runs = mock(ConceptFactoryRunRepository.class);
        ConceptSlotRepository slots = mock(ConceptSlotRepository.class);
        IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
        IdeaBriefFieldRepository fields = mock(IdeaBriefFieldRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        TaskRunService tasks = mock(TaskRunService.class);
        ConceptFactoryService service = new ConceptFactoryService(
            runs, slots, mock(ConceptRepository.class), briefs, fields, projects,
            mock(ConceptAttemptRepository.class), mock(ConceptRejectionSummaryRepository.class),
            mock(ConceptLegalAssessmentRepository.class),
            mock(ConceptLegalEvidenceLinkRepository.class), tasks, mock(CanonicalInputHasher.class),
            new ObjectMapper(), mock(JobEventPublisher.class), new LegalJurisdictionResolver(),
            new ConceptFactoryRetryPolicy());
        Project project = mock(Project.class); User owner = mock(User.class); IdeaBrief brief = mock(IdeaBrief.class);
        IdeaBriefField region = mock(IdeaBriefField.class);
        when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
        when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
        when(brief.isConfirmed()).thenReturn(true); when(brief.getId()).thenReturn("brief-1");
        when(briefs.findByIdAndProjectIdAndDeletedAtIsNull("brief-1", 41L)).thenReturn(Optional.of(brief));
        when(region.getDecisionState()).thenReturn(IdeaDecisionState.LOCKED);
        when(region.getProvenance()).thenReturn(IdeaFieldProvenance.USER_CONFIRMED);
        when(region.getFieldValue()).thenReturn("미국 캘리포니아");
        when(fields.findByBriefIdAndFieldKey("brief-1", "targetRegion")).thenReturn(Optional.of(region));

        assertThatThrownBy(() -> service.create(7L, 41L, new CreateRunRequest("brief-1")))
            .isInstanceOfSatisfying(BusinessException.class, failure ->
                org.assertj.core.api.Assertions.assertThat(failure.getErrorCode())
                    .isEqualTo(ErrorCode.LEGAL_JURISDICTION_UNSUPPORTED));
        verify(runs, never()).save(any());
        verify(slots, never()).saveAll(any());
        verifyNoInteractions(tasks);
    }
}
