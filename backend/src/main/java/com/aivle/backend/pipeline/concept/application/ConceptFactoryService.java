package com.aivle.backend.pipeline.concept.application;

import static com.aivle.backend.pipeline.concept.api.ConceptFactoryApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlot;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptSlotRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConceptFactoryService {
    private final ConceptFactoryRunRepository runs;
    private final ConceptSlotRepository slots;
    private final ConceptRepository concepts;
    private final IdeaBriefRepository ideaBriefs;
    private final ProjectRepository projects;

    @Transactional
    public RunResponse create(Long ownerId, Long projectId, CreateRunRequest request) {
        Project project = projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        IdeaBrief snapshot = ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull(request.ideaBriefSnapshotId(), projectId)
            .filter(IdeaBrief::isConfirmed)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        ConceptFactoryRun current = runs.findCurrentOwned(ownerId, projectId).orElse(null);
        if (current != null && !current.isTerminal()) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING, "Concept Factory run is already active.");
        }
        ConceptFactoryRun run = runs.save(ConceptFactoryRun.create(
            project, snapshot.getId(), snapshot.getSnapshotHash(), ownerId
        ));
        List<ConceptSlot> fiveSlots = Arrays.stream(VariationFocus.values())
            .map(focus -> ConceptSlot.create(run, focus.ordinal() + 1, focus))
            .toList();
        slots.saveAll(fiveSlots);
        return response(run);
    }

    @Transactional(readOnly = true)
    public RunResponse current(Long ownerId, Long projectId) {
        return response(runs.findCurrentOwned(ownerId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Concept Factory run was not found.")));
    }

    @Transactional(readOnly = true)
    public RunResponse get(Long ownerId, Long projectId, String runId) {
        return response(requireOwned(ownerId, projectId, runId));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> slots(Long ownerId, Long projectId, String runId) {
        requireOwned(ownerId, projectId, runId);
        return slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, projectId)
            .stream().map(this::response).toList();
    }

    @Transactional
    public RunResponse retry(Long ownerId, Long projectId, String runId) {
        ConceptFactoryRun run = runs.findOwnedForUpdate(ownerId, projectId, runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Concept Factory run was not found."));
        if (run.getStatus() != ConceptFactoryRunStatus.FAILED && run.getStatus() != ConceptFactoryRunStatus.NEEDS_INPUT) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, projectId).stream()
            .filter(slot -> slot.getStatus() == ConceptSlotStatus.FAILED || slot.getStatus() == ConceptSlotStatus.NEEDS_INPUT)
            .forEach(slot -> slot.transitionTo(ConceptSlotStatus.QUEUED));
        run.transitionTo(ConceptFactoryRunStatus.QUEUED);
        return response(run);
    }

    @Transactional(readOnly = true)
    public ConceptListResponse publicConcepts(Long ownerId, Long projectId) {
        ConceptFactoryRun run = runs.findCurrentOwned(ownerId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Concept Factory run was not found."));
        if (run.getStatus() != ConceptFactoryRunStatus.COMPLETED) return new ConceptListResponse(run.getId(), List.of());
        List<ConceptResponse> result = concepts.findAllByRunIdAndProjectIdAndPublishedTrueAndDeletedAtIsNullOrderBySlotSlotNumber(run.getId(), projectId)
            .stream().map(this::response).toList();
        if (result.size() != 5) throw new IllegalStateException("completed run must expose exactly five concepts");
        return new ConceptListResponse(run.getId(), result);
    }

    private ConceptFactoryRun requireOwned(Long ownerId, Long projectId, String runId) {
        return runs.findOwned(ownerId, projectId, runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Concept Factory run was not found."));
    }

    private RunResponse response(ConceptFactoryRun run) {
        return new RunResponse(run.getId(), run.getSourceIdeaBriefSnapshotId(), run.getSourceSnapshotHash(), run.getStatus(),
            run.getReplacementRounds(), run.getInspectedCandidateCount(), run.getProviderTransientRetryCount(), run.getUpdatedAt());
    }

    private SlotResponse response(ConceptSlot slot) {
        return new SlotResponse(slot.getSlotNumber(), slot.getVariationFocus(), slot.getStatus(), slot.getAttemptCount(), slot.getLegalRedesignCount());
    }

    private ConceptResponse response(Concept concept) {
        return new ConceptResponse(concept.getId(), concept.getSlot().getSlotNumber(), concept.getSlot().getVariationFocus(),
            concept.getTitle(), concept.getSummary(), concept.getLegalStatus());
    }
}
