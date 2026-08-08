package com.aivle.backend.pipeline.techops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.techops.api.TechOpsApiModels.ProposalActionResponse;
import com.aivle.backend.pipeline.techops.api.TechOpsApiModels.ProposalDecisionRequest;
import com.aivle.backend.pipeline.techops.api.TechOpsController;
import com.aivle.backend.pipeline.techops.application.TechOpsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TechOpsControllerAsyncTests {
    @Test
    void alternativeReturnsAcceptedWhileProviderFreeDecisionReturnsOk() {
        TechOpsService service = mock(TechOpsService.class);
        CurrentUserProvider user = mock(CurrentUserProvider.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(user.currentUserId()).thenReturn(7L);
        when(request.getHeader("X-Request-Id")).thenReturn("request-1");
        ProposalDecisionRequest alternative = new ProposalDecisionRequest(
            "REJECT_AND_REQUEST_ALTERNATIVE", null);
        when(service.decideProposal(7L, 41L, "deliveryOrProductionMethod", alternative,
            "command-1", "request-1")).thenReturn(new ProposalActionResponse(
                null, "task-1", "task-1", "QUEUED", "REJECT_AND_REQUEST_ALTERNATIVE",
                "deliveryOrProductionMethod", 2));
        TechOpsController controller = new TechOpsController(service, user);

        var accepted = controller.decide(41L, "deliveryOrProductionMethod", alternative,
            "command-1", request);

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ProposalDecisionRequest edit = new ProposalDecisionRequest("EDIT_AND_ACCEPT", null);
        when(service.decideProposal(7L, 41L, "deliveryOrProductionMethod", edit,
            null, "request-1")).thenReturn(new ProposalActionResponse(
                null, null, null, "COMPLETED", "EDIT_AND_ACCEPT",
                "deliveryOrProductionMethod", 1));
        var completed = controller.decide(41L, "deliveryOrProductionMethod", edit, null, request);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
