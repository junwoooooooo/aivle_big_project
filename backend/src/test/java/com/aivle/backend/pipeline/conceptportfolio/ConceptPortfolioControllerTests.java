package com.aivle.backend.pipeline.conceptportfolio;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioApiModels.RunResponse;
import com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioApiModels.ContinuationAcceptedResponse;
import com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioController;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioService;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioContinuationService;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ConceptPortfolioControllerTests {
    @Mock ConceptPortfolioService service;
    @Mock ConceptPortfolioContinuationService continuationService;
    @Mock CurrentUserProvider users;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
            new ConceptPortfolioController(service, continuationService, users)).build();
    }

    @Test
    void postCreatesOfficialPortfolioRunWithAcceptedStatus() throws Exception {
        when(users.currentUserId()).thenReturn(7L);
        when(service.create(eq(7L), eq(42L), any())).thenReturn(new RunResponse(
            "run", "brief", "sha256:" + "a".repeat(64), ConceptPortfolioRunStatus.QUEUED,
            5, 0, 0, 0, "task", "task", null, null, null, "WAIT",
            Instant.parse("2026-08-10T00:00:00Z")));
        mvc.perform(post("/api/v3/projects/42/concept-portfolio-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ideaBriefSnapshotId\":\"brief\",\"maxConcepts\":5,\"idempotencyKey\":\"idem\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.runId").value("run"))
            .andExpect(jsonPath("$.data.productStatus").value("QUEUED"));
    }

    @Test
    void postRejectsMaximumOutsideOneToFive() throws Exception {
        for (int invalid : new int[] {0, 6}) {
            mvc.perform(post("/api/v3/projects/42/concept-portfolio-runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ideaBriefSnapshotId\":\"brief\",\"maxConcepts\":" + invalid
                        + ",\"idempotencyKey\":\"idem\"}"))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void candidateInputResponseReturnsAcceptedAndRejectsBrowserArtifact() throws Exception {
        when(users.currentUserId()).thenReturn(7L);
        when(continuationService.submit(eq(7L), eq(42L), eq("run"), eq("input"), any()))
            .thenReturn(new ContinuationAcceptedResponse(
                "response", "input", "ANSWERED", "task", "run", "task"));
        mvc.perform(post("/api/v3/projects/42/concept-portfolio-runs/run/input-requests/input/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedFacts\":{\"sellerRole\":\"직접 판매\"},\"idempotencyKey\":\"idem\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.continuationTaskRunId").value("task"));

        mvc.perform(post("/api/v3/projects/42/concept-portfolio-runs/run/input-requests/input/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedFacts\":{\"sellerRole\":\"직접 판매\"},"
                    + "\"idempotencyKey\":\"idem2\",\"continuationArtifact\":{}}"))
            .andExpect(status().isBadRequest());
    }
}
