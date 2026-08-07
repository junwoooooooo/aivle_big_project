package com.aivle.backend.pipeline.idea;

import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.AnswerCommand;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.AnswersRequest;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.ConfirmRequest;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.DeriveRequest;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.FieldCommand;
import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.PatchFieldsRequest;
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
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
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

    @Test
    void deriveStoresOverviewWithoutDuplicatingItIntoAssumptions() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create("derive-" + suffix + "@example.com", "hash", "derive-" + suffix));
        Project project = projects.saveAndFlush(Project.create(user, "overview separation", null, "AI"));

        var response = service.derive(user.getId(), project.getId(),
            new DeriveRequest("Original overview", List.of(), java.util.Set.of()),
            "derive-" + suffix, "correlation-" + suffix);

        assertThat(response.overview()).isEqualTo("Original overview");
        assertThat(response.fieldCatalog()).hasSize(15);
        assertThat(fields.findByBriefIdAndFieldKey(response.briefId(), "assumptions")).isEmpty();
        assertThat(response.fields()).noneMatch(value -> value.fieldKey().equals("assumptions"));
        assertThat(taskRuns.findById(response.activeJobId()).orElseThrow().getInputSnapshot())
            .contains("\"fieldKey\":\"physicalActivity\"")
            .contains("\"requiredForConcept\":true")
            .contains("\"regulatorySensitive\":true");
    }

    @Test
    void answerIsStoredAndAppliedToItsCanonicalTargetFieldInOneTransaction() {
        Fixture fixture = fixture();
        IdeaQuestion question = questions.save(IdeaQuestion.create(
            fixture.brief(), "fixedConditions", IdeaQuestionType.FREE_TEXT,
            "What must remain fixed?", "[]", 0, 0));

        var response = service.answer(fixture.user().getId(), fixture.project().getId(),
            new AnswersRequest(List.of(new AnswerCommand(question.getId(), "\"local production\""))),
            "answer-" + UUID.randomUUID());

        IdeaBriefField field = fields.findByBriefIdAndFieldKey(fixture.brief().getId(), "fixedConditions").orElseThrow();
        assertThat(field.getFieldValue()).isEqualTo("local production");
        assertThat(field.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_CONFIRMED);
        assertThat(field.getDecisionState()).isEqualTo(
            IdeaBriefFieldCatalog.require("fixedConditions").defaultDecisionState());
        assertThat(answers.findAllByBriefIdOrderById(fixture.brief().getId())).hasSize(1);
        assertThat(response.status()).isEqualTo(IdeaBriefStatus.DERIVING);
        assertThat(response.clarificationRound()).isEqualTo(1);
        assertThat(response.activeJobId()).isNotBlank();
    }

    @Test
    void finalQuestionAnswerQueuesFinalSynthesisWithoutIncreasingRound() {
        Fixture fixture = fixture();
        fixture.brief().startClarification("old-task-1");
        fixture.brief().needsInput();
        fixture.brief().startClarification("old-task-2");
        fixture.brief().needsInput();
        IdeaQuestion question = questions.save(IdeaQuestion.create(
            fixture.brief(), "problem", IdeaQuestionType.UNDECIDED,
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
    void needsInputWithoutQuestionsExposesRequiredFieldsForManualCompletion() {
        Fixture fixture = fixture();
        fixture.brief().applyAssessment("More facts required", "[]",
            "[\"physicalActivity\",\"personalData\"]", "NEEDS_INPUT", 70,
            assessmentHasher.hash(fixture.brief(), List.of()));

        var response = service.get(fixture.user().getId(), fixture.project().getId());

        assertThat(response.status()).isEqualTo(IdeaBriefStatus.NEEDS_INPUT);
        assertThat(response.questions()).isEmpty();
        assertThat(response.readiness().missingFieldKeys())
            .contains("physicalActivity", "personalData");
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
    void emptyAnswersRemainRejectedByBeanValidation() {
        assertThat(validator.validate(new AnswersRequest(List.of())))
            .anyMatch(violation -> violation.getPropertyPath().toString().equals("answers"));
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create("idea-" + suffix + "@example.com", "hash", "idea-" + suffix));
        Project project = projects.saveAndFlush(Project.create(user, "canonical idea", null, "AI"));
        IdeaBrief brief = IdeaBrief.initial(project, user.getId());
        brief.updateOverview("Canonical overview only");
        brief.needsInput();
        return new Fixture(user, project, briefs.saveAndFlush(brief));
    }

    private record Fixture(User user, Project project, IdeaBrief brief) { }
}
