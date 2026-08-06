package com.aivle.backend.taskrun.contract;

import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class LegalSourcePipelineContract {
    private static final Set<String> TASK_TYPES = Set.of("IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION");
    private static final Set<String> SOURCE_STATUSES = Set.of("SOURCE_COMPLETE", "SOURCE_PARTIAL", "REGISTRY_GAP");
    private static final Set<String> ROUTE_STATUSES = Set.of("APPLIES", "POSSIBLE", "NOT_APPLICABLE", "UNKNOWN");
    private static final Set<String> ROLES = Set.of("REQUIREMENT", "SANCTION", "SCOPE", "SUPPORTING");
    private static final Set<String> CATEGORIES = Set.of("BUSINESS_REGISTRATION", "LICENSE_AND_PERMIT",
        "PRIVACY_AND_DATA", "TERMS_AND_CONTRACT", "INTELLECTUAL_PROPERTY", "CONSUMER_PROTECTION",
        "ADVERTISING_AND_MARKETING", "LABOR_AND_EMPLOYMENT", "INDUSTRY_SPECIFIC", "TAX_AND_FINANCIAL");

    private LegalSourcePipelineContract() { }

    public static void validate(JsonNode result, String expectedTaskType) {
        exact(result, Set.of("taskType", "sourceStatus", "registryVersion", "routes", "findings", "evidence",
            "requiredUserInputs", "sourceWarnings"));
        if (!TASK_TYPES.contains(text(result, "taskType")) || !expectedTaskType.equals(result.get("taskType").asText())
            || !SOURCE_STATUSES.contains(text(result, "sourceStatus"))) invalid();
        text(result, "registryVersion");
        array(result, "routes"); array(result, "findings"); array(result, "evidence");
        array(result, "requiredUserInputs"); stringArray(result.get("sourceWarnings"));

        Set<String> routeIds = new HashSet<>();
        for (JsonNode route : result.get("routes")) {
            exact(route, Set.of("routeId", "topic", "status", "evidenceQuotes", "reason", "categories"));
            if (!routeIds.add(text(route, "routeId")) || !ROUTE_STATUSES.contains(text(route, "status"))) invalid();
            text(route, "topic"); text(route, "reason"); stringArray(route.get("evidenceQuotes"));
            categories(route.get("categories"));
        }
        Set<String> evidenceIds = new HashSet<>();
        for (JsonNode evidence : result.get("evidence")) {
            exact(evidence, Set.of("evidenceId", "routeId", "category", "registryVersion", "lawName", "article", "title", "role",
                "plainSummary", "whyRelevant", "excerpt", "effectiveDate", "lawUrl", "verifiedAt"));
            if (!evidenceIds.add(text(evidence, "evidenceId")) || !routeIds.contains(text(evidence, "routeId"))
                || !CATEGORIES.contains(text(evidence, "category")) || !ROLES.contains(text(evidence, "role"))) invalid();
            if (!text(result, "registryVersion").equals(text(evidence, "registryVersion"))) invalid();
            for (String field : List.of("lawName", "article", "plainSummary", "whyRelevant", "excerpt", "lawUrl", "verifiedAt")) text(evidence, field);
            nullableText(evidence.get("title")); nullableText(evidence.get("effectiveDate"));
        }
        Set<String> findingCategories = new HashSet<>();
        for (JsonNode finding : result.get("findings")) {
            exact(finding, Set.of("category", "applicability", "summary", "evidenceIds", "reasoning"));
            String category = text(finding, "category");
            if (!CATEGORIES.contains(category) || !findingCategories.add(category)
                || !Set.of("APPLIES", "POSSIBLE").contains(text(finding, "applicability"))) invalid();
            text(finding, "summary"); references(finding.get("evidenceIds"), evidenceIds);
            JsonNode reasoning = finding.get("reasoning");
            exact(reasoning, Set.of("category", "inputBasis", "regulatoryArea", "obligation", "consequence",
                "requiredAction", "evidenceIds"));
            if (!category.equals(text(reasoning, "category"))) invalid();
            stringArray(reasoning.get("inputBasis"));
            for (String field : List.of("regulatoryArea", "obligation", "consequence", "requiredAction")) text(reasoning, field);
            references(reasoning.get("evidenceIds"), evidenceIds);
        }
        for (JsonNode question : result.get("requiredUserInputs")) {
            exact(question, Set.of("question", "relatedRouteIds")); text(question, "question");
            stringArray(question.get("relatedRouteIds"));
            for (JsonNode routeId : question.get("relatedRouteIds")) if (!routeIds.contains(routeId.asText())) invalid();
        }
    }

    private static void exact(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || !Set.copyOf(value.propertyNames()).equals(fields)) invalid();
    }
    private static String text(JsonNode value, String field) {
        JsonNode item = value == null ? null : value.get(field);
        if (item == null || !item.isTextual() || item.asText().isBlank()) invalid();
        return item.asText();
    }
    private static void nullableText(JsonNode value) {
        if (value != null && !value.isNull() && !value.isTextual()) invalid();
    }
    private static void array(JsonNode value, String field) {
        if (value.get(field) == null || !value.get(field).isArray()) invalid();
    }
    private static void stringArray(JsonNode values) {
        if (values == null || !values.isArray()) invalid();
        for (JsonNode value : values) if (!value.isTextual() || value.asText().isBlank()) invalid();
    }
    private static void categories(JsonNode values) {
        stringArray(values);
        for (JsonNode value : values) if (!CATEGORIES.contains(value.asText())) invalid();
    }
    private static void references(JsonNode values, Set<String> allowed) {
        stringArray(values);
        for (JsonNode value : values) if (!allowed.contains(value.asText())) invalid();
    }
    private static void invalid() {
        throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "LEGAL_SOURCE_CONTRACT_INVALID", false);
    }

    public record Result(String taskType, String sourceStatus, String registryVersion, List<Route> routes,
        List<Finding> findings, List<Evidence> evidence, List<Question> requiredUserInputs,
        List<String> sourceWarnings) { }
    public record Route(String routeId, String topic, String status, List<String> evidenceQuotes,
        String reason, List<String> categories) { }
    public record Evidence(String evidenceId, String routeId, String category, String registryVersion, String lawName, String article,
        String title, String role, String plainSummary, String whyRelevant, String excerpt,
        String effectiveDate, String lawUrl, String verifiedAt) { }
    public record Reasoning(String category, List<String> inputBasis, String regulatoryArea, String obligation,
        String consequence, String requiredAction, List<String> evidenceIds) { }
    public record Finding(String category, String applicability, String summary, List<String> evidenceIds,
        Reasoning reasoning) { }
    public record Question(String question, List<String> relatedRouteIds) { }
}
