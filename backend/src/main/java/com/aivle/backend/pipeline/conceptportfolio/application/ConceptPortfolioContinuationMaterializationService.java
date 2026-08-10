package com.aivle.backend.pipeline.conceptportfolio.application;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioContinuationResultContract.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ConceptPortfolioContinuationMaterializationService {
    private final ConceptPortfolioRunRepository runs;
    private final ConceptPortfolioConceptRepository concepts;
    private final ConceptInputRequestRepository inputRequests;
    private final ConceptPortfolioContinuationResultContract contract;
    private final ConceptPortfolioJsonHasher hashes;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptPortfolioContinuationMaterializationService(ConceptPortfolioRunRepository runs,
            ConceptPortfolioConceptRepository concepts,
            ConceptInputRequestRepository inputRequests,
            ConceptPortfolioContinuationResultContract contract,
            ConceptPortfolioJsonHasher hashes, TaskRunService taskRuns,
            ObjectMapper mapper, Clock clock) {
        this.runs = runs; this.concepts = concepts; this.inputRequests = inputRequests;
        this.contract = contract; this.hashes = hashes; this.taskRuns = taskRuns;
        this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public ConceptPortfolioContinuationOutcome complete(TaskRunService.Claim claim,
            TaskRunWorkerContext context, ExecutionResponse response) {
        var validated = contract.validate(response.result());
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        ConceptPortfolioRun run = lockedRun(context);
        ConceptInputRequest request = lockedRequest(validated.inputRequestId(), run);
        if (request.getStatus() != ConceptInputRequestStatus.ANSWERED
                || !claim.taskRunId().equals(request.getContinuationTaskRunId())) {
            throw new ContractViolation();
        }
        JsonNode result = validated.result();
        String payload = mapper.writeValueAsString(result);
        if (validated.outcome() == ConceptPortfolioContinuationOutcome.SYSTEM_FAILURE) {
            String failureCode = textOr(result.get("failureCode"), "EXECUTION_FAILED");
            taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failureCode, "PERMANENT_EXECUTION_FAILURE", false);
            preservePortfolioAfterFailure(run);
            return validated.outcome();
        }
        if (validated.outcome() == ConceptPortfolioContinuationOutcome.NEEDS_INPUT) {
            taskRuns.adoptNeedsInput(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                payload, context.inputHash(), response.resultSchemaVersion());
        } else {
            taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                payload, context.inputHash(), response.resultSchemaVersion());
        }

        request.resolve(LocalDateTime.now(clock));
        if (validated.outcome() == ConceptPortfolioContinuationOutcome.ACCEPTED) {
            mergeAccepted(run, validated);
        } else if (validated.outcome() == ConceptPortfolioContinuationOutcome.NEEDS_INPUT) {
            createFollowUp(run, request, result);
        }
        completeRun(run, validated.outcome());
        return validated.outcome();
    }

    @Transactional
    public void failExecution(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        preservePortfolioAfterFailure(lockedRun(context));
    }

    @Transactional
    public void failContract(TaskRunService.Claim claim, TaskRunWorkerContext context,
            JsonNode invalidResult) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        taskRuns.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            invalidResult == null ? "{}" : mapper.writeValueAsString(invalidResult), "1.0",
            "AI_RESULT_INVALID");
        preservePortfolioAfterFailure(lockedRun(context));
    }

    private void mergeAccepted(ConceptPortfolioRun run,
            ConceptPortfolioContinuationResultContract.Validated result) {
        if (concepts.existsByRunIdAndLineageIdAndDeletedAtIsNull(run.getId(), result.lineageId())) {
            throw new ContractViolation();
        }
        int order = concepts.findFirstByRunIdAndDeletedAtIsNullOrderByDisplayOrderDesc(run.getId())
            .map(value -> value.getDisplayOrder() + 1).orElse(1);
        if (order > 5) throw new ContractViolation();
        JsonNode envelope = result.result().get("candidate");
        JsonNode candidate = envelope.get("candidate");
        JsonNode legal = result.result().get("legalReview");
        ConceptPortfolioContinuationResultContract.require(candidate != null && candidate.isObject());
        concepts.save(ConceptPortfolioConcept.create(run, order, result.candidateId(),
            result.lineageId(), ConceptPortfolioContinuationResultContract.text(envelope, "planId"),
            optionalText(envelope.get("parentCandidateId")),
            ConceptPortfolioContinuationResultContract.text(candidate, "conceptName"),
            ConceptPortfolioContinuationResultContract.text(candidate, "conceptDefinition"),
            ConceptPortfolioContinuationResultContract.text(legal, "route"),
            mapper.writeValueAsString(envelope), mapper.writeValueAsString(legal), hashes.hash(envelope)));
    }

    private void createFollowUp(ConceptPortfolioRun run, ConceptInputRequest previous, JsonNode result) {
        JsonNode required = result.get("requiredInput");
        JsonNode artifact = result.get("continuationArtifact");
        String candidateId = ConceptPortfolioContinuationResultContract.text(required, "candidateId");
        if (!candidateId.equals(ConceptPortfolioContinuationResultContract.text(artifact, "candidateId"))) {
            throw new ContractViolation();
        }
        ObjectNode requestIdentity = mapper.createObjectNode();
        requestIdentity.set("requiredInput", required);
        requestIdentity.put("parentInputRequestId", previous.getId());
        inputRequests.save(ConceptInputRequest.open(run, previous.getContinuation(), candidateId,
            ConceptPortfolioContinuationResultContract.text(artifact, "lineageId"),
            optionalText(required.get("scope")), optionalText(required.get("question")),
            optionalText(required.get("reason")), optionalText(required.get("possibleUserAction")),
            optionalText(required.get("safeSummary")), arrayJson(required.get("unknownFacts")),
            arrayJson(required.get("affectedFields")), mapper.writeValueAsString(artifact),
            hashes.hash(requestIdentity)));
    }

    private void completeRun(ConceptPortfolioRun run, ConceptPortfolioContinuationOutcome outcome) {
        int produced = Math.toIntExact(concepts.countByRunIdAndDeletedAtIsNull(run.getId()));
        int open = Math.toIntExact(inputRequests.countByRunIdAndStatusInAndDeletedAtIsNull(
            run.getId(), List.of(ConceptInputRequestStatus.OPEN)));
        ConceptPortfolioRunStatus status;
        String failureCode = null;
        if (produced > 0) {
            status = open > 0 ? ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT
                : ConceptPortfolioRunStatus.RESULTS_AVAILABLE;
        } else if (open > 0) {
            status = ConceptPortfolioRunStatus.NEEDS_INPUT;
        } else {
            status = ConceptPortfolioRunStatus.FAILED;
            failureCode = "NO_ACCEPTED_CONCEPTS";
        }
        run.completeContinuation(status, produced, open, failureCode);
    }

    private void preservePortfolioAfterFailure(ConceptPortfolioRun run) {
        int produced = Math.toIntExact(concepts.countByRunIdAndDeletedAtIsNull(run.getId()));
        int open = Math.toIntExact(inputRequests.countByRunIdAndStatusInAndDeletedAtIsNull(
            run.getId(), List.of(ConceptInputRequestStatus.OPEN)));
        run.continuationFailed(produced, open);
    }

    private ConceptPortfolioRun lockedRun(TaskRunWorkerContext context) {
        ConceptPortfolioRun run = runs.findLocked(context.subjectId())
            .orElseThrow(ContractViolation::new);
        if (!"CONCEPT_PORTFOLIO_RUN".equals(context.subjectType())
                || !run.getProject().getId().equals(context.projectId())
                || !context.taskRunId().equals(run.getActiveTaskRunId())) {
            throw new ContractViolation();
        }
        return run;
    }

    private ConceptInputRequest lockedRequest(String id, ConceptPortfolioRun run) {
        ConceptInputRequest request = inputRequests.findLocked(id).orElseThrow(ContractViolation::new);
        if (!request.getRun().getId().equals(run.getId())) throw new ContractViolation();
        return request;
    }

    private String arrayJson(JsonNode value) {
        ConceptPortfolioContinuationResultContract.require(value != null && value.isArray());
        return mapper.writeValueAsString(value);
    }

    private String optionalText(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    private String textOr(JsonNode value, String fallback) {
        return value == null || !value.isTextual() || value.asText().isBlank()
            ? fallback : value.asText();
    }
}
