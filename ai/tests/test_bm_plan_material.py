# -*- coding: utf-8 -*-
"""컨셉의 `_bm_plan` 이 `concept_snapshot` 까지 도달하는지 — **계획 5칸의 급수관 검사.**

왜 따로 두나: BM 캔버스 9칸은 성격이 둘로 갈린다(`research2/harness/vocab.json` §canvas).

    측정·판정 4칸  고객 세그먼트·가치 제안·채널·수익원   ← 조사 슬롯이 근거로 채운다
    계획     5칸  고객 관계·핵심 자원·핵심 활동·핵심 파트너·비용 구조
                                                     ← `concept_snapshot`·`execution_constraints`

계획 5칸은 슬롯이 «불필요»하다. 그래서 재료가 안 실려도 **조사는 성공하고 칸만 빈다** —
BM 프롬프트가 「입력에 없으면 content=[]」 라고 지시하기 때문에 모델은 규칙대로 동작한 것이고,
로그에도 예외에도 흔적이 남지 않는다. 실제로 그 상태로 오래 굴러갔다.

그 침묵을 깨는 것이 이 파일이다.
"""
from __future__ import annotations

import importlib.util
import io
import json
import os
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
AI_ROOT = os.path.dirname(HERE)
RESEARCH2 = os.path.join(AI_ROOT, "app", "research", "research2")
ADAPTER = os.path.join(RESEARCH2, "service", "bm_adapter.py")
CONCEPT = os.path.join(RESEARCH2, "data", "concept_beauty-noshow.json")

sys.path.insert(0, AI_ROOT)


def _load_adapter():
    """`test_bm_contract_parity` 와 같은 방식 — research2 는 패키지가 아니라 파일로 연다."""
    spec = importlib.util.spec_from_file_location("bm_adapter_plan_material", ADAPTER)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


adapter = _load_adapter()


@pytest.fixture(scope="module")
def concept() -> dict:
    with io.open(CONCEPT, encoding="utf-8") as fp:
        return json.load(fp)


def test_plan_block_is_underscored(concept):
    """`_bm_plan` 은 **`_` 로 시작해야 한다.**

    `run.py:37 load_concept` 이 `_` 키를 걸러낸다. 이름에서 밑줄을 빼면 이 서술이
    A1·A3 수집 프롬프트로 넘어가 절대 규칙 6(가설·서술은 수집에 넣지 않는다)이 깨진다 —
    그러면 엔진이 **자기가 쓴 문장을 찾으러 나간다.**
    """
    assert "_bm_plan" in concept
    assert "bm_plan" not in concept


def test_snapshot_carries_every_plan_field(concept):
    """`_bm_plan` 의 일곱 칸이 하나도 빠짐없이 `concept_snapshot` 에 실린다."""
    snapshot = adapter._snapshot(concept)
    dumped = snapshot.model_dump(mode="json")
    plan = concept["_bm_plan"]

    for key in adapter.PLAN_FIELDS:
        assert dumped.get(key), f"{key} 가 concept_snapshot 까지 오지 않았다"

    assert dumped["revenue_model"] == plan["revenue_model"]
    assert dumped["channel"] == plan["channel"]
    assert dumped["key_activities"] == plan["key_activities"]
    assert dumped["key_resources"] == plan["key_resources"]
    assert dumped["key_partners"] == plan["key_partners"]
    assert dumped["customer_relationship"] == plan["customer_relationship"]


def test_differentiation_does_not_come_from_hypotheses(concept):
    """차별점을 `hypotheses` 에서 읽으면 **항상 빈다.**

    그 필드는 규칙 6 때문에 비어 있어야 하고(`research_view()` 가 수집으로 그대로 넘긴다),
    실제로 비어 있다. 예전 어댑터가 거기서 읽어 `differentiation` 이 늘 `[]` 였다.
    """
    assert concept["hypotheses"] == [], "이 컨셉의 hypotheses 는 비어 있어야 한다(규칙 6)"

    snapshot = adapter._snapshot(concept)
    assert snapshot.differentiation, "차별점이 비었다 — hypotheses 를 다시 읽고 있는가"
    assert snapshot.differentiation == concept["_bm_plan"]["differentiation"][:6]


