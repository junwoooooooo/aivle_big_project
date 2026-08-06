package com.aivle.backend.pipeline.planning.application;

import static com.aivle.backend.pipeline.planning.api.PlanningApiModels.*;

import com.aivle.backend.common.exception.*;
import com.aivle.backend.pipeline.integration.domain.*;
import com.aivle.backend.pipeline.integration.repository.*;
import com.aivle.backend.pipeline.planning.domain.*;
import com.aivle.backend.pipeline.planning.repository.*;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.selection.domain.SelectedConceptSnapshot;
import com.aivle.backend.pipeline.selection.repository.*;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;

@Service @RequiredArgsConstructor
public class PlanningService {
    public static final String CONTRACT = "finalized-planning-snapshot-v1";
    private final ProjectRepository projects;
    private final ConceptSelectionRepository selections;
    private final SelectedConceptSnapshotRepository selectedSnapshots;
    private final MarketAnalysisResultRepository marketResults;
    private final PlanningChangeProposalRepository proposals;
    private final PlanningChangeDecisionRepository decisions;
    private final PlanningSnapshotRepository planningSnapshots;
    private final FinalizedPlanningSnapshotRepository finalizedSnapshots;
    private final DeterministicPlanningPatch patch;
    private final SnapshotHasher hasher;
    private final ObjectMapper mapper;
    private final MeaningfulPlanningLabel labels;

