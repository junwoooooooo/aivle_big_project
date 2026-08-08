package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.finance.api.FinancialApiModels.EstimateActionResponse;
import com.aivle.backend.pipeline.finance.api.FinancialApiModels.EstimateDecisionRequest;
import com.aivle.backend.pipeline.finance.api.FinancialController;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FinancialControllerAsyncTests {
    @Test
    void generateAndAlternativeReturnAcceptedWhileAcceptReturnsOk() {
        FinancialService service = mock(FinancialService.class);
        CurrentUserProvider user = mock(CurrentUserProvider.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(user.currentUserId()).thenReturn(7L);
        when(request.getHeader("X-Request-Id")).thenReturn("request-1");
        when(service.generateEstimate(7L, 41L, "totalMarketingCost", "command-1", "request-1"))
            .thenReturn(new EstimateActionResponse(null, "task-1", "task-1", "QUEUED", "GENERATE", "totalMarketingCost", 1));
        FinancialController controller = new FinancialController(service, user);

        assertThat(controller.generateEstimate(41L, "totalMarketingCost", "command-1", request).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);

        EstimateDecisionRequest alternative = new EstimateDecisionRequest("REQUEST_ALTERNATIVE", null);
        when(service.decideEstimate(7L, 41L, "totalMarketingCost", alternative, "command-2", "request-1"))
            .thenReturn(new EstimateActionResponse(null, "task-2", "task-2", "QUEUED", "REQUEST_ALTERNATIVE", "totalMarketingCost", 2));
        assertThat(controller.decideEstimate(41L, "totalMarketingCost", alternative, "command-2", request).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);

        EstimateDecisionRequest accept = new EstimateDecisionRequest("ACCEPT", null);
        when(service.decideEstimate(7L, 41L, "totalMarketingCost", accept, null, "request-1"))
            .thenReturn(new EstimateActionResponse(null, null, null, "COMPLETED", "ACCEPT", "totalMarketingCost", 1));
        assertThat(controller.decideEstimate(41L, "totalMarketingCost", accept, null, request).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }
}
