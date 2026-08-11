package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aivle.backend.common.exception.GlobalExceptionHandler;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.common.web.RequestIdFilter;
import com.aivle.backend.common.web.RequestIds;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MarketResearchRequestIdTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void marketPostWithoutRequestHeaderUsesOneServerIdForTaskAndResponse() throws Exception {
        MarketResearchService service = mock(MarketResearchService.class);
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        when(service.startFull(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(new MarketResearchService.RunView(
                1L, "FULL", "QUEUED", "market-task-1", "QUEUED", null, false));
        MockMvc mvc = mvc(service, currentUser);

        MvcResult result = mvc.perform(post("/api/v3/projects/41/market-research")
                .header("Idempotency-Key", "market-idempotency-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isAccepted())
            .andReturn();

        String responseRequestId = result.getResponse().getHeader(RequestIds.HEADER);
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        ArgumentCaptor<String> correlation = ArgumentCaptor.forClass(String.class);
        verify(service).startFull(eq(7L), eq(41L), isNull(),
            eq("market-idempotency-1"), correlation.capture());
        assertThat(correlation.getValue()).isNotBlank().hasSizeLessThanOrEqualTo(128);
        assertThat(response.path("meta").path("requestId").asText())
            .isEqualTo(responseRequestId)
            .isEqualTo(correlation.getValue());
    }

    @Test
    void taskRunFailureFromMarketKeepsItsStatusAndRequestId() throws Exception {
        MarketResearchService service = mock(MarketResearchService.class);
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        when(service.startFull(anyLong(), anyLong(), any(), any(), any()))
            .thenThrow(new TaskRunFailure(
                "VALIDATION_ERROR", "TASK_RUN_INPUT_INVALID", HttpStatus.BAD_REQUEST, false));
        MockMvc mvc = mvc(service, currentUser);

        MvcResult result = mvc.perform(post("/api/v3/projects/41/market-research")
                .header("Idempotency-Key", "market-idempotency-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andReturn();

        String responseRequestId = result.getResponse().getHeader(RequestIds.HEADER);
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.path("meta").path("requestId").asText()).isEqualTo(responseRequestId);
    }

    @Test
    void taskAlreadyRunningFromMarketRemainsConflict() throws Exception {
        MarketResearchService service = mock(MarketResearchService.class);
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        when(service.startFull(anyLong(), anyLong(), any(), any(), any()))
            .thenThrow(new TaskRunFailure(
                "TASK_ALREADY_RUNNING", "SAME_INPUT_ACTIVE", HttpStatus.CONFLICT, false));

        mvc(service, currentUser).perform(post("/api/v3/projects/41/market-research")
                .header("Idempotency-Key", "market-idempotency-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isConflict());
    }

    private MockMvc mvc(MarketResearchService service, CurrentUserProvider currentUser) {
        return MockMvcBuilders.standaloneSetup(new MarketResearchController(service, currentUser))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
    }
}
