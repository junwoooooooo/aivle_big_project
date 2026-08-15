package com.aivle.backend.taskrun.contract;

import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * MARKET_RESEARCH 결과 계약. {@link LegalSourcePipelineContract} 의 형제다.
 *
 * <p>한 TaskType 에 <b>두 모드</b>가 있다.
 * <ul>
 *   <li>{@code FULL} — 1단계. 7과목 성적표 + 근거 원장</li>
 *   <li>{@code BM}   — 2단계. 캔버스 9칸 + BM 판정</li>
 * </ul>
 * 봉투(최상위 필드 집합)는 <b>두 모드가 같고</b>, 해당 없는 칸은 {@code null} 이다.
 * 그래야 {@code exact()} 한 번으로 봉투를 못박을 수 있다.
 *
 * <p><b>이 검증기가 지키는 것 중 하나는 스키마가 아니라 경계다.</b>
 * 칸이 인용한 근거에 {@code caveats} 가 있으면 <b>그 문장이 칸에도 있어야 한다</b>.
 * 실측된 결함이 있다 — BM 모델이 경계를 최종 문장에 안 싣는다(0/2). 그래서 AI 쪽에서
 * 기계로 파생하고, 여기서 <b>한 번 더</b> 막는다. AI 쪽 회귀가 DB 에 못 들어오게.
 */
public final class MarketResearchContract {

    private static final Set<String> ENVELOPE = Set.of(
        "runId", "conceptId", "asOf", "generatedAt", "mode",
        "stages", "degradations",
        "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes");

    private static final Set<String> MODES = Set.of("FULL", "BM");
    private static final Set<String> STAGE_STATES = Set.of("OK", "SKIPPED", "FAILED");
    private static final Set<String> GRADES = Set.of("확정", "실무 신뢰", "추정", "근거 없음");
    private static final Set<String> EVIDENCE_KINDS = Set.of("관측", "계산");
    /**
     * 계산식 한 항의 «판정». AI 쪽 {@code verdict.FACTOR_BASES} 와 같은 목록이어야 한다.
     *
     * <p>{@code 가설} 은 <b>우리가 정한 값</b>이다(가격 등). {@code 가정} 은 규칙 파일이
     * 값과 근거를 함께 선언한 것이고, {@code 관측} 은 원장에 출처가 있다. 셋을 한 칸에
     * 뭉치면 「우리가 정한 39,000원」과 「국가통계 115,310개」가 같은 무게로 읽힌다.
     */
    private static final Set<String> FACTOR_BASES = Set.of("관측", "가정", "가설");

    private static final Set<String> SUBJECTS = Set.of(
        "MARKET_SIZE", "GROWTH", "COMPETITOR", "PRICE", "DEMAND", "CALCULATION", "NOT_FOUND");
    private static final Set<String> SCORE_STATES = Set.of("FILLED", "PARTIAL", "MISSING", "REPORTED");

    private static final Set<String> CANVAS_CELLS = Set.of(
        "CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "CUSTOMER_RELATIONSHIPS",
        "REVENUE_STREAMS", "KEY_RESOURCES", "KEY_ACTIVITIES", "KEY_PARTNERS", "COST_STRUCTURE");
    private static final Set<String> CANVAS_STATUSES = Set.of(
        "VERIFIED", "PARTIAL", "UNVERIFIED", "PLAN", "BLOCKED");
    /** AI 쪽 {@code ALLOWED_CANVAS_SOURCE_LABELS} 와 같은 목록이어야 한다. */
    private static final Set<String> SOURCE_LABELS = Set.of(
        "concept_snapshot", "market_size", "growth_rate", "competitor_analysis",
        "price_analysis", "demand_evidence", "execution_constraints");

    private static final Set<String> DECISIONS = Set.of(
        "PASS", "CONDITIONAL", "REVISION_REQUIRED", "BLOCKED");
    private static final Set<String> CONFIDENCES = Set.of("HIGH", "MEDIUM");
    private static final Set<String> FIT_STATES = Set.of("PASS", "PARTIAL", "FAIL");
    private static final Set<String> LEGAL_STATUSES = Set.of(
        "PASS", "CONDITIONAL", "BLOCKED", "UNVERIFIED");

    private MarketResearchContract() { }

