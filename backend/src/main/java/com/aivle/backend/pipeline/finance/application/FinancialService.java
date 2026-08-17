package com.aivle.backend.pipeline.finance.application;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Binding;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.pipeline.finance.domain.*;
import com.aivle.backend.pipeline.finance.repository.*;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.pipeline.market.MarketResearchVersion;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
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
    private final CurrentConceptSourceResolver currentConcepts;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final MarketResearchService marketResearch;
    private final MarketResearchVersionRepository marketVersions;
    private final FinancialInputPreparationRepository preparations;
    private final FinancialInputSnapshotRepository snapshots;
    private final FinancialPreparationFactory preparationFactory;
    private final FinancialInputSnapshotFactory snapshotFactory;
    private final FinancialReadiness readiness;
    private final FinancialCalculator calculator;
    private final ObjectMapper mapper;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final SnapshotHasher snapshotHasher;
    private final JobEventPublisher jobEvents;

    @Transactional
    public PreparationView initialize(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
        var direct = preparations.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
            projectId, "DIRECT_INPUT");
        if (direct.isPresent()) return view(direct.get());
        CurrentSources source;
        try { source = currentSources(ownerId, projectId); }
        catch (BusinessException unavailable) {
            var initial = preparationFactory.createIndependent();
            ObjectNode lineage = mapper.createObjectNode(); lineage.put("sourceMode", "DIRECT_INPUT");
            String id = UUID.randomUUID().toString();
            return view(preparations.save(FinancialInputPreparation.createIndependent(id, projectId,
                snapshotHasher.hash(lineage), mapper.writeValueAsString(initial.financialFields()),
                mapper.writeValueAsString(initial.upstreamReferences()), mapper.writeValueAsString(initial.assistance()), ownerId)));
        }
        var existing = preparations
            .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                projectId, source.market().getId(), source.businessModel().getId());
        if (existing.isPresent()) return view(existing.get());
        var initial = preparationFactory.createFromMarketAndBusinessModel(
            mapper.readTree(source.market().getResultJson()), mapper.readTree(source.businessModel().getResultJson()),
            currentConceptHypotheses(source), source.market().getId(), source.businessModel().getId());
        ObjectNode lineage = mapper.createObjectNode();
        lineage.put("marketResearchVersionId", source.market().getId());
        lineage.put("businessModelVersionId", source.businessModel().getId());
        String id = UUID.randomUUID().toString();
        FinancialInputPreparation preparation = FinancialInputPreparation.createFromMarketAndBusinessModel(id, projectId,
            source.market().getId(), source.businessModel().getId(), snapshotHasher.hash(lineage),
            mapper.writeValueAsString(initial.financialFields()), mapper.writeValueAsString(initial.upstreamReferences()),
            mapper.writeValueAsString(initial.assistance()), ownerId);
        bind(preparation, currentConcepts.require(projectId, "현재 확정된 사업안이 필요합니다."));
        var saved = preparations.save(preparation);
        return view(saved);
    }

    @Transactional(readOnly = true)
    public PreparationView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return view(requireCurrent(ownerId, projectId));
    }

    @Transactional
    public PreparationView patchFields(Long ownerId, Long projectId, FinancialFieldsPatch request) {
        requireOwnedForUpdate(ownerId, projectId);
        FinancialInputPreparation preparation = lockedCurrent(ownerId, projectId);
        ensureMutable(preparation);
        if (!request.values().isObject()) throw invalid("재무 입력은 필드별 객체여야 합니다.");
        ObjectNode fields = (ObjectNode) mapper.readTree(preparation.getFinancialFieldsJson());
        ObjectNode assistance = (ObjectNode) mapper.readTree(preparation.getAssistanceJson());
        for (String key : request.values().propertyNames()) {
            if (!FinancialPreparationFactory.ALL_KEYS.contains(key)) throw invalid("지원하지 않는 재무 입력 필드입니다: " + key);
            ObjectNode field = (ObjectNode) fields.path(key);
            if (field.path("readOnly").asBoolean(false)) throw invalid("상위 단계에서 확정된 값은 다시 입력할 수 없습니다: " + key);
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
        FinancialInputPreparation preparation = lockedCurrent(ownerId, projectId); ensureMutable(preparation);
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
        requireOwnedForUpdate(ownerId, projectId); FinancialInputPreparation preparation=lockedCurrent(ownerId, projectId); ensureMutable(preparation);
        validateEstimateField(preparation, fieldKey);
        ObjectNode fields=(ObjectNode) mapper.readTree(preparation.getFinancialFieldsJson());
        ObjectNode assistance=(ObjectNode) mapper.readTree(preparation.getAssistanceJson());
        ObjectNode proposal=(ObjectNode) assistance.path(fieldKey); String action=request.action().strip().toUpperCase();
        ObjectNode field=(ObjectNode) fields.path(fieldKey);
        if (field.path("readOnly").asBoolean(false)) throw invalid("상위 확정값은 AI 추정으로 바꾸지 않습니다.");
        switch(action) {
            case "ACCEPT" -> {
                JsonNode value=decisionValue(fieldKey, proposal.path("proposalValue"));
                validateField(fieldKey,value,FinancialPreparationFactory.REQUIRED_KEYS.contains(fieldKey));
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
            case "REJECT" -> {
                proposal.put("decision", "REJECTED"); proposal.put("estimateStatus", "NONE");
                proposal.putNull("activeTaskRunId"); proposal.putNull("safeError");
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
        FinancialInputPreparation preparation = lockedCurrent(ownerId, projectId);
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
        FinancialInputSnapshot snapshot;
        if ("DIRECT_INPUT".equals(preparation.getSourceMode())) {
            built = snapshotFactory.createIndependent(id, now, preparation);
            snapshot = FinancialInputSnapshot.createIndependent(id, projectId, preparation.getId(),
                preparation.getRevision(), FinancialInputSnapshotFactory.SCHEMA_VERSION, built.hash(),
                mapper.writeValueAsString(built.body()), ownerId, now);
        } else {
            snapshot = FinancialInputSnapshot.createFromMarketAndBusinessModel(
                id, projectId, preparation.getId(), preparation.getSourceMarketResearchVersionId(),
                preparation.getSourceBusinessModelVersionId(), FinancialInputSnapshotFactory.SCHEMA_VERSION,
                built.hash(), mapper.writeValueAsString(built.body()), ownerId, now);
            bind(snapshot, currentConcepts.require(projectId, "현재 확정된 사업안이 필요합니다."));
        }
        return snapshotView(snapshots.save(snapshot));
    }

    @Transactional
    public SnapshotView importUserDocument(Long ownerId, Long projectId, String artifactId,
            String documentHash, JsonNode normalizedValues) {
        requireOwnedForUpdate(ownerId, projectId);
        if (normalizedValues == null || !normalizedValues.isObject())
            throw invalid("문서 입력 내용을 확인해 주세요.");
        ObjectNode fields = mapper.createObjectNode();
        for (String key : FinancialPreparationFactory.ALL_KEYS) {
            JsonNode value = normalizedValues.path(key);
            if (value.isMissingNode()) value = mapper.nullNode();
            validateField(key, value, FinancialPreparationFactory.REQUIRED_KEYS.contains(key));
            ObjectNode field = fields.putObject(key);
            field.set("value", value.deepCopy()); field.put("source", "USER_DOCUMENT_INPUT");
            field.put("decision", FinancialPreparationFactory.present(value) ? "LOCKED" : "OPEN");
            field.put("readOnly", false); field.put("sourceDocumentArtifactId", artifactId);
            field.put("provenance", "uploaded-finance-document." + key);
            JsonNode userNote = normalizedValues.path(FinancialInputDocumentService.INPUT_NOTES).path(key);
            if (userNote.isTextual() && !userNote.asText().isBlank()) field.put("userNote", userNote.asText());
        }
        if (calculator.calculateCac(fields) == null)
            throw invalid("마케팅비·영업비와 신규 고객 수를 확인해 주세요.");
        ObjectNode lineage = mapper.createObjectNode();
        lineage.put("sourceMode", "USER_DOCUMENT_INPUT"); lineage.put("sourceDocumentArtifactId", artifactId);
        lineage.put("sourceDocumentHash", documentHash); lineage.set("normalizedValues", normalizedValues.deepCopy());
        String sourceHash = snapshotHasher.hash(lineage);
        ObjectNode references = mapper.createObjectNode(); references.set("userDocument", lineage.deepCopy());
        ObjectNode assistance = mapper.createObjectNode();
        for (String key : FinancialPreparationFactory.ALL_KEYS) assistance.putObject(key).put("decision", "NOT_USED");
        String preparationId = UUID.randomUUID().toString();
        FinancialInputPreparation preparation = preparations.save(FinancialInputPreparation.createFromUserDocument(
            preparationId, projectId, artifactId, documentHash, sourceHash,
            mapper.writeValueAsString(fields), mapper.writeValueAsString(references),
            mapper.writeValueAsString(assistance), ownerId));
        String snapshotId = UUID.randomUUID().toString(); Instant now = Instant.now();
        var built = snapshotFactory.createUserDocument(snapshotId, now, preparation);
        FinancialInputSnapshot snapshot = FinancialInputSnapshot.createFromUserDocument(
            snapshotId, projectId, preparationId, preparation.getRevision(), artifactId, documentHash,
            FinancialInputSnapshotFactory.SCHEMA_VERSION, built.hash(), mapper.writeValueAsString(built.body()), ownerId, now);
        return snapshotView(snapshots.save(snapshot));
    }

    /** 동일 프로젝트의 Finance import command를 artifact 생성 전부터 직렬화한다. */
    @Transactional
    public void lockImportCommand(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
    }

    @Transactional
    public PreparationView reopenPreparation(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
        FinancialInputPreparation preparation = lockedCurrent(ownerId, projectId);
        FinancialInputSnapshot snapshot = snapshots
            .findByPreparationIdAndProjectIdAndDeletedAtIsNull(preparation.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY));
        snapshot.softDelete();
        return view(preparation);
    }

    @Transactional(readOnly = true)
    public SnapshotView currentSnapshot(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        var userDocument = snapshots.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByFinalizedAtDesc(
            projectId, "USER_DOCUMENT_INPUT");
        if (userDocument.isPresent()) return snapshotView(userDocument.get());
        var direct = snapshots.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByFinalizedAtDesc(
            projectId, "DIRECT_INPUT");
        if (direct.isPresent()) return snapshotView(direct.get());
        CurrentSources source = currentSources(ownerId, projectId);
        return snapshotView(snapshots
            .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByFinalizedAtAsc(
                projectId, source.market().getId(), source.businessModel().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY)));
    }

    @Transactional(readOnly = true)
    public SnapshotView snapshot(Long ownerId, Long projectId, String snapshotId) {
        requireOwned(ownerId, projectId);
        FinancialInputSnapshot snapshot = snapshots.findById(snapshotId)
            .filter(value -> value.getProjectId().equals(projectId) && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY));
        return snapshotView(snapshot);
    }

    @Transactional(readOnly = true)
    public PreparationView preparation(Long ownerId, Long projectId, String preparationId) {
        requireOwned(ownerId, projectId);
        FinancialInputPreparation preparation = preparations.findById(preparationId)
            .filter(value -> value.getProjectId().equals(projectId) && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED));
        return view(preparation);
    }

    private PreparationView view(FinancialInputPreparation value) {
        JsonNode fields = mapper.readTree(value.getFinancialFieldsJson());
        List<String> missing = readiness.missing(fields);
        String snapshotId = snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(value.getId(), value.getProjectId())
            .map(FinancialInputSnapshot::getId).orElse(null);
        return new PreparationView(FinancialPreparationFactory.CONTRACT, FinancialPreparationFactory.SCHEMA_VERSION,
            value.getId(), value.getProjectId(), value.getSourceTechOpsSnapshotId(), value.getSourceMarketSeedSnapshotId(),
            value.getSourceMarketResearchVersionId(), value.getSourceBusinessModelVersionId(), value.getSourceSnapshotHash(),
            stale(value), value.getRevision(), fields, mapper.readTree(value.getUpstreamReferencesJson()),
            mapper.readTree(value.getAssistanceJson()), calculator.calculateCac(fields), missing, missing.isEmpty(),
            snapshotId, value.getUpdatedAt());
    }

    private SnapshotView snapshotView(FinancialInputSnapshot value) {
        return new SnapshotView(FinancialInputSnapshotFactory.CONTRACT, value.getId(), value.getSchemaVersion(),
            value.getProjectId(), value.getPreparationId(), value.getSourceTechOpsSnapshotId(),
            value.getSourceMarketSeedSnapshotId(), value.getSourceMarketResearchVersionId(),
            value.getSourceBusinessModelVersionId(), value.getSnapshotHash(), value.getFinalizedAt(),
            mapper.readTree(value.getSnapshotJson()), stale(value));
    }

    private TaskRunService.CreateResult queueEstimate(Long ownerId, Long projectId, FinancialInputPreparation preparation,
            String fieldKey, int version, JsonNode rejected, String idempotencyKey,
            String correlationId, String actionType) {
        String contextJson = mapper.writeValueAsString(java.util.Map.of(
            "marketAndBmReferences", mapper.readTree(preparation.getUpstreamReferencesJson()),
            "financialFields", mapper.readTree(preparation.getFinancialFieldsJson())));
        String rejectedJson = rejected == null || rejected.isNull()
            ? "" : mapper.writeValueAsString(rejected);
        JsonNode input = mapper.valueToTree(java.util.Map.ofEntries(
            java.util.Map.entry("preparationId", preparation.getId()),
            java.util.Map.entry("fieldKey", fieldKey), java.util.Map.entry("proposalVersion", version),
            java.util.Map.entry("rejectedProposalJson", rejectedJson),
            java.util.Map.entry("sourceMarketResearchVersionId", preparation.getSourceMarketResearchVersionId()),
            java.util.Map.entry("sourceBusinessModelVersionId", preparation.getSourceBusinessModelVersionId()),
            java.util.Map.entry("sourceSnapshotHash", preparation.getSourceSnapshotHash()),
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
        if (!FinancialPreparationFactory.ALL_KEYS.contains(fieldKey) || "revenueModel".equals(fieldKey)) {
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

    private FinancialInputPreparation requireCurrent(Long ownerId, Long projectId) {
        var userDocument = preparations.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
            projectId, "USER_DOCUMENT_INPUT");
        if (userDocument.isPresent()) return userDocument.get();
        var direct = preparations.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
            projectId, "DIRECT_INPUT");
        if (direct.isPresent()) return direct.get();
        CurrentSources source = currentSources(ownerId, projectId);
        return preparations
            .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                projectId, source.market().getId(), source.businessModel().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED));
    }

    private FinancialInputPreparation lockedCurrent(Long ownerId, Long projectId) {
        FinancialInputPreparation current = requireCurrent(ownerId, projectId);
        return preparations.findLocked(current.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED));
    }

    private CurrentSources currentSources(Long ownerId, Long projectId) {
        var marketCurrent = marketResearch.current(ownerId, projectId, MarketResearchRun.Kind.FULL);
        var bmCurrent = marketResearch.current(ownerId, projectId, MarketResearchRun.Kind.BM);
        if (marketCurrent.version() == null || marketCurrent.stale()) {
            throw new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED,
                "current Market Research 결과가 필요합니다.");
        }
        if (bmCurrent.version() == null || bmCurrent.stale()) {
            throw new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED,
                "current Business Model 결과와 재무 전달정보가 필요합니다.");
        }
        MarketResearchVersion market = marketVersions.findById(marketCurrent.version().id())
            .filter(value -> value.getProject().getId().equals(projectId) && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED));
        MarketResearchVersion businessModel = marketVersions.findById(bmCurrent.version().id())
            .filter(value -> value.getProject().getId().equals(projectId) && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED));
        if (businessModel.getKind() != MarketResearchRun.Kind.BM
                || market.getKind() != MarketResearchRun.Kind.FULL
                || !market.getId().equals(businessModel.getSourceRun().getSourceMarketVersionId())) {
            throw new BusinessException(ErrorCode.FINANCIAL_PREPARATION_REQUIRED,
                "Market/BM source lineage가 current 상태가 아닙니다.");
        }
        return new CurrentSources(market, businessModel);
    }

    private JsonNode currentConceptHypotheses(CurrentSources source) {
        String seedId = source.market().getSourceRun().getSourceMarketSeedSnapshotId();
        return marketSeeds.findById(seedId)
            .filter(seed -> seed.getDeletedAt() == null && seed.getStaleAt() == null
                && source.market().getProject().getId().equals(seed.getProjectId()))
            .map(seed -> mapper.readTree(seed.getSnapshotJson()).path("finalHypotheses").deepCopy())
            .orElseGet(mapper::createObjectNode);
    }

    private boolean stale(FinancialInputPreparation value) {
        if ("DIRECT_INPUT".equals(value.getSourceMode())) {
            return preparations.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
                value.getProjectId(), "DIRECT_INPUT").map(current -> !current.getId().equals(value.getId())).orElse(true);
        }
        if ("USER_DOCUMENT_INPUT".equals(value.getSourceMode())) {
            return preparations.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
                value.getProjectId(), "USER_DOCUMENT_INPUT").map(current -> !current.getId().equals(value.getId())
                    || !current.getSourceDocumentHash().equals(value.getSourceDocumentHash())).orElse(true);
        }
        if (!exactCurrentConcept(value)) return true;
        try {
            CurrentSources current = currentSources(value.getUpdatedByUserId(), value.getProjectId());
            return !current.market().getId().equals(value.getSourceMarketResearchVersionId())
                || !current.businessModel().getId().equals(value.getSourceBusinessModelVersionId());
        } catch (BusinessException unavailable) { return true; }
    }

    private boolean stale(FinancialInputSnapshot value) {
        if ("DIRECT_INPUT".equals(value.getSourceMode())) {
            return preparations.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
                value.getProjectId(), "DIRECT_INPUT").map(current -> !current.getId().equals(value.getPreparationId())
                    || current.getRevision() != value.getPreparationRevision()).orElse(true);
        }
        if ("USER_DOCUMENT_INPUT".equals(value.getSourceMode())) {
            return preparations.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(
                value.getProjectId(), "USER_DOCUMENT_INPUT").map(current -> !current.getId().equals(value.getPreparationId())
                    || current.getRevision() != value.getPreparationRevision()
                    || !current.getSourceDocumentHash().equals(value.getSourceDocumentHash())).orElse(true);
        }
        if (!exactCurrentConcept(value)) return true;
        try {
            CurrentSources current = currentSources(value.getCreatedByUserId(), value.getProjectId());
            return !current.market().getId().equals(value.getSourceMarketResearchVersionId())
                || !current.businessModel().getId().equals(value.getSourceBusinessModelVersionId());
        } catch (BusinessException unavailable) { return true; }
    }

    private void bind(FinancialInputPreparation value, Source source) {
        Binding binding = currentConcepts.binding(source);
        value.bindCurrentConcept(binding.marketSeedSnapshotId(), binding.selectionId(),
            binding.selectionRevision(), binding.bmPlanRevision(), bindingHash(binding));
    }

    private void bind(FinancialInputSnapshot value, Source source) {
        Binding binding = currentConcepts.binding(source);
        value.bindCurrentConcept(binding.marketSeedSnapshotId(), binding.selectionId(),
            binding.selectionRevision(), binding.bmPlanRevision(), bindingHash(binding));
    }

    private boolean exactCurrentConcept(FinancialInputPreparation value) {
        Source current = currentConcepts.currentOrNull(value.getProjectId());
        if (current == null || value.getSourceSelectionRevision() == null
                || value.getSourceBmPlanRevision() == null) return false;
        Binding binding = currentConcepts.binding(current);
        return binding.marketSeedSnapshotId().equals(value.getSourceCurrentMarketSeedSnapshotId())
            && binding.selectionId().equals(value.getSourceSelectionId())
            && binding.selectionRevision() == value.getSourceSelectionRevision()
            && binding.bmPlanRevision() == value.getSourceBmPlanRevision()
            && bindingHash(binding).equals(value.getCurrentConceptBindingHash());
    }

    private boolean exactCurrentConcept(FinancialInputSnapshot value) {
        Source current = currentConcepts.currentOrNull(value.getProjectId());
        if (current == null || value.getSourceSelectionRevision() == null
                || value.getSourceBmPlanRevision() == null) return false;
        Binding binding = currentConcepts.binding(current);
        return binding.marketSeedSnapshotId().equals(value.getSourceCurrentMarketSeedSnapshotId())
            && binding.selectionId().equals(value.getSourceSelectionId())
            && binding.selectionRevision() == value.getSourceSelectionRevision()
            && binding.bmPlanRevision() == value.getSourceBmPlanRevision()
            && bindingHash(binding).equals(value.getCurrentConceptBindingHash());
    }

    private String bindingHash(Binding binding) {
        ObjectNode value = mapper.createObjectNode();
        value.put("marketSeedSnapshotId", binding.marketSeedSnapshotId());
        value.put("selectionId", binding.selectionId());
        value.put("selectionRevision", binding.selectionRevision());
        value.put("bmPlanRevision", binding.bmPlanRevision());
        return snapshotHasher.hash(value);
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
                throw invalid("수익 모델은 ONE_TIME, SUBSCRIPTION, HYBRID 중 하나여야 합니다.");
            return;
        }
        if ("monthlyChurnRate".equals(key)) {
            if (!value.isNumber() || value.asDouble() < 0 || value.asDouble() > 100)
                throw invalid("월 이탈률은 0~100 사이여야 합니다.");
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

    private JsonNode decisionValue(String fieldKey, JsonNode proposalValue) {
        if ("monthlyChurnRate".equals(fieldKey) && proposalValue.isObject()) {
            return proposalValue.path("percent");
        }
        if ("newCustomerCount".equals(fieldKey) && proposalValue.isObject()) {
            return proposalValue.path("count");
        }
        return proposalValue;
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
    private record CurrentSources(MarketResearchVersion market, MarketResearchVersion businessModel) {}
}
