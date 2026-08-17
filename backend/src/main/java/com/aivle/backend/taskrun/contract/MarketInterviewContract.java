package com.aivle.backend.taskrun.contract;

import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Strict v2 profile-bank interview result contract. */
public final class MarketInterviewContract {
    private static final Set<String> ROOT = Set.of("contract", "schemaVersion", "synthetic", "source",
        "targeting", "participants", "interviews", "themes", "crossRelationships", "comprehension", "differentiation",
        "objections", "unmetNeeds", "purchaseTriggers", "followUpQuestions", "limitations",
        "transcriptProvenance", "codingTrace", "respondentFailures", "saturation");
    private static final Set<String> SOURCE = Set.of("marketSeedSnapshotId", "selectionId",
        "selectionRevision", "marketSeedSnapshotHash", "bmPlanRevision");
    private static final Set<String> TARGETING = Set.of("criteria", "criteriaText", "requestedSampleSize",
        "drawnSampleSize", "attemptedCount", "usableCount", "failedCount", "targetCount",
        "nonTargetCount", "targetCoverageWarning");
    private static final Set<String> CRITERIA = Set.of("ageMin", "ageMax", "genders",
        "householdSizeMin", "householdSizeMax", "regions", "incomeKeywords", "jobKeywords",
        "hasChildren", "householdRoles");
    private static final Set<String> PARTICIPANT = Set.of("participantId", "label", "profile", "context", "needs", "group");
    private static final Set<String> INTERVIEW = Set.of("participantId", "questions", "concerns", "purchaseTriggers", "objections", "unmetNeeds");
    private static final Set<String> ANSWER = Set.of("question", "answer", "uncertainty");
    private static final Set<String> THEME = Set.of("axis", "title", "description", "participantIds", "mentionCount", "targetCount", "nonTargetCount", "quote");
    private static final Set<String> CROSS = Set.of("suggestionTitle", "relatedAxis", "relatedTitle", "respondentIds", "overlapCount");
    private static final Set<String> TRANSCRIPT = Set.of("transcriptId", "participantId", "answerCount", "group");
    private static final Set<String> CODING = Set.of("participantId", "themeTitles", "comprehension", "differentiation", "alternativeLabel", "group");
    private static final Set<String> RESPONDENT_FAILURE = Set.of("participantId", "group", "attempts", "code");
    private static final Set<String> RESPONDENT_FAILURE_CODES = Set.of(
        "TRANSIENT_RETRY_EXHAUSTED", "PERMANENT_PROVIDER_FAILURE", "INVALID_RESPONDENT_OUTPUT");
    private static final Set<String> SATURATION = Set.of("participantCount", "codedParticipantCount", "themeCount",
        "axisLabelCounts", "maxMentionByAxis", "saturatedThemes", "alternativeSum", "assessment", "limitation");
    private static final Set<String> AXES = Set.of("LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION");
    private static final Pattern RESPONDENT_ID = Pattern.compile("R\\d{3}");
    // Kept byte-for-byte semantically aligned with Python service.STATISTICAL_CLAIM.
    private static final Pattern STATISTICAL = Pattern.compile(
        "(?i)((?:응답자|참여자|고객|소비자)(?:들|들\\s*중|의)?\\s*\\d+(?:\\.\\d+)?\\s*%"
        + "|\\d+(?:\\.\\d+)?\\s*%\\s*의\\s*(?:응답자|참여자|고객|소비자)"
        + "|대부분의\\s*(?:시장|고객|소비자|응답자|참여자)"
        + "|전국\\s*(?:소비자|고객|사용자)"
        + "|실제\\s*(?:사용자|고객|소비자)(?:들)?(?:은|는|이|가)"
        + "|구매\\s*확률(?:은|는|이|가)?\\s*\\d+(?:\\.\\d+)?\\s*%"
        + "|구매\\s*전환율(?:은|는|이|가)?\\s*\\d*(?:\\.\\d+)?\\s*%)");

    private MarketInterviewContract() { }

