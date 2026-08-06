package com.aivle.backend.journey.boundary;

import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.OpportunityFieldValueRepository;
import com.aivle.backend.journey.foundation.SnapshotHasher;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RegulatoryBoundaryCompletionService {
    private final RegulatoryBoundaryRunRepository runs;
    private final RegulatoryBoundaryVersionRepository versions;
    private final BoundaryEvidenceRepository evidence;
    private final BoundaryRuleRepository rules;
    private final BoundaryQuestionRepository questions;
    private final OpportunityFieldValueRepository fields;
    private final TaskRunService tasks;
    private final SnapshotHasher snapshotHasher;
    private final ObjectMapper mapper;

    public RegulatoryBoundaryCompletionService(RegulatoryBoundaryRunRepository runs,
            RegulatoryBoundaryVersionRepository versions, BoundaryEvidenceRepository evidence,
            BoundaryRuleRepository rules, BoundaryQuestionRepository questions,
            OpportunityFieldValueRepository fields, TaskRunService tasks,
            SnapshotHasher snapshotHasher, ObjectMapper mapper) {
        this.runs = runs; this.versions = versions; this.evidence = evidence;
        this.rules = rules; this.questions = questions; this.fields = fields;
        this.tasks = tasks; this.snapshotHasher = snapshotHasher; this.mapper = mapper;
    }

    @Transactional
    public RegulatoryBoundaryVersion complete(TaskRunService.Claim claim, ExecutionResponse response) {
        RegulatoryBoundaryContract.validate(response.result());
        RegulatoryBoundaryRun run = runs.findByTaskRunIdAndDeletedAtIsNull(claim.taskRunId()).orElseThrow();
        RegulatoryBoundaryVersion existing = versions.findByRunIdAndDeletedAtIsNull(run.getId()).orElse(null);
        if (existing != null) return existing;
        validateLockedConflicts(run, response.result());
        tasks.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        RegulatoryBoundaryVersion.Status status = RegulatoryBoundaryVersion.Status
            .valueOf(response.result().path("status").asText());
        run.complete(RegulatoryBoundaryRun.State.valueOf(status.name()), LocalDateTime.now());
        String snapshot = mapper.writeValueAsString(response.result());
        int number = Math.toIntExact(versions.countByProjectIdAndDeletedAtIsNull(run.getProject().getId()) + 1);
        RegulatoryBoundaryVersion version = versions.save(RegulatoryBoundaryVersion.create(
            run, number, status, snapshot, snapshotHasher.hash(snapshot)));
        persistEvidence(version, response.result().path("evidence"));
        persistRules(version, response.result().path("rules"));
        persistQuestions(version, response.result().path("questions"));
        return version;
    }

    private void validateLockedConflicts(RegulatoryBoundaryRun run, JsonNode result) {
        Map<String, FieldDecisionStatus> decisions = fields
            .findByBriefVersionIdAndDeletedAtIsNullOrderByFieldKey(run.getBriefVersion().getId()).stream()
            .collect(Collectors.toMap(value -> value.getFieldKey(), value -> value.getDecisionStatus()));
        for (JsonNode conflict : result.path("conflicts")) {
            if (decisions.get(conflict.path("affectedFieldKey").asText()) != FieldDecisionStatus.LOCKED) {
                throw new IllegalArgumentException("BOUNDARY_CONFLICT_REQUIRES_LOCKED_FIELD");
            }
        }
    }

    private void persistEvidence(RegulatoryBoundaryVersion version, JsonNode values) {
        for (JsonNode item : values) evidence.save(BoundaryEvidence.create(version,
            item.path("evidenceId").asText(), item.path("sourceType").asText(), item.path("lawName").asText(),
            nullableText(item, "article"), nullableText(item, "title"), item.path("excerpt").asText(),
            item.path("plainSummary").asText(), item.path("whyRelevant").asText(),
            nullableText(item, "effectiveDate"), item.path("officialUrl").asText(),
            item.path("sourceStatus").asText(), parseTime(item.path("retrievedAt").asText()),
            item.path("contentHash").asText()));
    }
    private void persistRules(RegulatoryBoundaryVersion version, JsonNode values) {
        for (JsonNode item : values) rules.save(BoundaryRule.create(version,
            item.path("ruleId").asText(), BoundaryRule.RuleType.valueOf(item.path("ruleType").asText()),
            item.path("structureKey").asText(), item.path("title").asText(), item.path("description").asText(),
            item.path("normalizedRequirement").asText(), item.path("affectedBriefFields").toString(),
            item.path("evidenceIds").toString(), item.path("severity").asText(),
            item.path("sourceStatus").asText(), item.path("appliesWhen").toString(),
            item.path("userFacingReason").asText(), item.path("alternatives").toString(),
            item.path("requiredQualifications").toString(), nullableText(item, "requiredPartnerRole"),
            nullableText(item, "requiredDisclosure"), item.path("professionalReviewRecommended").asBoolean(),
            item.path("userActionOptions").toString()));
    }
    private void persistQuestions(RegulatoryBoundaryVersion version, JsonNode values) {
        for (JsonNode item : values) questions.save(BoundaryQuestion.open(version,
            item.path("questionId").asText(), item.path("fieldKey").asText(), item.path("question").asText(),
            item.path("reason").asText(), BoundaryQuestion.AnswerType.valueOf(item.path("answerType").asText()),
            item.path("options").toString(), item.path("required").asBoolean(),
            item.path("relatedRuleIds").toString(), item.path("relatedEvidenceIds").toString()));
    }
    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field); return value == null || value.isNull() ? null : value.asText();
    }
    private static LocalDateTime parseTime(String value) {
        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
