package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class MarketTwinControllerSemanticsTests {
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);

    @Test
    void longRunningMarketBmDraftAndSurveyStartsReturnAccepted() {
        when(currentUser.currentUserId()).thenReturn(7L);
        MarketResearchService market = mock(MarketResearchService.class);
        when(market.startFull(any(), any(), any(), any(), any()))
            .thenReturn(new MarketResearchService.RunView(1L, "FULL", "QUEUED", "m-1", "QUEUED", null, false));
        when(market.startBm(any(), any(), any(), any()))
            .thenReturn(new MarketResearchService.RunView(2L, "BM", "QUEUED", "b-1", "QUEUED", null, false));
        MarketResearchController marketController = new MarketResearchController(market, currentUser);

        assertThat(marketController.startFull(41L,
            new MarketResearchController.StartRequest("2026-08-11"), request).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(marketController.startBm(41L, new MarketResearchController.BmRequest(), request).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);

        TwinSurveyService twin = mock(TwinSurveyService.class);
        TwinSurveyStimulusDraftService draft = mock(TwinSurveyStimulusDraftService.class);
        when(draft.start(any(), any(), any(), any())).thenReturn(
            new TwinSurveyStimulusDraftService.DraftRunView("d-1", "QUEUED", null, false, null, "c-1", "Concept"));
        when(twin.start(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(
            new TwinSurveyService.RunView(3L, "QUEUED", 50, "t-1", "QUEUED", null, false));
        TwinSurveyController twinController = new TwinSurveyController(twin, draft, currentUser);
        ObjectMapper mapper = new ObjectMapper();

        assertThat(twinController.stimulusDraft(41L, null, request).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(twinController.start(41L, new TwinSurveyController.StartRequest(
            "choose one product", mapper.readTree("[{\"pairId\":\"P1\"}]"), 50), request).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);
    }
}
