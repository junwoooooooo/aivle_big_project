package com.aivle.backend.taskrun.contract;

import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * MARKET_INTERVIEW 결과 계약. {@link TwinSurveyContract} 의 형제다.
 *
 * <p><b>이 검증기가 지키는 것 중 셋은 스키마가 아니라 경계다.</b>
 *
 * <ol>
 *   <li>{@code caveats} 는 <b>비어 있으면 안 된다</b>. 이 저장소가 경계를 강제하는 방식은
 *       배너가 아니라 값과 같은 자리에 실린 데이터이고, 빈 배열은 「경계가 없다」가 아니라
 *       「경계를 떨어뜨렸다」다.</li>
 *   <li><b>백분율 칸이 없다.</b> 이 조사가 내는 수치는 {@code mentionCount}(사람 수)뿐이다.
 *       비율 칸을 계약에 열어 두면 언젠가 채워지고, 채워지는 순간 이 조사가 답하지 못하는
 *       것을 답한 것이 된다.</li>
 *   <li>{@code mentionCount} 는 <b>표본을 넘을 수 없다</b>. 넘으면 LLM 이 센 숫자가 실린
 *       것이고, 세는 일은 AI 쪽에서도 코드가 하기로 돼 있다.</li>
 * </ol>
 *
 * <p>{@code unclassified} 가 0 이 아닌 것은 실패가 아니다 — 이해도 판정을 못 받은 사람을
 * 조용히 「부분 이해」로 밀어 넣지 않기로 한 결과다.
 *
 * <p><b>2026-08-13 — 40/40 이후 늘어난 검사 둘.</b> n=40 실행에서 모든 주제가 40/40 으로
 * 나왔고 대안 3개가 <b>동시에</b> 40/40 이었다(한 사람이 셋을 다 한다는 뜻이라 성립할 수
 * 없다). 그런데 그 결과가 이 계약을 <b>통과했다</b>. 그래서 둘을 더 건다.
 *
 * <ol>
 *   <li>{@code mentionCount} 는 {@code respondentIds} 의 <b>길이와 정확히 같아야 한다</b>.
 *       AI 가 센 수를 받지 않는다 — 명단이 근거다.</li>
 *   <li>{@code alternatives} 의 언급 수 <b>합계가 답한 사람 수를 넘을 수 없다</b>.
 *       1인 1대안이고, 넘는다는 것은 코딩이 사람을 복제했다는 뜻이다.</li>
 * </ol>
 */
public final class MarketInterviewContract {

    private static final Set<String> ENVELOPE = Set.of(
        "conceptBoard", "sampleSize", "sampling", "targeting", "comprehension",
        "differentiation", "themes", "alternatives", "segments", "contrast",
        "suggestionLinks", "interviews", "transcripts", "telemetry", "caveats", "notes");

    private static final Set<String> BOARD = Set.of(
        "conceptName", "targetUsers", "problemScenario", "featureSet",
        "differentiators", "priceKrw", "rendered");

    private static final Set<String> TARGETING = Set.of(
        "criteria", "criteriaText", "targetRequested", "nonTargetRequested",
        "targetDrawn", "nonTargetDrawn", "shortfall",
        "targetShortCells", "nonTargetShortCells");

    /**
     * 타겟 술어. <b>{@code ai/app/interview/targeting.TargetCriteria} 와 정확히 같은 집합</b>
     * 이어야 한다 — 한쪽만 늘리면 결과가 통째로 거부된다.
     *
     * <p>{@code hasChildren}·{@code householdRoles} 는 2026-08-15 에 붙었다. 「초등 저학년
     * 자녀를 둔 맞벌이 부모」가 거를 칸이 없어 직업 키워드로 밀려났고, 뱅크에 「맞벌이」가
     * 0회라 타겟이 0명이 된 판에서 왔다. 둘은 <b>한 쌍</b>이다 — 자녀 있는 가구의 27% 가
     * 그 집 자녀 본인이라 세대구성만 보면 자녀가 부모로 잡힌다.
     */
    private static final Set<String> CRITERIA = Set.of(
        "ageMin", "ageMax", "genders", "householdSizeMin", "householdSizeMax",
        "regions", "incomeKeywords", "jobKeywords", "hasChildren", "householdRoles");

    private static final Set<String> COMPREHENSION = Set.of(
        "accurate", "partial", "misunderstood", "unclassified", "misreadPoints");

    private static final Set<String> DIFFERENTIATION = Set.of(
        "different", "similar", "unclear", "unclassified");

