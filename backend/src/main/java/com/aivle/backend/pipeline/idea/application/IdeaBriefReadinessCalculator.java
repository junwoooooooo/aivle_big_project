package com.aivle.backend.pipeline.idea.application;

import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefFieldCatalog;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestion;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class IdeaBriefReadinessCalculator {
    public static final String UNDECIDED = "__UNDECIDED__";
    public static final int MAX_CLARIFICATION_ROUNDS = IdeaBrief.MAX_CLARIFICATION_ROUNDS;
    private final ObjectMapper mapper;

    public IdeaBriefReadinessCalculator(ObjectMapper mapper) { this.mapper = mapper; }

    public Assessment calculate(IdeaBrief brief, List<IdeaBriefField> fieldEntities,
            List<IdeaQuestion> activeQuestions) {
        return calculate(brief, fieldEntities, activeQuestions, brief.getAssessmentInputHash() != null);
    }

    public Assessment calculate(IdeaBrief brief, List<IdeaBriefField> fieldEntities,
            List<IdeaQuestion> activeQuestions, boolean assessmentCurrent) {
        Map<String, IdeaBriefField> byKey = new LinkedHashMap<>();
        fieldEntities.forEach(field -> byKey.put(field.getFieldKey(), field));
        List<String> requiredMissing = IdeaBriefFieldCatalog.fields().stream()
            .filter(IdeaBriefFieldCatalog.FieldDefinition::requiredForConcept)
            .filter(definition -> incomplete(byKey.get(definition.key())))
            .map(IdeaBriefFieldCatalog.FieldDefinition::key)
            .toList();
        LinkedHashSet<String> missingKeys = new LinkedHashSet<>(requiredMissing);
        persistedMissingKeys(brief.getMissingFieldKeysJson()).stream()
            .filter(key -> IdeaBriefFieldCatalog.require(key).requiredForConcept())
            .filter(key -> incomplete(byKey.get(key))).forEach(missingKeys::add);
        List<String> missing = List.copyOf(missingKeys);
        int total = (int) IdeaBriefFieldCatalog.fields().stream()
            .filter(IdeaBriefFieldCatalog.FieldDefinition::requiredForConcept).count();
        int completed = total - requiredMissing.size();
        int unanswered = (int) activeQuestions.stream().filter(question -> !question.isAnswered()).count();
        List<Contradiction> contradictions = contradictions(brief.getContradictionsJson());
        int blocking = (int) contradictions.stream().filter(value -> unresolved(value, byKey)).count();
        int completionScore = total == 0 ? 0 : completed * 100 / total;
        int aiScore = brief.getAiReadinessStatus() == null ? completionScore : brief.getReadinessScore();
        int score = Math.max(0, Math.min(completionScore, aiScore) - blocking * 10 - unanswered * 5);
        boolean ready = requiredMissing.isEmpty() && unanswered == 0 && blocking == 0
            && assessmentCurrent;
        return new Assessment(total, completed, missing, unanswered, blocking, score, ready);
    }

    public List<Contradiction> contradictions(String json) {
        try {
            JsonNode values = mapper.readTree(json == null ? "[]" : json);
            if (!values.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(JsonNode::isObject)
                .map(value -> new Contradiction(
                    value.path("fieldKeys").isArray()
                        ? java.util.stream.StreamSupport.stream(value.path("fieldKeys").spliterator(), false)
                            .filter(JsonNode::isTextual).map(JsonNode::asText)
                            .filter(IdeaBriefFieldCatalog::contains).toList()
                        : List.of(),
                    value.path("summary").isTextual() ? value.path("summary").asText() : ""
                ))
                .filter(value -> !value.fieldKeys().isEmpty() && !value.summary().isBlank())
                .toList();
        } catch (RuntimeException invalid) {
            return List.of();
        }
    }

    private List<String> persistedMissingKeys(String json) {
        try {
            JsonNode values = mapper.readTree(json == null ? "[]" : json);
            if (!values.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(JsonNode::isTextual).map(JsonNode::asText)
                .filter(IdeaBriefFieldCatalog::contains).distinct().toList();
        } catch (RuntimeException invalid) {
            return List.of();
        }
    }

    private boolean incomplete(IdeaBriefField field) {
        return field == null || field.getProvenance() == IdeaFieldProvenance.MISSING
            || field.getFieldValue() == null || field.getFieldValue().isBlank()
            || UNDECIDED.equals(field.getFieldValue());
    }

    private boolean unresolved(Contradiction contradiction, Map<String, IdeaBriefField> fields) {
        return contradiction.fieldKeys().stream().anyMatch(key -> {
            IdeaBriefField field = fields.get(key);
            return field == null || (field.getProvenance() != IdeaFieldProvenance.USER_INPUT
                && field.getProvenance() != IdeaFieldProvenance.USER_CONFIRMED);
        });
    }

    public record Assessment(int totalRequiredFieldCount, int completedRequiredFieldCount,
        List<String> missingFieldKeys, int unansweredQuestionCount, int contradictionCount,
        int score, boolean readyForConfirm) { }

    public record Contradiction(List<String> fieldKeys, String summary) {
        public Contradiction { fieldKeys = List.copyOf(fieldKeys); }
    }
}
