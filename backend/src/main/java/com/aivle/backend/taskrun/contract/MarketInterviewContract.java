package com.aivle.backend.taskrun.contract;

import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

public final class MarketInterviewContract {
    private static final Set<String> ROOT = Set.of("contract", "schemaVersion", "synthetic", "participants",
        "interviews", "themes", "objections", "unmetNeeds", "purchaseTriggers", "followUpQuestions", "limitations",
        "transcriptProvenance", "codingTrace", "saturation");
    private static final Set<String> PARTICIPANT = Set.of("participantId", "label", "profile", "context", "needs");
    private static final Set<String> INTERVIEW = Set.of("participantId", "questions", "concerns", "purchaseTriggers", "objections", "unmetNeeds");
    private static final Set<String> ANSWER = Set.of("question", "answer", "uncertainty");
    private static final Set<String> THEME = Set.of("title", "description", "participantIds");
    private static final Set<String> TRANSCRIPT_PROVENANCE = Set.of("transcriptId", "participantId", "answerCount");
    private static final Set<String> CODING_ASSIGNMENT = Set.of("participantId", "themeTitles");
    private static final Set<String> SATURATION = Set.of("participantCount", "codedParticipantCount", "themeCount", "assessment", "limitation");
    private static final Pattern PARTICIPANT_ID = Pattern.compile("P[1-5]");
    private static final Pattern STATISTICAL = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?\\s*%|퍼센트|구매\\s*전환율|전국\\s*소비자|대부분의\\s*(시장|고객|소비자)|실제\\s*(사용자|고객)(들)?은)");

    private MarketInterviewContract() { }

    public static void validate(JsonNode result) {
        exact(result, ROOT);
        if (!"market-interview-result-v1".equals(text(result, "contract"))
                || !"1.0".equals(text(result, "schemaVersion"))
                || !result.path("synthetic").isBoolean() || !result.path("synthetic").asBoolean()) invalid();
        JsonNode participants = array(result, "participants", 3, 5);
        Set<String> participantIds = new HashSet<>();
        for (JsonNode participant : participants) {
            exact(participant, PARTICIPANT);
            String id = text(participant, "participantId");
            if (!PARTICIPANT_ID.matcher(id).matches() || !participantIds.add(id)) invalid();
            text(participant, "label"); text(participant, "profile"); text(participant, "context");
            stringArray(participant.get("needs"), false, 8);
        }
        JsonNode interviews = array(result, "interviews", 3, 5);
        Set<String> interviewed = new HashSet<>();
        for (JsonNode interview : interviews) {
            exact(interview, INTERVIEW);
            String id = text(interview, "participantId");
            if (!participantIds.contains(id) || !interviewed.add(id)) invalid();
            JsonNode questions = array(interview, "questions", 3, 10);
            for (JsonNode answer : questions) {
                exact(answer, ANSWER); text(answer, "question"); text(answer, "answer"); text(answer, "uncertainty");
            }
            for (String field : Set.of("concerns", "purchaseTriggers", "objections", "unmetNeeds"))
                stringArray(interview.get(field), true, 8);
        }
        if (interviews.size() != participants.size() || !interviewed.equals(participantIds)) invalid();
        JsonNode themes = array(result, "themes", 1, 12);
        for (JsonNode theme : themes) {
            exact(theme, THEME); text(theme, "title"); text(theme, "description");
            JsonNode ids = stringArray(theme.get("participantIds"), false, 6);
            for (JsonNode id : ids) if (!participantIds.contains(id.asText())) invalid();
        }
        for (String field : Set.of("objections", "unmetNeeds", "purchaseTriggers", "followUpQuestions"))
            stringArray(result.get(field), true, 12);
        stringArray(result.get("limitations"), false, 8);
        Set<String> transcriptIds = new HashSet<>();
        for (JsonNode item : array(result, "transcriptProvenance", participants.size(), participants.size())) {
            exact(item, TRANSCRIPT_PROVENANCE);
            String participantId = text(item, "participantId");
            if (!participantIds.contains(participantId) || !transcriptIds.add(participantId)
                    || !text(item, "transcriptId").equals("T-" + participantId)
                    || !integer(item, "answerCount", 3, 10)) invalid();
        }
        Set<String> codedIds = new HashSet<>();
        Set<String> themeTitles = new HashSet<>();
        themes.forEach(theme -> themeTitles.add(theme.path("title").asText()));
        for (JsonNode item : array(result, "codingTrace", participants.size(), participants.size())) {
            exact(item, CODING_ASSIGNMENT);
            String participantId = text(item, "participantId");
            if (!participantIds.contains(participantId) || !codedIds.add(participantId)) invalid();
            for (JsonNode title : stringArray(item.get("themeTitles"), true, 12))
                if (!themeTitles.contains(title.asText())) invalid();
        }
        JsonNode saturation = result.get("saturation");
        exact(saturation, SATURATION);
        if (!integer(saturation, "participantCount", participants.size(), participants.size())
                || !integer(saturation, "codedParticipantCount", 0, participants.size())
                || !integer(saturation, "themeCount", 1, 12)
                || !"EXPLORATORY_ONLY".equals(text(saturation, "assessment"))) invalid();
        text(saturation, "limitation");
        rejectStatisticalClaims(result);
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
    private static boolean integer(JsonNode value, String field, int min, int max) {
        JsonNode item = value == null ? null : value.get(field);
        return item != null && item.isIntegralNumber() && item.asInt() >= min && item.asInt() <= max;
    }
    private static void invalid() {
        throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", false);
    }
}
