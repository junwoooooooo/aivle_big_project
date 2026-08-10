"""7개 Hypothesis가 실제 사업 가설인지 snapshot 생성 전에 검증한다."""

from __future__ import annotations

import re
from typing import Any

from .language_policy import is_governance_placeholder
from .models import HypothesisDecision, HypothesisValueAssessment


_TEXT_TYPES = {"TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS"}
_TYPE_MARKERS = {
    "TARGET_REGION": ("대한민국", "한국", "전국", "국내", "수도권", "서울", "부산", "대구", "인천",
                      "광주", "대전", "울산", "세종", "경기", "강원", "충청", "전라", "경상", "제주",
                      "해외", "글로벌", "지역", "권역"),
    "REVENUE_MODEL": ("구독", "판매", "마진", "수수료", "계약", "광고", "라이선스", "이용료", "서비스료", "요금"),
    "PRICE": ("원", "만원", "무료", "정액", "건당", "구독료", "이용료", "요금", "가격대", "범위"),
    "CHANNELS": ("앱", "웹", "api", "매장", "영업", "파트너", "온라인", "오프라인", "커뮤니티", "전화"),
}


def _text(value: Any) -> str:
    return " ".join(str(item) for item in value) if isinstance(value, list) else str(value or "")


def _unresolved(value: Any) -> bool:
    text = _text(value).strip()
    return not text or is_governance_placeholder(text)


def assess_hypothesis_value(hypothesis_type: str, value: Any) -> HypothesisValueAssessment:
    if hypothesis_type in _TEXT_TYPES:
        text = _text(value).strip()
        if _unresolved(text):
            return HypothesisValueAssessment(hypothesisType=hypothesis_type, status="UNRESOLVED",
                reason="실제 사업값이 아닌 미정·미제공 placeholder입니다.")
        if hypothesis_type == "DIFFERENTIATORS":
            valid = len(re.sub(r"\s+", "", text)) >= 4
        else:
            valid = any(marker in text.casefold() for marker in _TYPE_MARKERS[hypothesis_type])
        return HypothesisValueAssessment(hypothesisType=hypothesis_type,
            status="VALID" if valid else "INVALID",
            reason="실제 사업 가설값입니다." if valid else "필드 의미에 맞는 구체적 사업값을 확인할 수 없습니다.",
            normalizedValue=text if valid else None)

    payload = value.model_dump(mode="json") if hasattr(value, "model_dump") else value
    if not isinstance(payload, dict):
        return HypothesisValueAssessment(hypothesisType=hypothesis_type, status="INVALID",
                                         reason="구조화된 SOM 가설이 아닙니다.")
    if hypothesis_type == "PRE_MARKET_SOM_SHARE":
        valid = (isinstance(payload.get("targetSharePercent"), (int, float))
                 and payload["targetSharePercent"] > 0
                 and isinstance(payload.get("horizonYears"), int) and payload["horizonYears"] > 0
                 and bool(payload.get("assumptions")))
    elif hypothesis_type == "PRE_MARKET_SOM":
        valid = (isinstance(payload.get("amount"), (int, float)) and payload["amount"] > 0
                 and bool(payload.get("currency")) and bool(payload.get("calculationBasis"))
                 and bool(payload.get("assumptions")))
    else:
        return HypothesisValueAssessment(hypothesisType=hypothesis_type, status="INVALID",
                                         reason="지원하지 않는 Hypothesis type입니다.")
    return HypothesisValueAssessment(hypothesisType=hypothesis_type,
        status="VALID" if valid else "UNRESOLVED",
        reason="수치·기간·산식 가설이 명시되었습니다." if valid else "실제 수치·기간·산식 가설이 부족합니다.",
        normalizedValue=payload if valid else None)


def assess_hypotheses(hypotheses: list[HypothesisDecision], *, use_final: bool = False
                      ) -> list[HypothesisValueAssessment]:
    return [assess_hypothesis_value(item.hypothesisType,
            item.finalValue if use_final and item.finalValue is not None else item.proposedValue)
            for item in hypotheses]
