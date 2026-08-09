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
import com.aivle.backend.pipeline.concept.repository.ConceptAttemptRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptSlotRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptRejectionSummaryRepository;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalAssessmentRepository;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalEvidenceLinkRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver.Jurisdiction;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Arrays;
import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class ConceptFactoryService {
    private final ConceptFactoryRunRepository runs;
    private final ConceptSlotRepository slots;
    private final ConceptRepository concepts;
    private final IdeaBriefRepository ideaBriefs;
    private final IdeaBriefFieldRepository ideaBriefFields;
    private final ProjectRepository projects;
    private final ConceptAttemptRepository attempts;
    private final ConceptRejectionSummaryRepository rejections;
    private final ConceptLegalAssessmentRepository legalAssessments;
    private final ConceptLegalEvidenceLinkRepository legalEvidenceLinks;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final ObjectMapper objectMapper;
    private final JobEventPublisher jobEvents;
    private final LegalJurisdictionResolver jurisdictions;
    private final ConceptFactoryRetryPolicy retryPolicy;

    @Transactional
    public RunResponse create(Long ownerId, Long projectId, CreateRunRequest request) {
        Project project = projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        IdeaBrief snapshot = ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull(request.ideaBriefSnapshotId(), projectId)
            .filter(IdeaBrief::isConfirmed)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        ideaBriefFields.findByBriefIdAndFieldKey(snapshot.getId(), "targetRegion")
            .filter(field -> field.getDecisionState() == IdeaDecisionState.LOCKED)
            .filter(field -> field.getProvenance() == IdeaFieldProvenance.USER_INPUT
                || field.getProvenance() == IdeaFieldProvenance.USER_CONFIRMED)
            .filter(field -> jurisdictions.resolve(field.getFieldValue()) != Jurisdiction.KR)
            .ifPresent(field -> { throw new BusinessException(ErrorCode.LEGAL_JURISDICTION_UNSUPPORTED); });
        ConceptFactoryRun current = runs.findCurrentOwned(ownerId, projectId).orElse(null);
        if (current != null && !current.isTerminal()) {
            if (current.getSourceIdeaBriefSnapshotId().equals(snapshot.getId())) return response(current);
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING, "Concept Factory run is already active.");
        }
        ConceptFactoryRun run = runs.save(ConceptFactoryRun.create(
            project, snapshot.getId(), snapshot.getSnapshotHash(), ownerId
        ));
        List<ConceptSlot> fiveSlots = Arrays.stream(VariationFocus.values())
            .map(focus -> ConceptSlot.create(run, focus.ordinal() + 1, focus))
            .toList();
        slots.saveAll(fiveSlots);
        String input = objectMapper.writeValueAsString(java.util.Map.of(
            "runId", run.getId(), "ideaBriefSnapshotId", snapshot.getId(), "snapshotHash", snapshot.getSnapshotHash()
        ));
        String key = "concept-factory:" + run.getId();
        TaskRun task = taskRuns.create(ownerId, projectId, TaskType.CONCEPT_FACTORY_RUN, "CONCEPT_FACTORY_RUN",
            run.getId(), input, inputHasher.hash(TaskType.CONCEPT_FACTORY_RUN, "1.0", "ko-KR", input), key, key, 1);
        run.attachTaskRun(task.getId());
        jobEvents.publish(new JobEventPublisher.Command(projectId, task.getId(), task.getId(), "QUEUED",
            "job.concept.run.queued", JobEvent.Status.QUEUED, "job.concept.run.queued", java.util.Map.of(), null));
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
    public RunResponse retry(Long ownerId, Long projectId, String runId, String idempotencyKey) {
        ConceptFactoryRun run = runs.findOwnedForUpdate(ownerId, projectId, runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Concept Factory run was not found."));
        if (run.retryReplay(idempotencyKey)) return response(run);
        IdeaBrief latestBrief = ideaBriefs.findCurrentOwned(ownerId, projectId).orElse(null);
        if (latestBrief != null && latestBrief.isConfirmed()
            && !latestBrief.getId().equals(run.getSourceIdeaBriefSnapshotId())) {
            if (run.getStatus() == ConceptFactoryRunStatus.FAILED) run.transitionTo(ConceptFactoryRunStatus.STALE);
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED,
                "Idea Brief snapshot changed; start a new Concept Factory run.");
        }
        ConceptFactoryRetryPolicy.Decision retryDecision = retryDecision(run, latestBrief);
        if (!retryDecision.canResume()) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, projectId).stream()
            .filter(slot -> slot.getStatus() == ConceptSlotStatus.FAILED)
            .forEach(slot -> slot.transitionTo(ConceptSlotStatus.QUEUED));
        run.transitionTo(ConceptFactoryRunStatus.QUEUED);
        String input = objectMapper.writeValueAsString(java.util.Map.of(
            "runId", run.getId(), "ideaBriefSnapshotId", run.getSourceIdeaBriefSnapshotId(),
            "snapshotHash", run.getSourceSnapshotHash(), "resume", true
        ));
        String taskKey = "concept-factory-retry:" + run.getId() + ":" + idempotencyKey;
        TaskRun task = taskRuns.create(ownerId, projectId, TaskType.CONCEPT_FACTORY_RUN,
            "CONCEPT_FACTORY_RUN", run.getId(), input,
            inputHasher.hash(TaskType.CONCEPT_FACTORY_RUN, "1.0", "ko-KR", input),
            taskKey, taskKey, 1);
        run.attachRetryTaskRun(task.getId(), idempotencyKey);
        jobEvents.publish(new JobEventPublisher.Command(projectId, task.getId(), task.getId(), "QUEUED",
            "job.concept.run.queued", JobEvent.Status.QUEUED, "job.concept.run.queued", java.util.Map.of(), null));
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
        List<ConceptSlot> runSlots = slots
            .findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(run.getId(), run.getProject().getId());
        List<com.aivle.backend.pipeline.concept.domain.ConceptAttempt> allAttempts = runSlots.stream()
            .flatMap(slot -> attempts.findAllBySlotIdOrderByAttemptNumber(slot.getId()).stream()).toList();
        var latestFailure = allAttempts.stream().filter(value -> value.getSafeErrorCode() != null)
            .max(Comparator.comparing(com.aivle.backend.pipeline.concept.domain.ConceptAttempt::getUpdatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
        IdeaBrief latestBrief = ideaBriefs.findCurrentOwned(run.getCreatedByUserId(), run.getProject().getId()).orElse(null);
        ConceptFactoryRetryPolicy.Decision retryDecision = retryDecision(run, latestBrief, runSlots);
        boolean resumable = retryDecision.canResume();
        List<RequiredInput> requiredInputs = requiredInputs(runSlots);
        String nextAction = run.getStatus() == ConceptFactoryRunStatus.NEEDS_INPUT
            ? (requiredInputs.isEmpty() ? "COMPLETE_IDEA_BRIEF" : "PROVIDE_REQUIRED_INPUTS")
            : run.getStatus() == ConceptFactoryRunStatus.STALE ? "START_NEW_RUN"
            : retryDecision.nextAction();
        int generated = (int) allAttempts.stream().filter(value ->
            value.getPhase() != com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.LEGAL_REVIEW
                && value.getResultJson() != null).count();
        int initialCandidates = (int) allAttempts.stream().filter(value ->
            value.getPhase() == com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.INITIAL
                && value.getResultJson() != null).count();
        int generationFailures = (int) allAttempts.stream().filter(value ->
            value.getPhase() != com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.LEGAL_REVIEW
                && value.getResultJson() == null && value.getErrorClassification() != null).count();
        int replacementCandidates = (int) allAttempts.stream().filter(value ->
            value.getPhase() == com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.REPLACEMENT
                && value.getResultJson() != null).count();
        int redesigns = runSlots.stream().mapToInt(ConceptSlot::getLegalRedesignCount).sum();
        int eligible = (int) runSlots.stream().filter(value -> value.getStatus() == ConceptSlotStatus.ELIGIBLE).count();
        String failureScope = latestFailure != null
            && latestFailure.getErrorClassification()
                == com.aivle.backend.pipeline.concept.domain.ConceptAttemptError.REQUEST_CONTRACT_INVALID
            ? "RUN" : latestFailure == null ? null : "SLOT";
        return new RunResponse(run.getId(), run.getSourceIdeaBriefSnapshotId(), run.getSourceSnapshotHash(), run.getStatus(),
            run.getReplacementRounds(), run.getInspectedCandidateCount(), run.getProviderTransientRetryCount(), run.getTaskRunId(),
            eligible, initialCandidates, generated, generationFailures, redesigns, replacementCandidates,
            rejections.countBySlotRunIdAndDeletedAtIsNull(run.getId()),
            failureScope, latestFailure == null ? null : latestFailure.getSafeErrorCode(),
            resumable, resumable, run.isTerminal(), nextAction, requiredInputs, utc(run.getUpdatedAt()));
    }

    private List<RequiredInput> requiredInputs(List<ConceptSlot> runSlots) {
        List<RequiredInput> result = new ArrayList<>();
        for (ConceptSlot slot : runSlots) {
            attempts.findAllBySlotIdOrderByAttemptNumber(slot.getId()).stream()
                .filter(value -> value.getPhase()
                    == com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.LEGAL_REVIEW
                    && value.getResultJson() != null)
                .reduce((first, second) -> second).ifPresent(value -> {
                    JsonNode legal = objectMapper.readTree(value.getResultJson());
                    if (!"NEEDS_FACTS".equals(legal.path("status").asText())) return;
                    for (JsonNode question : legal.path("unknownFacts")) {
                        String text = question.asText("").trim();
                        if (!text.isBlank()) result.add(new RequiredInput(
                            "LEGAL_EXTERNAL_FACT_UNRESOLVED", text, "LEGAL_REVIEW", slot.getSlotNumber()));
                    }
                });
        }
        return List.copyOf(result);
    }

    private SlotResponse response(ConceptSlot slot) {
        List<com.aivle.backend.pipeline.concept.domain.ConceptAttempt> values = attempts
            .findAllBySlotIdOrderByAttemptNumber(slot.getId());
        var latest = values.isEmpty() ? null : values.get(values.size() - 1);
        var failure = values.stream().filter(value -> value.getSafeErrorCode() != null)
            .reduce((first, second) -> second).orElse(null);
        int candidateCount = (int) values.stream().filter(value -> value.getPhase() != com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.LEGAL_REVIEW
            && value.getResultJson() != null).count();
        int legalReviewCount = (int) values.stream().filter(value -> value.getPhase() == com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.LEGAL_REVIEW).count();
        int replacementCount = (int) values.stream().filter(value -> value.getPhase() == com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.REPLACEMENT
            && value.getResultJson() != null).count();
        boolean preserved = candidateCount > 0;
        return new SlotResponse(slot.getSlotNumber(), slot.getVariationFocus(), slot.getStatus(),
            latest == null ? null : latest.getPhase().name(), candidateCount, legalReviewCount,
            slot.getLegalRedesignCount(), replacementCount,
            failure == null ? null : failure.getPhase().name(), failure == null ? null : failure.getSafeErrorCode(),
            failure != null && failure.isRetryable(), preserved, utc(slot.getUpdatedAt()));
    }

    private ConceptResponse response(Concept concept) {
        var assessment = legalAssessments.findByConceptIdAndProjectIdAndDeletedAtIsNull(concept.getId(), concept.getProjectId())
            .orElseThrow(() -> new IllegalStateException("published concept requires a legal assessment"));
        List<EvidenceView> evidence = legalEvidenceLinks
            .findAllByAssessmentIdAndProjectIdAndDeletedAtIsNull(assessment.getId(), concept.getProjectId()).stream()
            .map(link -> new EvidenceView(link.getEvidence().getSourceType(), link.getEvidence().getLawId(),
                link.getEvidence().getLawName(), link.getEvidence().getArticleReference(),
                link.getEvidence().getTitle(), link.getEvidence().getEffectiveDate(),
                utc(link.getEvidence().getRetrievedAt()), link.getEvidence().getOfficialSourceUri())).toList();
        LegalReviewView legal = new LegalReviewView(assessment.getStatus(), assessment.getSafeSummary(),
            objectMapper.readTree(assessment.getAssessmentJson()), evidence);
        return new ConceptResponse(concept.getId(), concept.getSlot().getSlotNumber(), concept.getSlot().getVariationFocus(),
            concept.getTitle(), concept.getSummary(), concept.getLegalStatus(), concept.getSourceSnapshotHash(),
            concept.getCanonicalHash(), concept.getMajorFieldHash(), concept.getRun().getStatus() == ConceptFactoryRunStatus.STALE,
            objectMapper.readTree(concept.getCandidateJson()), legal);
    }

    private ConceptFactoryRetryPolicy.Decision retryDecision(ConceptFactoryRun run, IdeaBrief latestBrief) {
        return retryDecision(run, latestBrief, slots
            .findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(run.getId(), run.getProject().getId()));
    }

    private ConceptFactoryRetryPolicy.Decision retryDecision(ConceptFactoryRun run, IdeaBrief latestBrief,
            List<ConceptSlot> runSlots) {
        boolean snapshotCurrent = latestBrief != null && latestBrief.isConfirmed()
            && latestBrief.getId().equals(run.getSourceIdeaBriefSnapshotId());
        List<ConceptFactoryRetryPolicy.SlotState> states = runSlots.stream().map(slot -> {
            List<com.aivle.backend.pipeline.concept.domain.ConceptAttempt> values =
                attempts.findAllBySlotIdOrderByAttemptNumber(slot.getId());
            var latest = values.isEmpty() ? null : values.get(values.size() - 1);
            boolean preserved = values.stream().anyMatch(value ->
                value.getPhase() != com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase.LEGAL_REVIEW
                    && value.getResultJson() != null && value.getErrorClassification() == null);
            return new ConceptFactoryRetryPolicy.SlotState(slot.getStatus(),
                latest == null ? null : latest.getErrorClassification(),
                latest != null && latest.isRetryable(), preserved);
        }).toList();
        return retryPolicy.evaluate(run.getStatus(), snapshotCurrent, states);
    }

    private Instant utc(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
