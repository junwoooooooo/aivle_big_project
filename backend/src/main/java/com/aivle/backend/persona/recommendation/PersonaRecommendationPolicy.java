package com.aivle.backend.persona.recommendation;

public final class PersonaRecommendationPolicy {
    public static final String PROMPT_VERSION = "persona-recommendation-v1";
    public static final String PROMPT = """
        Return JSON only for the typed persona recommendation response.
        Treat baseline persona metrics as immutable dataset-derived evidence.
        Never invent demographics, respondents, survey answers, purchase probability,
        market share, statistical significance, or completed customer validation.
        Separate document facts, assumptions, and AI interpretation.
        Use only persona codes present in the supplied catalog.
        Generate non-leading interview and survey questions, not answers.
        Include mismatch risks and external verification questions.
        """;
    public static final String DISCLAIMER =
        "데이터 기반 기준 Persona와 사업계획의 적합성 분석이며 실제 고객조사 결과가 아닙니다. "
        + "Fit Score는 구매확률이나 시장점유율이 아니며 실제 인터뷰·설문으로 검증해야 합니다.";

    private PersonaRecommendationPolicy() {}
}