    private static final Set<String> THEME = Set.of(
        "axis", "label", "mentionCount", "respondentIds", "resolvedCount", "quote");
    private static final Set<String> ALTERNATIVE = Set.of("label", "mentionCount");

    private static final Set<String> SEGMENT = Set.of("axis", "label", "mentionCount", "breakdown");
    private static final Set<String> BREAKDOWN = Set.of("dimension", "buckets");
    private static final Set<String> BUCKET = Set.of("label", "count");
    private static final Set<String> CONTRAST = Set.of(
        "axis", "label", "targetCount", "nonTargetCount");
    private static final Set<String> SUGGESTION_LINK = Set.of("label", "mentionCount", "links");
    private static final Set<String> LINK = Set.of("axis", "label", "overlapCount");

    /** 고정 9문항과 1:1이다. 문항이 늘면 여기와 프롬프트를 <b>같이</b> 고친다. */
    private static final Set<String> INTERVIEW = Set.of(
        "comprehension", "profile", "firstImpression", "restatement", "like", "concern",
        "differentiation", "relevance", "usageScene", "barrier", "suggestion");
    private static final List<String> INTERVIEW_ANSWERS = List.of(
        "firstImpression", "restatement", "like", "concern", "differentiation",
        "relevance", "usageScene", "barrier", "suggestion");

    /** 전원 응답. 대표 카드에 {@code id}·{@code target} 두 칸이 더 붙은 모양이다. */
    private static final Set<String> TRANSCRIPT = Set.of(
        "id", "target", "profile", "firstImpression", "restatement", "like", "concern",
        "differentiation", "relevance", "usageScene", "barrier", "suggestion");

    private static final Set<String> PROFILE = Set.of(
        "age", "gender", "household", "region", "income", "job");

    private static final Set<String> AXES = Set.of(
        "LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION");
    private static final Set<String> COMPREHENSION_LEVELS = Set.of(
        "accurate", "partial", "misunderstood");

    /** 정성 조사의 표준 n(8~20)에 맞춘 세 값. DB CHECK·화면 슬라이더와 맞물려 있다. */
    private static final Set<Integer> SAMPLE_SIZES = Set.of(20, 40, 80);
    private static final int INTERVIEWS_MAX = 5;
    private static final int TRANSCRIPTS_MAX = 80;
    private static final int THEMES_MAX = 36;
    private static final int ALTERNATIVES_MAX = 12;
    private static final int SEGMENTS_MAX = 8;

    private static final Set<String> TELEMETRY_REQUIRED = Set.of(
        "cells", "rateLimited", "timeouts", "retries", "formatViolations", "failures",
        "truncated", "waitSeconds", "promptTokens", "completionTokens",
        "model", "concurrency", "seconds", "llmCalls", "answered", "homogeneity");

    /** 포화 진단. 여기도 <b>사람 수와 이름표 수뿐</b>이다 — 비율 칸을 열지 않는다. */
    private static final Set<String> HOMOGENEITY = Set.of(
        "axisLabelCounts", "maxMentionByAxis", "saturatedThemes", "alternativeSum");

    private MarketInterviewContract() { }

    public static void validate(JsonNode result) {
        exact(result, ENVELOPE);

        JsonNode sampleSize = result.get("sampleSize");
        if (sampleSize == null || !sampleSize.isIntegralNumber()
            || !SAMPLE_SIZES.contains(sampleSize.asInt())) invalid();
        int n = sampleSize.asInt();

        board(result.get("conceptBoard"));
        sampling(result.get("sampling"));
        targeting(result.get("targeting"));
        telemetry(result.get("telemetry"));
        comprehension(result.get("comprehension"), n);
        counts(result.get("differentiation"), DIFFERENTIATION, n);
        themes(result.get("themes"), n);
        alternatives(result.get("alternatives"), n, answered(result));
        segments(result.get("segments"), n);
        contrast(result.get("contrast"), n);
        suggestionLinks(result.get("suggestionLinks"), n);
        interviews(result.get("interviews"));
        transcripts(result.get("transcripts"));

        // ⚠ 여기가 경계다. 빈 배열은 「경계 없음」이 아니라 「경계 소실」이다.
        nonEmptyStringArray(result.get("caveats"));
        nonEmptyStringArray(result.get("notes"));
    }

    private static int answered(JsonNode result) {
        return result.get("telemetry").get("answered").asInt();
    }

