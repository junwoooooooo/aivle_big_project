package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

class ProjectEventQueryServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final JobEventRepository events = mock(JobEventRepository.class);
    private final ProjectEventQueryService service = new ProjectEventQueryService(
        projects, events, new ObjectMapper());

    @Test
    void replayUsesGlobalEventRowIdAsProjectCursor() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L))
            .thenReturn(Optional.of(mock(com.aivle.backend.project.entity.Project.class)));
        when(events.findByProjectIdAndIdGreaterThanAndDeletedAtIsNullOrderById(
                eq(41L), eq(120L), any(Pageable.class))).thenReturn(List.of());
        when(events.findTopByProjectIdAndDeletedAtIsNullOrderByIdDesc(41L)).thenReturn(Optional.empty());

        var page = service.poll(7L, 41L, 120L);

        assertThat(page.nextEventId()).isEqualTo(120L);
        verify(events).findByProjectIdAndIdGreaterThanAndDeletedAtIsNullOrderById(
            eq(41L), eq(120L), any(Pageable.class));
    }

    @Test
    void rejectsProjectNotOwnedByCallerBeforeReadingEvents() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.replay(8L, 41L, 0L))
            .isInstanceOf(BusinessException.class);
    }
}