def test_core_value_is_not_a_copy_of_solution(concept):
    """같은 문장이 두 칸에 있으면 둘 다 신호가 없다. 핵심 가치는 별도 자리에서 온다."""
    snapshot = adapter._snapshot(concept)
    assert snapshot.core_value
    assert snapshot.core_value != snapshot.solution


def test_execution_constraints_are_integers(concept):
    """비용 구조 칸의 원천. **부동소수점 금지** — canonical hash 가 런타임에 거부한다."""
    constraints = adapter.execution_constraints_of(concept)
    assert constraints == {"budget_krw": 5000000, "months": 10, "team": 2}
    assert all(isinstance(v, int) for v in constraints.values())


def test_missing_plan_block_is_empty_not_invented():
    """재료가 없으면 **빈 채로 둔다.** 지어내면 계획이 관측처럼 읽힌다."""
    snapshot = adapter._snapshot({"name": "n", "solution": "s"})
    assert snapshot.differentiation == []
    assert snapshot.revenue_model is None
    assert snapshot.channel is None
    assert adapter.execution_constraints_of({}) == {}


# ══════════════════════════════════════════════════════════════
# 제품 경로 — 컨셉 생성 산출물이 계획 칸의 재료가 된다
# ══════════════════════════════════════════════════════════════
#: `ConceptCandidateResult` 의 모양만 흉내 낸 최소 컨셉. `_bm_plan` 이 **없다** —
#: 제품에서 컨셉은 컨셉 생성 단계가 만들고, 그 산출물엔 `_bm_plan` 이라는 칸이 없다.
GENERATED_CONCEPT = {
    "name": "노쇼 관리",
    "solution": "예약 보증금으로 노쇼를 줄인다",
    "revenueModel": "월 구독",
    "channels": ["네이버 예약 제휴", "직접 영업"],
    "differentiators": ["보증금 자동 환불"],
    "operatingModel": "예약 데이터를 받아 보증금을 자동 청구한다",
    "transactionFlow": ["예약 접수", "보증금 청구", "방문 시 환불"],
    "platformRole": "예약·결제 중개",
    "featureSet": ["보증금 결제", "노쇼 통계"],
    "partnerModel": "예약 플랫폼과 API 제휴",
    "partnerRequirements": ["PG 계약", "예약 플랫폼 API 승인"],
}


def test_generated_concept_fills_the_plan_cells():
    """⭐ 컨셉 생성이 이미 만드는 필드가 계획 칸까지 온다.

    이 배선이 없던 동안, 견본 `beauty-noshow` 를 뺀 **모든** 컨셉에서 활동·자원·파트너가
    말없이 비었다. 필드는 검증되고 저장되고 있었는데 이 파이프라인이 그 이름을 몰랐다.
    """
    plan = adapter.plan_material_of(GENERATED_CONCEPT)
    assert plan["key_activities"] == [
        "예약 데이터를 받아 보증금을 자동 청구한다", "예약 접수", "보증금 청구", "방문 시 환불"]
    assert plan["key_resources"] == ["예약·결제 중개", "보증금 결제", "노쇼 통계"]
    assert plan["key_partners"] == ["예약 플랫폼과 API 제휴", "PG 계약", "예약 플랫폼 API 승인"]
    assert plan["revenue_model"] == "월 구독"
    assert plan["channel"] == ["네이버 예약 제휴", "직접 영업"]
    assert plan["differentiation"] == ["보증금 자동 환불"]


