package com.aivle.backend.pipeline.idea.application;

import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestion;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestionType;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaQuestionRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class IdeaBriefDerivationCommitService {
    private static final Set<String> RESULT_FIELDS = Set.of(
        "safetyReview", "interpretation", "commitmentCandidates", "questions", "contradictions", "readiness", "userFacingSummary"
    );
    private static final Set<String> QUESTION_FIELDS = Set.of(
        "targetFieldKey", "prompt", "type", "options"
    );

    private final IdeaBriefRepository briefs;
    private final IdeaBriefFieldRepository fields;
    private final IdeaQuestionRepository questions;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;
    private final IdeaBriefReadinessCalculator readinessCalculator;
    private final IdeaBriefAssessmentHasher assessmentHasher;

    @Autowired
    public IdeaBriefDerivationCommitService(IdeaBriefRepository briefs, IdeaBriefFieldRepository fields,
            IdeaQuestionRepository questions, TaskRunService taskRuns, ObjectMapper mapper,
            IdeaBriefReadinessCalculator readinessCalculator, IdeaBriefAssessmentHasher assessmentHasher) {
        this.briefs = briefs;
        this.fields = fields;
        this.questions = questions;
        this.taskRuns = taskRuns;
        this.mapper = mapper;
        this.readinessCalculator = readinessCalculator;
        this.assessmentHasher = assessmentHasher;
    }

    public IdeaBriefDerivationCommitService(IdeaBriefRepository briefs, IdeaBriefFieldRepository fields,
            IdeaQuestionRepository questions, TaskRunService taskRuns, ObjectMapper mapper,
            IdeaBriefReadinessCalculator readinessCalculator) {
        this(briefs, fields, questions, taskRuns, mapper, readinessCalculator,
            new IdeaBriefAssessmentHasher(mapper));
    }

    @Transactional
    public CommitResult complete(
        TaskRunService.Claim claim,
        TaskRunWorkerContext context,
        ExecutionResponse response
    ) {
        JsonNode result = response.result();
        requireObject(result, RESULT_FIELDS);
        IdeaBrief brief = briefs.findByIdAndProjectIdAndDeletedAtIsNull(context.subjectId(), context.projectId())
            .orElseThrow(() -> new IllegalArgumentException("idea brief task subject not found"));
        if (!claim.taskRunId().equals(brief.getActiveTaskRunId())) {
            throw new IllegalStateException("idea brief active task changed");
        }
        String mode = mapper.readTree(context.inputSnapshot()).path("mode").asText("INITIAL");
        if ("FINAL_SYNTHESIS".equals(mode) && result.path("questions").size() != 0) {
            throw new IllegalArgumentException("final synthesis cannot generate questions");
        }

        JsonNode safety = result.get("safetyReview");
        requireObject(safety, Set.of("decision", "categories", "restrictions", "userFacingReason"));
        String safetyDecision = text(safety, "decision", 40);
        JsonNode safetyCategories = requireTextArray(safety, "categories", 10, 80);
        JsonNode safetyRestrictions = requireTextArray(safety, "restrictions", 10, 500);

        JsonNode interpretation = result.get("interpretation");
        requireObject(interpretation, Set.of(
            "interpretedProblem", "interpretedTargetUsers", "usageContext", "industryCategory",
            "researchScope", "conciseIdeaDefinition", "targetRegionInterpretation",
            "relevantKnownCompetitorContext"
        ));
        for (String key : Set.of("interpretedProblem", "interpretedTargetUsers", "usageContext",
                "industryCategory", "researchScope", "conciseIdeaDefinition")) {
            text(interpretation, key, 20_000);
        }
        optionalText(interpretation, "targetRegionInterpretation", 20_000);
        optionalText(interpretation, "relevantKnownCompetitorContext", 20_000);
        JsonNode commitmentCandidates = requireArray(result, "commitmentCandidates", 10);
        ArrayNode acceptedCandidates = mapper.createArrayNode();
        java.util.Map<String, com.aivle.backend.pipeline.idea.domain.IdeaBriefField> currentFields =
            fields.findAllByBriefIdOrderById(brief.getId()).stream().collect(java.util.stream.Collectors.toMap(
                com.aivle.backend.pipeline.idea.domain.IdeaBriefField::getFieldKey, value -> value));
        java.util.Set<String> seenCommitments = new java.util.HashSet<>();
        for (JsonNode candidate : commitmentCandidates) {
            requireObject(candidate, Set.of("fieldKey", "value", "evidenceQuote", "source", "origin", "authority"));
            String fieldKey = text(candidate, "fieldKey", 80);
            if (IdeaBriefFieldCatalog.require(fieldKey).requiredForConcept()) {
                throw new IllegalArgumentException("commitment candidate field invalid");
            }
            if (!seenCommitments.add(fieldKey)
                || !"AI_DERIVED".equals(text(candidate, "source", 30))
                || !"USER_TEXT".equals(text(candidate, "origin", 30))
                || !"REVIEWABLE".equals(text(candidate, "authority", 30))) {
                throw new IllegalArgumentException("commitment candidate metadata invalid");
            }
            text(candidate, "value", 20_000);
            text(candidate, "evidenceQuote", 1_000);
            var explicit = currentFields.get(fieldKey);
            if (explicit == null || explicit.getProvenance()
                    != com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance.USER_INPUT
                    || explicit.getDecisionState()
                    != com.aivle.backend.pipeline.idea.domain.IdeaDecisionState.LOCKED) {
                acceptedCandidates.add(candidate.deepCopy());
            }
        }
        ObjectNode storedInterpretation = (ObjectNode) interpretation.deepCopy();
        storedInterpretation.set("commitmentCandidates", acceptedCandidates);
        brief.applySafetyAndInterpretation(
            safetyDecision,
            mapper.writeValueAsString(safetyCategories),
            mapper.writeValueAsString(safetyRestrictions),
            text(safety, "userFacingReason", 1_000),
            mapper.writeValueAsString(storedInterpretation)
        );

        questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId())
            .forEach(IdeaQuestion::retire);
        int order = 0;
        for (JsonNode item : requireArray(result, "questions", 4)) {
            requireObject(item, QUESTION_FIELDS);
            JsonNode options = item.get("options");
            if (options == null || !options.isArray() || options.size() > 12) {
                throw new IllegalArgumentException("question options invalid");
            }
            questions.save(IdeaQuestion.create(
                brief,
                text(item, "targetFieldKey", 80),
                IdeaQuestionType.valueOf(text(item, "type", 30)),
                text(item, "prompt", 500),
                mapper.writeValueAsString(options),
                order++,
                brief.getClarificationRound()
            ));
        }
        JsonNode readiness = result.get("readiness");
        if (readiness == null || !readiness.isObject()) throw new IllegalArgumentException("readiness invalid");
        requireObject(readiness, Set.of("status", "score", "missingFieldKeys"));
        String readinessStatus = text(readiness, "status", 30);
        JsonNode score = readiness.get("score");
        JsonNode missingFieldKeys = readiness.get("missingFieldKeys");
        JsonNode contradictions = result.get("contradictions");
        if (score == null || !score.isIntegralNumber() || score.intValue() < 0 || score.intValue() > 100
            || missingFieldKeys == null || !missingFieldKeys.isArray() || missingFieldKeys.size() > 15
            || contradictions == null || !contradictions.isArray() || contradictions.size() > 12) {
            throw new IllegalArgumentException("readiness metadata invalid");
        }
        for (JsonNode key : missingFieldKeys) IdeaBriefFieldCatalog.require(textValue(key, 80));
        for (JsonNode contradiction : contradictions) validateContradiction(contradiction);
        String assessmentInputHash = assessmentHasher.hash(
            brief, fields.findAllByBriefIdOrderById(brief.getId()));
        brief.applyAssessment(
            text(result, "userFacingSummary", 1_000),
            mapper.writeValueAsString(contradictions),
            mapper.writeValueAsString(missingFieldKeys),
            readinessStatus,
            score.intValue(),
            assessmentInputHash
        );
        IdeaBriefReadinessCalculator.Assessment assessment = readinessCalculator.calculate(
            brief,
            fields.findAllByBriefIdOrderById(brief.getId()),
            questions.findAllByBriefIdAndActiveTrueOrderByDisplayOrder(brief.getId()),
            true
        );
        if ("BLOCK_OR_REFRAME".equals(safetyDecision)) {
            if (order > 0) throw new IllegalArgumentException("blocked safety result cannot ask follow-up questions");
            brief.safetyBlocked();
        } else if (assessment.unansweredQuestionCount() > 0 || !assessment.missingFieldKeys().isEmpty()) {
            brief.needsInput(assessment.unansweredQuestionCount(), assessment.missingFieldKeys().size());
        } else {
            brief.readyForReview();
        }

        if (brief.getStatus() == com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus.NEEDS_INPUT) {
            taskRuns.adoptNeedsInput(
                claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                mapper.writeValueAsString(result), response.canonicalInputHash(), response.resultSchemaVersion()
            );
        } else {
            taskRuns.adopt(
                claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                mapper.writeValueAsString(result), response.canonicalInputHash(), response.resultSchemaVersion()
            );
        }
        return new CommitResult(brief.getStatus().name(), order);
    }

    @Transactional
    public void fail(String briefId, Long projectId) {
        briefs.findByIdAndProjectIdAndDeletedAtIsNull(briefId, projectId).ifPresent(IdeaBrief::failDerivation);
    }

    private JsonNode requireArray(JsonNode root, String name, int max) {
        JsonNode value = root.get(name);
        if (value == null || !value.isArray() || value.size() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return value;
    }

    private JsonNode requireTextArray(JsonNode root, String name, int max, int itemMax) {
        JsonNode value = requireArray(root, name, max);
        for (JsonNode item : value) optionalTextValue(item, itemMax);
        return value;
    }

    private void requireObject(JsonNode value, Set<String> expected) {
        if (value == null || !value.isObject() || !Set.copyOf(value.propertyNames()).equals(expected)) {
            throw new IllegalArgumentException("result object fields invalid");
        }
    }

    private String text(JsonNode value, String name, int max) {
        JsonNode field = value.get(name);
        if (field == null || !field.isTextual() || field.asText().isBlank() || field.asText().length() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return field.asText();
    }

    private String textValue(JsonNode value, int max) {
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > max) {
            throw new IllegalArgumentException("text value invalid");
        }
        return value.asText();
    }

    private String optionalText(JsonNode value, String name, int max) {
        JsonNode field = value.get(name);
        if (field == null || !field.isTextual() || field.asText().length() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return field.asText();
    }

    private String optionalTextValue(JsonNode value, int max) {
        if (value == null || !value.isTextual() || value.asText().length() > max) {
            throw new IllegalArgumentException("text value invalid");
        }
        return value.asText();
    }

    private void validateContradiction(JsonNode value) {
        requireObject(value, Set.of("fieldKeys", "summary"));
        JsonNode keys = value.get("fieldKeys");
        if (keys == null || !keys.isArray() || keys.size() < 2 || keys.size() > 6) {
            throw new IllegalArgumentException("contradiction fields invalid");
        }
        for (JsonNode key : keys) IdeaBriefFieldCatalog.require(textValue(key, 80));
        text(value, "summary", 500);
    }

    public record CommitResult(String status, int questionCount) {}
}
