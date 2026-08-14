"""자극 초안 — 컨셉에서 「비교할 두 안」을 뽑는다.

트윈 조사가 안 팔리던 이유는 엔진이 아니라 **입구**였다. 사용자가 속성명·양쪽 값·라벨·
가격을 손으로 치고, 「가격은 양쪽 같게, 속성은 하나만」이라는 규칙까지 스스로 지켜야 했다.
이 모듈이 그 첫 칸을 채운다.

**두 가지를 프롬프트가 아니라 구조와 코드로 막는다.**

1. *속성 하나·가격 동일* — LLM 은 «축 이름 하나 + 양쪽 값 둘» 만 돌려준다.
   속성 dict 도 가격도 LLM 이 만들지 않는다. 가격은 입력으로 받은 값을 양쪽에 그대로 얹는다.
   그래서 「속성을 둘 바꿨다」·「가격을 다르게 매겼다」가 **표현될 수 없다**.
2. *팔 수 있는 유형만* — 뽑힌 쌍을 전부 :mod:`app.twin.task_type` 의 게이트에 통과시키고
   우열형이 아니면 **버린다**. 프롬프트로 부탁하지 않는다. 0쌍이면 정직하게 실패한다.

산출 모양은 화면의 편집기(`StimulusEditor.jsx`)가 그대로 먹는 모양이다 —
초안을 고른 뒤 손으로 다듬는 길이 이어져야 하기 때문이다.

⚠ **부동소수점 금지.** 가격은 원 단위 정수다(`TwinSurveyInputFactory` 참조).
"""

import json

from pydantic import Field, ValidationError, model_validator

from app.providers import ProviderFailure, execute_structured_prompt
from app.twin.models import Pair, Side, StrictModel
from app.twin.task_type import SERVICEABLE, classify

__all__ = ["execute_twin_stimulus_draft"]

#: LLM 에게 요구하는 후보 수. 게이트가 몇 개를 버리므로 필요한 것보다 넉넉히 받는다.
CANDIDATES_MIN = 4
CANDIDATES_MAX = 6
#: 돌려주는 쌍의 상한. `TwinSurveyInput.pairs` 가 4개까지다.
DRAFT_MAX = 4

SYSTEM_PROMPT = """너는 선택형 설문의 «자극»을 설계한다. 두 상품안을 나란히 놓고 어느 쪽이
이기는지 묻는 질문이다. 한국어로만 쓴다.

규칙 — 어기면 그 쌍은 버려진다.
1. 한 쌍은 **속성 하나**만 대비한다. axis 는 그 속성의 이름이고, X.value 와 Y.value 는
   그 속성의 두 값이다. 두 값은 서로 달라야 한다.
2. axis 는 주어진 featureSet·differentiators 안에 실제로 있는 것에서 고른다.
   컨셉에 없는 기능을 지어내지 않는다.
3. **윤리·환경·인증·지속가능성·유기농 같은 가치 축을 쓰지 않는다.** 이 축은 측정 성적이
   없어 영구히 제공하지 않는다.
4. 가격은 쓰지 않는다. 가격은 시스템이 양쪽에 같은 값으로 얹는다.
5. X 와 Y 는 어느 쪽이 이길지 **명백한** 대비여야 한다. 팽팽한 대비는 측정 한계 이하라
   답을 낼 수 없다.
6. label 은 응답자에게 보일 짧은 이름이다(예: «신선 보관형», «냉동 보관형»).
   axis 값을 그대로 쓰지 말고 상품처럼 읽히게 쓴다.
7. rationale 은 이 대비를 왜 물을 만한지 한 문장으로 쓴다. 답을 예측하지 않는다.

situation 은 응답자가 선택하는 상황 한 문장이다. 상품을 고르는 장면을 쓰되
어느 쪽이 좋다는 암시를 넣지 않는다."""