    public static void validate(JsonNode result) {
        exact(result, ENVELOPE);
        for (String field : List.of("runId", "conceptId", "asOf", "generatedAt")) text(result, field);
        String mode = text(result, "mode");
        if (!MODES.contains(mode)) invalid();

        stages(result.get("stages"));
        degradations(result.get("degradations"));
        stringArray(result.get("notes"));

        Set<String> evidenceIds = evidence(result.get("evidence"));

        if ("FULL".equals(mode)) {
            scorecard(result.get("scorecard"));
            market(result.get("market"), evidenceIds);
            mustBeNull(result, "canvas");
            mustBeNull(result, "bm");
        } else {
            mustBeNull(result, "scorecard");
            mustBeNull(result, "market");
            canvas(result.get("canvas"), result.get("evidence"), evidenceIds);
            bm(result.get("bm"));
        }
        summary(result.get("summary"), evidenceIds);
    }

    // ── 공통 ────────────────────────────────────────────────────────────
    private static void stages(JsonNode stages) {
        if (stages == null || !stages.isArray() || stages.isEmpty()) invalid();
        for (JsonNode stage : stages) {
            exact(stage, Set.of("name", "status", "seconds", "llmCalls"));
            text(stage, "name");
            if (!STAGE_STATES.contains(text(stage, "status"))) invalid();
            nonNegative(stage, "seconds");
            nonNegative(stage, "llmCalls");
        }
    }

    private static void degradations(JsonNode items) {
        if (items == null || !items.isArray()) invalid();
        for (JsonNode item : items) {
            exact(item, Set.of("stage", "code", "detail"));
            for (String field : List.of("stage", "code", "detail")) text(item, field);
        }
    }

