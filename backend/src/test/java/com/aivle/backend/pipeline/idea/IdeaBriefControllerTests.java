package com.aivle.backend.pipeline.idea;

import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.idea.api.IdeaBriefController;
import com.aivle.backend.pipeline.idea.application.IdeaBriefService;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class IdeaBriefControllerTests {
    @Mock IdeaBriefService service;
    @Mock CurrentUserProvider currentUser;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(currentUser.currentUserId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(new IdeaBriefController(service, currentUser)).build();
    }

    @Test
    void deriveUsesCanonicalV3RouteAndReturnsQueuedTaskIdentifier() throws Exception {
        IdeaBriefResponse response = new IdeaBriefResponse(
            "brief-1", IdeaBriefStatus.DERIVING, "local food waste service", List.of(), List.of(),
            List.of(), null, List.of(), new ReadinessView(10, 0, List.of(
                "problem", "targetCustomers", "beneficiaries", "usageContext", "expectedOutcome",
                "targetRegion", "physicalActivity", "personalData", "payment", "requiredPartners"
            ), 0, 0, 0, false), 0, 2, "task-1", null, LocalDateTime.of(2026, 8, 6, 12, 0)
        );
        when(service.derive(eq(7L), eq(42L), any(DeriveRequest.class), eq("derive-1"), eq("request-1")))
            .thenReturn(response);

        mockMvc.perform(post("/api/v3/projects/42/idea-brief/derive")
                .header("Idempotency-Key", "derive-1")
                .header("X-Request-Id", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overview\":\"local food waste service\",\"fields\":[],\"attachmentFileIds\":[]}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.briefId").value("brief-1"))
            .andExpect(jsonPath("$.data.status").value("DERIVING"))
            .andExpect(jsonPath("$.data.activeJobId").value("task-1"));
    }
}
