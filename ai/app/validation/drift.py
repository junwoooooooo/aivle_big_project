# -*- coding: utf-8 -*-
"""드리프트 계약 — 「컨셉이 <b>유지된다</b>」의 정의. **정본은 이 파일 하나다.**

시장 근거로 컨셉을 다듬을 때, 어디까지가 «구체화»이고 어디부터가 «다른 사업»인지를
정하는 곳이다. 이 선이 없으면 다듬기 루프가 사용자가 고른 사업안을 조용히 다른 것으로
바꿔 놓고, 그런데도 「그 사업안을 검증했다」고 말한다 — 우리가 없애려는 실패 그 자체다.

Java 쪽이 같은 목록을 들고 있고(`ConceptDriftContract`), 두 목록이 같은지
`ai/tests/test_drift_contract_alignment.py` 가 대조한다. **한쪽만 고치면 그 테스트가 깬다.**

⚠ **가장 약한 고리는 `targetUsers`·`targetRegion` 좁히기의 기계 판정이다.** 토큰 검사는
완전하지 않다 — 「20대 여성」을 「MZ 여성」으로 바꾸면 토큰은 늘었는데 사람이 보기엔
좁힌 것일 수도, 넓힌 것일 수도 있다. **애매하면 기각한다.** 기각은 되돌릴 수 있지만
통과는 사업안이 바뀐 뒤에야 드러난다.
"""
from __future__ import annotations

import re
from typing import Any

from app.concept_portfolio_v2.hypothesis_value_contract import (
    HypothesisValueContractError,
    normalize_hypothesis_value,
)

#: 동결 — 이건 구체화가 아니라 **다른 사업**이다. 한 글자라도 바뀌면 기각.
FROZEN_FIELDS = (
    "sellerRole",
    "providerRole",
    "intermediaryRole",
    "transactionFlow",
    "paymentFlow",
    "personalDataUsage",
    "physicalActivities",
    "partnerRequirements",
    "qualificationRequirements",
    "advertisingClaims",
    "conceptName",
    "conceptDefinition",
    "coreValue",
    "operatingModel",
    "platformRole",
)

#: 가격이 움직일 수 있는 폭. 원본 대비 ±30%.
PRICE_TOLERANCE = 0.30

#: 항목을 더하거나 갈아 끼울 수 있는 개수 — 목록 한 칸당 1개.
LIST_CHANGE_ALLOWANCE = 1

#: 다듬을 수 있는 면. 값은 판정 방식이다.
REFINABLE_FIELDS = {
    "price": "PRICE_BAND",
    "channels": "LIST_ADD_OR_SWAP",
    "differentiators": "LIST_ADD_OR_SWAP",
    "targetRegion": "NARROW_ONLY",
    "targetUsers": "NARROW_ONLY",
    "featureSet": "SUBSET_ONLY",
    "revenueModel": "STRUCTURE_ONLY",
}

#: SOM 가설 둘은 **법률 중립**이라 근거 인용이 붙는 한 자유다.
#: (나머지 가설 5개는 법률 민감이라 여기 없다 — 그쪽은 DELTA_LEGAL 을 거쳐야 한다.)
FREE_WITH_EVIDENCE_FIELDS = ("preMarketSomShare", "preMarketSom")

#: BM 4칸은 자유. 단 `keyPartners` 가 동결 필드 `partnerRequirements` 와 어휘가 겹치면
#: 기각한다 — 파트너 «요건»을 바꾸는 것은 사업 구조를 바꾸는 것이다.
FREE_BM_FIELDS = ("keyActivities", "keyResources", "keyPartners", "customerRelationships")

