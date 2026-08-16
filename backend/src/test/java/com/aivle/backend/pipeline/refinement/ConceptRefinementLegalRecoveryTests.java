package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;

@ExtendWith(MockitoExtension.class)
class ConceptRefinementLegalRecoveryTests {
    static final long OWNER=7L,PROJECT=41L; static final String HASH="sha256:"+"a".repeat(64);
    @Mock ProjectRepository projects; @Mock ConceptRefinementRoundRepository rounds;
    @Mock ConceptRefinementLineageGuard lineage; @Mock ConceptPortfolioSelectionRepository selections;
    @Mock ConceptPortfolioHypothesisDecisionRepository hypotheses;
    @Mock ConceptLegalRegulatoryReportRepository reports; @Mock BmPlanPreparationService bmPlans;
    @Mock ConceptPortfolioSelectionService selectionService; @Mock ConceptRefinementService refinement;
    @Mock Project project; @Mock User owner;
    final ObjectMapper mapper=new ObjectMapper();
    final Clock clock=Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"),ZoneOffset.UTC);
    ConceptPortfolioJsonHasher hasher; ConceptRefinementDecisionContract decisions;
    ConceptRefinementApplicationBeforeContract before; ConceptRefinementLegalRecoveryService recovery;
    ConceptPortfolioHypothesisDecision prior; ConceptRefinementRound round; ConceptPortfolioSelection selection;

    @BeforeEach void setup(){
        hasher=new ConceptPortfolioJsonHasher(mapper);decisions=new ConceptRefinementDecisionContract(mapper,hasher);
        before=new ConceptRefinementApplicationBeforeContract(hypotheses,decisions,hasher,mapper);
        recovery=new ConceptRefinementLegalRecoveryService(projects,rounds,lineage,before,decisions,selections,
            hypotheses,reports,bmPlans,selectionService,refinement,hasher,mapper,clock);
        lenient().when(project.getOwner()).thenReturn(owner);lenient().when(owner.getId()).thenReturn(OWNER);
        lenient().when(projects.findByIdForUpdate(PROJECT)).thenReturn(Optional.of(project));
        lenient().when(reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(31L,"CURRENT")).thenReturn(List.of());
    }

