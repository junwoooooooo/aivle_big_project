package com.aivle.backend.pipeline.idea;

import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.AnswerCommand;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.AnswersRequest;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.ConfirmRequest;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.DeriveRequest;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.FieldCommand;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.PatchFieldsRequest;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.CommitmentDecisionCommand;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.ReviewCommitmentsRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.idea.application.IdeaBriefReadinessCalculator;
import com.aivle.backend.pipeline.idea.application.IdeaBriefAssessmentHasher;
import com.aivle.backend.pipeline.idea.application.IdeaBriefService;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestion;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestionType;
import com.aivle.backend.pipeline.idea.repository.IdeaAnswerRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaQuestionRepository;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.jobevent.JobEventRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDateTime;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "app.task-run.idea-brief-poll-interval-ms=3600000",
    "app.task-run.idea-brief-recovery-interval-ms=3600000"
})
@ActiveProfiles("test")
@Transactional
class IdeaBriefCanonicalizationIntegrationTests {
    @Autowired IdeaBriefService service;
    @Autowired IdeaBriefRepository briefs;
    @Autowired IdeaBriefFieldRepository fields;
    @Autowired IdeaQuestionRepository questions;
    @Autowired IdeaAnswerRepository answers;
    @Autowired TaskRunRepository taskRuns;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired IdeaBriefAssessmentHasher assessmentHasher;
    @Autowired Validator validator;
    @Autowired TaskRunService taskRunService;
    @Autowired JobEventPublisher jobEvents;
    @Autowired JobEventRepository eventRepository;