def test_generated_concept_reaches_the_snapshot_as_extra_fields():
    """스냅샷까지 실려야 모델이 본다 — 표만 맞고 배선이 없으면 화면은 그대로 빈다."""
    dumped = adapter._snapshot(GENERATED_CONCEPT).model_dump()
    for key in ("key_activities", "key_resources", "key_partners"):
        assert dumped.get(key), f"{key} 가 스냅샷에 없다"
    assert dumped["revenue_model"] == "월 구독"


def test_customer_relationship_stays_empty_until_the_schema_has_it():
    """**유추하지 않는다.** 컨셉 스키마에 대응 필드가 없고, `solutionMechanism` 에서
    지어내면 `bm/prompt.py` §5(「입력에 명시된 것만」)를 어긴다. 빈 것이 정답이다."""
    plan = adapter.plan_material_of(GENERATED_CONCEPT)
    assert "customer_relationship" not in plan


def test_hand_written_plan_block_wins_over_derivation(concept):
    """견본이 들고 있는 `_bm_plan` 을 파생으로 덮지 않는다 — 견본 경로는 무변경이다."""
    doctored = {**concept, "operatingModel": "덮어쓰면 안 되는 값"}
    plan = adapter.plan_material_of(doctored)
    assert plan["key_activities"] == concept["_bm_plan"]["key_activities"]


# ══════════════════════════════════════════════════════════════
# 사용자가 BM 앞 화면에서 채운 계획 — 컨셉 계약이 주지 않는 넷
# ══════════════════════════════════════════════════════════════
def test_user_plan_beats_the_sample_stub(concept):
    """⭐ **사람이 방금 쓴 것이 파일의 스텁을 이긴다.**

    예전에는 `_bm_plan` 이 최우선이라, 견본 컨셉에서 사용자가 같은 칸을 채워도 **조용히
    무시**됐다. 화면이 「입력을 받았다」고 말해 놓고 그 값을 안 쓰는 것은 거짓말이다.
    """
    assert concept["_bm_plan"]["key_activities"], "견본 전제 확인 — 스텁이 있어야 시험이 된다"
    doctored = {**concept, adapter.USER_PLAN_KEY: {
        "key_activities": ["사용자가 쓴 활동"],
        "customer_relationship": "사용자가 쓴 고객 관계",
    }}
    plan = adapter.plan_material_of(doctored)
    assert plan["key_activities"] == ["사용자가 쓴 활동"]
    assert plan["customer_relationship"] == "사용자가 쓴 고객 관계"
    # 사용자가 안 건드린 칸은 그대로 — 덮어쓰지 않는다.
    assert plan["key_resources"] == concept["_bm_plan"]["key_resources"]


def test_user_plan_reaches_the_snapshot(concept):
    """스냅샷까지 실려야 모델이 본다 — 우선순위만 맞고 배선이 없으면 화면은 그대로 빈다."""
    doctored = {**concept, adapter.USER_PLAN_KEY: {"key_partners": ["결제 대행사"]}}
    dumped = adapter._snapshot(doctored).model_dump()
    assert dumped["key_partners"] == ["결제 대행사"]


def test_empty_user_values_do_not_erase_what_exists(concept):
    """⚠ 빈 값으로 덮지 않는다 — 「안 썼다」가 「지웠다」가 되면 안 된다."""
    doctored = {**concept, adapter.USER_PLAN_KEY: {
        "key_activities": [], "customer_relationship": ""}}
    plan = adapter.plan_material_of(doctored)
    assert plan["key_activities"] == concept["_bm_plan"]["key_activities"]


def test_user_plan_key_is_underscored():
    """`_` 로 시작해야 `run.py::load_concept` 이 걸러 수집 프롬프트로 안 샌다(절대 규칙 6).

    사용자가 쓴 계획이 A1·A3 수집 프롬프트에 들어가면 모델이 **그 계획을 확인해 주는
    자료만** 찾아오는 자기확인 회로가 된다 — `hypotheses` 를 비우게 하는 이유와 같다.
    """
    assert adapter.USER_PLAN_KEY.startswith("_")
