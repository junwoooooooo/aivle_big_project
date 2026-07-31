package com.aivle.backend.integration.ai.financial;

import com.aivle.backend.analysis.financial.entity.FinancialTypes.AssumptionSourceType;
import java.util.List;

/**
 * 재무 AI가 내놓는 것은 **가정 초안과 서술뿐**이다. 지표 계산은
 * {@code FinancialCalculationPolicy}가 하며 이 응답에 결과값은 없다.
 *
 * <p>모든 {@code PLAN} 출처 가정의 인용문은 기획서 섹션 원문의 부분문자열이어야 한다.
 * 지어낸 가정을 구조적으로 막기 위한 장치이며, 어댑터와 저장 계층에서 두 번 검증한다.
 */
public record FinancialAiResponse(
    String provider,
    String model,
    String providerRequestId,
    List<Assumption> assumptions,
    List<Conflict> conflicts,
    Narrative narrative
) {
    public FinancialAiResponse {
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    /**
     * 가정 하나.
     *
     * @param candidates 기획서에 후보가 여럿일 때(예: 소비자가·얼리버드가·B2B 공급가) 전부 싣는다.
     *                   사용자가 확정 단계에서 고른다. 후보가 하나뿐이면 비운다.
     */
    public record Assumption(
        String key,
        String label,
        Double value,
        String unit,
        Source source,
        List<Candidate> candidates
    ) {
        public Assumption {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /**
     * @param quote {@code PLAN}일 때 필수 — 섹션 원문의 부분문자열이어야 한다.
     * @param note  {@code DEFAULT}일 때 필수 — 왜 기본값을 썼는지 자수한다.
     */
    public record Source(
        AssumptionSourceType type, String sectionLabel, String quote, String note
    ) {}

    public record Candidate(Double value, String label, String quote) {}

    /**
     * 기획서 자체가 모순일 때(예: 단가×수량과 명시 매출이 다름) 사용자가 무엇을 진실로 볼지 고르게 한다.
     * AI가 임의로 하나를 고르지 않는다.
     */
    public record Conflict(String kind, String message, List<String> options) {
        public Conflict {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** 없으면 null — 화면은 지표만 표시한다(우아한 강등). */
    public record Narrative(
        String headline, String summary, String sensitivityNote, List<String> verifyFirst
    ) {
        public Narrative {
            verifyFirst = verifyFirst == null ? List.of() : List.copyOf(verifyFirst);
        }
    }
}