    @Transactional(readOnly = true)
    public PlanningCurrentView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        Context context = context(projectId);
        return currentView(context);
    }

    @Transactional(readOnly = true)
    public ChangeProposalListView proposalList(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId); Context context = context(projectId);
        return new ChangeProposalListView(context.proposals.stream().map(value -> proposalView(value, context.decisionMap)).toList());
    }

    @Transactional
    public PlanningCurrentView decide(Long ownerId, Long projectId, String proposalId, DecisionRequest request) {
        requireOwned(ownerId, projectId); Context context = context(projectId);
        if (finalizedSnapshots.findByProjectIdAndSourceSelectionSnapshotIdAndDeletedAtIsNull(projectId, context.selected.getId()).isPresent())
            throw new BusinessException(ErrorCode.PLAN_ALREADY_CONFIRMED, "이미 최종 확정된 기획입니다.");
        PlanningChangeProposal proposal = context.proposals.stream().filter(value -> value.getId().equals(proposalId)).findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "기획 변경 제안을 찾을 수 없습니다."));
        if (context.decisionMap.containsKey(proposalId)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 결정한 제안입니다.");
        ProposalDecisionStatus action;
        try { action = ProposalDecisionStatus.valueOf(request.action().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 결정입니다."); }
        String modified = request.modifiedAfter() == null ? null : mapper.writeValueAsString(request.modifiedAfter());
        try {
            decisions.save(PlanningChangeDecision.decide(proposalId, projectId, action, modified, ownerId, Instant.now()));
            proposal.decide(action, modified);
        } catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage()); }
        return currentView(context(projectId));
    }

    @Transactional
    public FinalizedSnapshotView finalizePlanning(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId).filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        Context context = context(projectId);
        var existing = finalizedSnapshots.findByProjectIdAndSourceSelectionSnapshotIdAndDeletedAtIsNull(projectId, context.selected.getId());
        if (existing.isPresent()) return finalizedView(existing.get());
        if (context.staleMarketResult) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        if (context.proposals.isEmpty() || context.decisionMap.size() != context.proposals.size())
            throw new BusinessException(ErrorCode.PLAN_INCOMPLETE, "모든 시장분석 제안을 결정해 주세요.");
        JsonNode source = mapper.readTree(context.selected.getSnapshotJson());
        JsonNode applied = patch.apply(source.path("concept"), context.proposals, context.decisionMap);
        String label = meaningfulLabel(context.proposals, context.decisionMap);
        int sequence = planningSnapshots.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId)
            .map(value -> value.getSequence() + 1).orElse(1);
        String planningParent = planningSnapshots.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId)
            .map(PlanningSnapshot::getId).orElse(null);
        String finalizedParent = finalizedSnapshots.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId)
            .map(FinalizedPlanningSnapshot::getId).orElse(null);
        Instant now = Instant.now(); String planningId = UUID.randomUUID().toString();
        ObjectNode planningBody = planningBody(applied);
        ObjectNode legalBody = legalBody(source.path("legalAssessment"));
        ArrayNode changeBody = decisionBody(context.proposals, context.decisionMap);
        ObjectNode planningEnvelope = mapper.createObjectNode();
        planningEnvelope.put("displayLabel", label); planningEnvelope.set("planning", planningBody);
        planningEnvelope.set("legalControls", legalBody); planningEnvelope.set("changeDecisions", changeBody);
        String planningHash = hasher.hash(planningEnvelope);
        planningSnapshots.save(PlanningSnapshot.create(planningId, projectId, context.selected.getId(), sequence, planningParent,
            label, mapper.writeValueAsString(planningEnvelope), planningHash, ownerId, now));
        String finalId = UUID.randomUUID().toString();
        ObjectNode finalBody = mapper.createObjectNode(); finalBody.put("contract", CONTRACT); finalBody.put("snapshotId", finalId);
        finalBody.put("projectId", projectId); finalBody.put("sourceSelectionSnapshotId", context.selected.getId());
        finalBody.put("displayLabel", "최종 확정 기획 — " + label.replace("시장분석 반영안 — ", ""));
        finalBody.set("planning", planningBody); finalBody.set("legalControls", legalBody); finalBody.set("changeDecisions", changeBody);
        finalBody.put("finalizedAt", now.toString()); String hash = hasher.hash(finalBody); finalBody.put("snapshotHash", hash);
        var saved = finalizedSnapshots.save(FinalizedPlanningSnapshot.create(finalId, projectId, planningId,
            context.selected.getId(), sequence, finalizedParent, finalBody.path("displayLabel").asText(), mapper.writeValueAsString(finalBody), hash, ownerId, now));
        return finalizedView(saved);
    }

    public String meaningfulLabel(List<PlanningChangeProposal> values, Map<String, PlanningChangeDecision> decisionMap) {
        return labels.create(values, decisionMap);
    }

    private PlanningCurrentView currentView(Context c) {
        JsonNode source=mapper.readTree(c.selected.getSnapshotJson()); JsonNode preview=patch.apply(source.path("concept"), c.proposals, c.decisionMap);
        var current=finalizedSnapshots.findByProjectIdAndSourceSelectionSnapshotIdAndDeletedAtIsNull(c.selected.getProjectId(), c.selected.getId()).map(this::finalizedView).orElse(null);
        var previous=finalizedSnapshots.findAllByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(c.selected.getProjectId()).stream()
            .filter(v -> current==null || !v.getId().equals(current.snapshotId())).map(this::finalizedView).toList();
        return new PlanningCurrentView(c.selected.getId(), source.path("concept"), c.proposals.stream().map(p -> proposalView(p,c.decisionMap)).toList(),
            preview, meaningfulLabel(c.proposals,c.decisionMap), current, previous, !c.proposals.isEmpty() && c.decisionMap.size()==c.proposals.size(), c.staleMarketResult);
    }
    private Context context(Long projectId) {
        var selection=selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId).orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        var selected=selectedSnapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(),projectId).orElseThrow();
        var result=marketResults.findFirstByProjectIdAndDeletedAtIsNullOrderByCompletedAtDesc(projectId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"시장분석 결과가 필요합니다."));
        var values=proposals.findAllByModuleRunIdAndDeletedAtIsNullOrderByCreatedAtAsc(result.getModuleRunId());
        var map=decisions.findAllByProjectIdAndDeletedAtIsNullOrderByDecidedAtAsc(projectId).stream()
            .filter(d -> values.stream().anyMatch(p -> p.getId().equals(d.getProposalId())))
            .collect(Collectors.toMap(PlanningChangeDecision::getProposalId,Function.identity(),(a,b)->b,LinkedHashMap::new));
        return new Context(selected,values,map,!selected.getId().equals(result.getInputSnapshotId()));
    }
    private ChangeProposalView proposalView(PlanningChangeProposal p, Map<String,PlanningChangeDecision> map) {
        var d=map.get(p.getId()); return new ChangeProposalView(p.getId(),p.getMeaningfulTitle(),readStrings(p.getAffectedFieldsJson()),
            mapper.readTree(p.getBeforeJson()),mapper.readTree(p.getAfterJson()),p.getReason(),mapper.readTree(p.getEvidenceReferencesJson()),readStrings(p.getImpactAreasJson()),
            d==null?"PENDING":d.getDecision().name(),d==null||d.getAppliedValueJson()==null?null:mapper.readTree(d.getAppliedValueJson()));
    }
    private ObjectNode planningBody(JsonNode c) { ObjectNode p=mapper.createObjectNode(); p.set("finalConcept",c);
        copy(p,"finalTarget",c,"targetCustomer","targetCustomers"); copy(p,"finalValueProposition",c,"valueProposition","coreValue");
        copy(p,"finalFeatures",c,"features","keyFeatures"); copy(p,"finalChannels",c,"channels","channelStrategy");
        copy(p,"finalPricingRevenueHypothesis",c,"pricingRevenueHypothesis","pricing","revenueModel"); copy(p,"finalOperatingStructure",c,"operatingStructure","operatingModel"); return p; }
    private ObjectNode legalBody(JsonNode l) { ObjectNode p=mapper.createObjectNode(); copy(p,"requiredControls",l,"requiredControls","controls");
        copy(p,"requiredDisclosures",l,"requiredDisclosures","disclosures"); copy(p,"allowedClaims",l,"allowedClaims"); copy(p,"prohibitedExpressions",l,"prohibitedExpressions","prohibitedClaims"); return p; }
    private void copy(ObjectNode out,String target,JsonNode source,String... keys){for(String k:keys)if(source.has(k)){out.set(target,source.get(k));return;}out.set(target,NullNode.getInstance());}
    private ArrayNode decisionBody(List<PlanningChangeProposal> ps,Map<String,PlanningChangeDecision> ds){ArrayNode a=mapper.createArrayNode();ps.stream().sorted(Comparator.comparing(PlanningChangeProposal::getId)).forEach(p->{var d=ds.get(p.getId());ObjectNode n=mapper.createObjectNode();n.put("proposalId",p.getId());n.put("meaningfulTitle",p.getMeaningfulTitle());n.put("decision",d.getDecision().name());if(d.getAppliedValueJson()!=null)n.set("appliedValue",mapper.readTree(d.getAppliedValueJson()));a.add(n);});return a;}
    private List<String> readStrings(String json){return mapper.readValue(json,new TypeReference<List<String>>(){});}
    private FinalizedSnapshotView finalizedView(FinalizedPlanningSnapshot v){JsonNode n=mapper.readTree(v.getSnapshotJson());return new FinalizedSnapshotView(CONTRACT,v.getId(),v.getProjectId(),v.getSourceSelectionSnapshotId(),v.getSequence(),v.getDisplayLabel(),n.path("planning"),n.path("legalControls"),n.path("changeDecisions"),v.getSnapshotHash(),v.getFinalizedAt());}
    private void requireOwned(Long ownerId,Long projectId){projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId,ownerId).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private record Context(SelectedConceptSnapshot selected,List<PlanningChangeProposal> proposals,Map<String,PlanningChangeDecision> decisionMap,boolean staleMarketResult){}
}
