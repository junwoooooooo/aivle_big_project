package com.aivle.backend.pipeline.planning;
import static org.assertj.core.api.Assertions.assertThat;
import com.aivle.backend.pipeline.integration.domain.*;import com.aivle.backend.pipeline.planning.application.*;import com.aivle.backend.pipeline.planning.domain.PlanningChangeDecision;import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.time.Instant;import java.util.*;import org.junit.jupiter.api.Test;import tools.jackson.databind.ObjectMapper;
class PlanningPatchAndSnapshotTests {private final ObjectMapper mapper=new ObjectMapper();
 private PlanningChangeProposal proposal(){return PlanningChangeProposal.pending("p1","r1",1L,"초기 고객을 계약 단지로 좁히기","[\"targetCustomer\"]","\"전국 사용자\"","\"계약 단지\"","근거","[]","[]");}
 @Test void patchIsDeterministicAndUsesExactUserDecision(){var p=proposal();var d=PlanningChangeDecision.decide("p1",1L,ProposalDecisionStatus.PARTIALLY_ADOPT,"\"3개 계약 단지\"",7L,Instant.EPOCH);var patch=new DeterministicPlanningPatch(mapper);var original=mapper.readTree("{\"title\":\"A\",\"targetCustomer\":\"전국 사용자\"}");assertThat(patch.apply(original,List.of(p),Map.of("p1",d))).isEqualTo(patch.apply(original,List.of(p),Map.of("p1",d)));assertThat(patch.apply(original,List.of(p),Map.of("p1",d)).path("targetCustomer").asText()).isEqualTo("3개 계약 단지");}
 @Test void finalizedHashIsStableAcrossPropertyOrder(){var h=new SnapshotHasher(mapper);assertThat(h.hash(mapper.readTree("{\"planning\":{\"target\":\"A\"},\"projectId\":1}"))).isEqualTo(h.hash(mapper.readTree("{\"projectId\":1,\"planning\":{\"target\":\"A\"}}"))).matches("sha256:[0-9a-f]{64}");}
}
