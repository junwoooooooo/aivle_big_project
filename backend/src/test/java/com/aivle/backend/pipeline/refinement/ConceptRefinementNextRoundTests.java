package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.user.entity.User;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class ConceptRefinementNextRoundTests {
    private static final Long OWNER = 7L, PROJECT = 41L;
    private static final String HASH = "sha256:" + "a".repeat(64);
    @Mock ProjectRepository projects; @Mock BusinessValidationCoordinator validations;
    @Mock ConceptPortfolioSelectionRepository selections; @Mock MarketAnalysisSeedSnapshotRepository seeds;
    @Mock ConceptRefinementRoundRepository rounds; @Mock ConceptRefinementMaterialFactory materials;
    @Mock ConceptPortfolioSelectionTaskFactory tasks; @Mock CanonicalInputHasher inputHasher;
    @Mock ConceptRefinementDecisionContract decisions; @Mock ConceptRefinementLineageGuard lineage;
    @Mock ConceptRefinementApplicationBeforeContract applicationBefore;
    @Mock Project project; @Mock User owner; @Mock ConceptPortfolioSelection selection; @Mock TaskRun task;
    private final ObjectMapper mapper = new ObjectMapper();
    private ConceptRefinementService service;
    private final CompletedSource source = new CompletedSource("session-1", 91L, 92L,
        "seed-1", 31L, 4, 3, HASH);

    @BeforeEach void setUp() {
        service = new ConceptRefinementService(projects, validations, selections, seeds, rounds,
            materials, tasks, inputHasher, mapper, decisions, lineage, applicationBefore);
        lenient().when(project.getOwner()).thenReturn(owner); lenient().when(owner.getId()).thenReturn(OWNER);
        lenient().when(projects.findByIdForUpdate(PROJECT)).thenReturn(Optional.of(project));
        lenient().when(selection.getId()).thenReturn(31L); lenient().when(selection.getHypothesisRevision()).thenReturn(4);
        lenient().when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(PROJECT)).thenReturn(Optional.of(selection));
        lenient().when(task.getId()).thenReturn("round-2-task"); lenient().when(task.getInputHash()).thenReturn(HASH);
        lenient().when(tasks.create(eq(OWNER), same(selection), eq("REFINE_FROM_MARKET"), any(), anyString(), anyString())).thenReturn(task);
        lenient().when(materials.inputForRound(eq(PROJECT), same(selection), any(), eq(1), anyList()))
            .thenReturn(mapper.createObjectNode().put("action", "REFINE_FROM_MARKET"));
        lenient().when(rounds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void awaitingDecisionExplicitNextDeclinesParentAndCreatesBoundRoundTwoWithoutProductMutation() {
        ConceptRefinementRound parent = awaitingRound();
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(parent));
        when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(PROJECT,"session-1"))
            .thenReturn(List.of(parent));
        when(lineage.proposalBaselineCurrent(OWNER, PROJECT, parent)).thenReturn(true);
        when(decisions.proposalSet(parent)).thenReturn(new ConceptRefinementDecisionContract.ProposalSet(
            HASH, mapper.createArrayNode(), Map.of(), List.of()));

        var view = service.next(OWNER, PROJECT, "next-1", "request-1", 1, HASH, null);

        assertThat(parent.getState()).isEqualTo(ConceptRefinementRound.State.DECLINED);
        assertThat(view.state()).isEqualTo("PROPOSING"); assertThat(view.round()).isEqualTo(2);
        ArgumentCaptor<ConceptRefinementRound> saved = ArgumentCaptor.forClass(ConceptRefinementRound.class);
        verify(rounds).save(saved.capture());
        assertThat(saved.getValue().getParentRoundId()).isEqualTo(101L);
        assertThat(saved.getValue().getBusinessValidationSessionId()).isEqualTo("session-1");
        assertThat(saved.getValue().getSourceMarketVersionId()).isEqualTo(91L);
        assertThat(saved.getValue().getSourceBmVersionId()).isEqualTo(92L);
        assertThat(saved.getValue().getSourceMarketSeedSnapshotId()).isEqualTo("seed-1");
        assertThat(saved.getValue().baselineSelectionRevision()).isEqualTo(4);
        assertThat(saved.getValue().baselineBmPlanRevision()).isEqualTo(3);
    }

    @Test void appliedRoundContinuesWithAppliedBaselineMergedOverlayAndSeedRebuildFlag() {
        ConceptRefinementRound parent = appliedRound();
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(parent));
        when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(PROJECT,"session-1"))
            .thenReturn(List.of(parent));
        when(lineage.postApplyCurrent(PROJECT, parent)).thenReturn(true);
        when(selection.getHypothesisRevision()).thenReturn(5);
        ObjectNode plan = mapper.createObjectNode(); plan.putObject("hypotheses").put("PRICE", "12,000원");
        plan.putObject("bmPlan"); plan.putObject("overlay").put("targetUsers", "B 고객");
        when(decisions.applicationPlan(parent)).thenReturn(plan);

        var view = service.next(OWNER, PROJECT, "next-2", "request-2", 1, null, HASH);

        assertThat(parent.getState()).isEqualTo(ConceptRefinementRound.State.CONTINUED);
        assertThat(view.round()).isEqualTo(2);
        ArgumentCaptor<ConceptRefinementRound> saved = ArgumentCaptor.forClass(ConceptRefinementRound.class);
        verify(rounds).save(saved.capture());
        assertThat(saved.getValue().baselineSelectionRevision()).isEqualTo(5);
        assertThat(saved.getValue().baselineBmPlanRevision()).isEqualTo(4);
        assertThat(mapper.readTree(saved.getValue().baselineOverlayJson()).path("targetUsers").asText()).isEqualTo("B 고객");
        assertThat(saved.getValue().isSeedRebuildRequired()).isTrue();
    }

    @Test void roundThreeCannotCreateRoundFourOrTask() {
        ConceptRefinementRound parent = awaitingRound(); ReflectionTestUtils.setField(parent,"roundNumber",3);
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(parent));
        when(decisions.proposalSet(parent)).thenReturn(new ConceptRefinementDecisionContract.ProposalSet(
            HASH, mapper.createArrayNode(), Map.of(), List.of()));
        assertThatThrownBy(() -> service.next(OWNER,PROJECT,"next-4","request",3,HASH,null))
            .isInstanceOf(BusinessException.class);
        verify(tasks,never()).create(anyLong(),any(),anyString(),any(),anyString(),anyString());
    }

    @Test void recoveredRoundContinuesFromRecoveredRevisionsWithoutBlockedOverlay() {
        ConceptRefinementRound parent=appliedRound();
        ReflectionTestUtils.setField(parent,"state",ConceptRefinementRound.State.RECOVERED);
        ReflectionTestUtils.setField(parent,"recoveredAt",Instant.parse("2026-08-17T00:03:00Z"));
        ReflectionTestUtils.setField(parent,"appliedSelectionRevision",6);
        ReflectionTestUtils.setField(parent,"appliedBmPlanRevision",5);
        ReflectionTestUtils.setField(parent,"baselineOverlayJson","{\"targetUsers\":\"baseline\"}");
        ReflectionTestUtils.setField(parent,"seedRebuildRequired",true);
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(parent));
        when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(PROJECT,"session-1"))
            .thenReturn(List.of(parent));
        when(lineage.postApplyCurrent(PROJECT,parent)).thenReturn(true);
        when(selection.getHypothesisRevision()).thenReturn(6);

        service.next(OWNER,PROJECT,"next-recovered","request",1,null,HASH);

        ArgumentCaptor<ConceptRefinementRound> saved=ArgumentCaptor.forClass(ConceptRefinementRound.class);
        verify(rounds).save(saved.capture());
        assertThat(saved.getValue().baselineSelectionRevision()).isEqualTo(6);
        assertThat(saved.getValue().baselineBmPlanRevision()).isEqualTo(5);
        assertThat(mapper.readTree(saved.getValue().baselineOverlayJson()).path("targetUsers").asText()).isEqualTo("baseline");
        assertThat(saved.getValue().isSeedRebuildRequired()).isTrue();
        verify(decisions,never()).applicationPlan(parent);
    }

    @Test void recoveryProjectionAndRoundThreeNextAvailabilityAreStateBound() {
        ConceptRefinementRound blocked=appliedRound();ReflectionTestUtils.setField(blocked,"state",ConceptRefinementRound.State.LEGAL_BLOCKED);
        when(applicationBefore.available(blocked)).thenReturn(true);when(decisions.proposalSet(blocked)).thenReturn(
            new ConceptRefinementDecisionContract.ProposalSet(HASH,mapper.createArrayNode(),Map.of(),List.of()));
        assertThat(service.view(blocked,false).recovery().available()).isTrue();
        ReflectionTestUtils.setField(blocked,"state",ConceptRefinementRound.State.RECOVERED);
        ReflectionTestUtils.setField(blocked,"roundNumber",3);ReflectionTestUtils.setField(blocked,"recoveredAt",Instant.now());
        var view=service.view(blocked,false);assertThat(view.nextRound().available()).isFalse();
    }

    @Test void sameNextIdempotencyKeyReplaysItsChildEvenAfterALaterRoundExists() {
        ConceptRefinementRound parent = awaitingRound(); parent.declined();
        ConceptRefinementRound child = ConceptRefinementRound.next(parent,4,3,"{}",false,
            "round-2-task","next-1",HASH); ReflectionTestUtils.setField(child,"id",202L);
        ConceptRefinementRound roundThree = ConceptRefinementRound.next(child,4,3,"{}",false,
            "round-3-task","next-2",HASH); ReflectionTestUtils.setField(roundThree,"id",303L);
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(roundThree));
        when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(PROJECT,"session-1"))
            .thenReturn(List.of(parent,child,roundThree));
        when(rounds.findByParentRoundIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(child));
        when(decisions.proposalSet(parent)).thenReturn(new ConceptRefinementDecisionContract.ProposalSet(
            HASH,mapper.createArrayNode(),Map.of(),List.of()));
        when(inputHasher.hash(any(),eq("1.0"),eq("ko-KR"),anyString())).thenReturn(HASH);

        var replay = service.next(OWNER,PROJECT,"next-1","retry",1,HASH,null);

        assertThat(replay.round()).isEqualTo(2); assertThat(replay.state()).isEqualTo("PROPOSING");
        verify(tasks,never()).create(anyLong(),any(),anyString(),any(),anyString(),anyString());
        verify(rounds,never()).save(any());
    }

    private ConceptRefinementRound awaitingRound() {
        ConceptRefinementRound round = ConceptRefinementRound.start(PROJECT,source,"proposal-1","start",HASH);
        ReflectionTestUtils.setField(round,"id",101L); round.materialize("[]","[]",true); return round;
    }

    private ConceptRefinementRound appliedRound() {
        ConceptRefinementRound round = ConceptRefinementRound.start(PROJECT,source,"proposal-1","start",HASH);
        ReflectionTestUtils.setField(round,"id",101L); round.materialize("[]","[]",true);
        round.recordDecision("{\"plan\":{}}",HASH,"decision",OWNER,Instant.parse("2026-08-17T00:00:00Z"),false);
        round.startLocalApplication("apply",HASH,Instant.parse("2026-08-17T00:01:00Z"));
        round.recordAppliedLineage(5,4,Instant.parse("2026-08-17T00:02:00Z")); round.readyForFinalization();
        return round;
    }
}
