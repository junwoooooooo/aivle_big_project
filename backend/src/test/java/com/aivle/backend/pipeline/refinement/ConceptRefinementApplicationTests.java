package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.market.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import com.aivle.backend.user.entity.User;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;

@ExtendWith(MockitoExtension.class)
class ConceptRefinementApplicationTests {
    private static final String HASH = "sha256:" + "a".repeat(64);
    private static final Long PROJECT_ID = 41L;
    private static final Long OWNER_ID = 7L;

    @Mock ProjectRepository projects;
    @Mock ConceptRefinementRoundRepository rounds;
    @Mock ConceptRefinementLineageGuard lineage;
    @Mock ConceptPortfolioSelectionRepository selections;
    @Mock ConceptPortfolioSelectionService selectionService;
    @Mock BmPlanPreparationService bmPlans;
    @Mock MarketAnalysisSeedSnapshotRepository seeds;
    @Mock ConceptRefinementService refinement;
    @Mock ConceptPortfolioHypothesisDecisionRepository hypotheses;
    @Mock ConceptPortfolioDeltaLegalReviewRepository deltas;
    @Mock ConceptLegalRegulatoryReportRepository reports;
    @Mock TaskRunService taskRuns;
    @Mock BusinessValidationCoordinator validations;
    @Mock MarketResearchVersionRepository marketVersions;
    @Mock Project project;
    @Mock User owner;
    @Mock ConceptPortfolioSelection selection;
    @Mock TaskRun task;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);
    private ConceptRefinementDecisionContract contract;
    private ConceptRefinementApplicationService application;
    private ConceptRefinementApplicationMaterializationService materialization;
    private CompletedSource source;
    private MarketAnalysisSeedSnapshot seed;

    @BeforeEach
    void setUp() {
        contract = new ConceptRefinementDecisionContract(mapper, new ConceptPortfolioJsonHasher(mapper));
        application = new ConceptRefinementApplicationService(projects, rounds, contract, lineage,
            selections, selectionService, bmPlans, seeds, refinement, mapper, clock);
        materialization = new ConceptRefinementApplicationMaterializationService(rounds, selections,
            hypotheses, deltas, reports, seeds, bmPlans, selectionService, contract,
            new ConceptPortfolioJsonHasher(mapper), taskRuns, mapper, clock);
        source = new CompletedSource("session-1", 91L, 92L, "seed-1", 31L, 4, 3, HASH);
        seed = MarketAnalysisSeedSnapshot.createPortfolio("seed-1", PROJECT_ID, 31L,
            "concept-1", "legal-1", "2.0", HASH, HASH, "{}", OWNER_ID, clock.instant());
        lenient().when(project.getOwner()).thenReturn(owner);
        lenient().when(owner.getId()).thenReturn(OWNER_ID);
        lenient().when(projects.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(lineage.proposalBaselineCurrent(eq(OWNER_ID), eq(PROJECT_ID), any())).thenReturn(true);
        lenient().when(lineage.postApplyCurrent(eq(PROJECT_ID), any())).thenReturn(true);
        lenient().when(selection.getId()).thenReturn(31L);
        lenient().when(selection.getProjectId()).thenReturn(PROJECT_ID);
        lenient().when(selection.getHypothesisRevision()).thenReturn(4);
        lenient().when(selection.isCurrent()).thenReturn(true);
        lenient().when(selections.findLocked(31L)).thenReturn(Optional.of(selection));
        lenient().when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(PROJECT_ID))
            .thenReturn(Optional.of(selection));
        lenient().when(seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-1"))
            .thenReturn(Optional.of(seed));
        lenient().when(seeds.findByIdAndDeletedAtIsNull("seed-1")).thenReturn(Optional.of(seed));
        lenient().when(task.getId()).thenReturn("confirm-task");
        lenient().when(refinement.view(any(), anyBoolean())).thenReturn(null);
    }

    @Test
    void bmPatchChangesOnlySelectedFieldAndPreservesConstraints() {
        BmPlanPreparationRepository repository = mock(BmPlanPreparationRepository.class);
        BmPlanPreparationService service = new BmPlanPreparationService(repository, mapper);
        BmPlanPreparation entity = BmPlanPreparation.create("bm-1", PROJECT_ID,
            "{\"key_activities\":[\"A\"],\"key_resources\":[\"B\"],\"key_partners\":[\"C\"],\"customer_relationship\":\"D\"}",
            "{\"budget_krw\":1000,\"months\":6,\"team\":3}", OWNER_ID);
        when(repository.findByProjectIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(entity));
        ObjectNode patch = mapper.createObjectNode(); patch.putArray("key_activities").add("A2");

        var result = service.patchForRefinement(PROJECT_ID, OWNER_ID, 1, patch);

        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.plan().path("key_activities").get(0).asText()).isEqualTo("A2");
        assertThat(result.plan().path("key_resources").get(0).asText()).isEqualTo("B");
        assertThat(result.plan().path("key_partners").get(0).asText()).isEqualTo("C");
        assertThat(result.plan().path("customer_relationship").asText()).isEqualTo("D");
        assertThat(entity.getConstraintJson()).isEqualTo("{\"budget_krw\":1000,\"months\":6,\"team\":3}");
    }

    @Test
    void bmPatchRejectsRevisionMismatchUnknownAndConstraintKeys() {
        BmPlanPreparationRepository repository = mock(BmPlanPreparationRepository.class);
        BmPlanPreparationService service = new BmPlanPreparationService(repository, mapper);
        BmPlanPreparation entity = BmPlanPreparation.create("bm-1", PROJECT_ID, "{}", "{}", OWNER_ID);
        when(repository.findByProjectIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(entity));
        ObjectNode valid = mapper.createObjectNode(); valid.putArray("key_activities").add("A");
        assertThatThrownBy(() -> service.patchForRefinement(PROJECT_ID, OWNER_ID, 0, valid))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.MODULE_INPUT_STALE));
        ObjectNode unknown = mapper.createObjectNode().put("budget_krw", 1000);
        assertThatThrownBy(() -> service.patchForRefinement(PROJECT_ID, OWNER_ID, 1, unknown))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(entity.getRevision()).isEqualTo(1);
    }

    @Test
    void localOnlyInvalidationFollowsBmAndOverlayDependenciesExactly() {
        ConceptRefinementRound bmRound = decidedRound(proposal("keyActivities", List.of("A"), List.of("A2")));
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(bmRound));
        when(bmPlans.patchForRefinement(eq(PROJECT_ID), eq(OWNER_ID), eq(3), any()))
            .thenReturn(new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 4));

        application.apply(OWNER_ID, PROJECT_ID, "apply-bm", 1, bmRound.getDecisionHash());

        assertThat(bmRound.getState()).isEqualTo(ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION);
        assertThat(bmRound.getAppliedSelectionRevision()).isEqualTo(4);
        assertThat(bmRound.getAppliedBmPlanRevision()).isEqualTo(4);
        assertThat(seed.getStaleAt()).isNull();
        verify(seeds, never()).findByIdAndDeletedAtIsNull("seed-1");
        verify(selectionService, never()).confirmFromRefinement(anyLong(), anyLong(), anyLong(), any(), any(), anyString());

        seed = freshSeed();
        when(seeds.findByIdAndDeletedAtIsNull("seed-1")).thenReturn(Optional.of(seed));
        ConceptRefinementRound overlayRound = decidedRound(proposal("targetUsers", "기존", "신규"));
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(overlayRound));
        when(bmPlans.patchForRefinement(eq(PROJECT_ID), eq(OWNER_ID), eq(3), any()))
            .thenReturn(new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3));
        application.apply(OWNER_ID, PROJECT_ID, "apply-overlay", 1, overlayRound.getDecisionHash());
        assertThat(overlayRound.getAppliedBmPlanRevision()).isEqualTo(3);
        assertThat(overlayRound.getState()).isEqualTo(ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION);
        assertThat(seed.getStaleAt()).isEqualTo(clock.instant());

        seed = freshSeed();
        when(seeds.findByIdAndDeletedAtIsNull("seed-1")).thenReturn(Optional.of(seed));
        ConceptRefinementRound combinedRound = decidedRound(
            proposal("keyActivities", List.of("A"), List.of("A2")),
            proposal("targetUsers", "기존", "신규"));
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(combinedRound));
        when(bmPlans.patchForRefinement(eq(PROJECT_ID), eq(OWNER_ID), eq(3), any()))
            .thenReturn(new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 4));
        application.apply(OWNER_ID, PROJECT_ID, "apply-combined", 1, combinedRound.getDecisionHash());
        assertThat(combinedRound.getAppliedBmPlanRevision()).isEqualTo(4);
        assertThat(combinedRound.getState()).isEqualTo(ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION);
        assertThat(seed.getStaleAt()).isEqualTo(clock.instant());
        verify(seeds, times(2)).findByIdAndDeletedAtIsNull("seed-1");
        verifyNoInteractions(reports);
    }

    @Test
    void hypothesisApplyQueuesOneTaggedConfirmBeforeAnyProductMutationAndReplays() {
        ConceptRefinementRound round = decidedRound(proposal("price", "10,000원", "12,500원"));
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(round));
        when(selectionService.confirmFromRefinement(eq(OWNER_ID), eq(PROJECT_ID), eq(31L),
            any(), any(), eq("apply-h"))).thenReturn(task);

        application.apply(OWNER_ID, PROJECT_ID, "apply-h", 1, round.getDecisionHash());
        application.apply(OWNER_ID, PROJECT_ID, "apply-h", 1, round.getDecisionHash());

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.APPLYING_HYPOTHESES);
        assertThat(round.getApplicationTaskRunId()).isEqualTo("confirm-task");
        assertThat(round.getApplicationAttempt()).isEqualTo(1);
        assertThat(seed.getStaleAt()).isNull();
        assertThat(selection.getHypothesisRevision()).isEqualTo(4);
        verify(selectionService, times(1)).confirmFromRefinement(eq(OWNER_ID), eq(PROJECT_ID), eq(31L),
            any(), any(), eq("apply-h"));
        verify(bmPlans, never()).patchForRefinement(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void preApplySourceMismatchMarksStaleAndCreatesNoTaskOrPatch() {
        ConceptRefinementRound round = decidedRound(proposal("price", "10,000원", "12,500원"));
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(round));
        when(lineage.proposalBaselineCurrent(OWNER_ID, PROJECT_ID, round)).thenReturn(false);

        application.apply(OWNER_ID, PROJECT_ID, "apply-stale", 1, round.getDecisionHash());

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.STALE);
        verify(selectionService, never()).confirmFromRefinement(anyLong(), anyLong(), anyLong(), any(), any(), anyString());
        verify(bmPlans, never()).patchForRefinement(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void confirmFailurePreservesSelectionAndAllowsExplicitApplicationRetry() {
        ConceptRefinementRound round = applyingRound("confirm-task", proposal("price", "10,000원", "12,500원"));
        ConceptPortfolioSelection real = activeSelection("confirm-task", "CONFIRM_HYPOTHESES", 4,
            ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        when(rounds.findByIdForUpdate(101L)).thenReturn(Optional.of(round));
        when(selections.findLocked(31L)).thenReturn(Optional.of(real));
        JsonNode input = taggedInput("CONFIRM_HYPOTHESES", round, 4, 3);

        materialization.fail(claim("confirm-task"), context("confirm-task", input),
            "AI_SERVICE_UNAVAILABLE", "TRANSIENT", true, input);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.APPLY_FAILED);
        assertThat(real.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        assertThat(real.getHypothesisRevision()).isEqualTo(4);
        assertThat(real.getActiveTaskRunId()).isNull();
        assertThat(seed.getStaleAt()).isNull();
        verify(bmPlans, never()).patchForRefinement(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void externalBmChangeBeforeConfirmResultRejectsAllProductMutation() {
        ConceptRefinementRound round = applyingRound("confirm-task", proposal("price", "10,000원", "12,500원"));
        ConceptPortfolioSelection real = activeSelection("confirm-task", "CONFIRM_HYPOTHESES", 4,
            ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        when(rounds.findByIdForUpdate(101L)).thenReturn(Optional.of(round));
        when(selections.findLocked(31L)).thenReturn(Optional.of(real));
        when(bmPlans.current(PROJECT_ID)).thenReturn(new BmPlanPreparationService.PlanView(
            mapper.createObjectNode(), mapper.createObjectNode(), 4));
        JsonNode input = taggedInput("CONFIRM_HYPOTHESES", round, 4, 3);

        assertThatThrownBy(() -> materialization.complete(claim("confirm-task"),
            context("confirm-task", input), response(confirmResult(false)), confirmResult(false), input))
            .isInstanceOf(ConceptRefinementMaterializationService.StaleResult.class);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.STALE);
        assertThat(real.getHypothesisRevision()).isEqualTo(4);
        assertThat(seed.getStaleAt()).isNull();
        verify(hypotheses, never()).findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(anyLong(), any());
        verify(bmPlans, never()).patchForRefinement(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void confirmSuccessAppliesHypothesesBmAndDependentsThenCapturesPostLineage() {
        ConceptRefinementRound round = applyingRound("confirm-task",
            proposal("price", "10,000원", "12,500원"),
            proposal("keyActivities", List.of("A"), List.of("A2")));
        ConceptPortfolioSelection real = activeSelection("confirm-task", "CONFIRM_HYPOTHESES", 4,
            ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        List<ConceptPortfolioHypothesisDecision> current = hypothesisRows(false);
        stubMaterialization(round, real, current, 3, 4);
        ConceptLegalRegulatoryReport report = mock(ConceptLegalRegulatoryReport.class);
        when(reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(31L, "CURRENT")).thenReturn(List.of(report));
        when(seeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(31L)).thenReturn(List.of(seed));
        JsonNode input = taggedInput("CONFIRM_HYPOTHESES", round, 4, 3);
        JsonNode result = confirmResult(false);

        materialization.complete(claim("confirm-task"), context("confirm-task", input),
            response(result), result, input);

        assertThat(real.getHypothesisRevision()).isEqualTo(5);
        assertThat(real.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT);
        assertThat(round.getAppliedSelectionRevision()).isEqualTo(5);
        assertThat(round.getAppliedBmPlanRevision()).isEqualTo(4);
        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION);
        assertThat(seed.getStaleAt()).isEqualTo(clock.instant());
        verify(report).markStale();
        verify(taskRuns).adopt(eq("confirm-task"), anyString(), anyString(), anyString(), eq(HASH), eq("1.0"));
    }

    @Test
    void confirmSuccessWithDeltaQueuesOneTaggedLegalTask() {
        ConceptRefinementRound round = applyingRound("confirm-task", proposal("price", "10,000원", "12,500원"));
        ConceptPortfolioSelection real = activeSelection("confirm-task", "CONFIRM_HYPOTHESES", 4,
            ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        List<ConceptPortfolioHypothesisDecision> current = hypothesisRows(true);
        stubMaterialization(round, real, current, 3, 3);
        when(reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(31L, "CURRENT")).thenReturn(List.of());
        when(seeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(31L)).thenReturn(List.of(seed));
        TaskRun deltaTask = mock(TaskRun.class); when(deltaTask.getId()).thenReturn("delta-task");
        when(selectionService.queueDeltaFromRefinement(eq(OWNER_ID), same(real), anyString(), any()))
            .thenReturn(deltaTask);
        JsonNode input = taggedInput("CONFIRM_HYPOTHESES", round, 4, 3);
        JsonNode result = confirmResult(true);

        materialization.complete(claim("confirm-task"), context("confirm-task", input),
            response(result), result, input);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.LEGAL_REVIEW_PENDING);
        assertThat(round.getDeltaLegalTaskRunId()).isEqualTo("delta-task");
        assertThat(round.getAppliedSelectionRevision()).isEqualTo(5);
        verify(selectionService, times(1)).queueDeltaFromRefinement(eq(OWNER_ID), same(real), anyString(), any());
    }

    @Test
    void taggedDeltaApprovedBlockedAndTransportFailureMapToDistinctRoundStates() {
        DeltaFixture approved = deltaFixture("delta-approved");
        JsonNode approvedResult = deltaResult(true);
        materialization.complete(claim("delta-approved"), context("delta-approved", approved.input),
            response(approvedResult), approvedResult, approved.input);
        assertThat(approved.round.getState()).isEqualTo(ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION);
        assertThat(approved.selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT);

        DeltaFixture blocked = deltaFixture("delta-blocked");
        JsonNode blockedResult = deltaResult(false);
        materialization.complete(claim("delta-blocked"), context("delta-blocked", blocked.input),
            response(blockedResult), blockedResult, blocked.input);
        assertThat(blocked.round.getState()).isEqualTo(ConceptRefinementRound.State.LEGAL_BLOCKED);
        assertThat(blocked.selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED);

        DeltaFixture failed = deltaFixture("delta-failed");
        materialization.fail(claim("delta-failed"), context("delta-failed", failed.input),
            "AI_SERVICE_UNAVAILABLE", "TRANSPORT", true, failed.input);
        assertThat(failed.round.getState()).isEqualTo(ConceptRefinementRound.State.LEGAL_REVIEW_FAILED);
        assertThat(failed.selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED);
    }

    @Test
    void legalFailureRequiresExplicitTaggedRetry() {
        DeltaFixture failed = deltaFixture("delta-old");
        failed.selection.failTask("delta-old", ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED, "AI_SERVICE_UNAVAILABLE");
        failed.round.legalFailed("delta-old", "AI_SERVICE_UNAVAILABLE");
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(failed.round));
        when(selections.findLocked(31L)).thenReturn(Optional.of(failed.selection));
        TaskRun retry = mock(TaskRun.class); when(retry.getId()).thenReturn("delta-retry");
        when(selectionService.queueDeltaFromRefinement(eq(OWNER_ID), same(failed.selection), eq("retry-legal"), any()))
            .thenReturn(retry);

        application.retryLegal(OWNER_ID, PROJECT_ID, "retry-legal", 1, failed.round.getDecisionHash());

        assertThat(failed.round.getState()).isEqualTo(ConceptRefinementRound.State.LEGAL_REVIEW_PENDING);
        assertThat(failed.round.getDeltaLegalTaskRunId()).isEqualTo("delta-retry");
        verify(selectionService, times(1)).queueDeltaFromRefinement(eq(OWNER_ID), same(failed.selection),
            eq("retry-legal"), any());
    }

    @Test
    void bmOnlyPostApplyLineageAcceptsSelfChangeAndRejectsLaterBmOrSelectionMutation() {
        ConceptRefinementRound round = decidedRound(proposal("keyActivities", List.of("A"), List.of("A2")));
        round.startLocalApplication("apply", contract.applicationHash(round), clock.instant());
        round.recordAppliedLineage(5, 4, clock.instant()); round.readyForFinalization();
        ConceptRefinementLineageGuard guard = new ConceptRefinementLineageGuard(validations, selections, seeds, bmPlans, marketVersions);
        when(selection.getHypothesisRevision()).thenReturn(5, 5, 6);
        when(bmPlans.current(PROJECT_ID)).thenReturn(
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 4),
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 5));

        assertThat(guard.postApplyCurrent(PROJECT_ID, round)).isTrue();
        assertThat(guard.postApplyCurrent(PROJECT_ID, round)).isFalse();
        assertThat(guard.postApplyCurrent(PROJECT_ID, round)).isFalse();
    }

    private ConceptRefinementRound decidedRound(ObjectNode... proposals) {
        ConceptRefinementRound round = ConceptRefinementRound.start(PROJECT_ID, source, "proposal-task", "start", HASH);
        ReflectionTestUtils.setField(round, "id", 101L);
        ArrayNode values = mapper.createArrayNode(); for (ObjectNode proposal : proposals) values.add(proposal);
        round.materialize(values.toString(), "[]", true);
        var set = contract.proposalSet(round);
        var decision = contract.decision(round, set, set.orderedKeys(), false);
        round.recordDecision(decision.snapshot().toString(), decision.hash(), "decision", OWNER_ID, clock.instant(), false);
        return round;
    }

    private ConceptRefinementRound applyingRound(String taskId, ObjectNode... proposals) {
        ConceptRefinementRound round = decidedRound(proposals);
        round.startApplication("apply", contract.applicationHash(round), taskId, clock.instant());
        return round;
    }

    private ObjectNode proposal(String field, Object current, Object proposed) {
        ObjectNode value = mapper.createObjectNode(); value.put("fieldKey", field);
        value.set("currentValue", mapper.valueToTree(current)); value.set("proposedValue", mapper.valueToTree(proposed));
        value.put("source", "MARKET"); value.putArray("evidenceIds").add("E-1"); return value;
    }

    private MarketAnalysisSeedSnapshot freshSeed() {
        return MarketAnalysisSeedSnapshot.createPortfolio("seed-1", PROJECT_ID, 31L, "concept-1", "legal-1",
            "2.0", HASH, HASH, "{}", OWNER_ID, clock.instant());
    }

    private ConceptPortfolioSelection activeSelection(String taskId, String action, int revision,
            ConceptPortfolioSelectionStatus status) {
        ConceptPortfolioSelection value = ConceptPortfolioSelection.create(PROJECT_ID, "run-1", "concept-1",
            "candidate-1", HASH, HASH, "선택", HASH, "selection", OWNER_ID, clock.instant());
        ReflectionTestUtils.setField(value, "id", 31L); ReflectionTestUtils.setField(value, "status", status);
        ReflectionTestUtils.setField(value, "hypothesisRevision", revision); value.attachTask(taskId, action); return value;
    }

    private JsonNode taggedInput(String action, ConceptRefinementRound round, int selectionRevision, int bmRevision) {
        ObjectNode input = mapper.createObjectNode(); input.put("action", action);
        input.put("expectedHypothesisRevision", selectionRevision);
        input.set("refinementApplication", application.binding(round, round.getApplicationHash(), selectionRevision, bmRevision));
        return input;
    }

    private TaskRunService.Claim claim(String taskId) { return new TaskRunService.Claim(taskId, "attempt-1", "claim-1"); }
    private TaskRunWorkerContext context(String taskId, JsonNode input) {
        return new TaskRunWorkerContext(taskId, PROJECT_ID, OWNER_ID,
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION, "CONCEPT_PORTFOLIO_SELECTION", "31",
            input.toString(), HASH, "key", "correlation", "1.0", "1.0", "ko-KR", 1, 2);
    }
    private ExecutionResponse response(JsonNode result) {
        return new ExecutionResponse("internal-ai-execution-v1", TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION.name(),
            "1.0", "task", "attempt-1", "request", HASH, "1.0", result, null, null, null);
    }

    private List<ConceptPortfolioHypothesisDecision> hypothesisRows(boolean deltaRequired) {
        List<ConceptPortfolioHypothesisDecision> values = new ArrayList<>();
        for (PortfolioHypothesisType type : PortfolioHypothesisType.values()) {
            boolean delta = deltaRequired && type == PortfolioHypothesisType.PRICE;
            values.add(ConceptPortfolioHypothesisDecision.create(31L, PROJECT_ID, "concept-1", type,
                "\"old\"", "\"old\"", "USER", "ACCEPTED", 1, true, "VALID", null,
                delta ? "REVIEW_REQUIRED" : "NONE", delta ? "PENDING" : "NOT_REQUIRED",
                delta, OWNER_ID, clock.instant()));
        }
        return values;
    }

    private JsonNode confirmResult(boolean deltaRequired) {
        ObjectNode result = mapper.createObjectNode(); ArrayNode array = result.putArray("hypotheses");
        for (PortfolioHypothesisType type : PortfolioHypothesisType.values()) {
            ObjectNode item = array.addObject(); item.put("hypothesisType", type.name());
            item.put("finalValue", "new-" + type.name()); item.put("source", "USER");
            item.put("decisionStatus", "USER_EDITED_ACCEPTED"); item.put("locked", true);
            item.put("semanticStatus", "VALID"); item.putNull("semanticReason");
            boolean delta = deltaRequired && type == PortfolioHypothesisType.PRICE;
            item.put("legalImpact", delta ? "REVIEW_REQUIRED" : "NONE");
            item.put("legalReviewStatus", delta ? "PENDING" : "NOT_REQUIRED");
            item.put("deltaLegalRequired", delta);
        }
        return result;
    }

    private JsonNode deltaResult(boolean approved) {
        ObjectNode result = (ObjectNode) confirmResult(false);
        ObjectNode delta = result.putObject("deltaLegalResult"); delta.put("approved", approved);
        delta.put("reviewToken", "review-1"); delta.put("status", approved ? "PASSED" : "BLOCKED");
        delta.putArray("hypothesisTypes").add("PRICE"); return result;
    }

    private void stubMaterialization(ConceptRefinementRound round, ConceptPortfolioSelection real,
            List<ConceptPortfolioHypothesisDecision> current, int currentBmRevision, int patchedBmRevision) {
        when(rounds.findByIdForUpdate(101L)).thenReturn(Optional.of(round));
        when(selections.findLocked(31L)).thenReturn(Optional.of(real));
        when(bmPlans.current(PROJECT_ID)).thenReturn(new BmPlanPreparationService.PlanView(
            mapper.createObjectNode(), mapper.createObjectNode(), currentBmRevision));
        when(bmPlans.patchForRefinement(eq(PROJECT_ID), eq(OWNER_ID), eq(3), any()))
            .thenReturn(new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), patchedBmRevision));
        when(seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-1")).thenReturn(Optional.of(seed));
        when(hypotheses.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(31L))
            .thenReturn(current);
        for (ConceptPortfolioHypothesisDecision value : current) {
            lenient().when(hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                31L, value.getHypothesisType())).thenReturn(Optional.of(value));
        }
    }

    private DeltaFixture deltaFixture(String taskId) {
        ConceptRefinementRound round = applyingRound("confirm-task", proposal("price", "old", "new"));
        round.recordAppliedLineage(5, 3, clock.instant()); round.legalPending(taskId);
        ConceptPortfolioSelection real = activeSelection(taskId, "DELTA_LEGAL", 5,
            ConceptPortfolioSelectionStatus.DELTA_LEGAL_PENDING);
        lenient().when(rounds.findByIdForUpdate(101L)).thenReturn(Optional.of(round));
        lenient().when(selections.findLocked(31L)).thenReturn(Optional.of(real));
        lenient().when(bmPlans.current(PROJECT_ID)).thenReturn(new BmPlanPreparationService.PlanView(
            mapper.createObjectNode(), mapper.createObjectNode(), 3));
        List<ConceptPortfolioHypothesisDecision> current = hypothesisRows(false);
        lenient().when(hypotheses.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(31L))
            .thenReturn(current);
        for (ConceptPortfolioHypothesisDecision value : current) {
            lenient().when(hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                31L, value.getHypothesisType())).thenReturn(Optional.of(value));
        }
        JsonNode input = taggedInput("DELTA_LEGAL", round, 5, 3);
        return new DeltaFixture(round, real, input);
    }

    private record DeltaFixture(ConceptRefinementRound round,
                                ConceptPortfolioSelection selection, JsonNode input) { }
}
