package com.aivle.backend.analysis.financial.entity;

/**
 * 재무 분석 도메인 타입.
 *
 * <p>{@link Verdict}는 타당성의 판정 문법(PROMISING/CONDITIONAL/HIGH_RISK/INSUFFICIENT_INFORMATION)을
 * 그대로 따르되 별도 enum으로 둔다 — 임계값이 다르고, 재무가 타당성 패키지에 의존하지 않게 하기 위함이다.
 */
public final class FinancialTypes {
    private FinancialTypes() {}

    public enum Verdict { PROMISING, CONDITIONAL, HIGH_RISK, INSUFFICIENT_INFORMATION }

    /** 가정 집합의 확정 상태. 값은 assumptions_json 안에 실린다(별도 컬럼 아님). */
    public enum AssumptionState { NEEDS_ASSUMPTIONS, CONFIRMED }

    /** 가정 하나의 출처. 화면에서 배지로 자수한다. */
    public enum AssumptionSourceType {
        /** 기획서 인용 — quote는 원문 부분문자열 검증을 통과해야 한다. */
        PLAN,
        /** 기획서에 없어 시스템 기본값을 적용 — note 필수. */
        DEFAULT,
        /** 사용자가 확정 단계에서 채우거나 고침. */
        USER
    }

    /**
     * 지표를 계산하지 못한 이유. null 옆에 함께 실어 화면이 "왜 못 냈는지"를 말할 수 있게 한다.
     * 계산 불가는 예외가 아니라 정상 출력이다(설계 원칙 6).
     */
    public enum UnavailableReason {
        /** 필요한 가정이 아직 없다. 어떤 키인지는 missingKeys로 함께 전달한다. */
        MISSING_ASSUMPTION,
        /** 건당 공헌이익이 0 이하 — 팔수록 손해라 손익분기가 존재하지 않는다. */
        NON_POSITIVE_CONTRIBUTION,
        /** 월 영업이익이 0 이하 — 초기투자를 회수할 흐름이 없다. */
        NON_POSITIVE_MONTHLY_PROFIT,
        /** 부호가 바뀌지 않아 IRR 해가 존재하지 않는다. */
        NO_SIGN_CHANGE
    }
}
