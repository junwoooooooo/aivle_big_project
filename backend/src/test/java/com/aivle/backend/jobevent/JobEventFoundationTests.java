package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobEventFoundationTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired JobEventRepository events;

    @Test
    void persistsDurableOrderedEventContractAndScopesReplayToProject() {
        User owner = users.saveAndFlush(User.create("event-owner@example.com", "hashed", "event-owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "events", null, "AI"));
        Project other = projects.saveAndFlush(Project.create(owner, "other events", null, "AI"));
        String jobId = "job-foundation-1";

        events.save(JobEvent.create(jobId, project, null, "IDEA_INTAKE", "MESSAGE_SAVED",
            JobEvent.Status.QUEUED, "job.idea.message.saved", "{\"messageId\":1}", null, 1, LocalDateTime.now()));
        events.save(JobEvent.create(jobId, project, null, "IDEA_INTAKE", "BRIEF_DRAFT_CREATED",
            JobEvent.Status.COMPLETED, "job.idea.brief.created", "{\"briefVersion\":1}", null, 2, LocalDateTime.now()));

        assertThat(events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            jobId, project.getId(), 0)).extracting(JobEvent::getSequence).containsExactly(1L, 2L);
        assertThat(events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            jobId, other.getId(), 0)).isEmpty();
        assertThat(events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            jobId, project.getId()))
            .get().extracting(JobEvent::getSequence).isEqualTo(2L);
        assertThat(events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            jobId, other.getId())).isEmpty();
    }
}
