package com.aivle.backend.pipeline.finance.application;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.finance.domain.*;
import com.aivle.backend.pipeline.finance.repository.*;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Instant;
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
public class FinancialService {
    private static final Set<String> TARGET_METRICS = Set.of("salesVolume", "customerCount", "subscriberCount", "transactionCount");
    private final ProjectRepository projects;
    private final ConceptSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final TechOpsInputSnapshotRepository techOpsSnapshots;
    private final FinancialInputPreparationRepository preparations;
    private final FinancialInputSnapshotRepository snapshots;
    private final FinancialPreparationFactory preparationFactory;
    private final FinancialInputSnapshotFactory snapshotFactory;
    private final FinancialReadiness readiness;
    private final FinancialCalculator calculator;
    private final ObjectMapper mapper;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher jobEvents;

    @Transactional
    public PreparationView initialize(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
        TechOpsInputSnapshot source = currentTechOpsSnapshot(projectId);
        var existing = preparations.findByProjectIdAndSourceTechOpsSnapshotIdAndDeletedAtIsNull(projectId, source.getId());
        if (existing.isPresent()) return view(existing.get());
        var initial = preparationFactory.create(source);
        String id = UUID.randomUUID().toString();
        var saved = preparations.save(FinancialInputPreparation.create(id, projectId, source.getId(),
            source.getSourceMarketSeedSnapshotId(), source.getSnapshotHash(),
            mapper.writeValueAsString(initial.financialFields()), mapper.writeValueAsString(initial.upstreamReferences()),
            mapper.writeValueAsString(initial.assistance()), ownerId));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public PreparationView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return view(requireCurrent(projectId));
    }

    @Transactional
    public PreparationView patchFields(Long ownerId, Long projectId, FinancialFieldsPatch request) {
        requireOwnedForUpdate(ownerId, projectId);
        FinancialInputPreparation preparation = lockedCurrent(projectId);
        ensureMutable(preparation);
        if (!request.values().isObject()) throw invalid("재무 입력은 필드별 객체여야 합니다.");
        ObjectNode fields = (ObjectNode) mapper.readTree(preparation.getFinancialFieldsJson());
        ObjectNode assistance = (ObjectNode) mapper.readTree(preparation.getAssistanceJson());
        for (String key : request.values().propertyNames()) {
            if (!FinancialPreparationFactory.ALL_KEYS.contains(key)) throw invalid("지원하지 않는 재무 입력 필드입니다: " + key);
            ObjectNode field = (ObjectNode) fields.path(key);
            if (field.path("readOnly").asBoolean(false)) throw invalid("기술·운영 단계에서 가져온 값은 다시 입력할 수 없습니다: " + key);
            JsonNode value = request.values().get(key);
            validateField(key, value, false);
            field.set("value", value == null ? mapper.nullNode() : value.deepCopy());
            field.put("source", "USER_INPUT");
            field.put("decision", FinancialPreparationFactory.present(value) ? "LOCKED" : "OPEN");
            field.putNull("sourceSnapshotId");
            field.putNull("provenance");
            JsonNode assistanceNode = assistance.path(key);
            if (assistanceNode.isObject()) {
                ObjectNode estimate = (ObjectNode) assistanceNode;
                estimate.put("estimateStatus", "NONE"); estimate.putNull("activeTaskRunId");
                estimate.putNull("safeError");
            }
        }
        preparation.updateFinancialFields(mapper.writeValueAsString(fields), ownerId);
        preparation.updateAssistance(mapper.writeValueAsString(assistance), ownerId);
        return view(preparation);
    }