    /**
     * 응답자가 실제로 본 것. {@code rendered} 를 계약에 넣은 이유는 화면이 「무엇을 보여주고
     * 얻은 답인가」를 그대로 보일 수 있어야 하기 때문이다 — 자극을 못 보는 조사 결과는
     * 해석할 수 없다.
     */
    private static void board(JsonNode board) {
        exact(board, BOARD);
        text(board, "conceptName");
        text(board, "rendered");
        for (String field : List.of("targetUsers", "problemScenario", "differentiators")) {
            JsonNode value = board.get(field);
            if (value == null || !value.isTextual()) invalid();
        }
        stringArray(board.get("featureSet"));
        JsonNode price = board.get("priceKrw");
        if (price == null) invalid();
        // 실수는 거부한다 — 가격은 원 단위 정수이거나 «미정»(null)이다.
        if (!price.isNull() && (!price.isIntegralNumber() || price.asLong() < 0)) invalid();
    }

    /**
     * ⚠ {@code shortCells} 의 값은 정수가 아니라 {@code {quota, available}} 객체다
     * ({@code ai/app/twin/bank.py:117}). {@link TwinSurveyContract#validate} 는 이 자리를
     * 정수로 검사하고 있어서, 층이 얕아 목표를 못 채우는 실행을 계약이 거절한다 —
     * 젊은 층이 마르는 큰 표본에서만 드러나는 잠복 결함이다. 여기서는 반복하지 않는다.
     */
    private static void sampling(JsonNode sampling) {
        exact(sampling, Set.of("requested", "drawn", "strata", "shortCells"));
        nonNegativeInteger(sampling, "requested");
        nonNegativeInteger(sampling, "drawn");
        JsonNode strata = sampling.get("strata");
        if (strata == null || !strata.isObject()) invalid();
        for (String name : strata.propertyNames()) nonNegativeInteger(strata, name);
        shortCells(sampling.get("shortCells"));
    }

    private static void shortCells(JsonNode shortCells) {
        if (shortCells == null || !shortCells.isObject()) invalid();
        for (String name : shortCells.propertyNames()) {
            JsonNode cell = shortCells.get(name);
            exact(cell, Set.of("quota", "available"));
            nonNegativeInteger(cell, "quota");
            nonNegativeInteger(cell, "available");
        }
    }

    /**
     * 타겟 조건식. <b>조건식을 계약에 넣은 이유는 화면에 보이기 위해서다</b> —
     * 자유 서술(「맞벌이 부모」)을 기계 술어로 옮긴 것이라 틀릴 수 있고, 틀렸는지 아는 사람은
     * 사용자뿐이다. 안 보이면 조용히 엉뚱한 사람들에게 물은 조사가 된다.
     */
    private static void targeting(JsonNode targeting) {
        exact(targeting, TARGETING);
        exact(targeting.get("criteria"), CRITERIA);
        text(targeting, "criteriaText");
        for (String field : List.of("targetRequested", "nonTargetRequested",
                                    "targetDrawn", "nonTargetDrawn", "shortfall")) {
            nonNegativeInteger(targeting, field);
        }
        for (String field : List.of("targetShortCells", "nonTargetShortCells")) {
            shortCells(targeting.get(field));
        }
        JsonNode criteria = targeting.get("criteria");
        for (String field : List.of("ageMin", "ageMax", "householdSizeMin", "householdSizeMax",
                                    "hasChildren")) {
            nonNegativeInteger(criteria, field);
        }
        for (String field : List.of("genders", "regions", "incomeKeywords", "jobKeywords",
                                    "householdRoles")) {
            stringArray(criteria.get(field));
        }
    }

    private static void telemetry(JsonNode telemetry) {
        exact(telemetry, TELEMETRY_REQUIRED);
        text(telemetry, "model");
        for (String field : TELEMETRY_REQUIRED) {
            if (!"model".equals(field) && !"homogeneity".equals(field)) {
                nonNegativeNumber(telemetry, field);
            }
        }
        JsonNode homogeneity = telemetry.get("homogeneity");
        exact(homogeneity, HOMOGENEITY);
        for (String field : List.of("axisLabelCounts", "maxMentionByAxis")) {
            JsonNode counts = homogeneity.get(field);
            if (counts == null || !counts.isObject()) invalid();
            for (String axis : counts.propertyNames()) {
                if (!AXES.contains(axis)) invalid();
                nonNegativeInteger(counts, axis);
            }
        }
        stringArray(homogeneity.get("saturatedThemes"));
        nonNegativeInteger(homogeneity, "alternativeSum");
    }