class DraftInput(StrictModel):
    """컨셉에서 가져온 재료. **스냅샷 전체가 아니다** — 필요한 칸만 받는다.

    재료를 얻는 길이 둘이다. 시장조사(`research.pipeline`)와 **같은 규율**을 쓴다:

    · 백엔드가 마켓 시드 스냅샷에서 꺼내 보낸 재료 — 정상 여정
    · `conceptId` 이름표만 온 경우 — AI 서버가 들고 있는 **견본 컨셉**에서 꺼낸다

    **명시 재료가 항상 이긴다.** 이름표는 컨셉 파이프라인이 아직 안 찬 환경에서
    이 단계를 시연·시험하기 위한 길이고, 조용한 기본값을 만들지는 않는다 —
    둘 다 없으면 실패한다.
    """

    conceptId: str | None = Field(default=None, max_length=64)
    conceptName: str = Field(default="", max_length=200)
    targetUsers: str = Field(default="", max_length=1000)
    problemScenario: str = Field(default="", max_length=2000)
    featureSet: list[str] = Field(default_factory=list, max_length=30)
    #: 자유문장이다. 확정 가설의 값이 텍스트라 그 모양 그대로 받는다
    #: (`ConceptSelectionActionCompletionService.validateValue`).
    differentiators: str = Field(default="", max_length=2000)
    #: 원 단위 정수 또는 null. 확정 가설의 가격은 «월 9,900원» 같은 자유문장이라
    #: 백엔드가 깨끗하게 읽히는 것만 정수로 넘기고 나머지는 null 이다.
    priceKrw: int | None = Field(default=None, strict=True, ge=0, le=100_000_000)

    @model_validator(mode="after")
    def material_or_label(self):
        if not self.conceptName.strip() and not (self.conceptId or "").strip():
            raise ValueError("conceptName 또는 conceptId 중 하나는 있어야 한다")
        return self


class DraftSide(StrictModel):
    """LLM 이 채우는 한쪽. **속성 dict 도 가격도 여기 없다** — 시스템이 조립한다."""

    label: str = Field(min_length=1, max_length=60)
    value: str = Field(min_length=1, max_length=120)


class DraftPair(StrictModel):
    axis: str = Field(min_length=1, max_length=40)
    X: DraftSide
    Y: DraftSide
    rationale: str = Field(min_length=1, max_length=300)


class DraftProviderResult(StrictModel):
    situation: str = Field(min_length=5, max_length=300)
    pairs: list[DraftPair] = Field(min_length=1, max_length=CANDIDATES_MAX)


def _preset(concept_id: str) -> DraftInput:
    """견본 컨셉 이름표를 재료로 편다. **시장조사와 같은 표를 쓴다**
    (`research.pipeline.CONCEPTS`) — 여기서 따로 들고 있으면 두 화면이 다른 컨셉을 보게 된다.

    import 를 함수 안에서 하는 이유: `research` 는 sys.path 를 만지는 무거운 패키지라
    twin 모듈이 그것 없이도 import·테스트되어야 한다(`app/twin/__init__.py` 와 같은 규율).
    """
    import io
    import json
    import os

    from app.research.pipeline import CONCEPTS
    from app.research.runner import RESEARCH_HOME

    preset = CONCEPTS.get(concept_id.strip())
    if preset is None:
        raise ProviderFailure(
            "INVALID_REQUEST", "TWIN_STIMULUS_CONCEPT_UNKNOWN", 422, False,
            safe_diagnostics={"known": sorted(CONCEPTS)})
    with io.open(os.path.join(RESEARCH_HOME, preset[0]), encoding="utf-8") as handle:
        concept = json.load(handle)

    # 견본 컨셉의 가격은 대개 null 이다. **실수면 버린다** — 원 단위 정수만 자극에 앉힌다.
    price = concept.get("price_hypothesis_krw")
    return DraftInput(
        conceptId=concept_id,
        conceptName=str(concept.get("name") or "").strip(),
        targetUsers=str(concept.get("target") or "").strip(),
        problemScenario=str(concept.get("problem") or "").strip(),
        # 견본에는 featureSet 이 없다. 차별점 자리에 solution 자유문장이 온다 —
        # 축을 고를 재료로는 그것으로 충분하고, 없는 기능을 지어내라고 시키지 않는다.
        differentiators=str(concept.get("solution") or "").strip(),
        priceKrw=price if isinstance(price, int) and not isinstance(price, bool) else None,
    )


