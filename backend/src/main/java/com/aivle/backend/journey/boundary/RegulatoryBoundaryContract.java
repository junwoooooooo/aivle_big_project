package com.aivle.backend.journey.boundary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class RegulatoryBoundaryContract {
    private static final Set<String> RULE_TYPES = Set.of("PROHIBITED_ROLE", "PROHIBITED_ACTIVITY",
        "ALLOWED_PATTERN", "REQUIRED_CONTROL", "REQUIRED_PARTNER", "REQUIRED_DISCLOSURE", "UNRESOLVED_FACT");
    private static final Set<String> SOURCE_STATUSES = Set.of("COMPLETE", "PARTIAL", "WARNING", "UNAVAILABLE");
    private static final Set<String> TERMINAL = Set.of("READY", "NEEDS_INPUT", "BLOCKED");
    private static final Set<String> ANSWERS = Set.of("TEXT", "SINGLE_SELECT", "MULTI_SELECT", "BOOLEAN");

    private RegulatoryBoundaryContract() { }

    public static void validate(JsonNode root) {
        object(root, Set.of("taskType", "sourceStatus", "registryVersion", "routes", "evidence", "rules",
            "questions", "conflicts", "status", "userActionOptions", "sourceWarnings"));
        equal(text(root, "taskType"), "REGULATORY_BOUNDARY_GENERATION");
        member(text(root, "sourceStatus"), SOURCE_STATUSES);
        member(text(root, "status"), TERMINAL);
        text(root, "registryVersion"); arrays(root, "routes", "evidence", "rules", "questions", "conflicts",
            "userActionOptions", "sourceWarnings");

        Map<String, JsonNode> evidence = new HashMap<>();
        Set<String> evidenceDedupe = new HashSet<>();
        for (JsonNode item : root.get("evidence")) {
            object(item, Set.of("evidenceId", "sourceType", "lawName", "article", "title", "effectiveDate",
                "officialUrl", "excerpt", "plainSummary", "whyRelevant", "sourceStatus", "retrievedAt", "contentHash"));
            String id = text(item, "evidenceId");
            if (evidence.put(id, item) != null) invalid();
            for (String field : Set.of("sourceType", "lawName", "officialUrl", "excerpt", "plainSummary",
                    "whyRelevant", "retrievedAt", "contentHash")) text(item, field);
            if (!item.path("officialUrl").asText().startsWith("https://www.law.go.kr/")) invalid();
            if (!item.path("contentHash").asText().matches("sha256:[0-9a-f]{64}")) invalid();
            member(text(item, "sourceStatus"), SOURCE_STATUSES);
            String dedupe = canonical(item.path("lawName").asText()) + '|' + canonical(item.path("article").asText())
                + '|' + canonical(item.path("effectiveDate").asText()) + '|' + item.path("contentHash").asText();
            if (!evidenceDedupe.add(dedupe)) invalid();
        }

        Set<String> ruleIds = new HashSet<>();
        Set<String> ruleDedupe = new HashSet<>();
        for (JsonNode rule : root.get("rules")) {
            object(rule, Set.of("ruleId", "ruleType", "structureKey", "title", "description",
                "normalizedRequirement", "evidenceIds", "severity", "sourceStatus", "appliesWhen",
                "userFacingReason", "alternatives", "requiredQualifications", "requiredPartnerRole",
                "requiredDisclosure", "affectedBriefFields", "professionalReviewRecommended", "userActionOptions"));
            String id = text(rule, "ruleId");
            if (!ruleIds.add(id)) invalid();
            String type = text(rule, "ruleType"); member(type, RULE_TYPES);
            for (String field : Set.of("structureKey", "title", "description", "normalizedRequirement",
                    "severity", "sourceStatus", "userFacingReason")) text(rule, field);
            member(text(rule, "sourceStatus"), SOURCE_STATUSES);
            arrays(rule, "evidenceIds", "alternatives", "requiredQualifications", "affectedBriefFields", "userActionOptions");
            if (!rule.path("appliesWhen").isObject() || !rule.path("professionalReviewRecommended").isBoolean()) invalid();
            String normalized = canonical(rule.path("normalizedRequirement").asText());
            if (normalized.equals(canonical(rule.path("title").asText()))) invalid();
            if (!"UNRESOLVED_FACT".equals(type) && rule.path("evidenceIds").isEmpty()) invalid();
            for (JsonNode evidenceId : rule.path("evidenceIds")) {
                if (!evidenceId.isTextual() || !evidence.containsKey(evidenceId.asText())
                        || !"COMPLETE".equals(evidence.get(evidenceId.asText()).path("sourceStatus").asText())) invalid();
                if (normalized.equals(canonical(evidence.get(evidenceId.asText()).path("plainSummary").asText()))) invalid();
            }
            String dedupe = type + '|' + canonical(rule.path("structureKey").asText()) + '|' + normalized
                + '|' + canonical(rule.path("appliesWhen").toString());
            if (!ruleDedupe.add(dedupe)) invalid();
        }

        for (JsonNode question : root.get("questions")) {
            object(question, Set.of("questionId", "fieldKey", "question", "reason", "answerType", "options",
                "required", "relatedRuleIds", "relatedEvidenceIds"));
            for (String field : Set.of("questionId", "fieldKey", "question", "reason", "answerType")) text(question, field);
            member(question.path("answerType").asText(), ANSWERS);
            arrays(question, "options", "relatedRuleIds", "relatedEvidenceIds");
            if (!question.path("required").isBoolean()) invalid();
            references(question.path("relatedRuleIds"), ruleIds);
            references(question.path("relatedEvidenceIds"), evidence.keySet());
        }
        if (root.path("questions").size() > 4) invalid();

        for (JsonNode conflict : root.get("conflicts")) {
            object(conflict, Set.of("conflictId", "affectedFieldKey", "lockedValue", "conflictingRuleIds",
                "reason", "userActionOptions"));
            for (String field : Set.of("conflictId", "affectedFieldKey", "reason")) text(conflict, field);
            arrays(conflict, "conflictingRuleIds", "userActionOptions");
            if (conflict.path("conflictingRuleIds").isEmpty() || conflict.path("userActionOptions").isEmpty()) invalid();
            references(conflict.path("conflictingRuleIds"), ruleIds);
        }
        String status = root.path("status").asText();
        if (("BLOCKED".equals(status)) != !root.path("conflicts").isEmpty()) invalid();
        boolean unresolved = false;
        for (JsonNode rule : root.path("rules")) {
            if ("UNRESOLVED_FACT".equals(rule.path("ruleType").asText())) unresolved = true;
        }
        if ("READY".equals(status) && (!root.path("questions").isEmpty() || unresolved)) invalid();
        rejectForbidden(root);
    }

    private static void references(JsonNode values, Set<String> allowed) {
        for (JsonNode value : values) if (!value.isTextual() || !allowed.contains(value.asText())) invalid();
    }
    private static void arrays(JsonNode node, String... fields) {
        for (String field : fields) if (!node.path(field).isArray()) invalid();
    }
    private static void object(JsonNode node, Set<String> expected) {
        if (!node.isObject() || !Set.copyOf(node.propertyNames()).equals(expected)) invalid();
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) invalid();
        return value.asText();
    }
    private static String canonical(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static void member(String value, Set<String> allowed) { if (!allowed.contains(value)) invalid(); }
    private static void equal(String actual, String expected) { if (!expected.equals(actual)) invalid(); }
    private static void rejectForbidden(JsonNode node) {
        if (node.isObject()) for (String name : node.propertyNames()) {
            if (Set.of("prompt", "rawProviderBody", "providerBody", "authorization", "fullBrief").contains(name)) invalid();
            rejectForbidden(node.get(name));
        } else if (node.isArray()) node.forEach(RegulatoryBoundaryContract::rejectForbidden);
    }
    private static void invalid() { throw new IllegalArgumentException("REGULATORY_BOUNDARY_RESULT_INVALID"); }
}
