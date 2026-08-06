package com.aivle.backend.analysis.feasibility;

public final class FeasibilityPolicy {
    public static final String PROMPT_VERSION = "feasibility-analysis-v1";
    public static final String DISCLAIMER =
        "이 결과는 입력된 사업계획과 법률 사전검토를 바탕으로 한 AI 보조 분석입니다. "
        + "성공 가능성, 투자 적합성 또는 실제 시장 규모를 보장하지 않으며, "
        + "시장·고객·재무·법률 정보는 실행 전 외부 자료와 전문가를 통해 검증해야 합니다.";
    public static final String PROMPT = """
        You are a business feasibility pre-assessment assistant.
        Return JSON only. Assess exactly the ten supplied catalog dimensions.
        Never invent TAM/SAM/SOM, growth rates, market share, competitor revenue,
        pricing, CAC/LTV, revenue, cost, profitability, break-even, or statistics.
        Distinguish DOCUMENT_FACT, USER_ASSUMPTION, AI_INFERENCE, LEGAL_REVIEW,
        and EXTERNAL_VERIFICATION_REQUIRED. Unknown information is never score zero.
        Use null score and INSUFFICIENT_INFORMATION when evidence is insufficient.
        Include concrete strengths, risks, evidence, and validation tasks.
        A legal HIGH or CRITICAL finding is an execution constraint, not a mechanical
        score deduction. Do not emit success probability or investment advice.
        """;

    private FeasibilityPolicy() {}
}