    @Transactional
    public EstimateActionResponse generateEstimate(Long ownerId, Long projectId, String fieldKey,
            String idempotencyKey, String correlationId) {
        requireOwnedForUpdate(ownerId, projectId);
        FinancialInputPreparation preparation = lockedCurrent(projectId); ensureMutable(preparation);
        validateEstimateField(preparation, fieldKey);
        ObjectNode assistance = (ObjectNode) mapper.readTree(preparation.getAssistanceJson());
        ObjectNode proposal = (ObjectNode) assistance.withObject(fieldKey);
        if (active(proposal)) {
            TaskRun task = taskRuns.getOwned(ownerId, projectId, proposal.path("activeTaskRunId").asText());
            if (commandKey(idempotencyKey).equals(task.getIdempotencyKey())) {
                return queued(preparation, task, "GENERATE", fieldKey,
                    proposal.path("proposalVersion").asInt(0) + 1);
            }
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        int version = proposal.path("proposalValue").isNull() || proposal.path("proposalValue").isMissingNode()
            ? 1 : proposal.path("proposalVersion").asInt(1) + 1;
        JsonNode rejected = version > 1 ? proposal.path("proposalValue") : mapper.nullNode();
        TaskRunService.CreateResult creation = queueEstimate(ownerId, projectId, preparation, fieldKey, version,
            rejected, idempotencyKey, correlationId, "GENERATE");
        TaskRun task = creation.taskRun();
        if (!creation.createdNew()) return queued(preparation, task, "GENERATE", fieldKey, version);
        proposal.put("estimateStatus", "QUEUED"); proposal.put("activeTaskRunId", task.getId());
        proposal.putNull("safeError");
        preparation.updateAssistance(mapper.writeValueAsString(assistance), ownerId);
        return queued(preparation, task, "GENERATE", fieldKey, version);
    }

    @Transactional
    public EstimateActionResponse decideEstimate(Long ownerId, Long projectId, String fieldKey,
            EstimateDecisionRequest request, String idempotencyKey, String correlationId) {
        requireOwnedForUpdate(ownerId, projectId); FinancialInputPreparation preparation=lockedCurrent(projectId); ensureMutable(preparation);
        validateEstimateField(preparation, fieldKey);
        ObjectNode fields=(ObjectNode) mapper.readTree(preparation.getFinancialFieldsJson());
        ObjectNode assistance=(ObjectNode) mapper.readTree(preparation.getAssistanceJson());
        ObjectNode proposal=(ObjectNode) assistance.path(fieldKey); String action=request.action().strip().toUpperCase();
        ObjectNode field=(ObjectNode) fields.path(fieldKey);
        if (field.path("readOnly").asBoolean(false)) throw invalid("상위 확정값은 AI 추정으로 바꾸지 않습니다.");
        switch(action) {
            case "ACCEPT" -> {
                JsonNode value=proposal.path("proposalValue"); validateField(fieldKey,value,FinancialPreparationFactory.REQUIRED_KEYS.contains(fieldKey));
                field.set("value",value.deepCopy()); field.put("source","AI_ESTIMATE"); field.put("decision","ACCEPTED");
                field.put("provenance","assistance."+fieldKey+".proposalVersion:"+proposal.path("proposalVersion").asInt(1));
                proposal.put("decision","ACCEPTED"); proposal.put("estimateStatus", "ACCEPTED");
                proposal.putNull("activeTaskRunId"); proposal.putNull("safeError");
            }
            case "EDIT_AND_ACCEPT" -> {
                validateField(fieldKey,request.value(),FinancialPreparationFactory.REQUIRED_KEYS.contains(fieldKey));
                field.set("value",request.value().deepCopy()); field.put("source","USER_INPUT"); field.put("decision","USER_EDITED_ACCEPTED");
                field.put("provenance","user-edited-ai-estimate"); proposal.put("decision","USER_EDITED_ACCEPTED");
                proposal.put("estimateStatus", "ACCEPTED"); proposal.putNull("activeTaskRunId"); proposal.putNull("safeError");
            }
            case "REQUEST_ALTERNATIVE" -> {
                if (active(proposal)) {
                    TaskRun active = taskRuns.getOwned(ownerId, projectId, proposal.path("activeTaskRunId").asText());
                    if (commandKey(idempotencyKey).equals(active.getIdempotencyKey())) {
                        return queued(preparation, active, "REQUEST_ALTERNATIVE", fieldKey,
                            proposal.path("proposalVersion").asInt(1) + 1);
                    }
                    throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
                }
                validateField(fieldKey, proposal.path("proposalValue"),
                    FinancialPreparationFactory.REQUIRED_KEYS.contains(fieldKey));
                int version=proposal.path("proposalVersion").asInt(1)+1;
                TaskRunService.CreateResult creation = queueEstimate(ownerId, projectId, preparation, fieldKey, version,
                    proposal.path("proposalValue"), idempotencyKey, correlationId, "REQUEST_ALTERNATIVE");
                TaskRun task = creation.taskRun();
                if (!creation.createdNew()) {
                    return queued(preparation, task, "REQUEST_ALTERNATIVE", fieldKey, version);
                }
                proposal.put("estimateStatus", "QUEUED"); proposal.put("activeTaskRunId", task.getId());
                proposal.putNull("safeError");
                preparation.updateAssistance(mapper.writeValueAsString(assistance), ownerId);
                return queued(preparation, task, "REQUEST_ALTERNATIVE", fieldKey, version);
            }
            default -> throw invalid("AI 추정 결정 Action을 확인해 주세요.");
        }
        preparation.updateFinancialFields(mapper.writeValueAsString(fields),ownerId);
        preparation.updateAssistance(mapper.writeValueAsString(assistance),ownerId);
        return new EstimateActionResponse(view(preparation), null, null, "COMPLETED", action,
            fieldKey, proposal.path("proposalVersion").asInt(0));
    }

    @Transactional
    public SnapshotView finalizeSnapshot(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
        FinancialInputPreparation preparation = lockedCurrent(projectId);
        var existing = snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(preparation.getId(), projectId);
        if (existing.isPresent()) return snapshotView(existing.get());
        if (hasActiveEstimate(mapper.readTree(preparation.getAssistanceJson()))) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING,
                "AI 추천 작업이 완료된 뒤 Snapshot을 확정해 주세요.");
        }
        JsonNode fields = mapper.readTree(preparation.getFinancialFieldsJson());
        List<String> missing = readiness.missing(fields);
        if (!missing.isEmpty()) throw new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY,
            "필수 재무 입력을 완료해 주세요: " + String.join(", ", missing));
        for (String key : FinancialPreparationFactory.REQUIRED_KEYS) validateField(key, fields.path(key).path("value"), true);
        for (String key : FinancialPreparationFactory.ALL_KEYS)
            validateField(key, fields.path(key).path("value"), false);
        if (calculator.calculateCac(fields) == null) throw invalid("마케팅비와 영업비의 통화를 맞추고 신규 고객 수를 1 이상 입력해 주세요.");
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var built = snapshotFactory.create(id, now, preparation);
        return snapshotView(snapshots.save(FinancialInputSnapshot.create(id, projectId, preparation.getId(),
            preparation.getSourceTechOpsSnapshotId(), preparation.getSourceMarketSeedSnapshotId(),
            FinancialInputSnapshotFactory.SCHEMA_VERSION, built.hash(), mapper.writeValueAsString(built.body()), ownerId, now)));
    }

    @Transactional(readOnly = true)
    public SnapshotView currentSnapshot(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        TechOpsInputSnapshot source = currentTechOpsSnapshot(projectId);
        return snapshotView(snapshots.findBySourceTechOpsSnapshotIdAndProjectIdAndDeletedAtIsNull(source.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY)));
    }

    private PreparationView view(FinancialInputPreparation value) {
        JsonNode fields = mapper.readTree(value.getFinancialFieldsJson());
        List<String> missing = readiness.missing(fields);
        String snapshotId = snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(value.getId(), value.getProjectId())
            .map(FinancialInputSnapshot::getId).orElse(null);
        return new PreparationView(FinancialPreparationFactory.CONTRACT, FinancialPreparationFactory.SCHEMA_VERSION,
            value.getId(), value.getProjectId(), value.getSourceTechOpsSnapshotId(), value.getSourceMarketSeedSnapshotId(),
            value.getSourceSnapshotHash(), value.getRevision(), fields, mapper.readTree(value.getUpstreamReferencesJson()),
            mapper.readTree(value.getAssistanceJson()), calculator.calculateCac(fields), missing, missing.isEmpty(),
            snapshotId, value.getUpdatedAt());
    }

    private SnapshotView snapshotView(FinancialInputSnapshot value) {
        return new SnapshotView(FinancialInputSnapshotFactory.CONTRACT, value.getId(), value.getSchemaVersion(),
            value.getProjectId(), value.getPreparationId(), value.getSourceTechOpsSnapshotId(),
            value.getSourceMarketSeedSnapshotId(), value.getSnapshotHash(), value.getFinalizedAt(),
            mapper.readTree(value.getSnapshotJson()));
    }

    private TaskRunService.CreateResult queueEstimate(Long ownerId, Long projectId, FinancialInputPreparation preparation,
            String fieldKey, int version, JsonNode rejected, String idempotencyKey,
            String correlationId, String actionType) {
        TechOpsInputSnapshot source = currentTechOpsSnapshot(projectId);
        String contextJson = mapper.writeValueAsString(java.util.Map.of(
            "techOpsSnapshot", mapper.readTree(source.getSnapshotJson()),
            "financialFields", mapper.readTree(preparation.getFinancialFieldsJson())));
        String rejectedJson = rejected == null || rejected.isNull()
            ? "" : mapper.writeValueAsString(rejected);
        JsonNode input = mapper.valueToTree(java.util.Map.ofEntries(
            java.util.Map.entry("preparationId", preparation.getId()),
            java.util.Map.entry("fieldKey", fieldKey), java.util.Map.entry("proposalVersion", version),
            java.util.Map.entry("rejectedProposalJson", rejectedJson),
            java.util.Map.entry("sourceTechOpsSnapshotId", source.getId()),
            java.util.Map.entry("sourceSnapshotHash", source.getSnapshotHash()),
            java.util.Map.entry("expectedPreparationRevision", preparation.getRevision()),
            java.util.Map.entry("contextJson", contextJson),
            java.util.Map.entry("commandIdempotencyKey", commandKey(idempotencyKey))));
        String json = mapper.writeValueAsString(input); String key = commandKey(idempotencyKey);
        TaskRunService.CreateResult created = taskRuns.createWithDisposition(ownerId, projectId,
            TaskType.FINANCE_ESTIMATE, "FINANCIAL_PREPARATION", preparation.getId(), json,
            inputHasher.hash(TaskType.FINANCE_ESTIMATE, "1.0", "ko-KR", json), key,
            correlationId == null || correlationId.isBlank() ? key : correlationId, 1);
        TaskRun task = created.taskRun();
        if (created.createdNew()) {
            jobEvents.publish(new JobEventPublisher.Command(projectId, task.getId(), task.getId(), "QUEUED",
                "REQUEST_ALTERNATIVE".equals(actionType)
                    ? "job.finance.estimate.alternative.queued" : "job.finance.estimate.queued",
                JobEvent.Status.QUEUED, "job.finance.estimate.queued",
                java.util.Map.of("fieldKey", fieldKey), null));
        }
        return created;
    }

    private EstimateActionResponse queued(FinancialInputPreparation preparation, TaskRun task,
            String actionType, String fieldKey, int version) {
        return new EstimateActionResponse(view(preparation), task.getId(), task.getId(), task.getState().name(),
            actionType, fieldKey, version);
    }

    private void validateEstimateField(FinancialInputPreparation preparation, String fieldKey) {
        if (!FinancialPreparationFactory.ALL_KEYS.contains(fieldKey) || "newCustomerCount".equals(fieldKey)) {
            throw invalid("AI 추정 지원 대상이 아닙니다: " + fieldKey);
        }
        JsonNode field = mapper.readTree(preparation.getFinancialFieldsJson()).path(fieldKey);
        if (field.path("readOnly").asBoolean(false)) throw invalid("상위 확정값은 AI 추정으로 바꾸지 않습니다.");
    }

    private boolean active(JsonNode proposal) {
        return !proposal.path("activeTaskRunId").asText("").isBlank()
            && ("QUEUED".equals(proposal.path("estimateStatus").asText())
                || "RUNNING".equals(proposal.path("estimateStatus").asText()));
    }

    private boolean hasActiveEstimate(JsonNode assistance) {
        for (JsonNode proposal : assistance) if (active(proposal)) return true;
        return false;
    }

    private String commandKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return value.strip();
    }

    private FinancialInputPreparation requireCurrent(Long projectId) {
        TechOpsInputSnapshot source = currentTechOpsSnapshot(projectId);
        return preparations.findByProjectIdAndSourceTechOpsSnapshotIdAndDeletedAtIsNull(projectId, source.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED));
    }

    private FinancialInputPreparation lockedCurrent(Long projectId) {
        FinancialInputPreparation current = requireCurrent(projectId);
        return preparations.findLocked(current.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED));
    }

    private TechOpsInputSnapshot currentTechOpsSnapshot(Long projectId) {
        var selection = selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        var seed = marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
        return techOpsSnapshots.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(seed.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TECH_OPS_SNAPSHOT_NOT_READY));
    }

    private void ensureMutable(FinancialInputPreparation value) {
        if (snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(value.getId(), value.getProjectId()).isPresent())
            throw new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_IMMUTABLE);
    }

    private void validateField(String key, JsonNode value, boolean required) {
        if (value == null || value.isNull()) {
            if (required) throw invalid("필수 재무 입력이 비어 있습니다: " + key);
            return;
        }
        if ("threeYearTargets".equals(key)) {
            if (!validTargets(value)) throw invalid("3개년 목표는 지원 지표와 1~3년차의 0 이상 값이 필요합니다.");
            return;
        }
        if ("newCustomerCount".equals(key)) {
            if (!value.isIntegralNumber() || value.asLong() < 1)
                throw invalid("신규 고객 수는 1 이상의 정수여야 합니다.");
            return;
        }
        if ("revenueModel".equals(key)) {
            if (!value.isTextual() || !Set.of("ONE_TIME", "SUBSCRIPTION", "HYBRID").contains(value.asText()))
                throw invalid("revenueModel must be ONE_TIME, SUBSCRIPTION, or HYBRID");
            return;
        }
        if ("monthlyChurnRate".equals(key)) {
            if (!value.isNumber() || value.asDouble() < 0 || value.asDouble() > 100)
                throw invalid("monthlyChurnRate must be between 0 and 100");
            return;
        }
        if (!value.isObject() || !value.path("amount").isNumber() || value.path("amount").asDouble() < 0
                || value.path("currency").asText("").isBlank())
            throw invalid("비용은 0 이상의 금액과 통화를 포함해야 합니다: " + key);
    }

    private boolean validTargets(JsonNode value) {
        if (!value.isObject() || !TARGET_METRICS.contains(value.path("metric").asText())
                || !value.path("unit").isTextual() || value.path("unit").asText().isBlank()
                || !value.path("years").isArray() || value.path("years").size() != 3) return false;
        boolean[] years = new boolean[4];
        for (JsonNode item : value.path("years")) {
            int year = item.path("year").asInt(-1);
            if (year < 1 || year > 3 || years[year] || !item.path("value").isNumber()
                    || item.path("value").asDouble() < 0) return false;
            years[year] = true;
        }
        return years[1] && years[2] && years[3];
    }

    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.FINANCIAL_INPUT_INVALID, message); }
    private void requireOwnedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId).filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
