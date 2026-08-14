package com.aivle.backend.taskrun.contract;

import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * TWIN_SURVEY 결과 계약. {@link MarketResearchContract} 의 형제다.
 *
 * <p><b>이 검증기가 지키는 것 중 둘은 스키마가 아니라 판매 경계다.</b>
 *
 * <ol>
 *   <li>{@code taskType} 은 외적 타당성 시험에서 성적이 난 두 유형뿐이다.
 *       윤리·가치형과 미묘한 우열형은 AI 쪽 게이트가 LLM 호출 0회로 막지만,
 *       그 게이트가 회귀하면 <b>여기서 막힌다</b>. 성적이 없는 유형의 답이 DB 에
 *       들어가는 순간 그것은 근거 없는 수치가 된다.</li>
 *   <li>{@code caveats} 는 쌍마다 <b>비어 있으면 안 된다</b>. 이 저장소가 경계를
 *       강제하는 방식은 배너가 아니라 값과 같은 자리에 실린 데이터이고,
 *       빈 배열은 「경계가 없다」가 아니라 「경계를 떨어뜨렸다」다.</li>
 * </ol>
 *
 * <p>{@code winner} 가 {@code null} 이거나 {@code TIE} 인 것은 실패가 아니다 —
 * 「못 잼」이 정직한 산출이라 계약이 그것을 허용한다.
 */
public final class TwinSurveyContract {

    private static final Set<String> ENVELOPE = Set.of(
        "situation", "sampleSize", "sampling", "pairs", "telemetry", "notes");

    private static final Set<String> PAIR = Set.of(
        "pairId", "taskType", "taskTypeReason", "labels", "profiles",
        "winner", "winnerLabel", "measurable", "decisionReason",
        "deltaAvg", "confidenceInterval", "positionComponent",
        "contentShare", "contentShareLower", "mde",
        "nPaired", "nRespondents", "respondentClasses", "interviews", "caveats");

    private static final Set<String> INTERVIEW = Set.of("choice", "profile", "quote");
    private static final Set<String> PROFILE = Set.of(
        "age", "gender", "household", "region", "income", "job");
    private static final Set<String> INTERVIEW_CHOICES = Set.of("X", "Y", "UNDECIDED");
    private static final int INTERVIEWS_MAX = 5;

    /**
     * 팔 수 있는 유형. <b>2026-08-10 부터 우열형 하나뿐이다.</b>
     *
     * <p>가격형은 계기 재측정에서 방향이 반전됐다 — 같은 25명에게 같은 자극을 물었는데
     * CLI +0.23 / gpt-4o-mini −0.68 / gpt-5.6-terra +1.00 이었다(B3). 지불의사의 임계는
     * 응답자가 아니라 실행 모델이 가진 값이라, 모델을 고르는 문제로 풀리지 않는다.
     */
    private static final Set<String> SERVICEABLE_TASK_TYPES = Set.of("DOMINANCE");
    private static final Set<String> WINNERS = Set.of("X", "Y", "TIE");
    /**
     * 응답자 분류는 다섯이고 <b>나온 것만 실린다</b>({@code Counter} → dict).
     * 여기를 {@code exact()} 로 못박았다가 실스택에서 걸렸다 — 전원 미결정인 실행은
     * {@code undecided} 하나만 싣는다. 그건 실패가 아니라 실제로 일어나는 결과다.
     */
    private static final Set<String> RESPONDENT_CLASSES = Set.of(
        "content_X", "content_Y", "position_driven", "anti_position", "undecided");
    private static final Set<Integer> SAMPLE_SIZES = Set.of(50, 100, 300);

    private static final Set<String> TELEMETRY_REQUIRED = Set.of(
        "cells", "rateLimited", "timeouts", "retries", "formatViolations", "failures",
        "truncated", "waitSeconds", "promptTokens", "completionTokens",
        "wave2Cells", "model", "requestFingerprint", "concurrency", "seconds", "llmCalls");
    /** 예산이 말라 2파를 건너뛴 경우에만 실린다 — 없는 것이 정상이다. */
    private static final Set<String> TELEMETRY_OPTIONAL = Set.of("wave2Skipped");

    private TwinSurveyContract() { }

    public static void validate(JsonNode result) {
        exact(result, ENVELOPE);
        text(result, "situation");
        JsonNode sampleSize = result.get("sampleSize");
        if (sampleSize == null || !sampleSize.isIntegralNumber()
            || !SAMPLE_SIZES.contains(sampleSize.asInt())) invalid();
        sampling(result.get("sampling"));
        telemetry(result.get("telemetry"));
        nonEmptyStringArray(result.get("notes"));

        JsonNode pairs = result.get("pairs");
        if (pairs == null || !pairs.isArray() || pairs.isEmpty() || pairs.size() > 4) invalid();
        Set<String> seen = new HashSet<>();
        for (JsonNode pair : pairs) {
            if (!seen.add(pair(pair))) invalid();
        }
    }

    private static void sampling(JsonNode sampling) {
        exact(sampling, Set.of("requested", "drawn", "strata", "shortCells"));
        nonNegativeInteger(sampling, "requested");
        nonNegativeInteger(sampling, "drawn");
        for (String field : List.of("strata", "shortCells")) {
            JsonNode counts = sampling.get(field);
            if (counts == null || !counts.isObject()) invalid();
            for (String name : counts.propertyNames()) nonNegativeInteger(counts, name);
        }
    }

