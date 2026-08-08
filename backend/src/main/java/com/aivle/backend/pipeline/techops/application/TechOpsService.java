package com.aivle.backend.pipeline.techops.application;

import static com.aivle.backend.pipeline.techops.api.TechOpsApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.shared.ThreeYearTargetsContract;
import com.aivle.backend.pipeline.techops.domain.*;
import com.aivle.backend.pipeline.techops.repository.*;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final ProjectEvidenceArtifactRepository artifacts;
    private final TechOpsInputSnapshotRepository snapshots;
    private final TechOpsPreparationFactory preparationFactory;
    private final TechOpsInputSnapshotFactory snapshotFactory;
    private final TechOpsReadiness readiness;
    private final ObjectMapper mapper;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher jobEvents;

    @Transactional
    public PreparationView initialize(Long ownerId, Long projectId, String idempotencyKey, String correlationId) {
        requireOwnedForUpdate(ownerId, projectId); MarketAnalysisSeedSnapshot source = currentMarketSeed(projectId);
        var existing = preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(projectId, source.getId());
        if (existing.isPresent()) return view(existing.get());
        var initial = preparationFactory.create(source);
        String id = UUID.randomUUID().toString();
        var saved = preparations.save(TechOpsInputPreparation.create(id, projectId, source.getId(), source.getSnapshotHash(),
            mapper.writeValueAsString(initial.requiredFacts()), mapper.writeValueAsString(initial.proposalDecisions()), ownerId));
        if (hasMissingProposal(initial.proposalDecisions())) {
            queueInitial(ownerId, projectId, saved, source, idempotencyKey, correlationId);
        }
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
    public ProposalActionResponse decideProposal(Long ownerId, Long projectId, String fieldKey,
            ProposalDecisionRequest request, String idempotencyKey, String correlationId) {
        requireOwnedForUpdate(ownerId, projectId); TechOpsInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        if (!TechOpsPreparationFactory.PROPOSAL_KEYS.contains(fieldKey)) throw invalid("지원하지 않는 기술·운영 결정 필드입니다.");
        ObjectNode decisions = (ObjectNode) mapper.readTree(preparation.getProposalDecisionsJson());
        ObjectNode field = (ObjectNode) decisions.path(fieldKey); String action = request.action().strip().toUpperCase();
        switch (action) {
            case "ACCEPT" -> {
                JsonNode proposal = field.path("proposalValue"); validateDecisionValue(fieldKey, proposal);
                field.set("finalValue", proposal.deepCopy()); field.put("decision", "ACCEPTED"); field.put("alternativeRequested", false);
            }
            case "EDIT_ACCEPT", "EDIT_AND_ACCEPT" -> {
                validateDecisionValue(fieldKey, request.value()); field.set("finalValue", request.value().deepCopy());
                field.put("source", "USER_INPUT"); field.put("decision", "USER_EDITED_ACCEPTED"); field.put("alternativeRequested", false);
            }
            case "REJECT_AND_REQUEST_ALTERNATIVE" -> {
                if (preparation.proposalTaskActive()) {
                    TaskRun active = taskRuns.getOwned(ownerId, projectId, preparation.getActiveProposalTaskRunId());
                    if (commandKey(idempotencyKey).equals(active.getIdempotencyKey())
                            && active.getId().equals(field.path("pendingAlternativeTaskRunId").asText())) {
                        return queued(preparation, active, "REJECT_AND_REQUEST_ALTERNATIVE", fieldKey,
                            field.path("proposalVersion").asInt(1) + 1);
                    }
                    throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
                }
                int nextVersion = field.path("proposalVersion").asInt(1) + 1;
                JsonNode previous = field.path("proposalValue"); validateDecisionValue(fieldKey, previous);
                MarketAnalysisSeedSnapshot source = currentMarketSeed(projectId);
                JsonNode input = mapper.valueToTree(java.util.Map.ofEntries(
                    java.util.Map.entry("mode", "ALTERNATIVE"), java.util.Map.entry("preparationId", preparation.getId()),
                    java.util.Map.entry("fieldKey", fieldKey), java.util.Map.entry("currentProposalVersion", nextVersion - 1),
                    java.util.Map.entry("proposalVersion", nextVersion),
                    java.util.Map.entry("rejectedProposal", previous.deepCopy()),
                    java.util.Map.entry("rejectedProposalJson", mapper.writeValueAsString(previous)),
                    java.util.Map.entry("sourceMarketSeedSnapshotId", source.getId()),
                    java.util.Map.entry("sourceSnapshotHash", source.getSnapshotHash()),
                    java.util.Map.entry("expectedPreparationRevision", preparation.getRevision() + 1),
                    java.util.Map.entry("contextJson", source.getSnapshotJson()),
                    java.util.Map.entry("commandIdempotencyKey", commandKey(idempotencyKey))));
                TaskRunService.CreateResult creation = createTask(ownerId, projectId, preparation, input,
                    idempotencyKey, correlationId, "ALTERNATIVE", fieldKey);
                TaskRun task = creation.taskRun();
                if (creation.createdNew()) {
                    field.put("alternativeRequested", true);
                    field.put("pendingAlternativeTaskRunId", task.getId());
                    preparation.queueAlternativeTask(task.getId(), mapper.writeValueAsString(decisions), ownerId);
                }
                return queued(preparation, task, "REJECT_AND_REQUEST_ALTERNATIVE", fieldKey, nextVersion);
            }
            default -> throw invalid("제안 결정 Action을 확인해 주세요.");
        }
        preparation.updateProposalDecisions(mapper.writeValueAsString(decisions), ownerId);
        return new ProposalActionResponse(view(preparation), null, null, "COMPLETED", action,
            fieldKey, field.path("proposalVersion").asInt(1));
    }

    @Transactional
    public ProposalActionResponse retryInitialProposals(Long ownerId, Long projectId,
            String idempotencyKey, String correlationId) {
        requireOwnedForUpdate(ownerId, projectId);
        TechOpsInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        ObjectNode decisions = (ObjectNode) mapper.readTree(preparation.getProposalDecisionsJson());
        if (!hasMissingProposal(decisions)) {
            throw new BusinessException(ErrorCode.TECH_OPS_PROPOSAL_INVALID, "생성할 미확정 AI 제안이 없습니다.");
        }
        if (preparation.proposalTaskActive()) {
            TaskRun active = taskRuns.getOwned(ownerId, projectId, preparation.getActiveProposalTaskRunId());
            if (commandKey(idempotencyKey).equals(active.getIdempotencyKey())) {
                return queued(preparation, active, "RETRY_INITIAL", null, 1);
            }
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        MarketAnalysisSeedSnapshot source = currentMarketSeed(projectId);
        TaskRun task = queueInitial(ownerId, projectId, preparation, source, idempotencyKey, correlationId);
        return queued(preparation, task, "RETRY_INITIAL", null, 1);
    }

    @Transactional
    public PreparationView addEvidence(Long ownerId, Long projectId, EvidenceRequest request) {
        requireOwnedForUpdate(ownerId, projectId); TechOpsInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        String type=request.evidenceType().strip().toUpperCase();
        if (!EVIDENCE_TYPES.contains(type)) throw new BusinessException(ErrorCode.TECH_OPS_EVIDENCE_INVALID);
        ProjectEvidenceArtifact artifact = artifacts.findByIdAndProjectIdAndDeletedAtIsNull(request.artifactId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_ARTIFACT_NOT_FOUND));
        evidence.save(TechOpsEvidenceReference.create(UUID.randomUUID().toString(), preparation.getId(), projectId, type,
            artifact.getOriginalFilename(), artifact.getId(), request.description(), ownerId));
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
        if (preparation.proposalTaskActive()) throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING,
            "AI 운영 가설 작업이 완료된 뒤 Snapshot을 확정해 주세요.");
        JsonNode facts=mapper.readTree(preparation.getRequiredFactsJson()); JsonNode decisions=mapper.readTree(preparation.getProposalDecisionsJson());
        List<String> missing=readiness.missing(facts, decisions);
        if (!missing.isEmpty()) throw new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_NOT_READY,
            "필수 입력과 결정을 완료해 주세요: " + String.join(", ", missing));
        for (String key : TechOpsPreparationFactory.REQUIRED_FACT_KEYS) validateFact(key, facts.path(key).path("value"));
        for (String key : TechOpsPreparationFactory.PROPOSAL_KEYS) validateDecisionValue(key, decisions.path(key).path("finalValue"));
        String id=UUID.randomUUID().toString(); Instant now=Instant.now();
        List<TechOpsEvidenceReference> refs = evidence.findAllByPreparationIdAndDeletedAtIsNullOrderByCreatedAtAsc(preparation.getId());
        Map<String, ProjectEvidenceArtifact> artifactMap = artifacts(refs, projectId);
        if (refs.stream().anyMatch(ref -> ref.getArtifactId() == null || !artifactMap.containsKey(ref.getArtifactId()))) {
            throw new BusinessException(ErrorCode.TECH_OPS_EVIDENCE_INVALID,
                "삭제되었거나 연결되지 않은 근거 파일을 제거하고 다시 시도해 주세요.");
        }
        var built=snapshotFactory.create(id, now, preparation, refs, artifactMap);
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
        List<TechOpsEvidenceReference> evidenceRefs = evidence.findAllByPreparationIdAndDeletedAtIsNullOrderByCreatedAtAsc(value.getId());
        Map<String, ProjectEvidenceArtifact> artifactMap = artifacts(evidenceRefs, value.getProjectId());
        List<EvidenceView> refs=evidenceRefs.stream().map(item -> {
            ProjectEvidenceArtifact artifact = artifactMap.get(item.getArtifactId());
            return new EvidenceView(item.getId(), item.getEvidenceType(), item.getArtifactId(),
                artifact == null ? null : artifact.getOriginalFilename(), item.getDisplayName(),
                artifact == null ? null : artifact.getMediaType(), artifact == null ? null : artifact.getSizeBytes(),
                artifact == null ? null : artifact.getSha256(), item.getDescription(),
                "USER_PROVIDED_EVIDENCE", item.getCreatedAt());
        }).toList();
        return new PreparationView(TechOpsPreparationFactory.CONTRACT, TechOpsPreparationFactory.SCHEMA_VERSION,
            value.getId(), value.getProjectId(), value.getSourceMarketSeedSnapshotId(), value.getSourceSnapshotHash(),
            value.getRevision(), facts, decisions, refs, missing, missing.isEmpty(), snapshotId,
            value.getProposalGenerationStatus(), value.getActiveProposalTaskRunId(), value.getSafeProposalError(),
            value.getUpdatedAt());
    }

    private Map<String, ProjectEvidenceArtifact> artifacts(List<TechOpsEvidenceReference> refs, Long projectId) {
        List<String> ids = refs.stream().map(TechOpsEvidenceReference::getArtifactId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return artifacts.findAllByIdInAndProjectIdAndDeletedAtIsNull(ids, projectId).stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(ProjectEvidenceArtifact::getId, value -> value));
    }

    private TaskRun queueInitial(Long ownerId, Long projectId, TechOpsInputPreparation preparation,
            MarketAnalysisSeedSnapshot source, String idempotencyKey, String correlationId) {
        if (preparation.proposalTaskActive()) return taskRuns.getOwned(ownerId, projectId,
            preparation.getActiveProposalTaskRunId());
        JsonNode input = mapper.valueToTree(java.util.Map.ofEntries(
            java.util.Map.entry("mode", "INITIAL"), java.util.Map.entry("preparationId", preparation.getId()),
            java.util.Map.entry("proposalVersion", 1), java.util.Map.entry("rejectedProposalJson", ""),
            java.util.Map.entry("sourceMarketSeedSnapshotId", source.getId()),
            java.util.Map.entry("sourceSnapshotHash", source.getSnapshotHash()),
            java.util.Map.entry("expectedPreparationRevision", preparation.getRevision()),
            java.util.Map.entry("contextJson", source.getSnapshotJson()),
            java.util.Map.entry("commandIdempotencyKey", commandKey(idempotencyKey))));
        TaskRunService.CreateResult creation = createTask(ownerId, projectId, preparation, input,
            idempotencyKey, correlationId, "INITIAL", null);
        TaskRun task = creation.taskRun();
        if (creation.createdNew()) preparation.queueInitialProposalTask(task.getId());
        return task;
    }

    private TaskRunService.CreateResult createTask(Long ownerId, Long projectId, TechOpsInputPreparation preparation,
            JsonNode input, String idempotencyKey, String correlationId, String mode, String fieldKey) {
        String key = commandKey(idempotencyKey);
        String json = mapper.writeValueAsString(input);
        TaskRunService.CreateResult created = taskRuns.createWithDisposition(ownerId, projectId,
            TaskType.TECH_OPS_PROPOSAL, "TECH_OPS_PREPARATION", preparation.getId(), json,
            inputHasher.hash(TaskType.TECH_OPS_PROPOSAL, "1.0", "ko-KR", json), key,
            correlationId == null || correlationId.isBlank() ? key : correlationId, 1);
        TaskRun task = created.taskRun();
        if (created.createdNew()) {
            jobEvents.publish(new JobEventPublisher.Command(projectId, task.getId(), task.getId(), "QUEUED",
                "INITIAL".equals(mode) ? "job.tech-ops.proposals.queued" : "job.tech-ops.alternative.queued",
                JobEvent.Status.QUEUED, "job.tech-ops.proposal.queued",
                fieldKey == null ? java.util.Map.of() : java.util.Map.of("fieldKey", fieldKey), null));
        }
        return created;
    }

    private ProposalActionResponse queued(TechOpsInputPreparation preparation, TaskRun task,
            String actionType, String fieldKey, int version) {
        return new ProposalActionResponse(view(preparation), task.getId(), task.getId(), task.getState().name(),
            actionType, fieldKey, version);
    }

    private boolean hasMissingProposal(JsonNode decisions) {
        return TechOpsPreparationFactory.PROPOSAL_KEYS.stream()
            .anyMatch(key -> !TechOpsPreparationFactory.present(decisions.path(key).path("proposalValue")));
    }

    private String commandKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return value.strip();
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
