package com.aivle.backend.pipeline.conceptportfolio.application;

import static com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConceptPortfolioService {
    private static final List<ConceptPortfolioRunStatus> ACTIVE = List.of(
        ConceptPortfolioRunStatus.QUEUED, ConceptPortfolioRunStatus.RUNNING);
    private final ProjectRepository projects;
    private final IdeaBriefRepository ideaBriefs;
    private final IdeaBriefFieldRepository ideaFields;
    private final ConceptPortfolioRunRepository runs;
    private final ConceptPortfolioConceptRepository concepts;
    private final ConceptInputRequestRepository inputRequests;
    private final ConceptPortfolioSeedBuilder seeds;
    private final CanonicalInputHasher hasher;
    private final TaskRunService taskRuns;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    public ConceptPortfolioService(ProjectRepository projects, IdeaBriefRepository ideaBriefs,
            IdeaBriefFieldRepository ideaFields, ConceptPortfolioRunRepository runs,
            ConceptPortfolioConceptRepository concepts, ConceptInputRequestRepository inputRequests,
            ConceptPortfolioSeedBuilder seeds, CanonicalInputHasher hasher,
            TaskRunService taskRuns, JobEventPublisher events, ObjectMapper mapper) {
        this.projects = projects; this.ideaBriefs = ideaBriefs; this.ideaFields = ideaFields;
        this.runs = runs; this.concepts = concepts; this.inputRequests = inputRequests;
        this.seeds = seeds; this.hasher = hasher; this.taskRuns = taskRuns;
        this.events = events; this.mapper = mapper;
    }

    @Transactional
    public RunResponse create(Long ownerId, Long projectId, CreateRunRequest request) {
        Project project = projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        IdeaBrief source = ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull(
            request.ideaBriefSnapshotId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!source.isConfirmed()) throw new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED);
        var input = seeds.build(source, ideaFields.findAllByBriefIdOrderById(source.getId()),
            request.requestedMaximum());
        String requestHash = hasher.hash(TaskType.CONCEPT_PORTFOLIO_V2_RUN, "1.0", "ko-KR", input.json());

        var replay = runs.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(
            projectId, request.idempotencyKey());
        if (replay.isPresent()) {
            if (!replay.get().getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return response(replay.get());
        }
        IdeaBrief current = ideaBriefs.findCurrentOwned(ownerId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        if (!source.getId().equals(current.getId())
                && !source.getId().equals(current.getConfirmedSnapshotId())) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        }
        if (runs.findFirstByProjectIdAndProductStatusInAndDeletedAtIsNull(projectId, ACTIVE).isPresent()) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        runs.findCurrentForUpdate(projectId).ifPresent(ConceptPortfolioRun::markStale);
        ConceptPortfolioRun run = runs.save(ConceptPortfolioRun.queued(
            project, source, request.requestedMaximum(), requestHash, request.idempotencyKey(), ownerId));
        String correlationId = UUID.randomUUID().toString();
        var task = taskRuns.create(ownerId, projectId, TaskType.CONCEPT_PORTFOLIO_V2_RUN,
            "CONCEPT_PORTFOLIO_RUN", run.getId(), input.json(), requestHash,
            request.idempotencyKey(), correlationId, 2);
        run.attachInitialTask(task.getId());
        events.publish(new JobEventPublisher.Command(projectId, task.getId(), task.getId(),
            "QUEUED", "job.concept-portfolio.queued", JobEvent.Status.QUEUED,
            "job.concept-portfolio.queued", Map.of(), null));
        return response(run);
    }

    @Transactional(readOnly = true)
    public RunResponse current(Long ownerId, Long projectId) {
        return response(runs.findCurrentOwned(ownerId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public RunResponse get(Long ownerId, Long projectId, String runId) {
        return response(owned(ownerId, projectId, runId));
    }

    @Transactional(readOnly = true)
    public List<ConceptResponse> concepts(Long ownerId, Long projectId, String runId) {
        owned(ownerId, projectId, runId);
        return concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByDisplayOrder(runId, projectId)
            .stream().map(this::conceptResponse).toList();
    }

    private ConceptPortfolioRun owned(Long ownerId, Long projectId, String runId) {
        return runs.findOwned(ownerId, projectId, runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RunResponse response(ConceptPortfolioRun run) {
        int selectable = Math.toIntExact(concepts.countByRunIdAndSelectableTrueAndDeletedAtIsNull(run.getId()));
        return new RunResponse(run.getId(), run.getSourceIdeaBrief().getId(), run.getSourceSnapshotHash(),
            run.getProductStatus(), run.getRequestedMaxConcepts(), run.getProducedConceptCount(), selectable,
            run.getOpenInputCount(), run.getInitialTaskRunId(), run.getDownstreamReadiness(),
            run.getFailureCode(), nextAction(run.getProductStatus()),
            run.getUpdatedAt() == null ? null : run.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private ConceptResponse conceptResponse(ConceptPortfolioConcept value) {
        return new ConceptResponse(value.getId(), value.getCandidateId(), value.getLineageId(),
            value.getDisplayOrder(), value.getConceptName(), value.getSummary(), value.getLegalStatus(),
            value.getCanonicalHash(), value.isSelectable(), mapper.readTree(value.getCandidateSnapshotJson()),
            mapper.readTree(value.getLegalReviewJson()));
    }

    private String nextAction(ConceptPortfolioRunStatus status) {
        return switch (status) {
            case QUEUED, RUNNING -> "WAIT";
            case RESULTS_AVAILABLE, RESULTS_WITH_OPEN_INPUT -> "REVIEW_CONCEPTS";
            case NEEDS_INPUT -> "PROVIDE_REQUIRED_INPUTS";
            case FAILED -> "RETRY_OR_START_NEW";
            case STALE -> "START_NEW_RUN";
        };
    }
}
