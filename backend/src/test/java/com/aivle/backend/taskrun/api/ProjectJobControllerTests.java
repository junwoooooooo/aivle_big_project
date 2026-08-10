package com.aivle.backend.taskrun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.taskrun.service.ProjectJobQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectJobControllerTests {
    @Test
    void activeAndRecentEndpointsUseCurrentOwnerAndProject() {
        ProjectJobQueryService service = mock(ProjectJobQueryService.class);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(users.currentUserId()).thenReturn(7L);
        when(request.getHeader("X-Request-Id")).thenReturn("request-1");
        when(service.active(7L, 41L)).thenReturn(List.of());
        when(service.recent(7L, 41L)).thenReturn(List.of());
        ProjectJobController controller = new ProjectJobController(service, users);

        assertThat(controller.active(41L, request).data()).isEmpty();
        assertThat(controller.recent(41L, request).data()).isEmpty();
        verify(service).active(7L, 41L);
        verify(service).recent(7L, 41L);
    }
}
