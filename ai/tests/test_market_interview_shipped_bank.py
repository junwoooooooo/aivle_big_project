from collections import Counter
from pathlib import Path
import asyncio

import pytest

from app.providers import ProviderFailure
from app.tasks.market_interview.models import TargetCriteria
from app.tasks.market_interview.models import MarketInterviewInput
from app.tasks.market_interview.deep_engine import execute_deep_interview
from app.tasks.market_interview.panel_sampling import draw_panel
from app.twin import bank as bank_module


TARGET_TEXT = "운동 부족 직장인과 평소 활동량이 적은 동네 주민 (대한민국)"


def criteria(**overrides) -> TargetCriteria:
    values = {
        "ageMin": 0, "ageMax": 0, "genders": [],
        "householdSizeMin": 0, "householdSizeMax": 0,
        "regions": ["대한민국"], "incomeKeywords": [],
        "jobKeywords": ["직장인", "운동 부족"],
        "hasChildren": 0, "householdRoles": [],
    }
    values.update(overrides)
    return TargetCriteria.model_validate(values)


@pytest.fixture(scope="module")
def shipped_bank():
    directory = Path(__file__).resolve().parents[1] / "app" / "twin" / "bank"
    previous = bank_module.os.environ.get("TWIN_BANK_DIR")
    bank_module.os.environ["TWIN_BANK_DIR"] = str(directory)
    bank_module._cache = None
    try:
        cards, frame = bank_module.load()
        yield cards, frame
    finally:
        bank_module._cache = None
        if previous is None:
            bank_module.os.environ.pop("TWIN_BANK_DIR", None)
        else:
            bank_module.os.environ["TWIN_BANK_DIR"] = previous


@pytest.mark.parametrize("size,expected", [(20, (16, 4)), (40, (32, 8)), (80, (64, 16))])
def test_exercise_matching_uses_real_bank_and_deterministic_target_comparison(
        shipped_bank, size, expected):
    cards, frame = shipped_bank
    assert len(cards) == len(frame) == 8_604

    first, report = draw_panel(cards, frame, criteria(), size, TARGET_TEXT)
    second, replay = draw_panel(cards, frame, criteria(), size, TARGET_TEXT)
    groups = Counter(row["group"] for row in first)

    assert report["rawMatched"] == 0
    assert report["matched"] > 0
    assert report["relaxationLevel"] == 3
    assert (groups["TARGET"], groups["COMPARISON"]) == expected
    assert len(first) == size
    assert [(row["cardText"], row["group"]) for row in first] == [
        (row["cardText"], row["group"]) for row in second]
    assert replay["matched"] == report["matched"]


def test_impossible_explicit_hard_target_remains_unavailable(shipped_bank):
    cards, frame = shipped_bank
    impossible = criteria(ageMin=119, ageMax=120, regions=["가상특별시"], jobKeywords=[])

    with pytest.raises(ProviderFailure) as failure:
        draw_panel(cards, frame, impossible, 20, "만 119세 이상 가상특별시 거주자")

    assert failure.value.reason == "MARKET_INTERVIEW_TARGET_UNAVAILABLE"
    assert failure.value.safe_diagnostics["targetMatches"] == 0


def test_full_v2_input_reaches_respondent_provider_boundary_with_shipped_bank(shipped_bank):
    calls = Counter()

    async def provider_boundary(_system, _user, **kwargs):
        name = kwargs["schema_name"]
        calls[name] += 1
        if name == "market_interview_target_criteria_v2":
            return {"criteria": criteria().model_dump(mode="json")}
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE",
                              503, False)

    value = MarketInterviewInput.model_validate({
        "contract": "market-interview-input-v2", "schemaVersion": "2.0", "synthetic": True,
        "sampleSize": 20,
        "source": {"marketSeedSnapshotId": "seed-offline", "selectionId": 31,
                   "selectionRevision": 4, "marketSeedSnapshotHash": "sha256:" + "a" * 64,
                   "bmPlanRevision": 3},
        "selectedConcept": {
            "identity": {"conceptName": "동네 운동 파트너 매칭", "targetUsers": TARGET_TEXT},
            "solution": {"problemScenario": "혼자서는 운동을 지속하기 어렵다",
                         "featureSet": ["동네 운동 파트너 매칭"]}},
        "validatedHypotheses": {"targetRegion": {"value": "대한민국"}},
        "businessModel": {"plan": {}, "constraints": {}},
        "boundaries": ["실제 고객 조사가 아니다.", "통계를 추론하지 않는다.", "사업안을 변경하지 않는다."],
    })

    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(execute_deep_interview(value, provider_boundary))

    assert failure.value.reason == "MARKET_INTERVIEW_USABLE_SAMPLE_INSUFFICIENT"
    assert calls["market_interview_target_criteria_v2"] == 1
    assert calls["market_interview_answer_v2"] == 20
