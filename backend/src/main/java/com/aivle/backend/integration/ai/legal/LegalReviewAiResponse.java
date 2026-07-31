package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.entity.LegalApplicability;
import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.common.entity.RiskLevel;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record LegalReviewAiResponse(
    String provider,
    String model,
    String providerRequestId,
    RiskLevel overallRiskLevel,
    String summary,
    List<Finding> findings,
    List<Question> questions,
    List<RevisionRequestPayload> revisionRequests
) {
    public LegalReviewAiResponse {
        revisionRequests = revisionRequests == null ? List.of() : List.copyOf(revisionRequests);
    }

    /** 수정 요청 없는 하위호환 생성자(OpenAI 어댑터 등). */
    public LegalReviewAiResponse(
        String provider,
        String model,
        String providerRequestId,
        RiskLevel overallRiskLevel,
        String summary,
        List<Finding> findings,
        List<Question> questions
    ) {
        this(provider, model, providerRequestId, overallRiskLevel, summary,
            findings, questions, List.of());
    }

    public record Finding(
        LegalCategory category,
        LegalApplicability applicability,
        RiskLevel riskLevel,
        String title,
        String finding,
        String rationale,
        String recommendedAction,
        List<Evidence> evidence,
        Reasoning reasoning,
        List<String> sourceSectionCodes,
        boolean requiresProfessionalReview,
        BigDecimal confidence
    ) {
        public Finding {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    /**
     * 근거 조문 하나와 그 조문의 설명. 조문이 자기 설명을 들고 다녀야
     * 화면에서 "어느 조문이 무엇을 요구하는지"가 유지된다.
     *
     * <p>{@code excerpt}는 파이프라인이 350자로 자른 발췌다(전문이 아니다). 전문은 {@code lawUrl}로 보낸다.
     */
    public record Evidence(
        String lawName,
        String article,
        String title,
        EvidenceRole role,
        String plainSummary,
        String whyRelevant,
        String excerpt,
        String effectiveDate,
        String lawUrl
    ) {
        /**
         * 문자열 근거를 받는 하위호환 경로. 파이프라인 이전 형식과, 구조화 스키마를 지키지 않는
         * LLM 응답("법령명 제12조(제목) — …")을 조문명만이라도 살려 수용한다.
         */
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Evidence fromText(String text) {
            String raw = text == null ? "" : text.trim();
            Matcher matched = LEGACY_TEXT.matcher(raw);
            if (!matched.find()) {
                return new Evidence(raw.isEmpty() ? null : raw, null, null, null, null, null,
                    null, null, null);
            }
            return new Evidence(matched.group(1).trim(), matched.group(2), matched.group(3),
                null, null, null, null, null, null);
        }

        private static final Pattern LEGACY_TEXT =
            Pattern.compile("^(.+?)\\s(제\\d+조(?:의\\d+)?)(?:\\(([^)]*)\\))?");
    }

    /** 조문이 이 판정에서 하는 역할. 파이프라인 선별 분류(requirement/risk/scope)에서 온다. */
    public enum EvidenceRole { REQUIREMENT, SANCTION, SCOPE }

    /**
     * 판정에 이른 논리 사슬: 기획서 문장 → 걸린 규제 영역 → 발생 의무 → 위반 시 결과 → 조치.
     * 단계별로 결측 가능(정보 부족 범주는 전체가 null).
     */
    public record Reasoning(
        PlanBasis planBasis,
        RegulatoryPath regulatoryPath,
        List<Obligation> obligations,
        Consequence consequence,
        Conclusion conclusion
    ) {
        public Reasoning {
            obligations = obligations == null ? List.of() : List.copyOf(obligations);
        }

        public record PlanBasis(List<String> sectionLabels, List<String> quotes) {
            public PlanBasis {
                sectionLabels = sectionLabels == null ? List.of() : List.copyOf(sectionLabels);
                quotes = quotes == null ? List.of() : List.copyOf(quotes);
            }
        }

        public record RegulatoryPath(String topic, String status, String reason) {}

        public record Obligation(String article, String lawName, String summary) {}

        public record Consequence(List<String> sanctionArticles, String text) {
            public Consequence {
                sanctionArticles =
                    sanctionArticles == null ? List.of() : List.copyOf(sanctionArticles);
            }
        }

        public record Conclusion(String action, String timing) {}
    }

    public record Question(String question, String reason, List<LegalCategory> categories) {
        public Question {
            categories = categories == null ? List.of() : List.copyOf(categories);
        }

        public Question(String question, String reason) {
            this(question, reason, List.of());
        }
    }

    public record RevisionRequestPayload(
        LegalCategory category,
        String anchorSectionCode,
        String anchorQuote,
        String rationale,
        List<SuggestionPayload> suggestions
    ) {
        public RevisionRequestPayload {
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        }
    }

    public record SuggestionPayload(String label, String newText) {}
}
