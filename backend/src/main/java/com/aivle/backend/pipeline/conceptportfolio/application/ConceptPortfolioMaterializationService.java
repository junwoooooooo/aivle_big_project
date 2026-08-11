package com.aivle.backend.pipeline.conceptportfolio.application;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioResultContract.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConceptPortfolioMaterializationService {
    private final ConceptPortfolioRunRepository runs;
    private final ConceptPortfolioConceptRepository concepts;
    private final ConceptPortfolioContinuationRepository continuations;
    private final ConceptInputRequestRepository inputRequests;
    private final ConceptPortfolioResultContract contract;
    private final ConceptPortfolioProductStatusMapper statuses;
    private final ConceptPortfolioJsonHasher hashes;
    private final TaskRunService taskRuns;
    private final EffectiveAffectedFieldResolver affectedFields;
    private final ObjectMapper mapper;

    public ConceptPortfolioMaterializationService(ConceptPortfolioRunRepository runs,
            ConceptPortfolioConceptRepository concepts,
            ConceptPortfolioContinuationRepository continuations,
            ConceptInputRequestRepository inputRequests, ConceptPortfolioResultContract contract,
            ConceptPortfolioProductStatusMapper statuses, ConceptPortfolioJsonHasher hashes,
            TaskRunService taskRuns, EffectiveAffectedFieldResolver affectedFields,
            ObjectMapper mapper) {
        this.runs = runs; this.concepts = concepts; this.continuations = continuations;
        this.inputRequests = inputRequests; this.contract = contract; this.statuses = statuses;
        this.hashes = hashes; this.taskRuns = taskRuns;
        this.affectedFields = affectedFields; this.mapper = mapper;
    }

    @Transactional
    public void markRunning(String runId) {
        runs.findLocked(runId).orElseThrow(() -> new IllegalStateException("Portfolio run not found"))
            .markRunning();
    }

    @Transactional
    public ConceptPortfolioRunStatus complete(TaskRunService.Claim claim,
            TaskRunWorkerContext context, ExecutionResponse response) {
        JsonNode result = contract.validate(response.result());
        ConceptPortfolioRunStatus productStatus = statuses.map(result);
        String payload = mapper.writeValueAsString(result);
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (productStatus == ConceptPortfolioRunStatus.RESULTS_AVAILABLE
                || productStatus == ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT) {
            taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                payload, context.inputHash(), response.resultSchemaVersion());
        } else if (productStatus == ConceptPortfolioRunStatus.NEEDS_INPUT) {
            taskRuns.adoptNeedsInput(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                payload, context.inputHash(), response.resultSchemaVersion());
        } else {
            taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failureCode(result), failureReason(result), false);
        }

        ConceptPortfolioRun run = runs.findLocked(context.subjectId())
            .orElseThrow(() -> new ContractViolation("PORTFOLIO_RUN_NOT_FOUND"));
        if (!"CONCEPT_PORTFOLIO_RUN".equals(context.subjectType())
                || !run.getProject().getId().equals(context.projectId())) {
            throw new ContractViolation("PORTFOLIO_RUN_REFERENCE_INVALID");
        }
        int openInputs = 0;
        if (productStatus != ConceptPortfolioRunStatus.FAILED) {
            materializeConcepts(run, result.path("concepts"), result.path("legalSummaries"));
            ConceptPortfolioContinuation continuation = materializeContinuation(run,
                result.get("continuationContext"));
            openInputs = materializeInputs(run, continuation, result.path("requiredInputs"),
                result.path("continuationArtifacts"));
        }
        JsonNode summary = result.path("runSummary");
        int productConceptCount = productStatus == ConceptPortfolioRunStatus.FAILED
            ? 0 : result.path("producedConceptCount").intValue();
        run.materialize(productStatus, productConceptCount, openInputs,
            result.path("engineRunId").asText(null), result.path("engineStatus").asText(null),
            result.path("runtimeStage").asText(null), result.path("downstreamReadiness").asText(null),
            nullableText(result.get("engineDefaultConceptId")), result.path("contract").asText(),
            result.path("schemaVersion").asText(), payload,
            summary.isObject() ? nullableText(summary.get("failureCode")) : null);
        return productStatus;
    }

    @Transactional
    public void failExecution(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        runs.findLocked(context.subjectId())
            .orElseThrow(() -> new IllegalStateException("Portfolio run not found"))
            .markFailed(code);
    }

    @Transactional
    public void failContract(TaskRunService.Claim claim, TaskRunWorkerContext context,
            JsonNode invalidResult) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        taskRuns.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            invalidResult == null ? "{}" : mapper.writeValueAsString(invalidResult), "1.0", "AI_RESULT_INVALID");
        runs.findLocked(context.subjectId())
            .orElseThrow(() -> new IllegalStateException("Portfolio run not found"))
            .markFailed("RESULT_SCHEMA_INVALID");
    }

    private void materializeConcepts(ConceptPortfolioRun run, JsonNode values, JsonNode legalValues) {
        Map<String, JsonNode> latestLegal = new LinkedHashMap<>();
        legalValues.forEach(value -> latestLegal.put(ConceptPortfolioResultContract.text(value, "candidateId"), value));
        int order = 0;
        for (JsonNode envelope : values) {
            String candidateId = ConceptPortfolioResultContract.text(envelope, "candidateId");
            JsonNode legal = latestLegal.get(candidateId);
            if (legal == null) throw new ContractViolation("LEGAL_REVIEW_MISSING");
            JsonNode candidate = envelope.get("candidate");
            ConceptPortfolioResultContract.require(candidate != null && candidate.isObject());
            concepts.save(ConceptPortfolioConcept.create(run, ++order, candidateId,
                ConceptPortfolioResultContract.text(envelope, "lineageId"),
                ConceptPortfolioResultContract.text(envelope, "planId"),
                ConceptPortfolioResultContract.optionalText(envelope, "parentCandidateId"),
                ConceptPortfolioResultContract.text(candidate, "conceptName"),
                ConceptPortfolioResultContract.text(candidate, "conceptDefinition"),
                ConceptPortfolioResultContract.text(legal, "route"),
                mapper.writeValueAsString(envelope), mapper.writeValueAsString(legal), hashes.hash(envelope)));
        }
    }

    private ConceptPortfolioContinuation materializeContinuation(ConceptPortfolioRun run, JsonNode context) {
        if (context == null || context.isNull()) return null;
        ConceptPortfolioResultContract.require(context.isObject());
        return continuations.save(ConceptPortfolioContinuation.create(run,
            ConceptPortfolioResultContract.text(context, "contextVersion"), hashes.hash(context),
            mapper.writeValueAsString(context)));
    }

    private int materializeInputs(ConceptPortfolioRun run, ConceptPortfolioContinuation continuation,
            JsonNode inputs, JsonNode artifactValues) {
        Map<String, JsonNode> artifacts = new LinkedHashMap<>();
        artifactValues.forEach(value -> artifacts.put(
            ConceptPortfolioResultContract.text(value, "candidateId"), value));
        int count = 0;
        for (JsonNode input : inputs) {
            String candidateId = ConceptPortfolioResultContract.optionalText(input, "candidateId");
            String scope = ConceptPortfolioResultContract.optionalText(input, "scope");
            JsonNode artifact = candidateId == null ? null : artifacts.get(candidateId);
            String lineageId = null;
            if ("CANDIDATE".equals(scope)) {
                ConceptPortfolioResultContract.require(continuation != null && artifact != null);
                ConceptPortfolioResultContract.require(candidateId.equals(
                    ConceptPortfolioResultContract.text(artifact, "candidateId")));
                lineageId = ConceptPortfolioResultContract.text(artifact, "lineageId");
            }
            inputRequests.save(ConceptInputRequest.open(run, "CANDIDATE".equals(scope) ? continuation : null,
                candidateId, lineageId, scope, ConceptPortfolioResultContract.optionalText(input, "question"),
                ConceptPortfolioResultContract.optionalText(input, "reason"),
                ConceptPortfolioResultContract.optionalText(input, "possibleUserAction"),
                ConceptPortfolioResultContract.optionalText(input, "safeSummary"),
                arrayJson(input.get("unknownFacts")),
                arrayJson(affectedFields.resolve(input, artifact)),
                artifact == null ? null : mapper.writeValueAsString(artifact), hashes.hash(input)));
            count++;
        }
        return count;
    }

    private String arrayJson(JsonNode value) {
        ConceptPortfolioResultContract.require(value != null && value.isArray());
        return mapper.writeValueAsString(value);
    }

    private String failureCode(JsonNode result) {
        JsonNode summary = result.path("runSummary");
        String code = summary.isObject() ? nullableText(summary.get("failureCode")) : null;
        return "RESULT_SCHEMA_INVALID".equals(code) ? "RESULT_SCHEMA_INVALID" : "EXECUTION_FAILED";
    }

    private String failureReason(JsonNode result) {
        JsonNode summary = result.path("runSummary");
        String code = summary.isObject() ? nullableText(summary.get("failureCode")) : null;
        return "RESULT_SCHEMA_INVALID".equals(code) ? "AI_RESULT_INVALID" : "PERMANENT_EXECUTION_FAILURE";
    }

    private String nullableText(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }
}