#: 같은 것을 가리키는 **다른 이름**. 가설은 `CHANNELS`, 칸은 `channels` 다.
#:
#: 모델은 두 어휘를 한 입력 안에서 같이 본다 — 게이트 사유와 캔버스가 가설 이름(대문자)을 쓰고,
#: `refinableFields` 는 칸 이름(소문자)을 쓴다. 그래서 `fieldKey` 에 가설 이름을 적어 낸다
#: (2026-08-13 실측: 라운드 1의 유일한 제안이 `CHANNELS` 로 와서 「계약에 없는 칸」으로 기각됐다).
#:
#: ⚠ **느슨하게 받는 것이 아니다.** 둘은 1:1 로 같은 칸이고, 여기 없는 이름은 여전히 기각된다.
#: 이름이 달라서 버려진 제안은 계약이 막은 것이 아니라 **어휘가 어긋난 것**이라, 그대로 두면
#: 라운드 상한만 태우고 아무것도 못 고친 채 끝난다.
FIELD_ALIASES = {
    "TARGET_REGION": "targetRegion",
    "REVENUE_MODEL": "revenueModel",
    "PRICE": "price",
    "CHANNELS": "channels",
    "DIFFERENTIATORS": "differentiators",
    "PRE_MARKET_SOM_SHARE": "preMarketSomShare",
    "PRE_MARKET_SOM": "preMarketSom",
    "TARGET_USERS": "targetUsers",
    "FEATURE_SET": "featureSet",
}


def canonical_field(field: str) -> str:
    """가설 이름으로 온 것을 칸 이름으로 되돌린다. 모르는 이름은 **그대로 둔다**(그래야 기각된다)."""
    return FIELD_ALIASES.get(field, field)


_TOKEN = re.compile(r"[0-9A-Za-z가-힣]+")


class DriftRejection(Exception):
    """드리프트 기각. `field` 와 사람이 읽는 `reason` 을 함께 든다."""

    def __init__(self, field: str, reason: str) -> None:
        super().__init__(f"{field}: {reason}")
        self.field = field
        self.reason = reason


def tokens(value: Any) -> set[str]:
    """토큰 집합. 목록이면 원소를 이어 붙여 센다."""
    if isinstance(value, (list, tuple)):
        text = " ".join(str(item) for item in value)
    else:
        text = str(value if value is not None else "")
    return {match.group(0).lower() for match in _TOKEN.finditer(text)}


def check(field: str, current: Any, proposed: Any, frozen: dict[str, Any] | None = None) -> None:
    """한 칸의 변경을 판정한다. 통과면 조용하고, 아니면 `DriftRejection`.

    `frozen` 은 동결 필드의 현재 값이다 — `keyPartners` 겹침 검사에 쓴다.
    """
    if field in FROZEN_FIELDS:
        if current != proposed:
            raise DriftRejection(field, "동결된 칸이다 — 바꾸면 다른 사업이 된다")
        return

    if field in FREE_WITH_EVIDENCE_FIELDS or field in FREE_BM_FIELDS:
        if field == "keyPartners":
            _reject_partner_overlap(proposed, (frozen or {}).get("partnerRequirements"))
        return

    rule = REFINABLE_FIELDS.get(field)
    if rule is None:
        # **모르는 칸은 기각한다.** 계약에 없는 칸을 통과시키면 계약이 아니다.
        raise DriftRejection(field, "드리프트 계약에 없는 칸이다")

    if rule == "PRICE_BAND":
        _check_price(field, current, proposed)
    elif rule == "LIST_ADD_OR_SWAP":
        _check_list(field, current, proposed)
    elif rule == "NARROW_ONLY":
        _check_narrow(field, current, proposed)
    elif rule == "SUBSET_ONLY":
        _check_subset(field, current, proposed)
    elif rule == "STRUCTURE_ONLY":
        _check_structure(field, current, proposed)


#: 값 안에서 **금액**을 찾는 자. 「1팩 8,900원」 같은 말에서 8900 을 꺼낸다.
_MONEY = re.compile(r"\d[\d,]*")


def _amount(value: Any) -> float | None:
    """금액을 꺼낸다. 못 꺼내면 `None`.

    ⚠ 실제 컨셉의 `price` 는 **숫자가 아니라 말**이다 — 「1팩 8,900원」처럼 단위와 포장 단위가
    붙어 있다(2026-08-13 실측). 숫자만 받겠다고 하면 모델이 원본과 같은 모양으로 답해도
    전부 기각되어, 가격은 영영 못 다듬는다.

    ⚠ **첫 번째 수를 쓰지 않는다.** 「1팩 8,900원」의 첫 수는 포장 단위 1 이다.
    가장 큰 수를 금액으로 본다 — 값을 지어내지 않으면서 단위 수를 피하는 가장 단순한 규칙이다.
    """
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return float(value)
    found = _MONEY.findall(str(value if value is not None else ""))
    numbers = [float(item.replace(",", "")) for item in found if item.strip(",")]
    return max(numbers) if numbers else None