    /** 네 칸의 합이 답을 낸 사람 수다. 표본보다 크면 어딘가에서 사람이 늘어난 것이다. */
    private static void comprehension(JsonNode value, int sampleSize) {
        exact(value, COMPREHENSION);
        long total = 0;
        for (String field : List.of("accurate", "partial", "misunderstood", "unclassified")) {
            nonNegativeInteger(value, field);
            total += value.get(field).asLong();
        }
        if (total > sampleSize) invalid();
        stringArray(value.get("misreadPoints"));
    }

    private static void themes(JsonNode themes, int sampleSize) {
        if (themes == null || !themes.isArray() || themes.size() > THEMES_MAX) invalid();
        Set<String> seen = new HashSet<>();
        for (JsonNode theme : themes) {
            exact(theme, THEME);
            String axis = text(theme, "axis");
            if (!AXES.contains(axis)) invalid();
            String label = text(theme, "label");
            // 같은 축에 같은 이름표가 둘이면 화면이 같은 막대를 두 번 그린다.
            if (!seen.add(axis + " " + label)) invalid();
            mentionCount(theme, sampleSize);
            nullableText(theme.get("quote"));

            // ⚠ 이 두 줄이 이번 개편의 핵심 검사다. AI 가 센 수를 받지 않는다 — 명단이 근거이고,
            //    그 명단 위에 세그먼트 교차와 제안↔우려 연결표가 선다.
            Set<String> ids = respondentIds(theme.get("respondentIds"), sampleSize);
            if (ids.size() != theme.get("mentionCount").asInt()) invalid();

            nonNegativeInteger(theme, "resolvedCount");
            if (theme.get("resolvedCount").asInt() > ids.size()) invalid();
        }
    }

    private static Set<String> respondentIds(JsonNode ids, int sampleSize) {
        stringArray(ids);
        Set<String> unique = new HashSet<>();
        for (JsonNode id : ids) unique.add(id.asText());
        // 같은 사람이 두 번 들면 언급 수가 부풀고, 표본보다 많으면 없는 사람이 들어온 것이다.
        if (unique.size() != ids.size() || unique.size() > sampleSize) invalid();
        return unique;
    }

    /** 배타 분류의 칸들. 합이 표본을 넘으면 어딘가에서 사람이 늘어난 것이다. */
    private static void counts(JsonNode value, Set<String> fields, int sampleSize) {
        exact(value, fields);
        long total = 0;
        for (String field : fields) {
            nonNegativeInteger(value, field);
            total += value.get(field).asLong();
        }
        if (total > sampleSize) invalid();
    }

    /** 각 축의 버킷 합이 언급 수와 다르면 화면의 두 수가 어긋난다. */
    private static void segments(JsonNode segments, int sampleSize) {
        if (segments == null || !segments.isArray() || segments.size() > SEGMENTS_MAX) invalid();
        for (JsonNode segment : segments) {
            exact(segment, SEGMENT);
            if (!AXES.contains(text(segment, "axis"))) invalid();
            text(segment, "label");
            mentionCount(segment, sampleSize);
            JsonNode breakdown = segment.get("breakdown");
            if (breakdown == null || !breakdown.isArray() || breakdown.isEmpty()) invalid();
            for (JsonNode dimension : breakdown) {
                exact(dimension, BREAKDOWN);
                text(dimension, "dimension");
                JsonNode buckets = dimension.get("buckets");
                if (buckets == null || !buckets.isArray() || buckets.isEmpty()) invalid();
                long total = 0;
                for (JsonNode bucket : buckets) {
                    exact(bucket, BUCKET);
                    text(bucket, "label");
                    nonNegativeInteger(bucket, "count");
                    total += bucket.get("count").asLong();
                }
                if (total != segment.get("mentionCount").asLong()) invalid();
            }
        }
    }

    /** 타겟 + 비타겟이 언급 수다. <b>둘을 나누지 않는다</b> — 분모가 다른 두 수다. */
    private static void contrast(JsonNode rows, int sampleSize) {
        if (rows == null || !rows.isArray() || rows.size() > THEMES_MAX) invalid();
        for (JsonNode row : rows) {
            exact(row, CONTRAST);
            if (!AXES.contains(text(row, "axis"))) invalid();
            text(row, "label");
            nonNegativeInteger(row, "targetCount");
            nonNegativeInteger(row, "nonTargetCount");
            if (row.get("targetCount").asLong() + row.get("nonTargetCount").asLong() > sampleSize) {
                invalid();
            }
        }
    }