def _user_message(request: DraftInput) -> str:
    payload = {
        "conceptName": request.conceptName,
        "targetUsers": request.targetUsers,
        "problemScenario": request.problemScenario,
        "featureSet": request.featureSet,
        "differentiators": request.differentiators,
        "candidatesWanted": CANDIDATES_MIN,
    }
    return json.dumps(payload, ensure_ascii=False, sort_keys=True)


def _assemble(draft: DraftPair, pair_id: str, price: int | None) -> Pair | None:
    """«축 하나 + 값 둘» 을 자극 한 쌍으로 세운다.

    가격은 **입력값을 양쪽에 그대로** 얹는다 — 그래서 가격형(지불의사)이 만들어질 수 없다.
    `Side`·`Pair` 의 제약이 여기서 걸리면 그 쌍만 버린다(전체를 실패시키지 않는다).
    """
    try:
        return Pair(
            pairId=pair_id,
            X=Side(label=draft.X.label, attrs={draft.axis: draft.X.value}, priceKrw=price),
            Y=Side(label=draft.Y.label, attrs={draft.axis: draft.Y.value}, priceKrw=price),
        )
    except ValidationError:
        return None


def _refuse(dropped: list[dict]) -> ProviderFailure:
    return ProviderFailure(
        "INVALID_REQUEST", "TWIN_STIMULUS_NO_SERVICEABLE_PAIR", 422, False,
        safe_diagnostics={"dropped": dropped[:CANDIDATES_MAX]})


async def execute_twin_stimulus_draft(payload: dict) -> dict:
    try:
        request = DraftInput.model_validate(payload)
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            validation_fields=[{"field": ".".join(str(p) for p in e["loc"]),
                                "reason": e["type"]} for e in failure.errors()[:12]]) from failure

    # 재료가 없으면 이름표를 편다. **명시 재료가 항상 이긴다.**
    if not request.conceptName.strip():
        request = _preset(request.conceptId or "")

    raw = await execute_structured_prompt(
        SYSTEM_PROMPT,
        _user_message(request),
        response_schema=DraftProviderResult.model_json_schema(),
        schema_name="twin_stimulus_draft_v1",
        task_type="TWIN_STIMULUS_DRAFT",
    )
    try:
        provider = DraftProviderResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure

    # ── 판매 경계 — 프롬프트가 아니라 코드가 거른다 ──────────────────────
    kept: list[dict] = []
    dropped: list[dict] = []
    for draft in provider.pairs:
        # pairId 는 **여기서 매긴다.** LLM 이 붙이게 두면 중복이 나오고, 중복은
        # `TwinSurveyInput.pair_ids_unique` 에서 실행 시점에 터진다.
        pair = _assemble(draft, f"P{len(kept) + 1}", request.priceKrw)
        if pair is None:
            dropped.append({"axis": draft.axis, "taskType": "MALFORMED",
                            "reason": "두 안을 같은 속성 공간으로 세울 수 없다."})
            continue
        verdict = classify(pair.as_stimulus())
        if verdict.task_type not in SERVICEABLE:
            dropped.append({"axis": draft.axis, "taskType": verdict.task_type,
                            "reason": verdict.reason})
            continue
        kept.append({
            "pairId": pair.pairId,
            "axis": draft.axis,
            "rationale": draft.rationale,
            "X": {"label": pair.X.label, "attrs": dict(pair.X.attrs), "priceKrw": pair.X.priceKrw},
            "Y": {"label": pair.Y.label, "attrs": dict(pair.Y.attrs), "priceKrw": pair.Y.priceKrw},
        })
        if len(kept) >= DRAFT_MAX:
            break

    if not kept:
        # 정직한 실패다. 빈 초안을 돌려주면 화면은 「만들었는데 비었다」로 읽고,
        # 사용자는 무엇을 고쳐야 하는지 모른 채 다시 누른다.
        raise _refuse(dropped)

    return {"situation": provider.situation, "pairs": kept, "dropped": dropped}
