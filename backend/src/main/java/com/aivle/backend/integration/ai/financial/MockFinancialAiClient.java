package com.aivle.backend.integration.ai.financial;

import com.aivle.backend.analysis.financial.FinancialPolicy;
import com.aivle.backend.analysis.financial.entity.FinancialTypes.AssumptionSourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 결정론 Mock. 섹션 텍스트에서 규칙으로 숫자를 뽑는다 — 없는 숫자는 만들지 않는다.
 *
 * <p>화면과 스모크가 의존하는 세 경로를 <b>반드시</b> 한 번씩 태운다:
 * <ul>
 *   <li><b>결측</b> — 월 고정비는 기획서에 없는 것이 정상이라 아예 방출하지 않는다(사용자 입력 대상).</li>
 *   <li><b>후보 다수</b> — 가격이 여러 개 적혀 있으면 전부 후보로 싣고 고르지 않는다.</li>
 *   <li><b>모순</b> — 단가×수량과 명시 매출이 어긋나면 conflict로 드러내고 해소하지 않는다.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockFinancialAiClient implements FinancialAiClient {
    /** "38,000원" / "약 8,500 원" 형태. 그룹 1이 숫자다. */
    private static final Pattern WON = Pattern.compile("([0-9][0-9,]{2,})\\s*원");
    /** "8,000개" 형태. */
    private static final Pattern QUANTITY = Pattern.compile("([0-9][0-9,]{2,})\\s*개");
    /** "2억 8천만 원" / "20억 원" 형태. */
    private static final Pattern EOK = Pattern.compile("([0-9]+)\\s*억(?:\\s*([0-9]+)\\s*천만)?\\s*원");
    /** "2,000만 원" 형태. */
    private static final Pattern MAN = Pattern.compile("([0-9][0-9,]*)\\s*만\\s*원");

    private static final int MONTHS_PER_YEAR = 12;

    @Override
    public FinancialAiResponse extract(FinancialAiRequest request) {
        String priceText = sectionText(request, "BUSINESS_MODEL");
        String costText = sectionText(request, "COST_PROFITABILITY");
        String salesText = sectionText(request, "SALES_GOALS_FINANCIAL_PROJECTIONS");

        List<FinancialAiResponse.Assumption> assumptions = new ArrayList<>();
        List<FinancialAiResponse.Conflict> conflicts = new ArrayList<>();

        // ── 단가: 후보가 여럿이면 전부 싣고 고르지 않는다
        List<FinancialAiResponse.Candidate> priceCandidates = candidates(priceText, WON, "가격");
        Double unitPrice = priceCandidates.isEmpty() ? null : priceCandidates.get(0).value();
        if (unitPrice != null) {
            assumptions.add(new FinancialAiResponse.Assumption(
                FinancialPolicy.UNIT_PRICE, "객단가", unitPrice, "KRW",
                plan("비즈니스 모델", priceCandidates.get(0).quote()),
                priceCandidates.size() > 1 ? priceCandidates : List.of()));
        }

        // ── 변동원가율: 원가 금액들의 합 ÷ 단가
        List<FinancialAiResponse.Candidate> costCandidates = candidates(costText, WON, "원가");
        if (unitPrice != null && unitPrice > 0 && !costCandidates.isEmpty()) {
            double variableCost = costCandidates.stream()
                .mapToDouble(FinancialAiResponse.Candidate::value)
                .filter(value -> value < unitPrice)
                .sum();
            if (variableCost > 0) {
                assumptions.add(new FinancialAiResponse.Assumption(
                    FinancialPolicy.VARIABLE_COST_RATE, "변동원가율",
                    round4(variableCost / unitPrice), "RATIO",
                    plan("원가·수익성", costCandidates.get(0).quote()), List.of()));
            }
        }

        // ── 월 판매량: 연 수량을 12로 나눈다(균등 분해는 우리가 정한 규칙이므로 DEFAULT로 자수)
        List<FinancialAiResponse.Candidate> volumes = candidates(salesText, QUANTITY, "수량");
        Double monthlyVolume = null;
        if (!volumes.isEmpty()) {
            monthlyVolume = round4(volumes.get(0).value() / MONTHS_PER_YEAR);
            assumptions.add(new FinancialAiResponse.Assumption(
                FinancialPolicy.MONTHLY_VOLUME, "월 판매량", monthlyVolume, "EA",
                new FinancialAiResponse.Source(AssumptionSourceType.DEFAULT, "판매 목표·재무 추정",
                    null, "기획서에 연 단위 수량만 있어 12로 균등 분해했습니다. "
                        + "실제 월별 계획이 있으면 바꿔 주세요."),
                List.of()));
        }

        // ── 초기투자: 원가·수익성의 "만 원" 표현(금형 개발비 등)
        Matcher investment = MAN.matcher(costText);
        if (investment.find()) {
            assumptions.add(new FinancialAiResponse.Assumption(
                FinancialPolicy.INITIAL_INVESTMENT, "초기 투자",
                parseNumber(investment.group(1)) * 10_000, "KRW",
                plan("원가·수익성", investment.group()), List.of()));
        }

        // ── 할인율: 기획서에 없는 것이 정상 — 기본값을 쓰고 자수한다
        assumptions.add(new FinancialAiResponse.Assumption(
            FinancialPolicy.DISCOUNT_RATE, "할인율(연)",
            FinancialPolicy.DEFAULT_DISCOUNT_RATE, "RATIO",
            new FinancialAiResponse.Source(AssumptionSourceType.DEFAULT, null, null,
                FinancialPolicy.DEFAULT_DISCOUNT_RATE_NOTE),
            List.of()));

        // ── 월 고정비는 방출하지 않는다. 기획서가 답하지 않는 값이며 사용자 입력이 정식 경로다.

        // ── 모순: 단가 × 연 수량 과 기획서에 적힌 연 매출이 다르면 드러낸다
        Double statedRevenue = firstAmount(salesText, EOK);
        if (unitPrice != null && !volumes.isEmpty() && statedRevenue != null) {
            double computed = unitPrice * volumes.get(0).value();
            if (Math.abs(computed - statedRevenue) / statedRevenue > 0.05) {
                conflicts.add(new FinancialAiResponse.Conflict(
                    "REVENUE_MISMATCH",
                    String.format(
                        "단가×수량(%,.0f원)과 기획서에 적힌 매출(%,.0f원)이 다릅니다. "
                            + "어느 쪽을 기준으로 삼을지 선택해 주세요.", computed, statedRevenue),
                    List.of("UNIT_TIMES_VOLUME", "STATED_REVENUE")));
            }
        }

        return new FinancialAiResponse(
            "mock", "mock-financial-analysis-v1",
            "mock-financial-" + request.structuredPlanId() + "-" + request.feasibilityAssessmentId(),
            List.copyOf(assumptions), List.copyOf(conflicts),
            new FinancialAiResponse.Narrative(
                "기획서에 적힌 가격과 원가로는 건당 이익이 남지만, 월 고정비가 없어 손익분기는 아직 답할 수 없습니다.",
                "가격·원가·수량은 기획서에서 읽었고 할인율은 기본값을 적용했습니다. "
                    + "월 고정비를 채우면 손익분기와 자금 소요가 계산됩니다.",
                "판매량 가정이 흔들리면 손익분기 시점이 가장 먼저 움직입니다.",
                List.of("월 고정비(인건비·마케팅비) 확정", "적용할 판매 단가 선택")));
    }

    // ------------------------------------------------------------------ 보조

    private String sectionText(FinancialAiRequest request, String code) {
        return request.sections().stream()
            .filter(section -> code.equals(section.code()))
            .map(FinancialAiRequest.Section::content)
            .filter(content -> content != null && !content.isBlank())
            .findFirst().orElse("");
    }

    /** 패턴에 걸린 숫자를 등장 순서대로 후보로 만든다. 중복 값은 접는다. */
    private List<FinancialAiResponse.Candidate> candidates(
        String text, Pattern pattern, String labelPrefix
    ) {
        List<FinancialAiResponse.Candidate> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        int index = 1;
        while (matcher.find()) {
            double value = parseNumber(matcher.group(1));
            if (found.stream().noneMatch(item -> item.value() == value)) {
                found.add(new FinancialAiResponse.Candidate(
                    value, labelPrefix + " 후보 " + index++, matcher.group()));
            }
        }
        return List.copyOf(found);
    }

    private Double firstAmount(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        double amount = parseNumber(matcher.group(1)) * 100_000_000;
        if (matcher.group(2) != null) {
            amount += parseNumber(matcher.group(2)) * 10_000_000;
        }
        return amount;
    }

    private FinancialAiResponse.Source plan(String sectionLabel, String quote) {
        return new FinancialAiResponse.Source(
            AssumptionSourceType.PLAN, sectionLabel, quote, null);
    }

    private double parseNumber(String raw) {
        return Double.parseDouble(raw.replace(",", ""));
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