def _check_price(field: str, current: Any, proposed: Any) -> None:
    base = _amount(current)
    new = _amount(proposed)
    if base is None or new is None:
        raise DriftRejection(field, "가격에서 금액을 찾을 수 없다")
    if base <= 0:
        raise DriftRejection(field, "원본 가격이 없어 폭을 잴 수 없다")
    if abs(new - base) / base > PRICE_TOLERANCE + 1e-9:
        raise DriftRejection(
            field, f"원본 대비 ±{int(PRICE_TOLERANCE * 100)}% 를 넘는다 ({base:,.0f} → {new:,.0f})")


#: 목록을 글로 적을 때 쓰는 구분자. 실제 컨셉이 이렇게 들고 있다.
_LIST_SPLIT = re.compile(r"[,·]|\s/\s")


def as_items(value: Any) -> list[str]:
    """목록으로 편다.

    ⚠ 실제 컨셉의 `channels`·`differentiators` 는 **배열이 아니라 한 줄 글**이다 —
    「자사몰 정기구독, 대형 이커머스 입점(…), 편의점·기업형 슈퍼마켓 냉동 매대」(2026-08-13 실측).
    양쪽을 같은 모양으로 펴지 않으면 항목 수 비교가 무의미해진다 — 실제로 「뺀 것 55 · 더한 것 4」
    같은 값이 나와 멀쩡한 제안이 전부 기각됐다.

    ⚠ 괄호 안의 `·` 는 한 항목 안의 나열이라 **괄호는 통째로 둔다**.
    """
    if isinstance(value, (list, tuple)):
        return [str(item).strip() for item in value if str(item).strip()]
    text = str(value if value is not None else "")
    if not text.strip():
        return []
    # 괄호 안을 잠시 가려 두고 자른 뒤 되돌린다.
    masked, kept = [], []
    depth = 0
    for at, char in enumerate(text):
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth = max(0, depth - 1)
        # 숫자 사이의 쉼표는 천 단위다 — 자르면 「1팩 8,900원」이 두 항목이 된다.
        thousands = (char == "," and 0 < at < len(text) - 1
                     and text[at - 1].isdigit() and text[at + 1].isdigit())
        masked.append("\x00" if (depth > 0 or thousands) and char in ",·" else char)
        kept.append(char)
    pieces, cursor = [], 0
    for part in _LIST_SPLIT.split("".join(masked)):
        piece = "".join(kept[cursor:cursor + len(part)])
        cursor += len(part) + 1
        if piece.strip():
            pieces.append(piece.strip())
    return pieces


def _check_list(field: str, current: Any, proposed: Any) -> None:
    before = as_items(current)
    after = as_items(proposed)
    kept = [item for item in before if item in after]
    # 기존 항목은 **유지**되어야 한다 — 통째로 갈아 끼우는 것은 다듬기가 아니다.
    removed = len(before) - len(kept)
    added = len([item for item in after if item not in before])
    if removed > LIST_CHANGE_ALLOWANCE or added > LIST_CHANGE_ALLOWANCE:
        # ⚠ 이 문장은 **화면 「못 푼 것」에 그대로 선다.** 세는 말(「뺀 것 2 · 더한 것 2」)만
        #   적으면 사용자는 무엇을 못 했는지 모른다 — 한 번에 얼마나 바꿀 수 있는지를 먼저 쓴다.
        raise DriftRejection(
            field, f"한 번에 {LIST_CHANGE_ALLOWANCE}개까지만 더하거나 바꿀 수 있어요"
                   f" (이번 제안은 {removed}개를 빼고 {added}개를 더했어요)")


