package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ConceptRefinementPresentationServiceTests {
    private final ObjectMapper mapper=new ObjectMapper();
    private final ProjectRepository projects=mock(ProjectRepository.class);
    private final ConceptRefinementRoundRepository rounds=mock(ConceptRefinementRoundRepository.class);
    private final ConceptRefinementFinalRepository finals=mock(ConceptRefinementFinalRepository.class);
    private final MarketAnalysisSeedSnapshotRepository seeds=mock(MarketAnalysisSeedSnapshotRepository.class);
    private final ConceptRefinementDecisionContract decisions=mock(ConceptRefinementDecisionContract.class);
    private final ConceptRefinementService refinement=mock(ConceptRefinementService.class);
    private final ConceptRefinementPresentationService service=new ConceptRefinementPresentationService(
        projects,rounds,finals,seeds,decisions,refinement,mapper);

    @Test void projectsRoundHistoryDecisionsEvidenceAndExactCommandTokens(){
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L,7L)).thenReturn(Optional.of(mock(Project.class)));
        var current=new ConceptRefinementService.CurrentView("session-1","AWAITING_DECISION",false,2,
            new ConceptRefinementService.PolicyView("v1",3,5,20,1),mapper.createArrayNode(),mapper.createArrayNode(),null,
            new ConceptRefinementService.RetryView(false,1,3),"sha256:"+"b".repeat(64),null,
            new ConceptRefinementService.NextRoundView(true,2,3,"AVAILABLE"),
            new ConceptRefinementService.LegalRecoveryView(false));
        when(refinement.current(7L,41L)).thenReturn(current);
        ConceptRefinementRound first=round(1,ConceptRefinementRound.State.CONTINUED,"decision-1");
        ConceptRefinementRound second=round(2,ConceptRefinementRound.State.AWAITING_DECISION,null);
        when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(41L,"session-1"))
            .thenReturn(List.of(first,second));
        ObjectNode accepted=proposal("p-1","price","E-1");
        ObjectNode declined=proposal("p-0","targetUsers","E-0");
        ObjectNode undecided=proposal("p-2","channels","E-2");
        when(decisions.proposalSet(first)).thenReturn(set("set-1",accepted,declined));
        when(decisions.proposalSet(second)).thenReturn(set("set-2",undecided));
        when(decisions.decisionView(first)).thenReturn(new ConceptRefinementDecisionContract.DecisionView(
            "CONTINUED","sha256:"+"a".repeat(64),List.of("p-1"),1,0,
            new ConceptRefinementDecisionContract.PlanSummary(1,0,0)));
        MarketAnalysisSeedSnapshot seed=mock(MarketAnalysisSeedSnapshot.class);
        when(seeds.findByIdAndDeletedAtIsNull("seed-source")).thenReturn(Optional.of(seed));
        when(seed.getSnapshotJson()).thenReturn("{\"selectedConcept\":{},\"finalHypotheses\":{}}");

        var value=service.current(7L,41L);
        assertThat(value.path("roundHistory")).hasSize(2); assertThat(value.path("changes")).hasSize(3);
        assertThat(value.path("changes").get(0).path("accepted").asBoolean()).isTrue();
        assertThat(value.path("changes").get(1).path("accepted").asBoolean()).isFalse();
        assertThat(value.path("changes").get(2).path("accepted").isNull()).isTrue();
        assertThat(value.path("changes").get(2).path("evidenceIds").get(0).asText()).isEqualTo("E-2");
        assertThat(value.path("command").path("proposalSetHash").asText()).isEqualTo("sha256:"+"b".repeat(64));
        assertThat(value.path("sourceBusinessValidationSessionId").asText()).isEqualTo("session-1");
    }

    @Test void finalizedPresentationUsesOnlyTheSameSessionFinalDocument(){
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L,7L)).thenReturn(Optional.of(mock(Project.class)));
        var current=new ConceptRefinementService.CurrentView("session-final","FINALIZED",false,2,
            new ConceptRefinementService.PolicyView("v1",3,5,20,1),mapper.createArrayNode(),mapper.createArrayNode(),null,
            new ConceptRefinementService.RetryView(false,1,3),null,null,
            new ConceptRefinementService.NextRoundView(false,2,3,"FINALIZED"),
            new ConceptRefinementService.LegalRecoveryView(false));
        when(refinement.current(7L,41L)).thenReturn(current);
        when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(
            41L,"session-final")).thenReturn(List.of());
        ConceptRefinementFinal fin=mock(ConceptRefinementFinal.class);
        when(fin.getSourceBusinessValidationSessionId()).thenReturn("session-final");
        when(fin.getFinalJson()).thenReturn("{\"selectedConcept\":{\"identity\":{\"conceptName\":\"최종안\"}},\"finalHypotheses\":{}}");
        when(finals.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(fin));

        var value=service.current(7L,41L);
        assertThat(value.path("finalized").asBoolean()).isTrue();
        assertThat(value.path("outcome").asText()).isEqualTo("CONVERGED");
        assertThat(value.path("concept").path("selectedConcept").path("identity").path("conceptName").asText())
            .isEqualTo("최종안");
    }

    private ConceptRefinementRound round(int number,ConceptRefinementRound.State state,String decisionJson){
        ConceptRefinementRound value=mock(ConceptRefinementRound.class);when(value.getRoundNumber()).thenReturn(number);
        when(value.getState()).thenReturn(state);when(value.getProposalJson()).thenReturn("[]");
        when(value.getDecisionJson()).thenReturn(decisionJson);when(value.getDriftRejectionsJson()).thenReturn("[]");
        when(value.getSourceMarketSeedSnapshotId()).thenReturn("seed-source");return value;}
    private ObjectNode proposal(String key,String field,String evidence){ObjectNode value=mapper.createObjectNode();
        value.put("proposalKey",key);value.put("fieldKey",field);value.put("currentValue","전");value.put("proposedValue","후");
        value.put("title",field);value.put("beforeText","전");value.put("afterText","후");value.put("rationale","근거 반영");
        value.put("source","MARKET");value.putArray("evidenceIds").add(evidence);return value;}
    private ConceptRefinementDecisionContract.ProposalSet set(String hash,ObjectNode... proposals){
        ArrayNode projected=mapper.createArrayNode();Map<String,ObjectNode> byKey=new java.util.LinkedHashMap<>();
        for(ObjectNode proposal:proposals){projected.add(proposal);byKey.put(proposal.path("proposalKey").asText(),proposal);}
        return new ConceptRefinementDecisionContract.ProposalSet(hash,projected,byKey,List.copyOf(byKey.keySet()));}
}
