import pytest
from pydantic import ValidationError

from app.tasks.marketing_strategy.models import (
    MarketingStrategyInput,
    MarketingStrategyResult,
)


def valid_result() -> dict:
    return {
        "contract":
            "marketing-strategy-result-v1",
        "executiveSummary":
            "검증 결과를 기반으로 초기 타깃에 집중합니다.",
        "targetCustomers": [
            "검증된 핵심 고객군",
        ],
        "positioning":
            "검증된 문제와 차별점을 연결한 포지셔닝",
        "coreMessages": [
            "검증된 핵심 가치 메시지",
        ],
        "channelStrategies": [
            {
                "channel": "Instagram",
                "objective": "초기 인지도 확보",
                "audience": "검증된 타깃 고객",
                "actions": [
                    "제품 사용 상황 콘텐츠 제작",
                ],
                "kpis": [
                    "도달 및 저장 반응 측정",
                ],
                "rationale":
                    "시장 및 패널 조사 결과를 반영",
            },
        ],
        "contentPillars": [
            "문제 공감",
            "제품 가치",
        ],
        "campaignRoadmap": [
            {
                "phase": "출시 전",
                "objective": "메시지 검증",
                "actions": [
                    "핵심 메시지 비교",
                ],
                "kpis": [
                    "메시지별 반응 비교",
                ],
            },
        ],
        "budgetGuidelines": [
            "재무 분석의 가용 예산 범위 준수",
        ],
        "risks": [
            "패널 결과를 전체 시장으로 일반화하지 않음",
        ],
        "evidenceRefs": [
            "MARKET:1",
        ],
    }


def test_result_contract_accepts_valid_value():
    value = MarketingStrategyResult.model_validate(
        valid_result()
    )

    assert (
        value.contract
        == "marketing-strategy-result-v1"
    )


def test_result_contract_rejects_extra_field():
    value = valid_result()
    value["unexpected"] = True

    with pytest.raises(ValidationError):
        MarketingStrategyResult.model_validate(
            value
        )


def test_input_rejects_missing_sources():
    with pytest.raises(ValidationError):
        MarketingStrategyInput.model_validate(
            {
                "contract":
                    "marketing-strategy-input-v1",
                "projectId": 1,
                "sourceManifestHash":
                    "sha256:" + ("a" * 64),
                "sourceManifest": [],
                "sources": {},
            }
        )