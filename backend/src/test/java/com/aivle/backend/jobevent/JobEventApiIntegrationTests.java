package com.aivle.backend.jobevent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobEventApiIntegrationTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired JobEventPublisher publisher;
    @Autowired JobEventRepository events;
    @Autowired MockMvc mockMvc;

    @Test
    void pollingReturnsOrderedReplayAndRejectsAnotherOwner() throws Exception {
        User owner = users.saveAndFlush(User.create("g2-api-owner@example.com", "hash", "g2-api-owner"));
        User outsider = users.saveAndFlush(User.create("g2-api-other@example.com", "hash", "g2-api-other"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 api", null, "AI"));
        String jobId = "g2-api-job";
        publisher.publish(command(project.getId(), jobId, "QUEUED", JobEvent.Status.QUEUED));
        publisher.publish(command(project.getId(), jobId, "STARTED", JobEvent.Status.RUNNING));

        mockMvc.perform(get("/api/v2/jobs/{jobId}/events", jobId)
                .queryParam("after", "1")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-User-Id", owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.events[0].sequence").value(2))
            .andExpect(jsonPath("$.data.events[0].eventType").value("STARTED"))
            .andExpect(jsonPath("$.data.nextSequence").value(2))
            .andExpect(jsonPath("$.data.latestSequence").value(2))
            .andExpect(jsonPath("$.data.hasMore").value(false));

        mockMvc.perform(get("/api/v2/jobs/{jobId}/events", jobId)
                .queryParam("after", "0")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-User-Id", outsider.getId()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PROJECT_ACCESS_DENIED"));
    }

    @Test
    void rejectsUnauthenticatedAndInvalidCursorRequests() throws Exception {
        User owner = users.saveAndFlush(User.create("g2-api-cursor@example.com", "hash", "g2-api-cursor"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 cursor", null, "AI"));
        String jobId = "g2-cursor-job";
        publisher.publish(command(project.getId(), jobId, "QUEUED", JobEvent.Status.QUEUED));

        mockMvc.perform(get("/api/v2/jobs/{jobId}/events", jobId)
                .queryParam("after", "0")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v2/jobs/{jobId}/events", jobId)
                .queryParam("after", "-1")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-User-Id", owner.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void pollingCapsEachPageAndReportsTheDurableLatestSequence() throws Exception {
        User owner = users.saveAndFlush(User.create("g2-api-page@example.com", "hash", "g2-api-page"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 page", null, "AI"));
        String jobId = "g2-page-job";
        events.saveAllAndFlush(LongStream.rangeClosed(1, 105)
            .mapToObj(sequence -> JobEvent.create(
                jobId, project, null, "IDEA_INTAKE", "STEP_" + sequence,
                JobEvent.Status.RUNNING, "job.idea.status", "{}", null, sequence,
                java.time.LocalDateTime.of(2026, 8, 5, 0, 0).plusSeconds(sequence)))
            .toList());

        mockMvc.perform(get("/api/v2/jobs/{jobId}/events", jobId)
                .queryParam("after", "0")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-User-Id", owner.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.events.length()").value(JobEventQueryService.MAX_POLL_EVENTS))
            .andExpect(jsonPath("$.data.nextSequence").value(100))
            .andExpect(jsonPath("$.data.latestSequence").value(105))
            .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void sseRejectsUnauthenticatedAndAnotherProjectOwnerBeforeRegistration() throws Exception {
        User owner = users.saveAndFlush(User.create("g2-sse-owner@example.com", "hash", "g2-sse-owner"));
        User outsider = users.saveAndFlush(User.create("g2-sse-other@example.com", "hash", "g2-sse-other"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 sse", null, "AI"));
        String jobId = "g2-protected-sse";
        publisher.publish(command(project.getId(), jobId, "QUEUED", JobEvent.Status.QUEUED));

        mockMvc.perform(get("/api/v2/jobs/{jobId}/events", jobId)
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v2/jobs/{jobId}/events", jobId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("X-User-Id", outsider.getId()))
            .andExpect(status().isForbidden());
    }

    private JobEventPublisher.Command command(Long projectId, String jobId, String type,
            JobEvent.Status status) {
        return new JobEventPublisher.Command(
            projectId, jobId, null, "IDEA_INTAKE", type, status,
            "job.idea.status", Map.of("state", status.name()), null);
    }
}
