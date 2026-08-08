package com.aivle.backend.pipeline.techops.application;

import static com.aivle.backend.pipeline.techops.api.TechOpsApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.shared.ThreeYearTargetsContract;
import com.aivle.backend.pipeline.techops.domain.*;
import com.aivle.backend.pipeline.techops.repository.*;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class TechOpsService {
    private static final Set<String> EVIDENCE_TYPES = Set.of("QUOTE", "BOM", "SUPPLIER", "SPECIFICATION", "PILOT");
    private final ProjectRepository projects;
    private final ConceptSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final TechOpsInputPreparationRepository preparations;
    private final TechOpsEvidenceReferenceRepository evidence;
    private final TechOpsInputSnapshotRepository snapshots;
    private final TechOpsPreparationFactory preparationFactory;
    private final TechOpsInputSnapshotFactory snapshotFactory;
    private final TechOpsReadiness readiness;
    private final TechOpsProposalGateway proposalGateway;
    private final ObjectMapper mapper;

    @Transactional
    public PreparationView initialize(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId); MarketAnalysisSeedSnapshot source = currentMarketSeed(projectId);
        var existing = preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(projectId, source.getId());
        if (existing.isPresent()) return view(existing.get());
        var initial = preparationFactory.create(source);
        fillMissingProposals(initial.proposalDecisions(), source.getSnapshotJson());
        String id = UUID.randomUUID().toString();
        var saved = preparations.save(TechOpsInputPreparation.create(id, projectId, source.getId(), source.getSnapshotHash(),
            mapper.writeValueAsString(initial.requiredFacts()), mapper.writeValueAsString(initial.proposalDecisions()), ownerId));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public PreparationView current(Long ownerId, Long projectId) { requireOwned(ownerId, projectId); return view(requireCurrent(projectId)); }

    @Transactional
    public PreparationView patchFacts(Long ownerId, Long projectId, RequiredFactsPatch request) {
        requireOwnedForUpdate(ownerId, projectId); TechOpsInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        if (!request.values().isObject()) throw invalid("사용자 입력은 필드별 객체여야 합니다.");
        ObjectNode facts = (ObjectNode) mapper.readTree(preparation.getRequiredFactsJson());
        for (String key : request.values().propertyNames()) {
            if (!TechOpsPreparationFactory.REQUIRED_FACT_KEYS.contains(key)) throw invalid("지원하지 않는 기술·운영 필드입니다: " + key);
            ObjectNode field = (ObjectNode) facts.path(key);
            if (field.path("readOnly").asBoolean(false)) throw new BusinessException(ErrorCode.TECH_OPS_INPUT_INVALID,
                "상위 Snapshot에서 가져온 값은 다시 입력하지 않습니다: " + key);
            JsonNode value = request.values().get(key); validateFact(key, value);
            field.set("value", value == null ? mapper.nullNode() : value.deepCopy()); field.put("source", "USER_INPUT");
            field.put("decision", TechOpsPreparationFactory.present(value) ? "LOCKED" : "OPEN"); field.putNull("sourceSnapshotId");
        }
        preparation.updateRequiredFacts(mapper.writeValueAsString(facts), ownerId); return view(preparation);
    }

    @Transactional
    public PreparationView decideProposal(Long ownerId, Long projectId, String fieldKey, ProposalDecisionRequest request) {
        requireOwnedForUpdate(ownerId, projectId); TechOpsInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        if (!TechOpsPreparationFactory.PROPOSAL_KEYS.contains(fieldKey)) throw invalid("지원하지 않는 기술·운영 결정 필드입니다.");
        ObjectNode decisions = (ObjectNode) mapper.readTree(preparation.getProposalDecisionsJson());
        ObjectNode field = (ObjectNode) decisions.path(fieldKey); String action = request.action().strip().toUpperCase();
        switch (action) {
            case "ACCEPT" -> {
                JsonNode proposal = field.path("proposalValue"); validateDecisionValue(fieldKey, proposal);
                field.set("finalValue", proposal.deepCopy()); field.put("decision", "ACCEPTED"); field.put("alternativeRequested", false);
            }
            case "EDIT_ACCEPT" -> {
                validateDecisionValue(fieldKey, request.value()); field.set("finalValue", request.value().deepCopy());
                field.put("source", "USER_INPUT"); field.put("decision", "USER_EDITED_ACCEPTED"); field.put("alternativeRequested", false);
            }
            case "REJECT_AND_REQUEST_ALTERNATIVE" -> {
                int nextVersion = field.path("proposalVersion").asInt(1) + 1;
                String previous = mapper.writeValueAsString(field.path("proposalValue"));
                JsonNode generated = proposalGateway.propose(
                    currentMarketSeed(projectId).getSnapshotJson(), nextVersion, previous).path(fieldKey);
                validateDecisionValue(fieldKey, generated);
                if (generated.equals(field.path("proposalValue"))) throw new BusinessException(ErrorCode.TECH_OPS_PROPOSAL_INVALID,
                    "새 제안은 직전 제안과 달라야 합니다.");
                field.set("proposalValue", generated.deepCopy()); field.putNull("finalValue");
                field.put("source", "AI_HYPOTHESIS"); field.put("decision", "PROPOSED");
                field.put("proposalVersion", nextVersion); field.put("alternativeRequested", false);
            }
            default -> throw invalid("제안 결정 Action을 확인해 주세요.");
        }
        preparation.updateProposalDecisions(mapper.writeValueAsString(decisions), ownerId); return view(preparation);
    }
    private void fillMissingProposals(ObjectNode decisions, String contextJson) {
        boolean missing = TechOpsPreparationFactory.PROPOSAL_KEYS.stream()
            .anyMatch(key -> !TechOpsPreparationFactory.present(decisions.path(key).path("proposalValue")));
        if (!missing) return;
        JsonNode generated = proposalGateway.propose(contextJson, 1, "");
        for (String key : TechOpsPreparationFactory.PROPOSAL_KEYS) {
            ObjectNode field = (ObjectNode) decisions.path(key);
            if (TechOpsPreparationFactory.present(field.path("proposalValue"))) continue;
            JsonNode proposal = generated.path(key); validateDecisionValue(key, proposal);
            field.set("proposalValue", proposal.deepCopy()); field.put("source", "AI_HYPOTHESIS");
        }
    }

    @Transactional
    public PreparationView addEvidence(Long ownerId, Long projectId, EvidenceRequest request) {
        requireOwnedForUpdate(ownerId, projectId); TechOpsInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        String type=request.evidenceType().strip().toUpperCase();
        if (!EVIDENCE_TYPES.contains(type)) throw new BusinessException(ErrorCode.TECH_OPS_EVIDENCE_INVALID);
        evidence.save(TechOpsEvidenceReference.create(UUID.randomUUID().toString(), preparation.getId(), projectId, type,
            request.displayName(), request.artifactRef(), request.description(), ownerId));
        return view(preparation);
    }

    @Transactional
    public PreparationView removeEvidence(Long ownerId, Long projectId, String evidenceId) {
        requireOwnedForUpdate(ownerId, projectId); TechOpsInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        var value = evidence.findByIdAndPreparationIdAndProjectIdAndDeletedAtIsNull(evidenceId, preparation.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        value.softDelete(); return view(preparation);
    }

    @Transactional
    public SnapshotView finalizeSnapshot(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId); TechOpsInputPreparation preparation = lockedCurrent(projectId);
        var existing = snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(preparation.getId(), projectId);
        if (existing.isPresent()) return snapshotView(existing.get());
        JsonNode facts=mapper.readTree(preparation.getRequiredFactsJson()); JsonNode decisions=mapper.readTree(preparation.getProposalDecisionsJson());
        List<String> missing=readiness.missing(facts, decisions);
        if (!missing.isEmpty()) throw new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_NOT_READY,
            "필수 입력과 결정을 완료해 주세요: " + String.join(", ", missing));
        for (String key : TechOpsPreparationFactory.REQUIRED_FACT_KEYS) validateFact(key, facts.path(key).path("value"));
        for (String key : TechOpsPreparationFactory.PROPOSAL_KEYS) validateDecisionValue(key, decisions.path(key).path("finalValue"));
        String id=UUID.randomUUID().toString(); Instant now=Instant.now();
        var built=snapshotFactory.create(id, now, preparation,
            evidence.findAllByPreparationIdAndDeletedAtIsNullOrderByCreatedAtAsc(preparation.getId()));
        return snapshotView(snapshots.save(TechOpsInputSnapshot.create(id, projectId, preparation.getId(),
            preparation.getSourceMarketSeedSnapshotId(), TechOpsInputSnapshotFactory.SCHEMA_VERSION, built.hash(),
            mapper.writeValueAsString(built.body()), ownerId, now)));
    }

    @Transactional(readOnly = true)
    public SnapshotView currentSnapshot(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId); MarketAnalysisSeedSnapshot source=currentMarketSeed(projectId);
        return snapshotView(snapshots.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(source.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_NOT_READY)));
    }

    private PreparationView view(TechOpsInputPreparation value) {
        JsonNode facts=mapper.readTree(value.getRequiredFactsJson()); JsonNode decisions=mapper.readTree(value.getProposalDecisionsJson());
        List<String> missing=readiness.missing(facts, decisions);
        String snapshotId=snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(value.getId(), value.getProjectId())
            .map(TechOpsInputSnapshot::getId).orElse(null);
        List<EvidenceView> refs=evidence.findAllByPreparationIdAndDeletedAtIsNullOrderByCreatedAtAsc(value.getId()).stream()
            .map(item -> new EvidenceView(item.getId(), item.getEvidenceType(), item.getDisplayName(), item.getArtifactRef(),
                item.getDescription(), "USER_PROVIDED_EVIDENCE", item.getCreatedAt())).toList();
        return new PreparationView(TechOpsPreparationFactory.CONTRACT, TechOpsPreparationFactory.SCHEMA_VERSION,
            value.getId(), value.getProjectId(), value.getSourceMarketSeedSnapshotId(), value.getSourceSnapshotHash(),
            value.getRevision(), facts, decisions, refs, missing, missing.isEmpty(), snapshotId, value.getUpdatedAt());
    }
    private SnapshotView snapshotView(TechOpsInputSnapshot value) {
        return new SnapshotView(TechOpsInputSnapshotFactory.CONTRACT, value.getId(), value.getSchemaVersion(), value.getProjectId(),
            value.getPreparationId(), value.getSourceMarketSeedSnapshotId(), value.getSnapshotHash(), value.getFinalizedAt(),
            mapper.readTree(value.getSnapshotJson()));
    }
    private TechOpsInputPreparation requireCurrent(Long projectId) {
        MarketAnalysisSeedSnapshot source=currentMarketSeed(projectId);
        return preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(projectId, source.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TECH_OPS_PREPARATION_REQUIRED));
    }
    private TechOpsInputPreparation lockedCurrent(Long projectId) {
        TechOpsInputPreparation current=requireCurrent(projectId);
        return preparations.findLocked(current.getId(), projectId).orElseThrow(() -> new BusinessException(ErrorCode.TECH_OPS_PREPARATION_REQUIRED));
    }
    private MarketAnalysisSeedSnapshot currentMarketSeed(Long projectId) {
        var selection=selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        return marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
    }
    private void ensureMutable(TechOpsInputPreparation value) {
        if (snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(value.getId(), value.getProjectId()).isPresent())
            throw new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_IMMUTABLE);
    }
    private void validateFact(String key, JsonNode value) {
        if (value == null || value.isNull()) return;
        if ("productServiceSpecification".equals(key) && (!value.isObject()
                || value.path("summary").asText("").isBlank() || !value.path("features").isArray()))
            throw invalid("제품·서비스 사양을 확인해 주세요.");
        if ("targetLaunchDate".equals(key)) {
            try { if (!value.isTextual()) throw new IllegalArgumentException(); LocalDate.parse(value.asText()); }
            catch (RuntimeException failure) { throw invalid("목표 출시일은 YYYY-MM-DD 형식이어야 합니다."); }
        }
        if ("ownedPersonnel".equals(key) && (!value.isArray() || value.isEmpty()
                || !allPersonnelValid(value))) throw invalid("보유 인력은 역할과 0 이상의 인원 수를 포함해야 합니다.");
        if ("ownedAssetsAndFacilities".equals(key) && (!value.isArray() || value.isEmpty()
                || !allText(value))) throw invalid("보유 자산·설비를 하나 이상 입력해 주세요. 없는 경우도 명시해 주세요.");
        if (("fixedOperatingCost".equals(key) || "initialInvestment".equals(key))
                && (!value.isObject() || !value.path("amount").isNumber() || value.path("amount").asDouble() < 0
                || value.path("currency").asText("").isBlank())) throw invalid("비용은 0 이상의 금액과 통화를 포함해야 합니다.");
        if ("threeYearTargets".equals(key) && !ThreeYearTargetsContract.valid(value))
            throw invalid("3개년 목표는 지표·단위와 1~3년 수치를 모두 포함해야 합니다.");
        if (!TechOpsPreparationFactory.present(value)) throw invalid("빈 값은 확정할 수 없습니다.");
    }
    private void validateDecisionValue(String key, JsonNode value) {
        if (!TechOpsPreparationFactory.present(value)) throw new BusinessException(ErrorCode.TECH_OPS_PROPOSAL_INVALID);
        if ("deliveryOrProductionMethod".equals(key) && (!value.isObject()
                || value.path("method").asText("").isBlank())) throw new BusinessException(ErrorCode.TECH_OPS_PROPOSAL_INVALID);
        if ("expectedMonthlyThroughputOrSales".equals(key)
                && (!value.isObject() || !value.path("amount").isNumber() || value.path("amount").asDouble() < 0
                || value.path("unit").asText("").isBlank())) throw new BusinessException(ErrorCode.TECH_OPS_PROPOSAL_INVALID);
        if ("technicalSupplyOperationalConstraints".equals(key)
                && (!value.isArray() || value.isEmpty() || !allText(value))) throw new BusinessException(ErrorCode.TECH_OPS_PROPOSAL_INVALID);
    }
    private boolean allPersonnelValid(JsonNode values) {
        for (JsonNode value : values) if (!value.isObject() || value.path("role").asText("").isBlank()
                || !value.path("count").canConvertToInt() || value.path("count").asInt() < 0) return false;
        return true;
    }
    private boolean allText(JsonNode values) {
        for (JsonNode value : values) if (!value.isTextual() || value.asText().isBlank()) return false;
        return true;
    }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.TECH_OPS_INPUT_INVALID, message); }
    private void requireOwnedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId).filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