def _check_narrow(field: str, current: Any, proposed: Any) -> None:
    """좁히기만 허용. **원본에 없던 명사가 생기면 기각한다.**

    ⚠ 이 판정은 완전하지 않다(모듈 주석 참고). 그래서 **애매하면 기각**하는 쪽으로 짰다 —
    새 토큰이 하나라도 있으면 통과시키지 않는다.
    """
    before = tokens(current)
    after = tokens(proposed)
    fresh = after - before
    if fresh:
        raise DriftRejection(field, f"원본에 없던 말이 생겼다 — 좁히기만 된다 ({', '.join(sorted(fresh))})")
    if not after:
        raise DriftRejection(field, "빈 값으로 좁힐 수 없다")


def _check_subset(field: str, current: Any, proposed: Any) -> None:
    before = as_items(current)
    after = as_items(proposed)
    extra = [item for item in after if item not in before]
    if extra:
        raise DriftRejection(field, f"원본에 없던 항목이다 — 부분집합·순서 변경만 된다 ({', '.join(map(str, extra))})")


def _check_structure(field: str, current: Any, proposed: Any) -> None:
    """수익 모델은 **기존 모델을 유지**하고 세부 구조만 바꿀 수 있다.

    모델 이름을 갈아 끼우는 것(구독 → 광고)은 다른 사업이다. 이름은 원본 토큰이
    남아 있는지로 본다.
    """
    before = tokens(current)
    after = tokens(proposed)
    if before and not (before & after):
        raise DriftRejection(field, "수익 모델 자체가 바뀌었다 — 세부 구조만 바꿀 수 있다")


def _reject_partner_overlap(proposed: Any, partner_requirements: Any) -> None:
    """`keyPartners` 가 동결된 `partnerRequirements` 와 어휘가 겹치면 기각.

    BM 칸은 자유지만, 파트너 «요건»은 동결이다. 캔버스에서 요건을 슬쩍 바꾸는 길을
    열어 두면 동결이 동결이 아니게 된다.
    """
    if not partner_requirements:
        return
    overlap = tokens(proposed) & tokens(partner_requirements)
    if overlap:
        raise DriftRejection(
            "keyPartners",
            f"동결된 파트너 요건과 어휘가 겹친다 ({', '.join(sorted(overlap))})")


#: 근거를 요구하지 않는 갈래. 법률이 시킨 수정은 조항(`legalRef`)이 근거 자리를 대신한다
#: (`app/tasks/concept_refinement.py` 프롬프트가 「`evidenceIds` 는 비워도 된다」고 적는다).
_LEGAL_SOURCE = "LEGAL"