    @Test
    void deriveStoresOverviewWithoutDuplicatingItIntoAssumptions() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create("derive-" + suffix + "@example.com", "hash", "derive-" + suffix));
        Project project = projects.saveAndFlush(Project.create(user, "overview separation", null, "AI"));

        var response = service.derive(user.getId(), project.getId(),
            new DeriveRequest("Original overview", "Original problem", "Original users", null, java.util.Set.of()),
            "derive-" + suffix, "correlation-" + suffix);

        assertThat(response.overview()).isEqualTo("Original overview");
        assertThat(response.fieldCatalog()).hasSize(13);
        assertThat(fields.findByBriefIdAndFieldKey(response.briefId(), "assumptions")).isEmpty();
        assertThat(response.fields()).noneMatch(value -> value.fieldKey().equals("assumptions"));
        assertThat(taskRuns.findById(response.activeJobId()).orElseThrow().getInputSnapshot())
            .contains("\"fieldKey\":\"targetRegion\"")
            .contains("\"requiredForConcept\":false")
            .contains("\"regulatorySensitive\":false");
    }

    @Test
    void answerIsStoredAndAppliedToItsCanonicalTargetFieldInOneTransaction() {
        Fixture fixture = fixture();
        IdeaQuestion question = questions.save(IdeaQuestion.create(
            fixture.brief(), "problem", IdeaQuestionType.FREE_TEXT,
            "What must remain fixed?", "[]", 0, 0));

        var response = service.answer(fixture.user().getId(), fixture.project().getId(),
            new AnswersRequest(List.of(new AnswerCommand(question.getId(), "\"local production\""))),
            "answer-" + UUID.randomUUID());

        IdeaBriefField field = fields.findByBriefIdAndFieldKey(fixture.brief().getId(), "problem").orElseThrow();
        assertThat(field.getFieldValue()).isEqualTo("local production");
        assertThat(field.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_INPUT);
        assertThat(field.getDecisionState()).isEqualTo(
            IdeaBriefFieldCatalog.require("problem").defaultDecisionState());
        assertThat(answers.findAllByBriefIdOrderById(fixture.brief().getId())).hasSize(1);
        assertThat(response.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(response.clarificationRound()).isEqualTo(1);
        assertThat(response.activeJobId()).isNotBlank();
    }

    @Test
    void finalQuestionAnswerQueuesFinalSynthesisWithoutIncreasingRound() {
        Fixture fixture = fixture();
        fixture.brief().startClarification("old-task-1");
        fixture.brief().needsInput(1, 0);
        fixture.brief().startClarification("old-task-2");
        fixture.brief().needsInput(1, 0);
        IdeaQuestion question = questions.save(IdeaQuestion.create(
            fixture.brief(), "problem", IdeaQuestionType.FREE_TEXT,
            "Can this remain undecided?", "[]", 0, 2));

        var response = service.answer(fixture.user().getId(), fixture.project().getId(),
            new AnswersRequest(List.of(new AnswerCommand(question.getId(), "\"__UNDECIDED__\""))),
            "bounded-" + UUID.randomUUID());

        assertThat(response.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(response.clarificationRound()).isEqualTo(IdeaBriefReadinessCalculator.MAX_CLARIFICATION_ROUNDS);
        assertThat(response.activeJobId()).isNotBlank();
        assertThat(taskRuns.count()).isEqualTo(1);
        assertThat(taskRuns.findById(response.activeJobId()).orElseThrow().getInputSnapshot())
            .contains("\"mode\":\"FINAL_SYNTHESIS\"");
        assertThat(response.fields()).filteredOn(value -> value.fieldKey().equals("problem"))
            .singleElement().satisfies(value -> {
                assertThat(value.explicitlyUndecided()).isTrue();
                assertThat(value.provenance()).isEqualTo(IdeaFieldProvenance.MISSING);
            });
    }

    @Test
    void confirmationCreatesAnImmutableCanonicalSnapshot() {
        Fixture fixture = fixture();
        for (var definition : IdeaBriefFieldCatalog.fields()) {
            if (!definition.requiredForConcept()) continue;
            fields.save(IdeaBriefField.userValue(fixture.brief(), definition.key(),
                "confirmed " + definition.key(), definition.defaultDecisionState()));
        }
        fixture.brief().applyAssessment("Ready summary", "[]", "[]", "READY_FOR_REVIEW", 100,
            assessmentHasher.hash(fixture.brief(), fields.findAllByBriefIdOrderById(fixture.brief().getId())));
        fixture.brief().readyForReview();

        var response = service.confirm(fixture.user().getId(), fixture.project().getId(),
            new ConfirmRequest(null), "confirm-" + UUID.randomUUID());

        assertThat(response.status()).isEqualTo(IdeaBriefStatus.CONFIRMED);
        assertThat(response.confirmedSnapshotId()).isEqualTo(fixture.brief().getId());
        assertThat(fixture.brief().getSnapshotHash()).startsWith("sha256:");
        assertThat(response.overview()).isEqualTo("Canonical overview only");
    }

    @Test
    void fieldPatchMarksAssessmentStaleAndQueuesFinalSynthesisBeforeConfirm() {
        Fixture fixture = fixture();
        fields.save(IdeaBriefField.userValue(fixture.brief(), "problem", "old problem", IdeaDecisionState.PREFERRED));
        fixture.brief().applyAssessment("Ready summary", "[]", "[]", "READY_FOR_REVIEW", 100,
            assessmentHasher.hash(fixture.brief(), fields.findAllByBriefIdOrderById(fixture.brief().getId())));
        fixture.brief().readyForReview();

        var patched = service.patchFields(fixture.user().getId(), fixture.project().getId(),
            new PatchFieldsRequest(List.of(new FieldCommand("problem", "new problem", IdeaDecisionState.PREFERRED))),
            "patch-" + UUID.randomUUID());

        assertThat(patched.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(patched.assessmentCurrent()).isFalse();
        assertThat(patched.activeJobId()).isNotBlank();
        assertThat(taskRuns.findById(patched.activeJobId()).orElseThrow().getInputSnapshot())
            .contains("\"mode\":\"FINAL_SYNTHESIS\"");
        assertThatThrownBy(() -> service.confirm(fixture.user().getId(), fixture.project().getId(),
            new ConfirmRequest(null), "confirm-stale-" + UUID.randomUUID()))
            .isInstanceOf(com.aivle.backend.common.exception.BusinessException.class);
    }

    @Test
    void commitmentConfirmationChangesCanonicalStateAndQueuesOneFreshFinalSynthesis() {
        Fixture fixture = commitmentFixture("price", "월 9,900원");
        String key = "commitment-confirm-" + UUID.randomUUID();

        var changed = service.reviewCommitments(fixture.user().getId(), fixture.project().getId(),
            new ReviewCommitmentsRequest(List.of(new CommitmentDecisionCommand("price", "CONFIRM", null))), key);
        var replay = service.reviewCommitments(fixture.user().getId(), fixture.project().getId(),
            new ReviewCommitmentsRequest(List.of(new CommitmentDecisionCommand("price", "CONFIRM", null))), key);

        assertThat(changed.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(changed.assessmentCurrent()).isFalse();
        assertThat(replay.activeJobId()).isEqualTo(changed.activeJobId());
        assertThat(taskRuns.count()).isEqualTo(1);
        assertThat(taskRuns.findById(changed.activeJobId()).orElseThrow().getInputSnapshot())
            .contains("\"mode\":\"FINAL_SYNTHESIS\"");
        assertThat(fields.findByBriefIdAndFieldKey(fixture.brief().getId(), "price")).get()
            .satisfies(value -> {
                assertThat(value.getFieldValue()).isEqualTo("월 9,900원");
                assertThat(value.getDecisionState()).isEqualTo(IdeaDecisionState.LOCKED);
                assertThat(value.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_CONFIRMED);
            });
    }

    @Test
    void editAndConfirmAndReturnToOpenBothQueueReassessmentOnlyWhenCanonicalStateChanges() {
        Fixture editedFixture = commitmentFixture("price", "월 9,900원");
        var edited = service.reviewCommitments(editedFixture.user().getId(), editedFixture.project().getId(),
            new ReviewCommitmentsRequest(List.of(new CommitmentDecisionCommand(
                "price", "EDIT_AND_CONFIRM", "월 10,900원"))), "commitment-edit-" + UUID.randomUUID());
        assertThat(edited.status()).isEqualTo(IdeaBriefStatus.DERIVING);

        Fixture openedFixture = commitmentFixture("channels", "온라인 직판");
        fields.save(IdeaBriefField.confirmedCommitment(openedFixture.brief(), "channels", "온라인 직판"));
        var opened = service.reviewCommitments(openedFixture.user().getId(), openedFixture.project().getId(),
            new ReviewCommitmentsRequest(List.of(new CommitmentDecisionCommand(
                "channels", "RETURN_TO_OPEN", null))), "commitment-open-" + UUID.randomUUID());
        assertThat(opened.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(fields.findByBriefIdAndFieldKey(openedFixture.brief().getId(), "channels")).get()
            .satisfies(value -> {
                assertThat(value.getFieldValue()).isEmpty();
                assertThat(value.getDecisionState()).isEqualTo(IdeaDecisionState.OPEN);
                assertThat(value.getProvenance()).isEqualTo(IdeaFieldProvenance.MISSING);
            });
    }

    @Test
    void commitmentReviewWithoutCanonicalChangeDoesNotQueueAReassessment() {
        Fixture fixture = commitmentFixture("price", "월 9,900원");
        fields.save(IdeaBriefField.userValue(fixture.brief(), "price", "월 9,900원", IdeaDecisionState.LOCKED));
        fixture.brief().applyAssessment("Ready", "[]", "[]", "READY_FOR_REVIEW", 100,
            assessmentHasher.hash(fixture.brief(), fields.findAllByBriefIdOrderById(fixture.brief().getId())));

        var unchanged = service.reviewCommitments(fixture.user().getId(), fixture.project().getId(),
            new ReviewCommitmentsRequest(List.of(new CommitmentDecisionCommand("price", "CONFIRM", null))),
            "commitment-noop-" + UUID.randomUUID());

        assertThat(unchanged.status()).isEqualTo(IdeaBriefStatus.READY_FOR_REVIEW);
        assertThat(unchanged.assessmentCurrent()).isTrue();
        assertThat(unchanged.activeJobId()).isNull();
        assertThat(taskRuns.count()).isZero();
    }

    @Test
    void needsInputWithoutQuestionsExposesRequiredFieldsForManualCompletion() {
        Fixture fixture = fixture();
        fixture.brief().applyAssessment("More facts required", "[]",
            "[\"ideaOverview\",\"problem\",\"targetUsers\"]", "NEEDS_INPUT", 70,
            assessmentHasher.hash(fixture.brief(), List.of()));

        var response = service.get(fixture.user().getId(), fixture.project().getId());

        assertThat(response.status()).isEqualTo(IdeaBriefStatus.NEEDS_INPUT);
        assertThat(response.questions()).isEmpty();
        assertThat(response.readiness().missingFieldKeys())
            .containsExactlyInAnyOrder("ideaOverview", "problem", "targetUsers");
    }

    @Test
    void recoveryReanalysisCreatesFinalSynthesisTaskRun() {
        Fixture fixture = fixture();

        var response = service.reanalyze(fixture.user().getId(), fixture.project().getId(),
            "reanalyze-" + UUID.randomUUID());

        assertThat(response.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(response.activeJobId()).isNotBlank();
        assertThat(taskRuns.findById(response.activeJobId()).orElseThrow().getInputSnapshot())
            .contains("\"mode\":\"FINAL_SYNTHESIS\"");
    }

    @Test
    void commandReplayKeepsExecutionButNewCommandRecoversSucceededZombieWithNewJob() {
        Fixture fixture = fixture();
        String keyA = "reanalyze-a-" + UUID.randomUUID();
        var first = service.reanalyze(fixture.user().getId(), fixture.project().getId(), keyA);
        var replay = service.reanalyze(fixture.user().getId(), fixture.project().getId(), keyA);
        assertThat(replay.activeJobId()).isEqualTo(first.activeJobId());

        finishTask(fixture, first.activeJobId(), false);
        jobEvents.publish(new JobEventPublisher.Command(fixture.project().getId(), first.activeJobId(),
            first.activeJobId(), "SUCCEEDED", "job.idea.completed", JobEvent.Status.COMPLETED,
            "job.idea.completed", Map.of(), null));
        TaskRun historicalRun = taskRuns.findById(first.activeJobId()).orElseThrow();
        eventRepository.save(JobEvent.create(first.activeJobId(), fixture.project(), historicalRun,
            "QUEUED", "job.idea.queued", JobEvent.Status.QUEUED, "job.idea.queued", "{}", null,
            3L, LocalDateTime.now()));
        long historicalEventCount = eventsFor(first.activeJobId(), fixture.project().getId()).size();

        var poisonedRead = service.get(fixture.user().getId(), fixture.project().getId());
        assertThat(poisonedRead.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(poisonedRead.executionStateConsistent()).isFalse();
        assertThat(poisonedRead.recoveryRequired()).isTrue();

        var recovered = service.reanalyze(fixture.user().getId(), fixture.project().getId(),
            "reanalyze-b-" + UUID.randomUUID());

        assertThat(recovered.activeJobId()).isNotEqualTo(first.activeJobId());
        assertThat(taskRuns.findById(first.activeJobId()).orElseThrow().getState())
            .isEqualTo(TaskRunState.SUCCEEDED);
        assertThat(taskRuns.findById(recovered.activeJobId()).orElseThrow().getState())
            .isEqualTo(TaskRunState.QUEUED);
        assertThat(eventsFor(first.activeJobId(), fixture.project().getId())).hasSize((int) historicalEventCount);
        assertThat(eventsFor(recovered.activeJobId(), fixture.project().getId()))
            .singleElement().extracting(JobEvent::getSequence).isEqualTo(1L);
    }

    @Test
    void newCommandRecoversNeedsInputTaskZombieWithoutReusingTerminalJob() {
        Fixture fixture = fixture();
        var first = service.reanalyze(fixture.user().getId(), fixture.project().getId(),
            "reanalyze-needs-a-" + UUID.randomUUID());
        finishTask(fixture, first.activeJobId(), true);
        jobEvents.publish(new JobEventPublisher.Command(fixture.project().getId(), first.activeJobId(),
            first.activeJobId(), "NEEDS_INPUT", "job.idea.completed", JobEvent.Status.NEEDS_INPUT,
            "job.idea.completed", Map.of(), null));

        var recovered = service.reanalyze(fixture.user().getId(), fixture.project().getId(),
            "reanalyze-needs-b-" + UUID.randomUUID());

        assertThat(recovered.activeJobId()).isNotEqualTo(first.activeJobId());
        assertThat(taskRuns.findById(first.activeJobId()).orElseThrow().getState())
            .isEqualTo(TaskRunState.NEEDS_INPUT);
        assertThat(taskRuns.findById(recovered.activeJobId()).orElseThrow().getState())
            .isEqualTo(TaskRunState.QUEUED);
    }

    @Test
    void emptyAnswersRemainRejectedByBeanValidation() {
        assertThat(validator.validate(new AnswersRequest(List.of())))
            .anyMatch(violation -> violation.getPropertyPath().toString().equals("answers"));
    }

    private void finishTask(Fixture fixture, String taskRunId, boolean needsInput) {
        TaskRun run = taskRuns.findById(taskRunId).orElseThrow();
        TaskRunService.Claim claim = taskRunService.claim(taskRunId, "idea-test-worker",
            Duration.ofMinutes(1), Duration.ofMinutes(2));
        taskRunService.startExecution(taskRunId, claim.taskAttemptId(), claim.claimToken());
        if (needsInput) {
            taskRunService.adoptNeedsInput(taskRunId, claim.taskAttemptId(), claim.claimToken(),
                "{\"status\":\"NEEDS_INPUT\"}", run.getInputHash(), "1.0");
        } else {
            taskRunService.adopt(taskRunId, claim.taskAttemptId(), claim.claimToken(),
                "{\"status\":\"READY_FOR_REVIEW\"}", run.getInputHash(), "1.0");
        }
    }

    private List<JobEvent> eventsFor(String jobId, Long projectId) {
        return eventRepository.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            jobId, projectId, 0);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create("idea-" + suffix + "@example.com", "hash", "idea-" + suffix));
        Project project = projects.saveAndFlush(Project.create(user, "canonical idea", null, "AI"));
        IdeaBrief brief = IdeaBrief.initial(project, user.getId());
        brief.updateOverview("Canonical overview only");
        brief.needsInput(0, 1);
        return new Fixture(user, project, briefs.saveAndFlush(brief));
    }

    private Fixture commitmentFixture(String fieldKey, String candidateValue) {
        Fixture fixture = fixture();
        fixture.brief().applySafetyAndInterpretation("ALLOW", "[]", "[]", "검토할 수 있습니다.", """
            {"interpretedProblem":"문제","interpretedTargetUsers":"사용자","usageContext":"맥락",
             "industryCategory":"업종","researchScope":"범위","conciseIdeaDefinition":"정의",
             "targetRegionInterpretation":"","relevantKnownCompetitorContext":"","userEdited":false,
             "commitmentCandidates":[{"fieldKey":"%s","value":"%s","evidenceQuote":"원문",
              "source":"AI_DERIVED","origin":"USER_TEXT","authority":"REVIEWABLE"}]}
            """.formatted(fieldKey, candidateValue));
        fixture.brief().readyForReview();
        return fixture;
    }

    private record Fixture(User user, Project project, IdeaBrief brief) { }
}