    @Test void applicationBeforeCapturesFullSelectedMetadataAndRetryKeepsSameSnapshot(){
        ConceptRefinementRound value=decidedRound();
        prior=priorDecision();when(hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
            31L,PortfolioHypothesisType.PRICE)).thenReturn(Optional.of(prior));
        var snapshot=before.capture(value);value.captureApplicationBefore(snapshot.json(),snapshot.hash());
        var stored=mapper.readTree(value.getApplicationBeforeJson()).path("hypotheses").get(0);
        assertThat(stored.path("hypothesisType").asText()).isEqualTo("PRICE");
        assertThat(stored.path("proposedValueJson").asText()).isEqualTo("\"8900\"");
        assertThat(stored.path("finalValueJson").asText()).isEqualTo("\"10000\"");
        assertThat(stored.path("source").asText()).isEqualTo("USER");
        assertThat(stored.path("proposalVersion").asInt()).isEqualTo(1);
        String json=value.getApplicationBeforeJson(),hash=value.getApplicationBeforeHash();
        value.startApplication("apply",decisions.applicationHash(value),"confirm-1",clock.instant());
        value.applicationFailed("confirm-1","FAILED");
        before.validate(value);value.retryApplication("retry",decisions.applicationHash(value),"confirm-2",clock.instant());
        assertThat(value.getApplicationBeforeJson()).isEqualTo(json);assertThat(value.getApplicationBeforeHash()).isEqualTo(hash);
    }

    @Test void validBlockedRecoveryRestoresNewHypothesisVersionBmBaselineAndIsIdempotent(){
        fixture();
        recovery.recover(OWNER,PROJECT,"recover",1,round.getDecisionHash());
        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.RECOVERED);
        assertThat(round.isSeedRebuildRequired()).isTrue();
        assertThat(selection.getHypothesisRevision()).isEqualTo(6);
        assertThat(round.getAppliedSelectionRevision()).isEqualTo(6);
        assertThat(round.getAppliedBmPlanRevision()).isEqualTo(5);
        ArgumentCaptor<List<ConceptPortfolioHypothesisDecision>> restored=ArgumentCaptor.forClass(List.class);
        verify(hypotheses).saveAll(restored.capture());
        assertThat(restored.getValue()).singleElement().satisfies(value->{
            assertThat(value.getProposalVersion()).isEqualTo(2);
            assertThat(value.getFinalValueJson()).isEqualTo("\"10000\"");
            assertThat(value.getLegalReviewStatus()).isEqualTo("PASSED");});
        ArgumentCaptor<ObjectNode> patch=ArgumentCaptor.forClass(ObjectNode.class);
        verify(bmPlans).patchForRefinement(eq(PROJECT),eq(OWNER),eq(4),patch.capture());
        assertThat(patch.getValue().path("key_activities").get(0).asText()).isEqualTo("A");
        verify(selectionService).finalizeReport(OWNER,PROJECT,31L);

        recovery.recover(OWNER,PROJECT,"recover",1,round.getDecisionHash());
        verify(hypotheses,times(1)).saveAll(any());
        verify(bmPlans,times(1)).patchForRefinement(anyLong(),anyLong(),anyInt(),any());
        verify(selectionService,times(1)).finalizeReport(anyLong(),anyLong(),anyLong());
    }

    @Test void missingSnapshotOrBrokenPostApplyLineageMarksStaleWithoutRollback(){
        round=decidedRound();blocked(round);when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT))
            .thenReturn(Optional.of(round));when(lineage.postApplyCurrent(PROJECT,round)).thenReturn(true);
        recovery.recover(OWNER,PROJECT,"missing",1,round.getDecisionHash());
        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.STALE);
        verify(hypotheses,never()).saveAll(any());verify(selectionService,never()).finalizeReport(anyLong(),anyLong(),anyLong());

        reset(rounds,lineage);round=decidedRound();prior=priorDecision();
        when(hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(31L,PortfolioHypothesisType.PRICE))
            .thenReturn(Optional.of(prior));var snapshot=before.capture(round);round.captureApplicationBefore(snapshot.json(),snapshot.hash());blocked(round);
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(round));
        when(lineage.postApplyCurrent(PROJECT,round)).thenReturn(false);
        recovery.recover(OWNER,PROJECT,"external",1,round.getDecisionHash());
        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.STALE);
        verify(hypotheses,never()).saveAll(any());
    }

    private void fixture(){
        round=decidedRound();prior=priorDecision();
        when(hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(31L,PortfolioHypothesisType.PRICE))
            .thenReturn(Optional.of(prior));var snapshot=before.capture(round);round.captureApplicationBefore(snapshot.json(),snapshot.hash());blocked(round);
        selection=ConceptPortfolioSelection.create(PROJECT,"run","concept","candidate",HASH,HASH,"선택",HASH,"select",OWNER,clock.instant());
        ReflectionTestUtils.setField(selection,"id",31L);ReflectionTestUtils.setField(selection,"status",ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED);
        ReflectionTestUtils.setField(selection,"hypothesisRevision",5);
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(round));
        when(lineage.postApplyCurrent(PROJECT,round)).thenReturn(true);when(selections.findLocked(31L)).thenReturn(Optional.of(selection));
        when(bmPlans.patchForRefinement(eq(PROJECT),eq(OWNER),eq(4),any())).thenReturn(
            new BmPlanPreparationService.PlanView(bmPlan(),mapper.createObjectNode().put("months",12),5));
    }

    private ConceptRefinementRound decidedRound(){
        CompletedSource source=new CompletedSource("session",91L,92L,"seed-1",31L,4,3,HASH);
        ConceptRefinementRound value=ConceptRefinementRound.start(PROJECT,source,"proposal","start",HASH);
        ReflectionTestUtils.setField(value,"id",101L);ArrayNode proposals=mapper.createArrayNode();
        proposals.add(proposal("price","10000","12500"));proposals.add(proposal("keyActivities",List.of("A"),List.of("B")));
        value.materialize(proposals.toString(),"[]",true);var set=decisions.proposalSet(value);
        var decision=decisions.decision(value,set,set.orderedKeys(),false);
        value.recordDecision(decision.snapshot().toString(),decision.hash(),"decision",OWNER,clock.instant(),false);return value;
    }
    private void blocked(ConceptRefinementRound value){ReflectionTestUtils.setField(value,"state",ConceptRefinementRound.State.LEGAL_BLOCKED);
        ReflectionTestUtils.setField(value,"applicationHash",decisions.applicationHash(value));
        ReflectionTestUtils.setField(value,"appliedSelectionRevision",5);ReflectionTestUtils.setField(value,"appliedBmPlanRevision",4);}
    private ConceptPortfolioHypothesisDecision priorDecision(){return ConceptPortfolioHypothesisDecision.create(31L,PROJECT,"concept",PortfolioHypothesisType.PRICE,
        "\"8900\"","\"10000\"","USER","ACCEPTED",1,true,"VALID","validated","NONE","PASSED",false,OWNER,clock.instant());}
    private ObjectNode proposal(String field,Object current,Object proposed){ObjectNode value=mapper.createObjectNode();value.put("fieldKey",field);
        value.set("currentValue",mapper.valueToTree(current));value.set("proposedValue",mapper.valueToTree(proposed));
        value.put("source","MARKET");value.putArray("evidenceIds").add("E-1");return value;}
    private ObjectNode bmPlan(){ObjectNode value=mapper.createObjectNode();value.putArray("key_activities").add("A");return value;}
}
