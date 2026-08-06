package com.aivle.backend.pipeline.integration.application;

import static com.aivle.backend.pipeline.integration.api.IntegrationApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.integration.domain.ModuleHandoff;
import com.aivle.backend.pipeline.integration.domain.ModuleRun;
import com.aivle.backend.pipeline.integration.domain.ModuleRunStatus;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.integration.repository.ModuleHandoffRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.selection.domain.SelectedConceptSnapshot;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.repository.SelectedConceptSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class ModuleIntegrationService {
    public static final String MARKET_INPUT_CONTRACT = "selected-concept-market-input-v1";
    private static final String DEFAULT_OPERATION = "START_MARKET_ANALYSIS";
    private final ProjectRepository projects;
    private final ConceptSelectionRepository selections;
    private final SelectedConceptSnapshotRepository snapshots;
    private final ModuleHandoffRepository handoffs;
    private final ModuleRunRepository runs;
    private final ObjectMapper mapper;

    @Transactional
    public HandoffResponse create(Long ownerId, Long projectId, CreateHandoffRequest request) {
        requireOwnedForUpdate(ownerId, projectId);
        ModuleType module = parseModule(request.module());
        var selection = selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        SelectedConceptSnapshot snapshot = snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId)
            .orElseThrow(() -> new IllegalStateException("current selection snapshot is missing"));
        if (request.inputSnapshotId() != null && !request.inputSnapshotId().isBlank()
            && !request.inputSnapshotId().equals(snapshot.getId())) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        String operation = request.requestedOperation() == null || request.requestedOperation().isBlank()
            ? DEFAULT_OPERATION : request.requestedOperation().strip();
        String key = HandoffIdempotencyKey.create(module, snapshot.getSnapshotHash(), operation);
        var existing = handoffs.findByIdempotencyKeyAndDeletedAtIsNull(key);
        if (existing.isPresent()) return response(existing.get(), runs.findByHandoffIdAndProjectIdAndDeletedAtIsNull(existing.get().getId(), projectId).orElseThrow());

        Instant requestedAt = Instant.now();
        String handoffId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        SelectedConceptMarketInputV1 input = marketInput(selection.getId(), snapshot);
        ModuleHandoff handoff = handoffs.save(ModuleHandoff.prepare(handoffId, projectId, module, MARKET_INPUT_CONTRACT,
            snapshot.getId(), snapshot.getSnapshotHash(), mapper.writeValueAsString(input), operation, key,
            "/api/v3/projects/" + projectId + "/module-runs/" + runId, ownerId, requestedAt));
        ModuleRun run = runs.save(ModuleRun.notConnected(runId, handoff));
        return response(handoff, run);
    }

    @Transactional(readOnly = true)
    public ModuleRunListResponse list(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        String currentSnapshotId = currentSnapshotId(projectId);
        return new ModuleRunListResponse(runs.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .stream().map(run -> response(run, currentSnapshotId)).toList());
    }

    @Transactional(readOnly = true)
    public ModuleRunResponse get(Long ownerId, Long projectId, String runId) {
        requireOwned(ownerId, projectId);
        ModuleRun run = runs.findByIdAndProjectIdAndDeletedAtIsNull(runId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Module Run을 찾을 수 없습니다."));
        return response(run, currentSnapshotId(projectId));
    }

    private SelectedConceptMarketInputV1 marketInput(Long selectionId, SelectedConceptSnapshot snapshot) {
        var body = mapper.readTree(snapshot.getSnapshotJson());
        return new SelectedConceptMarketInputV1(MARKET_INPUT_CONTRACT, snapshot.getProjectId(), selectionId, snapshot.getId(),
            body.path("concept"), body.path("legalAssessment"), snapshot.getSnapshotHash(), snapshot.getSelectedAt());
    }

    private HandoffResponse response(ModuleHandoff handoff, ModuleRun run) {
        return new HandoffResponse("module-handoff-v1", handoff.getId(), handoff.getProjectId(), handoff.getModule().name(),
            handoff.getInputSnapshotId(), handoff.getInputSnapshotHash(), handoff.getRequestedAt(),
            new CallbackView(handoff.getCallbackMode(), handoff.getCallbackReference()), handoff.getRequestedOperation(),
            handoff.getStatus(), mapper.readValue(handoff.getInputSnapshotJson(), SelectedConceptMarketInputV1.class),
            response(run, currentSnapshotId(handoff.getProjectId())));
    }

    private ModuleRunResponse response(ModuleRun run, String currentSnapshotId) {
        boolean stale = currentSnapshotId != null && !currentSnapshotId.equals(run.getInputSnapshotId());
        String status = stale ? ModuleRunStatus.STALE.name() : run.getStatus().name();
        return new ModuleRunResponse(run.getId(), run.getHandoffId(), run.getModule().name(), run.getInputSnapshotId(),
            run.getInputSnapshotHash(), status, stale, run.isCancelRequested(), run.getExternalRunReference(),
            run.getStartedAt(), run.getCompletedAt(), run.getResultReference(), run.getResultHash(), run.getSafeErrorCode());
    }

    private String currentSnapshotId(Long projectId) {
        return selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .flatMap(selection -> snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId))
            .map(SelectedConceptSnapshot::getId).orElse(null);
    }

    private ModuleType parseModule(String value) {
        try { return ModuleType.valueOf(value); }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 외부 Module입니다."); }
    }

    private void requireOwnedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId).filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
