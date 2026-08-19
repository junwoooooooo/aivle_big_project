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
        "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes",
        // 판 ㊸ — 사람 보고서의 2·8·9절. AI 쪽 {@code serialize.ENVELOPE} 와 같은 집합이다.
        "judgment", "prescriptions", "synthesis",
        // 엔진이 쓴 사람 보고서 본문. **칸은 항상 있고 값이 null 일 수 있다** —
        // 없는 것과 안 쓴 것이 같아지면 「보고서를 못 만들었다」를 화면이 말할 수 없다.
        "report");

    /**
     * 보고서 절의 주제. 성적표 과목({@link #SUBJECTS})과 <b>다른 목록</b>이다 —
     * {@code GROWTH}·{@code CALCULATION}·{@code NOT_FOUND} 는 사람이 읽는 절이 아니고,
     * 반대로 {@code GAPS}(8절 — 못 구한 것)·{@code SYNTHESIS}(9절 — 이 조사가 말하는 것)는
     * 성적표에 없는 절이다. AI 쪽 {@code _SECTION_SUBJECT} 와 같은 집합이어야 한다.
     */
    private static final Set<String> REPORT_SUBJECTS = Set.of(
        "MARKET_SIZE", "PRICE", "COMPETITOR", "CHANNEL", "DEMAND", "UNIT_ECONOMICS", "REGULATION",
        "GAPS", "SYNTHESIS");

    /**
     * {@code VALIDATION} 은 FULL+BM 을 한 실행으로 이은 것이다(여정 3번 「사업 검증」).
     * 봉투가 같아 검증기를 나누지 않는다 — 두 걸음의 산출이 원래 비어 있던 칸에 들어갈 뿐이다.
     */
    private static final Set<String> MODES = Set.of("FULL", "BM", "VALIDATION");
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

    /**
     * 성적표 과목 = <b>화면의 목차</b>다({@code MarketResultBody} 「성적표 과목이 곧 목차다」).
     *
     * <p>판 ㊸ 에서 셋이 늘었다 — {@code CHANNEL}·{@code UNIT_ECONOMICS}·{@code REGULATION}.
     * 절 체인(문서를 절 단위로 읽는 경로)이 채우는 과목이고, 엔진의 과목표에는 없어
     * <b>실린 사실 건수</b>에서 성적이 나온다.
     *
     * <p>⚠ AI 쪽 {@code serialize._SUBJECT} + {@code _SECTION_SUBJECT} 와 <b>같은 집합</b>이어야
     * 한다. 아래 {@code seen.equals(SUBJECTS)} 가 정확히 일치를 요구하므로, 한쪽만 고치면
     * 결과가 통째로 거부된다.
     */
    private static final Set<String> SUBJECTS = Set.of(
        "MARKET_SIZE", "GROWTH", "COMPETITOR", "PRICE", "DEMAND", "CALCULATION",
        "CHANNEL", "UNIT_ECONOMICS", "REGULATION", "NOT_FOUND");
    private static final Set<String> SCORE_STATES = Set.of("FILLED", "PARTIAL", "MISSING", "REPORTED");

    private static final Set<String> CANVAS_CELLS = Set.of(
        "CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "CUSTOMER_RELATIONSHIPS",
        "REVENUE_STREAMS", "KEY_RESOURCES", "KEY_ACTIVITIES", "KEY_PARTNERS", "COST_STRUCTURE");
    private static final Set<String> CANVAS_STATUSES = Set.of(
        "VERIFIED", "PARTIAL", "UNVERIFIED", "PLAN", "BLOCKED");
    /**
     * AI 쪽 {@code ALLOWED_CANVAS_SOURCE_LABELS} 와 같은 목록이어야 한다.
     *
     * <p>⚠ {@code channel_analysis} 는 2026-08-15 에 더한 여덟째다. 나머지 일곱은 전부
     * {@code MarketJoinData} 의 필드 이름인데 채널만 대응 필드가 없어, 채널 칸의 파생 라벨이
     * 구조적으로 0건이었고 폴백이 {@code concept_snapshot}(사용자가 쓴 컨셉 서술문)을
     * 되살렸다. <b>이 집합은 포함 검사만 하므로 늘려도 옛 봉투·골든 픽스처가 그대로 통과한다.</b>
     */
    private static final Set<String> SOURCE_LABELS = Set.of(
        "concept_snapshot", "market_size", "growth_rate", "competitor_analysis",
        "price_analysis", "demand_evidence", "channel_analysis", "execution_constraints");

    private static final Set<String> DECISIONS = Set.of(
        "PASS", "CONDITIONAL", "REVISION_REQUIRED", "BLOCKED");
    /** AI 쪽 {@code app/validation/gate.py} 의 규칙 코드와 같은 목록이어야 한다. */
    private static final Set<String> GATE_CODES = Set.of("G1", "G4", "G5");

    /**
     * 사유의 <b>갈래</b>. 「컨셉을 고쳐서 될 일인가」가 여기서 갈린다.
     *
     * <p>{@code UNCOLLECTED} 는 <b>재수집이 답</b>이고 컨셉을 고쳐도 안 고쳐진다.
     * {@code UNCITED} 는 찾아 놓고 인용을 안 한 것이라 사용자가 할 일이 없다.
     * {@code UNMAPPED} 는 성적표가 그 칸을 재지 않아 <b>갈래를 모른다</b>는 뜻이다.
     * 정본은 AI 쪽 {@code app/validation/gate.py} — 값을 늘리면 여기도 늘린다.
     */
    private static final Set<String> GATE_CAUSES = Set.of("UNCOLLECTED", "UNCITED", "UNMAPPED");
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

        // ⚠ 성적표는 **세 모드 모두** 온다. 예전에는 BM 이면 null 을 강제했는데,
        //    그러면 게이트가 「이 과목이 애초에 수집됐는지」를 모르고 사유의 갈래를
        //    못 가른다(계획서 1-2).
        scorecard(result.get("scorecard"));
        if ("FULL".equals(mode)) {
            market(result.get("market"), evidenceIds);
            mustBeNull(result, "canvas");
            mustBeNull(result, "bm");
        } else if ("BM".equals(mode)) {
            mustBeNull(result, "market");
            canvas(result.get("canvas"), result.get("evidence"), evidenceIds);
            bm(result.get("bm"));
        } else {
            // VALIDATION — 두 걸음이 **한 봉투**다. 그래서 셋이 다 찬 채로 온다.
            // ⚠ 예전에는 이 갈래가 BM 과 같은 가지에 있어 `market` 을 null 로 강제했고,
            //    사업 검증이 매번 RESULT_FIELD_CONSTRAINT_VIOLATION 으로 죽었다
            //    (2026-08-13 실측: 유료 실행이 71초 만에 거부됐다). 봉투는 안 늘렸지만
            //    **원래 비어 있던 칸이 찬다**는 것이 이 모드의 정의다.
            market(result.get("market"), evidenceIds);
            canvas(result.get("canvas"), result.get("evidence"), evidenceIds);
            bm(result.get("bm"));
        }
        summary(result.get("summary"), evidenceIds);
        report(result.get("report"));
        judgment(result.get("judgment"));
        prescriptions(result.get("prescriptions"));
        synthesis(result.get("synthesis"));
    }

    /**
     * 엔진이 쓴 <b>사람 보고서 본문</b>. {@code null} 이 정상인 경우가 여럿이다 —
     * 재채점 모드·예산 부족·생성 실패. <b>보고서가 없다고 시장조사 결과를 버리지 않는다</b>
     * ({@link #bm} 과 같은 원칙).
     *
     * <p>{@code unverifiedNumbers}·{@code conceptLeaks} 는 <b>경계 표시</b>다 — 「검증 안 된
     * 숫자가 몇 개인가」와 「컨셉 서술이 사실인 척 샜는가」를 세어 나른다. 그래서 문장이
     * 아니라 정수이고, 0 이상만 받는다.
     *
     * <p>{@code lead}·{@code tail} 은 <b>머리말·꼬리말 마크다운</b>이다. 화면이 절 안에 글을
     * 끼우는 대신 <b>보고서 전문</b>을 그리므로 봉투가 머리·꼬리까지 나른다. 없을 수 있지만
     * <b>빈 문자열은 거부</b>한다 — 「안 썼다」와 「빈칸을 썼다」가 같아진다.
     */
    private static void report(JsonNode report) {
        if (report == null || report.isNull()) return;
        exact(report, Set.of("writtenBy", "unverifiedNumbers", "conceptLeaks",
            "lead", "tail", "sections"));
        text(report, "writtenBy");
        nonNegativeInteger(report, "unverifiedNumbers");
        nonNegativeInteger(report, "conceptLeaks");
        nullableNonBlankText(report.get("lead"));
        nullableNonBlankText(report.get("tail"));
        JsonNode sections = report.get("sections");
        if (sections == null || !sections.isArray()) invalid();
        for (JsonNode section : sections) {
            exact(section, Set.of("subject", "markdown"));
            if (!REPORT_SUBJECTS.contains(text(section, "subject"))) invalid();
            // 본문이 비면 목차에 절만 서고 화면이 빈칸을 그린다.
            text(section, "markdown");
        }
    }

    // ── 판 ㊸ — 사람 보고서의 2·8·9절 ────────────────────────────────────
    /**
     * 2절 <b>가격 판단</b>. 셋 다 {@code null} 이 정상이다(BM 모드·절 체인 미실행).
     *
     * <p>⚠ {@code conclusion} 을 필수로 만들지 않는다. 비교쌍이 안 갖춰지면 기계가
     * <b>결론을 안 쓴다</b> — 그것이 설계이고, 여기서 강제하면 지어내라는 압력이 된다.
     */
    private static void judgment(JsonNode node) {
        if (node == null || node.isNull()) return;
        exact(node, Set.of("price", "lines", "conclusion"));
        nullableNumber(node.get("price"));
        nullableText(node.get("conclusion"));
        JsonNode lines = node.get("lines");
        if (lines == null || !lines.isArray()) invalid();
        for (JsonNode line : lines) {
            exact(line, Set.of("what", "sentence", "formula", "silentBecause", "sources"));
            text(line, "what");
            for (String f : List.of("sentence", "formula", "silentBecause")) nullableText(line.get(f));
            JsonNode sources = line.get("sources");
            if (sources == null || !sources.isArray()) invalid();
            for (JsonNode s : sources) {
                exact(s, Set.of("raw", "subject", "period", "url"));
                for (String f : List.of("raw", "subject", "url")) text(s, f);
                // 연도는 없을 수 있다 — **없는 것을 지어내지 않는다.**
                nullableText(s.get("period"));
            }
        }
    }

    /** 8절 <b>처방</b> — 「무엇을 못 구했나 / 왜 / 어디서」. 셋째 열이 처방이다. */
    private static void prescriptions(JsonNode items) {
        if (items == null || items.isNull()) return;
        if (!items.isArray()) invalid();
        for (JsonNode item : items) {
            exact(item, Set.of("section", "kind", "kindLabel", "what", "why", "where"));
            for (String f : List.of("section", "kind", "kindLabel", "what", "why", "where")) {
                text(item, f);
            }
        }
    }

    /** 9절 <b>지지 / 흔듦</b>. 검사에서 버려진 문장은 AI 쪽에서 이미 빠진다. */
    private static void synthesis(JsonNode items) {
        if (items == null || items.isNull()) return;
        if (!items.isArray()) invalid();
        for (JsonNode item : items) {
            exact(item, Set.of("key", "stance", "sentence", "what", "sources"));
            for (String f : List.of("key", "stance", "sentence", "what")) text(item, f);
            JsonNode sources = item.get("sources");
            if (sources == null || !sources.isArray()) invalid();
            for (JsonNode s : sources) {
                exact(s, Set.of("raw", "subject", "period"));
                for (String f : List.of("raw", "subject")) text(s, f);
                nullableText(s.get("period"));
            }
        }
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
                "caveats", "formula", "inputs", "materialIds", "assumptions",
                // 판 ㊸ — 절 배치가 **서버 것**이 됐다. 프론트가 다시 추론하지 않는다.
                "section", "placement", "issuer", "tableKey", "raw"));
            if (!ids.add(text(item, "id"))) invalid();
            if (!EVIDENCE_KINDS.contains(text(item, "kind"))) invalid();
            if (!GRADES.contains(text(item, "grade"))) invalid();
            text(item, "gradeReason");
            for (String field : List.of("metric", "subject", "period", "unit",
                "sourceUrl", "sourceKind", "retrievedAt", "quote", "formula",
                "section", "placement", "issuer", "tableKey", "raw")) nullableText(item.get(field));
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
        exact(bm, Set.of("decision", "gateReasons", "confidence", "summary",
            "marketFitStatus", "marketFitSummary",
            "consistencyStatus", "consistencySummary", "strengths", "weaknesses", "risks", "legal",
            "financialHandoff"));
        gateReasons(bm.get("gateReasons"));
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

    /**
     * 판정 게이트가 남긴 반증 사유. AI 쪽 {@code app/validation/gate.py} 가 낸다.
     *
     * <p><b>비어 있을 수 있다</b> — 규칙이 하나도 안 걸린 것이지 검사를 안 한 것이 아니다.
     * 반대로 {@code null} 이면 게이트를 안 돈 결과라 거부한다.
     *
     * <p>{@code cell} 은 null 일 수 있다 — 칸 하나가 아니라 캔버스 전체를 두고 걸리는
     * 규칙(G4)이 있다.
     */
    private static void gateReasons(JsonNode reasons) {
        if (reasons == null || !reasons.isArray()) invalid();
        for (JsonNode reason : reasons) {
            exact(reason, Set.of("code", "cell", "message", "evidenceIds", "cause"));
            if (!GATE_CODES.contains(text(reason, "code"))) invalid();
            if (!GATE_CAUSES.contains(text(reason, "cause"))) invalid();
            text(reason, "message");
            JsonNode cell = reason.get("cell");
            if (cell != null && !cell.isNull() && !CANVAS_CELLS.contains(cell.asText())) invalid();
            stringArray(reason.get("evidenceIds"));
        }
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
    /** null 은 「없다」, 빈 문자열은 <b>사고</b>다 — 둘을 갈라 받는다. */
    private static void nullableNonBlankText(JsonNode value) {
        if (value == null || value.isNull()) return;
        if (!value.isTextual() || value.asText().isBlank()) invalid();
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
    /** 세는 값은 <b>정수</b>다 — 「16.5개」가 들어오면 어딘가 계산이 어긋난 것이다. */
    private static void nonNegativeInteger(JsonNode value, String field) {
        JsonNode item = value.get(field);
        if (item == null || !item.isIntegralNumber() || item.asInt() < 0) invalid();
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