    public static void validate(JsonNode result) {
        exact(result, ROOT);
        if (!"market-interview-result-v2".equals(text(result, "contract"))
                || !"2.0".equals(text(result, "schemaVersion"))
                || !result.path("synthetic").isBoolean() || !result.path("synthetic").asBoolean()) invalid();
        source(result.get("source"));
        JsonNode targeting = result.get("targeting"); exact(targeting, TARGETING);
        criteria(targeting.get("criteria")); text(targeting, "criteriaText");
        int requested = sampleSize(targeting, "requestedSampleSize");
        int drawn = sampleSize(targeting, "drawnSampleSize");
        int attempted = integerValue(targeting, "attemptedCount", 20, 80);
        int usable = integerValue(targeting, "usableCount", 8, 80);
        int failed = integerValue(targeting, "failedCount", 0, 80);
        if (requested != drawn || drawn != attempted || usable + failed != attempted) invalid();
        int targetCount = integerValue(targeting, "targetCount", 0, 80);
        int nonTargetCount = integerValue(targeting, "nonTargetCount", 0, 80);
        if (targetCount + nonTargetCount != usable) invalid();
        nullableText(targeting, "targetCoverageWarning");

        Set<String> sampled = new HashSet<>();
        for (JsonNode item : array(result, "transcriptProvenance", usable, usable)) {
            exact(item, TRANSCRIPT);
            String id = respondentId(item, "participantId");
            if (!sampled.add(id) || !text(item, "transcriptId").equals("T-" + id)
                    || !integer(item, "answerCount", 9, 9)) invalid();
            group(item, "group");
        }

        Set<String> failedRespondents = new HashSet<>();
        for (JsonNode item : array(result, "respondentFailures", failed, failed)) {
            exact(item, RESPONDENT_FAILURE);
            String id = respondentId(item, "participantId");
            if (sampled.contains(id) || !failedRespondents.add(id)) invalid();
            group(item, "group");
            integerValue(item, "attempts", 1, 2);
            if (!RESPONDENT_FAILURE_CODES.contains(text(item, "code"))) invalid();
        }

        Set<String> representative = new HashSet<>();
        for (JsonNode item : array(result, "participants", 1, 5)) {
            exact(item, PARTICIPANT);
            String id = respondentId(item, "participantId");
            if (!sampled.contains(id) || !representative.add(id)) invalid();
            text(item, "label"); text(item, "profile"); text(item, "context");
            stringArray(item.get("needs"), true, 8); group(item, "group");
        }
        Set<String> interviewed = new HashSet<>();
        for (JsonNode item : array(result, "interviews", representative.size(), representative.size())) {
            exact(item, INTERVIEW);
            String id = respondentId(item, "participantId");
            if (!representative.contains(id) || !interviewed.add(id)) invalid();
            for (JsonNode answer : array(item, "questions", 9, 9)) {
                exact(answer, ANSWER); text(answer, "question"); text(answer, "answer"); text(answer, "uncertainty");
            }
            for (String field : Set.of("concerns", "purchaseTriggers", "objections", "unmetNeeds"))
                stringArray(item.get(field), true, 8);
        }
        if (!interviewed.equals(representative)) invalid();

        Set<String> themeTitles = new HashSet<>();
        JsonNode themes = array(result, "themes", 1, 36);
        for (JsonNode item : themes) {
            exact(item, THEME);
            if (!AXES.contains(text(item, "axis"))) invalid();
            String title = text(item, "title");
            if (!themeTitles.add(title)) invalid();
            text(item, "description"); text(item, "quote");
            JsonNode ids = stringArray(item.get("participantIds"), false, 80);
            Set<String> unique = new HashSet<>();
            for (JsonNode id : ids) if (!sampled.contains(id.asText()) || !unique.add(id.asText())) invalid();
            if (!integer(item, "mentionCount", ids.size(), ids.size())) invalid();
            int targetMentions = integerValue(item, "targetCount", 0, ids.size());
            int comparisonMentions = integerValue(item, "nonTargetCount", 0, ids.size());
            if (targetMentions + comparisonMentions != ids.size()) invalid();
        }
        for (JsonNode item : array(result, "crossRelationships", 0, 24)) {
            exact(item, CROSS); text(item, "suggestionTitle");
            if (!Set.of("CONCERN", "BARRIER").contains(text(item, "relatedAxis"))) invalid();
            text(item, "relatedTitle");
            JsonNode ids = stringArray(item.get("respondentIds"), false, 80);
            Set<String> unique = new HashSet<>();
            for (JsonNode id : ids) if (!sampled.contains(id.asText()) || !unique.add(id.asText())) invalid();
            if (!integer(item, "overlapCount", ids.size(), ids.size())) invalid();
        }

        Set<String> coded = new HashSet<>();
        for (JsonNode item : array(result, "codingTrace", usable, usable)) {
            exact(item, CODING);
            String id = respondentId(item, "participantId");
            if (!sampled.contains(id) || !coded.add(id)) invalid();
            for (JsonNode title : stringArray(item.get("themeTitles"), true, 18))
                if (!themeTitles.contains(title.asText())) invalid();
            if (!Set.of("accurate", "partial", "misunderstood").contains(text(item, "comprehension"))) invalid();
            if (!Set.of("different", "similar", "unclear").contains(text(item, "differentiation"))) invalid();
            JsonNode alternative = item.get("alternativeLabel");
            if (alternative == null || !alternative.isTextual()) invalid();
            group(item, "group");
        }
        if (!coded.equals(sampled)) invalid();
        classification(result.get("comprehension"), Set.of("accurate", "partial", "misunderstood"), usable);
        classification(result.get("differentiation"), Set.of("different", "similar", "unclear"), usable);
        for (String field : Set.of("objections", "unmetNeeds", "purchaseTriggers", "followUpQuestions"))
            stringArray(result.get(field), field.equals("followUpQuestions") ? false : true, 12);
        stringArray(result.get("limitations"), false, 8);

        JsonNode saturation = result.get("saturation"); exact(saturation, SATURATION);
        if (!integer(saturation, "participantCount", usable, usable)
                || !integer(saturation, "codedParticipantCount", 0, usable)
                || !integer(saturation, "themeCount", themes.size(), themes.size())
                || !integer(saturation, "alternativeSum", 0, usable)
                || !"EXPLORATORY_ONLY".equals(text(saturation, "assessment"))) invalid();
        axisCounts(saturation.get("axisLabelCounts"), 0, 36);
        axisCounts(saturation.get("maxMentionByAxis"), 0, usable);
        stringArray(saturation.get("saturatedThemes"), true, 36); text(saturation, "limitation");
        rejectStatisticalClaims(result);
    }

