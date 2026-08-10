package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryService;
import com.aivle.backend.pipeline.concept.api.ConceptFactoryApiModels.CreateRunRequest;
import com.aivle.backend.pipeline.concept.domain.*;
import com.aivle.backend.pipeline.concept.repository.*;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class ConceptFactoryReplacementIntegrationTests {
    @Autowired ConceptFactoryExecutionService execution;
    @Autowired ConceptFactoryRunRepository runs;
    @Autowired ConceptSlotRepository slots;
    @Autowired IdeaBriefRepository briefs;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConceptFactoryService factory;
    @Autowired TaskRunService taskRuns;

    @Test
    void realRepositoryLegalRejectionCommitsReplacementAndReturnsToGenerating() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create("replace-" + suffix + "@example.com", "hash", "replace-" + suffix));
        Project project = projects.saveAndFlush(Project.create(user, "replacement", null, "AI"));
        IdeaBrief brief = IdeaBrief.initial(project, user.getId());
        brief.updateOverview("confirmed source");
        brief.applyAssessment("ready", "[]", "[]", "READY_FOR_REVIEW", 100);
        brief.readyForReview();
        brief.confirm("sha256:" + "a".repeat(64), "confirm-" + suffix, "sha256:" + "b".repeat(64));
        briefs.saveAndFlush(brief);

        ConceptFactoryRun run = runs.saveAndFlush(ConceptFactoryRun.create(
            project, brief.getId(), brief.getSnapshotHash(), user.getId()));
        run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        runs.saveAndFlush(run);
        ConceptSlot slot = ConceptSlot.create(run, 1, VariationFocus.CUSTOMER_EXPERIENCE);
        slot.transitionTo(ConceptSlotStatus.GENERATING);
        slot.transitionTo(ConceptSlotStatus.GENERATED);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_ORIGIN);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_DISTINCTNESS);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_LEGAL);
        slot.transitionTo(ConceptSlotStatus.REJECTED);
        slot.transitionTo(ConceptSlotStatus.REPLACING);
        slots.saveAndFlush(slot);

        execution.beginReplacement(run.getId(), slot.getId(), 1);
        String attemptId = execution.beginAttempt(slot.getId(), ConceptAttemptPhase.REPLACEMENT, null);

        assertThat(runs.findById(run.getId()).orElseThrow().getReplacementRounds()).isEqualTo(1);
        assertThat(slots.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(ConceptSlotStatus.GENERATING);
        assertThat(attemptId).isNotBlank();
    }

    @Test
    void failedRetryCreatesOneNewTaskRunAndIsIdempotent() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create("retry-" + suffix + "@example.com", "hash", "retry-" + suffix));
        Project project = projects.saveAndFlush(Project.create(user, "retry", null, "AI"));
        IdeaBrief brief = IdeaBrief.initial(project, user.getId());
        brief.updateOverview("confirmed source");
        brief.applyAssessment("ready", "[]", "[]", "READY_FOR_REVIEW", 100);
        brief.readyForReview();
        brief.confirm("sha256:" + "c".repeat(64), "confirm-" + suffix, "sha256:" + "d".repeat(64));
        briefs.saveAndFlush(brief);
        var created = factory.create(user.getId(), project.getId(), new CreateRunRequest(brief.getId()));
        String failedTaskId = created.activeJobId();
        var claim = taskRuns.claim(failedTaskId, "test", Duration.ofMinutes(1), Duration.ofMinutes(1));
        taskRuns.startExecution(failedTaskId, claim.taskAttemptId(), claim.claimToken());
        taskRuns.fail(failedTaskId, claim.taskAttemptId(), claim.claimToken(),
            "RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED", false);
        ConceptFactoryRun run = runs.findById(created.runId()).orElseThrow();
        run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        run.transitionTo(ConceptFactoryRunStatus.FAILED);
        runs.saveAndFlush(run);
        var failedSlots = slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(run.getId(), project.getId());
        failedSlots.forEach(ConceptSlot::fail);
        slots.saveAllAndFlush(failedSlots);

        var retried = factory.retry(user.getId(), project.getId(), run.getId(), "retry-key");
        var replay = factory.retry(user.getId(), project.getId(), run.getId(), "retry-key");

        assertThat(retried.activeJobId()).isNotEqualTo(failedTaskId);
        assertThat(replay.activeJobId()).isEqualTo(retried.activeJobId());
        assertThat(taskRuns.getOwned(user.getId(), project.getId(), failedTaskId).getState().name()).isEqualTo("FAILED");
    }
}