def filter_ungrounded(proposals: list[dict],
                      evidence: list[dict] | None) -> tuple[list[dict], list[dict]]:
    """**근거 계약.** 근거 없는 제안을 기각한다. `(통과분, 기각분)`.

    <b>왜 따로 있나.</b> 프롬프트는 *"시장 근거로 고치는 제안에는 근거 id가 붙어야 한다.
    근거 없는 제안은 버려진다"* 고 약속하는데 **그 검사를 하는 코드가 아무 데도 없었다** —
    `filter_proposals` 는 값의 폭만 보고, Java `requireProposals()` 는 모양만 본다.
    그래서 근거 0건인 제안이 그대로 적용됐고, 화면에는 「근거 없음」 배지를 단 채 떴다
    (실측: 가격 1팩 8,900원 → 9,500원). 「시장 근거로 다듬는다」가 문장으로만 있었다.

    <b>왜 `filter_proposals` 안에 안 넣나.</b> 기존 시험 다섯이 **근거 없는 제안**으로 값
    판정만 시험한다. 안에 넣으면 그 다섯이 깨지고, 「값 계약」과 「근거 계약」이 한 함수에
    섞여 다음 사람이 어느 쪽 때문에 기각됐는지 못 가린다.

    두 가지를 본다:
      ① `MARKET` 인데 `evidenceIds` 가 비었다 → 기각
      ② 든 id 중 **봉투에 없는 것**은 떼어 낸다. 떼고 나서 **남는 것이 0건이면 기각**한다.
         저장소 규칙 §5-5(「AI 가 ID 를 돌려주는 task 는 보낸 ID 와 대조한다」)가 여기서만
         안 지켜지고 있었다. 같은 원리의 코드가
         `research/bm/analyze.py::validate_market_evidence_ids` 에 이미 있고, **둘 다 지운다.**

    ⚠ **②는 원래 「하나라도 없으면 통째로 기각」이었다. 2026-08-15 에 뒤집었다.**
      실측(`runs-generated/p47-refine-01.json`): 편의점 도시락 판매가 **18건을 제대로 인용한**
      가격 제안(8,900 → 6,900원)이 **열아홉 번째로 지어낸 번호 하나** 때문에 죽고, 대신
      **시장 규모 38조를 근거로 「차별점」을 바꾸자던 제안**이 통과했다 — 그 38조에는
      「음·식료품 전체다 · 상한으로만 읽어라」는 경계가 붙어 있다. **게이트가 정확히
      거꾸로 걸렀다.** 지어낸 번호 하나의 벌을 진짜 근거 18건에까지 물릴 이유가 없다.

    ⚠ **제안의 «값»은 여전히 안 지운다.** 떼는 것은 가리키는 번호뿐이고
      `proposedValue`·`afterText` 는 그대로다 — 그래서 「반쪽짜리 제안」이 되지 않는다.

    ⚠ `evidence` 가 `None` 이면(재료를 못 받은 실행) ②는 **재지 않는다.** 모르는 것을
      「환각」으로 단정하면 멀쩡한 제안이 전부 기각된다. ①은 그때도 잰다.
    """
    allowed = None
    if evidence is not None:
        allowed = {str(item.get("id")) for item in evidence
                   if isinstance(item, dict) and item.get("id") is not None}

    passed: list[dict] = []
    rejected: list[dict] = []
    for proposal in proposals:
        ids = [str(x) for x in (proposal.get("evidenceIds") or [])]
        source = str(proposal.get("source") or "MARKET")
        if source == _LEGAL_SOURCE:
            if str(proposal.get("legalRef") or "").strip():
                passed.append(proposal)
            else:
                rejected.append({**proposal,
                                 "rejectionReason": "법률 근거(legalRef)가 없다"})
            continue
        if not ids:
            rejected.append({**proposal,
                             "rejectionReason": "시장 근거가 0건이다 — 근거 없는 수정은 하지 않는다"})
            continue
        if allowed is not None:
            없는 = [x for x in ids if x not in allowed]
            남는 = [x for x in ids if x in allowed]
            if 없는 and not 남는:
                rejected.append({**proposal,
                                 "rejectionReason":
                                     f"조사 결과에 없는 근거를 들었다: {', '.join(없는[:3])}"})
                continue
            if 없는:
                # 지어낸 번호만 떼고 제안은 살린다. 값은 안 건드린다.
                proposal = {**proposal, "evidenceIds": 남는}
        passed.append(proposal)
    return passed, rejected


def filter_proposals(proposals: list[dict], concept: dict) -> tuple[list[dict], list[dict]]:
    """제안 목록을 계약으로 거른다. `(통과분, 기각분)`.

    기각분은 버리지 않는다 — **다음 라운드 입력으로 되먹인다.** 왜 막혔는지를 모델에
    돌려주지 않으면 같은 제안을 3라운드 내내 반복한다.
    """
    passed: list[dict] = []
    rejected: list[dict] = []
    for proposal in proposals:
        # 가설 이름으로 온 것을 칸 이름으로 되돌린다. **판정 전에** 해야 한다 —
        # 어휘가 어긋난 제안을 계약 위반으로 세면 라운드 상한만 태운다.
        field = canonical_field(str(proposal.get("fieldKey") or ""))
        proposal = {**proposal, "fieldKey": field}
        try:
            check(field, concept.get(field), proposal.get("proposedValue"), concept)
            hypothesis_type = {
                "channels": "CHANNELS", "differentiators": "DIFFERENTIATORS",
            }.get(field)
            if hypothesis_type:
                proposal = {**proposal, "proposedValue": normalize_hypothesis_value(
                    hypothesis_type, proposal.get("proposedValue"))}
        except (DriftRejection, HypothesisValueContractError) as failure:
            reason = failure.reason if isinstance(failure, DriftRejection) else str(failure)
            rejected.append({**proposal, "rejectionReason": reason})
        else:
            passed.append(proposal)
    return passed, rejected
