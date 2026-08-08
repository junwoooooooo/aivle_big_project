package com.aivle.backend.pipeline.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.selection.api.ConceptSelectionController;
import com.aivle.backend.pipeline.selection.api.SelectionApiModels.HypothesisAction;
import com.aivle.backend.pipeline.selection.api.SelectionApiModels.HypothesisActionRequest;
import com.aivle.backend.pipeline.selection.api.SelectionApiModels.HypothesisActionResponse;
import com.aivle.backend.pipeline.selection.application.ConceptSelectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConceptSelectionControllerAsyncTests {
    @Test
    void providerBackedActionReturnsAcceptedWithTaskIdentity() {
        ConceptSelectionService service = mock(ConceptSelectionService.class);
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        HypothesisActionRequest body = new HypothesisActionRequest(
            HypothesisAction.REQUEST_ALTERNATIVE, 1, null);
        HypothesisActionResponse queued = new HypothesisActionResponse(null, false,
            "task-1", "task-1", "QUEUED", "REQUEST_ALTERNATIVE", "CHANNELS", 1);
        when(service.decide(7L, 41L, "CHANNELS", body, "command-1", "request-1"))
            .thenReturn(queued);
        when(servletRequest.getHeader("X-Request-Id")).thenReturn("request-1");

        var response = new ConceptSelectionController(service, currentUser)
            .decide(41L, "CHANNELS", body, "command-1", servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().data().taskRunId()).isEqualTo("task-1");
    }

    @Test
    void providerFreeActionRemainsSynchronous() {
        ConceptSelectionService service = mock(ConceptSelectionService.class);
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        HypothesisActionRequest body = new HypothesisActionRequest(HypothesisAction.ACCEPT, 1, null);
        when(service.decide(7L, 41L, "CHANNELS", body, "command-2", null))
            .thenReturn(new HypothesisActionResponse(null, true, null, null,
                "COMPLETED", "ACCEPT", "CHANNELS", 1));

        var response = new ConceptSelectionController(service, currentUser)
            .decide(41L, "CHANNELS", body, "command-2", servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