    /** 연결의 근거는 「같은 사람이 둘 다 말했다」뿐이라 겹친 수가 제안 언급 수를 넘을 수 없다. */
    private static void suggestionLinks(JsonNode rows, int sampleSize) {
        if (rows == null || !rows.isArray() || rows.size() > THEMES_MAX) invalid();
        for (JsonNode row : rows) {
            exact(row, SUGGESTION_LINK);
            text(row, "label");
            mentionCount(row, sampleSize);
            JsonNode links = row.get("links");
            if (links == null || !links.isArray() || links.size() > THEMES_MAX) invalid();
            for (JsonNode link : links) {
                exact(link, LINK);
                if (!AXES.contains(text(link, "axis"))) invalid();
                text(link, "label");
                nonNegativeInteger(link, "overlapCount");
                long overlap = link.get("overlapCount").asLong();
                if (overlap == 0 || overlap > row.get("mentionCount").asLong()) invalid();
            }
        }
    }

    /**
     * 1인 1대안이라 <b>합계가 답한 사람 수를 넘을 수 없다</b>.
     *
     * <p>2026-08-12 에 대안 3개가 <b>동시에</b> 40/40 으로 나왔고 이 계약이 그것을 통과시켰다.
     * 한 사람이 「가끔 요리한다」와 「배달로 해결한다」와 「그냥 참는다」를 동시에 할 수는 없다.
     */
    private static void alternatives(JsonNode alternatives, int sampleSize, int answered) {
        if (alternatives == null || !alternatives.isArray()
            || alternatives.size() > ALTERNATIVES_MAX) invalid();
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (JsonNode alternative : alternatives) {
            exact(alternative, ALTERNATIVE);
            if (!seen.add(text(alternative, "label"))) invalid();
            mentionCount(alternative, sampleSize);
            total += alternative.get("mentionCount").asLong();
        }
        if (total > answered) invalid();
    }

    /** 0 이면 그 주제는 애초에 실리지 말았어야 한다. 표본을 넘으면 센 주체가 코드가 아니다. */
    private static void mentionCount(JsonNode value, int sampleSize) {
        nonNegativeInteger(value, "mentionCount");
        long count = value.get("mentionCount").asLong();
        if (count == 0 || count > sampleSize) invalid();
    }

    /**
     * 대표 응답자 카드. 여기서 두 가지를 막는다. 하나는 <b>모르는 필드</b> — 카드 원문이
     * 통째로 실려 오는 회귀를 막는다(카드는 재배포 금지 자산이고 학력·혼인·심리척도까지
     * 들어 있다). 다른 하나는 <b>말 없는 카드</b> — 9칸이 전부 비면 화면에 얼굴만 앉는다.
     */
    private static void interviews(JsonNode items) {
        if (items == null || !items.isArray() || items.size() > INTERVIEWS_MAX) invalid();
        for (JsonNode item : items) {
            exact(item, INTERVIEW);
            if (!COMPREHENSION_LEVELS.contains(text(item, "comprehension"))) invalid();
            answers(item);
            profile(item.get("profile"));
        }
    }

    /**
     * 전원 응답 — 검증 통로다. 사람 수가 대표 카드의 16배라 <b>유출 검사도 같이 넓혔다</b>:
     * 여기도 정확 집합이라 {@code pid_hash} 든 카드 원문이든 새 칸으로는 못 들어온다.
     */
    private static void transcripts(JsonNode items) {
        if (items == null || !items.isArray() || items.size() > TRANSCRIPTS_MAX) invalid();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            exact(item, TRANSCRIPT);
            if (!seen.add(text(item, "id"))) invalid();
            JsonNode target = item.get("target");
            if (target == null || !target.isBoolean()) invalid();
            answers(item);
            profile(item.get("profile"));
        }
    }

    /** 9칸이 전부 비면 화면에 얼굴만 앉는다. 한 칸이라도 말했어야 한다. */
    private static void answers(JsonNode item) {
        boolean spoke = false;
        for (String field : INTERVIEW_ANSWERS) {
            JsonNode answer = item.get(field);
            nullableText(answer);
            spoke |= answer != null && answer.isTextual() && !answer.asText().isBlank();
        }
        if (!spoke) invalid();
    }

    private static void profile(JsonNode profile) {
        exact(profile, PROFILE);
        JsonNode age = profile.get("age");
        if (age != null && !age.isNull() && (!age.isIntegralNumber() || age.asInt() < 0)) invalid();
        for (String field : List.of("gender", "household", "region", "income", "job")) {
            nullableText(profile.get(field));
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
    private static void nonNegativeNumber(JsonNode value, String field) {
        JsonNode item = value == null ? null : value.get(field);
        if (item == null || !item.isNumber() || item.asDouble() < 0) invalid();
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