    private static void source(JsonNode value) {
        exact(value, SOURCE); text(value, "marketSeedSnapshotId");
        integerValue(value, "selectionId", 1, Integer.MAX_VALUE);
        integerValue(value, "selectionRevision", 0, Integer.MAX_VALUE);
        if (!text(value, "marketSeedSnapshotHash").matches("sha256:[0-9a-f]{64}")) invalid();
        integerValue(value, "bmPlanRevision", 0, Integer.MAX_VALUE);
    }
    private static void criteria(JsonNode value) {
        exact(value, CRITERIA);
        integerValue(value, "ageMin", 0, 120); integerValue(value, "ageMax", 0, 120);
        integerValue(value, "householdSizeMin", 0, 20); integerValue(value, "householdSizeMax", 0, 20);
        integerValue(value, "hasChildren", 0, 2);
        stringArray(value.get("genders"), true, 2); stringArray(value.get("regions"), true, 20);
        stringArray(value.get("incomeKeywords"), true, 10); stringArray(value.get("jobKeywords"), true, 15);
        stringArray(value.get("householdRoles"), true, 4);
    }
    private static void classification(JsonNode value, Set<String> fields, int total) {
        exact(value, fields); int sum = 0;
        for (String field : fields) sum += integerValue(value, field, 0, total);
        if (sum != total) invalid();
    }
    private static void axisCounts(JsonNode value, int min, int max) {
        exact(value, AXES);
        for (String axis : AXES) integerValue(value, axis, min, max);
    }
    private static int sampleSize(JsonNode value, String field) {
        int size = integerValue(value, field, 20, 80);
        if (size != 20 && size != 40 && size != 80) invalid();
        return size;
    }
    private static String respondentId(JsonNode value, String field) {
        String id = text(value, field);
        if (!RESPONDENT_ID.matcher(id).matches()) invalid();
        return id;
    }
    private static void group(JsonNode value, String field) {
        if (!Set.of("TARGET", "COMPARISON").contains(text(value, field))) invalid();
    }
    private static void nullableText(JsonNode value, String field) {
        JsonNode item = value.get(field);
        if (item == null || (!item.isNull() && (!item.isTextual() || item.asText().isBlank()))) invalid();
    }
    private static void rejectStatisticalClaims(JsonNode value) {
        if (value.isTextual() && STATISTICAL.matcher(value.asText()).find()) invalid();
        else if (value.isObject() || value.isArray()) value.forEach(MarketInterviewContract::rejectStatisticalClaims);
    }
    private static JsonNode array(JsonNode root, String field, int min, int max) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray() || value.size() < min || value.size() > max) invalid();
        return value;
    }
    private static JsonNode stringArray(JsonNode value, boolean emptyAllowed, int max) {
        if (value == null || !value.isArray() || value.size() > max || (!emptyAllowed && value.isEmpty())) invalid();
        for (JsonNode item : value) if (!item.isTextual() || item.asText().isBlank()) invalid();
        return value;
    }
    private static void exact(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || !Set.copyOf(value.propertyNames()).equals(fields)) invalid();
    }
    private static String text(JsonNode value, String field) {
        JsonNode item = value == null ? null : value.get(field);
        if (item == null || !item.isTextual() || item.asText().isBlank()) invalid();
        return item.asText();
    }
    private static int integerValue(JsonNode value, String field, int min, int max) {
        JsonNode item = value == null ? null : value.get(field);
        if (item == null || !item.isIntegralNumber() || item.asInt() < min || item.asInt() > max) invalid();
        return item.asInt();
    }
    private static boolean integer(JsonNode value, String field, int min, int max) {
        JsonNode item = value == null ? null : value.get(field);
        return item != null && item.isIntegralNumber() && item.asInt() >= min && item.asInt() <= max;
    }
    private static void invalid() {
        throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", false);
    }
}