    /** 근거 원장. <b>id 는 유일해야 한다</b> — 중복이면 칸의 참조가 어느 것인지 모른다. */
    private static Set<String> evidence(JsonNode items) {
        if (items == null || !items.isArray()) invalid();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : items) {
            exact(item, Set.of("id", "kind", "metric", "subject", "period", "value", "unit",
                "grade", "gradeReason", "sourceUrl", "sourceKind", "retrievedAt", "quote",
                "caveats", "formula", "inputs", "materialIds", "assumptions"));
            if (!ids.add(text(item, "id"))) invalid();
            if (!EVIDENCE_KINDS.contains(text(item, "kind"))) invalid();
            if (!GRADES.contains(text(item, "grade"))) invalid();
            text(item, "gradeReason");
            for (String field : List.of("metric", "subject", "period", "unit",
                "sourceUrl", "sourceKind", "retrievedAt", "quote", "formula")) nullableText(item.get(field));
            nullableNumber(item.get("value"));
            nullableObject(item.get("inputs"));
            // 경계는 **항상 배열**이다. 없으면 빈 배열 — null 로 두면 「없음」과 「안 실었음」이 같아진다.
            stringArray(item.get("caveats"));
            stringArray(item.get("materialIds"));
            stringArray(item.get("assumptions"));
        }
        return ids;
    }

    // ── FULL ────────────────────────────────────────────────────────────
    private static void scorecard(JsonNode items) {
        if (items == null || !items.isArray()) invalid();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            exact(item, Set.of("subject", "state", "detail"));
            String subject = text(item, "subject");
            if (!SUBJECTS.contains(subject) || !seen.add(subject)) invalid();
            if (!SCORE_STATES.contains(text(item, "state"))) invalid();
            text(item, "detail");
        }
        // 7과목이 **전부** 있어야 한다. 빠진 과목은 「미확보」가 아니라 「안 쟀다」로 읽힌다.
        if (!seen.equals(SUBJECTS)) invalid();
    }

    private static void market(JsonNode market, Set<String> evidenceIds) {
        exact(market, Set.of("tam", "sam", "som", "growth", "price", "notFound", "coverageCaveat"));
        for (String field : List.of("tam", "sam", "som", "growth")) {
            JsonNode figure = market.get(field);
            if (figure == null || figure.isNull()) continue;
            exact(figure, Set.of("value", "unit", "grade", "formula", "factors",
                "assumptions", "caveats", "evidenceIds"));
            nullableNumber(figure.get("value"));
            text(figure, "unit");
            if (!GRADES.contains(text(figure, "grade"))) invalid();
            nullableText(figure.get("formula"));
            factors(figure.get("factors"));
            stringArray(figure.get("assumptions"));
            stringArray(figure.get("caveats"));
            references(figure.get("evidenceIds"), evidenceIds);
        }
        JsonNode price = market.get("price");
        if (price != null && !price.isNull()) {
            exact(price, Set.of("min", "base", "max", "currency", "baseKind", "baseNote",
                "grade", "caveats", "evidenceIds"));
            for (String field : List.of("min", "base", "max")) nullableNumber(price.get(field));
            text(price, "currency");
            // ⚠ 대표값의 **성격**은 일급 필드다. 자유 dict 안 문자열로 두면 언젠가 안 그려진다.
            if (!"MEDIAN_PROVISIONAL".equals(text(price, "baseKind"))) invalid();
            text(price, "baseNote");
            if (!GRADES.contains(text(price, "grade"))) invalid();
            stringArray(price.get("caveats"));
            references(price.get("evidenceIds"), evidenceIds);
        }
        JsonNode notFound = market.get("notFound");
        if (notFound == null || !notFound.isArray()) invalid();
        for (JsonNode item : notFound) {
            exact(item, Set.of("item", "detail"));
            text(item, "item");
            text(item, "detail");
        }
        nullableText(market.get("coverageCaveat"));
    }

    /**
     * 계산식의 항들. <b>「무엇이 관측이고 무엇이 가정인가」를 문장이 아니라 값으로</b> 나른다.
     *
     * <p>예전에는 이 정보가 {@code assumptions} 산문 안에만 있었고, 산문은 옮기다 빠진다.
     * 실측: 규칙 파일의 근거 서술이 {@code [:100]} 으로 잘려 문장 한가운데에서 끊긴 채
     * 화면까지 갔다. 항으로 나르면 자를 이유가 없다.
     *
     * <p>비어 있어도 된다 — 요인을 못 세우는 추정이 있다. 다만 <b>배열은 항상 있어야</b>
     * 한다. {@code null} 로 두면 「항이 없다」와 「안 실었다」가 같아진다.
     */
    private static void factors(JsonNode items) {
        if (items == null || !items.isArray()) invalid();
        for (JsonNode item : items) {
            exact(item, Set.of("name", "value", "unit", "basis", "note",
                "bound", "falsifiedIf", "sourceCount", "sourceDomains", "caveats"));
            text(item, "name");
            if (!FACTOR_BASES.contains(text(item, "basis"))) invalid();
            nullableNumber(item.get("value"));
            for (String field : List.of("unit", "note", "bound", "falsifiedIf")) {
                nullableText(item.get(field));
            }
            nonNegative(item, "sourceCount");
            stringArray(item.get("sourceDomains"));
            stringArray(item.get("caveats"));
            // 관측이라면서 출처가 0곳이면 표가 거짓말을 한다 — 그 조합은 계약 위반이다.
            if ("관측".equals(text(item, "basis")) && item.get("sourceCount").asInt() == 0) invalid();
        }
    }

    // ── BM ──────────────────────────────────────────────────────────────
    private static void canvas(JsonNode canvas, JsonNode evidenceItems, Set<String> evidenceIds) {
        exact(canvas, Set.of("cells"));
        JsonNode cells = canvas.get("cells");
        if (cells == null || !cells.isArray()) invalid();
        Set<String> seen = new HashSet<>();
        for (JsonNode cell : cells) {
            exact(cell, Set.of("canvasCell", "status", "content", "reason",
                "sourceLabels", "marketEvidenceIds", "missingEvidence", "caveats"));
            String name = text(cell, "canvasCell");
            if (!CANVAS_CELLS.contains(name) || !seen.add(name)) invalid();
            if (!CANVAS_STATUSES.contains(text(cell, "status"))) invalid();
            text(cell, "reason");
            stringArray(cell.get("content"));
            stringArray(cell.get("missingEvidence"));
            stringArray(cell.get("caveats"));
            for (JsonNode label : cell.get("sourceLabels").isArray() ? cell.get("sourceLabels") : invalidNode()) {
                if (!label.isTextual() || !SOURCE_LABELS.contains(label.asText())) invalid();
            }
            references(cell.get("marketEvidenceIds"), evidenceIds);
            // content 가 있으면 출처 라벨도 있어야 한다 (AI 쪽 규칙과 같은 문장)
            if (!cell.get("content").isEmpty() && cell.get("sourceLabels").isEmpty()) invalid();
            requireCaveats(cell, evidenceItems);
        }
        // 9칸이 **각각 정확히 한 번**. 빠지면 그 칸이 「없다」가 아니라 「안 봤다」가 된다.
        if (!seen.equals(CANVAS_CELLS)) invalid();
    }

    /**
     * <b>경계 불변식.</b> 칸이 인용한 근거의 경계는 그 칸에도 있어야 한다.
     *
     * <p>이것이 이 계약에서 스키마가 아닌 유일한 검사다. 실측: BM 모델이 경계를 최종 문장에
     * 안 싣는다(0/2). AI 쪽에서 기계로 파생하지만, 그 파생이 회귀하면 <b>여기서 막힌다</b>.
     */
    private static void requireCaveats(JsonNode cell, JsonNode evidenceItems) {
        Set<String> want = new HashSet<>();
        for (JsonNode ref : cell.get("marketEvidenceIds")) {
            for (JsonNode item : evidenceItems) {
                if (!ref.asText().equals(item.get("id").asText())) continue;
                for (JsonNode caveat : item.get("caveats")) want.add(caveat.asText());
            }
        }
        Set<String> have = new HashSet<>();
        for (JsonNode caveat : cell.get("caveats")) have.add(caveat.asText());
        if (!have.containsAll(want)) invalid();
    }

    private static void bm(JsonNode bm) {
        if (bm == null || bm.isNull()) return;      // BM 이 죽어도 시장조사 결과는 살린다
        exact(bm, Set.of("decision", "confidence", "summary", "marketFitStatus", "marketFitSummary",
            "consistencyStatus", "consistencySummary", "strengths", "weaknesses", "risks", "legal",
            "financialHandoff"));
        if (!DECISIONS.contains(text(bm, "decision"))
            || !CONFIDENCES.contains(text(bm, "confidence"))
            || !FIT_STATES.contains(text(bm, "marketFitStatus"))
            || !FIT_STATES.contains(text(bm, "consistencyStatus"))) invalid();
        for (String field : List.of("summary", "marketFitSummary", "consistencySummary")) text(bm, field);
        for (String field : List.of("strengths", "weaknesses", "risks")) stringArray(bm.get(field));
        JsonNode legal = bm.get("legal");
        exact(legal, Set.of("used", "status", "summary", "risks", "requiredActions"));
        if (legal.get("used") == null || !legal.get("used").isBoolean()
            || !LEGAL_STATUSES.contains(text(legal, "status"))) invalid();
        nullableText(legal.get("summary"));
        stringArray(legal.get("risks"));
        stringArray(legal.get("requiredActions"));
        JsonNode handoff = bm.get("financialHandoff");
        exact(handoff, Set.of("conceptId", "revenueModel", "priceMin", "priceBase", "priceMax",
            "tam", "sam", "som", "marketGrowthRate", "expectedRevenue", "unitCost",
            "fixedCostItems", "variableCostItems", "missingFinancialInputs", "handoffStatus"));
        text(handoff, "conceptId");
        nullableText(handoff.get("revenueModel"));
        for (String field : List.of("priceMin", "priceBase", "priceMax", "tam", "sam", "som",
                "marketGrowthRate", "expectedRevenue", "unitCost")) nullableNumber(handoff.get(field));
        if (!handoff.get("fixedCostItems").isArray() || !handoff.get("variableCostItems").isArray()) invalid();
        stringArray(handoff.get("missingFinancialInputs"));
        if (!Set.of("READY", "PARTIAL", "BLOCKED").contains(text(handoff, "handoffStatus"))) invalid();
    }

    private static void summary(JsonNode summary, Set<String> evidenceIds) {
        if (summary == null || summary.isNull()) return;
        if (!summary.isArray()) invalid();
        for (JsonNode line : summary) {
            exact(line, Set.of("cell", "sentence", "cardIds"));
            text(line, "cell");
            text(line, "sentence");
            references(line.get("cardIds"), evidenceIds);
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
    private static void nullableNumber(JsonNode value) {
        if (value != null && !value.isNull() && !value.isNumber()) invalid();
    }
    private static void nullableObject(JsonNode value) {
        if (value != null && !value.isNull() && !value.isObject()) invalid();
    }
    private static void nonNegative(JsonNode value, String field) {
        JsonNode item = value.get(field);
        if (item == null || !item.isNumber() || item.asDouble() < 0) invalid();
    }
    private static void mustBeNull(JsonNode value, String field) {
        JsonNode item = value.get(field);
        if (item != null && !item.isNull()) invalid();
    }
    private static void stringArray(JsonNode values) {
        if (values == null || !values.isArray()) invalid();
        for (JsonNode value : values) if (!value.isTextual() || value.asText().isBlank()) invalid();
    }
    private static void references(JsonNode values, Set<String> allowed) {
        stringArray(values);
        for (JsonNode value : values) if (!allowed.contains(value.asText())) invalid();
    }
    private static JsonNode invalidNode() {
        invalid();
        return null;
    }
    private static void invalid() {
        throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", false);
    }
}
