package com.aivle.backend.analysis.financial;

import java.util.List;

/** 재무 분석의 프롬프트·기본값·필수 가정 키. */
public final class FinancialPolicy {
    public static final String PROMPT_VERSION = "financial-analysis-v1";

    public static final String UNIT_PRICE = "UNIT_PRICE";
    public static final String VARIABLE_COST_RATE = "VARIABLE_COST_RATE";
    public static final String MONTHLY_VOLUME = "MONTHLY_VOLUME";
    public static final String MONTHLY_FIXED_COST = "MONTHLY_FIXED_COST";
    public static final String INITIAL_INVESTMENT = "INITIAL_INVESTMENT";
    public static final String DISCOUNT_RATE = "DISCOUNT_RATE";

    /** 이 여섯 개가 채워져야 확정할 수 있다. */
    public static final List<String> REQUIRED_KEYS = List.of(
        UNIT_PRICE, VARIABLE_COST_RATE, MONTHLY_VOLUME,
        MONTHLY_FIXED_COST, INITIAL_INVESTMENT, DISCOUNT_RATE);

    /** 기획서에 할인율이 적힌 경우가 거의 없어 기본값을 쓰되 화면에서 자수한다. */
    public static final double DEFAULT_DISCOUNT_RATE = 0.10;
    public static final String DEFAULT_DISCOUNT_RATE_NOTE =
        "기획서에 할인율이 없어 연 10%를 적용했습니다. 실제 자본비용으로 바꿔 주세요.";

    public static final String DISCLAIMER =
        "재무 자문이 아닙니다. 모든 수치는 기획서에 적힌 가정을 그대로 계산한 결과이며 "
        + "외부 시장 데이터가 반영되지 않았습니다. 가정이 바뀌면 결과도 달라집니다.";

    public static final String PROMPT = """
        You extract financial assumptions from a business plan. Return JSON only.
        Never invent TAM/SAM/SOM, growth rates, market share, competitor revenue,
        industry-average margins, or statistics. Never estimate revenue from market size.
        Only report numbers that literally appear in the supplied section text.

        For every assumption you report, quote the exact substring of the section text
        that contains the number. A quote that is not a substring will be discarded and
        the assumption dropped, so copy it verbatim rather than paraphrasing.

        When the plan states several candidate values for one assumption (for example a
        consumer price, an early-bird price and a wholesale price), return them all in
        candidates and do not pick one — the user decides.

        When the plan contradicts itself (for example unit price times volume does not
        match the stated revenue), report it in conflicts with the options to choose
        between. Do not silently resolve it.

        Do not compute contribution margin, break-even, ROI, NPV or IRR — those are
        calculated outside the model. Write the narrative in Korean.
        """;

    private FinancialPolicy() {}
}