    /**
     * 계측은 <b>필수 키 집합 + 선택 키</b>다. {@code exact()} 를 못 쓰는 이유는
     * {@code wave2Skipped} 가 예산 고갈 때만 실리기 때문이다 — 그 자리를 항상 채우게 하면
     * 「건너뛴 적 없음」과 「0개 건너뜀」이 같아진다.
     */
    private static void telemetry(JsonNode telemetry) {
        if (telemetry == null || !telemetry.isObject()) invalid();
        Set<String> present = Set.copyOf(telemetry.propertyNames());
        if (!present.containsAll(TELEMETRY_REQUIRED)) invalid();
        for (String name : present) {
            if (!TELEMETRY_REQUIRED.contains(name) && !TELEMETRY_OPTIONAL.contains(name)) invalid();
        }
        text(telemetry, "model");
        text(telemetry, "requestFingerprint");
        for (String field : List.of("cells", "rateLimited", "timeouts", "retries", "formatViolations",
            "failures", "truncated", "waitSeconds", "promptTokens", "completionTokens",
            "wave2Cells", "concurrency", "seconds")) nonNegativeNumber(telemetry, field);
        nonNegativeNumber(telemetry, "llmCalls");
        if (present.contains("wave2Skipped")) nonNegativeNumber(telemetry, "wave2Skipped");
    }

    /** @return pairId — 호출자가 중복을 센다. */
    private static String pair(JsonNode pair) {
        exact(pair, PAIR);
        String pairId = text(pair, "pairId");
        if (!SERVICEABLE_TASK_TYPES.contains(text(pair, "taskType"))) invalid();
        text(pair, "taskTypeReason");
        text(pair, "decisionReason");

        for (String side : List.of("labels", "profiles")) {
            JsonNode value = pair.get(side);
            exact(value, Set.of("X", "Y"));
            text(value, "X");
            text(value, "Y");
        }

        JsonNode winner = pair.get("winner");
        if (winner == null || (!winner.isNull() && !WINNERS.contains(winner.asText()))) invalid();
        nullableText(pair.get("winnerLabel"));
        // 이긴 쪽이 있으면 그 이름표도 있어야 한다. 없으면 화면이 «X» 라고만 쓰게 된다.
        boolean decided = !winner.isNull() && !"TIE".equals(winner.asText());
        JsonNode winnerLabel = pair.get("winnerLabel");
        if (decided == (winnerLabel == null || winnerLabel.isNull())) invalid();

        JsonNode measurable = pair.get("measurable");
        if (measurable == null || !measurable.isBoolean()) invalid();
        // 「못 잼」인데 이긴 쪽이 있으면 두 문장이 서로를 부정한다.
        if (measurable.asBoolean() != decided) invalid();

        for (String field : List.of("deltaAvg", "positionComponent", "contentShare",
            "contentShareLower", "mde")) number(pair, field);
        nonNegativeInteger(pair, "nPaired");
        nonNegativeInteger(pair, "nRespondents");

        JsonNode interval = pair.get("confidenceInterval");
        if (interval != null && !interval.isNull()) {
            exact(interval, Set.of("low", "high"));
            number(interval, "low");
            number(interval, "high");
            if (interval.get("low").asDouble() > interval.get("high").asDouble()) invalid();
        }

        JsonNode classes = pair.get("respondentClasses");
        if (classes == null || !classes.isObject()) invalid();
        for (String name : classes.propertyNames()) {
            if (!RESPONDENT_CLASSES.contains(name)) invalid();
            nonNegativeInteger(classes, name);
        }

        interviews(pair.get("interviews"));
        // ⚠ 여기가 경계다. 빈 배열은 「경계 없음」이 아니라 「경계 소실」이다.
        nonEmptyStringArray(pair.get("caveats"));
        return pairId;
    }

    /**
     * 대표 응답자 인터뷰. 화면이 «사람의 말»로 답하는 자리다.
     *
     * <p>여기서 두 가지를 막는다. 하나는 <b>빈 인용</b> — 프로필만 있고 말이 없으면
     * 화면에 얼굴만 앉는다. 다른 하나는 <b>모르는 필드</b> — 카드 원문이 통째로 실려 오는
     * 회귀를 막는다(카드는 재배포 금지 자산이고 학력·혼인·심리척도까지 들어 있다).
     */
    private static void interviews(JsonNode items) {
        if (items == null || !items.isArray() || items.size() > INTERVIEWS_MAX) invalid();
        for (JsonNode item : items) {
            exact(item, INTERVIEW);
            if (!INTERVIEW_CHOICES.contains(text(item, "choice"))) invalid();
            text(item, "quote");
            JsonNode profile = item.get("profile");
            exact(profile, PROFILE);
            JsonNode age = profile.get("age");
            if (age != null && !age.isNull() && (!age.isIntegralNumber() || age.asInt() < 0)) invalid();
            for (String field : List.of("gender", "household", "region", "income", "job")) {
                nullableText(profile.get(field));
            }
        }
    }

    // ── 원시 검사 ────────────────────────────────────────────────────────
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
    private static void number(JsonNode value, String field) {
        JsonNode item = value == null ? null : value.get(field);
        if (item == null || !item.isNumber()) invalid();
    }
    private static void nonNegativeNumber(JsonNode value, String field) {
        number(value, field);
        if (value.get(field).asDouble() < 0) invalid();
    }
    private static void nonNegativeInteger(JsonNode value, String field) {
        JsonNode item = value == null ? null : value.get(field);
        if (item == null || !item.isIntegralNumber() || item.asLong() < 0) invalid();
    }
    private static void stringArray(JsonNode values) {
        if (values == null || !values.isArray()) invalid();
        for (JsonNode value : values) if (!value.isTextual() || value.asText().isBlank()) invalid();
    }
    private static void nonEmptyStringArray(JsonNode values) {
        stringArray(values);
        if (values.isEmpty()) invalid();
    }
    private static void invalid() {
        throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", false);
    }
}
