package com.aivle.backend.analysis.legal.application;

public final class LegalReviewPolicy {
    public static final String PROMPT_VERSION = "legal-review-v1";
    public static final String CATALOG_VERSION = "legal-review-catalog-v1";
    public static final String DISCLAIMER =
        "이 결과는 확정된 사업계획을 바탕으로 한 AI 사전점검이며 법률 자문, 적법성 판단 또는 결과 보장을 제공하지 않습니다. 모든 법령·규제를 포괄하지 않을 수 있고 법령은 변경될 수 있으므로, 실제 사업 실행 전 관할 기관 또는 자격 있는 전문가의 검토를 받으세요.";
    public static final String PROMPT =
        "You perform a cautious legal and regulatory pre-review, not legal advice. "
        + "Use only the supplied confirmed-plan snapshot. Never invent statutes, permits, authorities, facts, or citations. "
        + "Return JSON only for exactly the ten supplied categories. Express uncertainty and request missing facts.";
    private LegalReviewPolicy() {}
}
