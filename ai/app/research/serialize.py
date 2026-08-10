# -*- coding: utf-8 -*-
"""원장·판정·카드 → **계약 봉투**. 번역이 일어나는 곳은 여기 **한 곳뿐**이다.

왜 한 곳인가: 이 저장소에서 **「같은 물음을 두 곳이 각자 푼다」가 여섯 번** 갈라졌다
(판 ⑫ 키 · ⑬ 라우팅 · ⑯ 별칭 · ㉒ 프롬프트-게이트 · ㉕ 축 · ㉙ 핀). 한글 키를 camelCase 로
옮기는 일도 정확히 그 모양이라, 두 번째 자리를 만들지 않는다.

**allowlist 다.** 엔진 원장에는 `prompt`·`rawProviderResponse`·본문 절단분처럼 **나가면 안 되는
것**이 섞여 있다. 그래서 「지울 것을 고르는」 방식(denylist)이 아니라 **「실을 것을 적는」**
방식으로 짠다 — 원장에 새 칸이 생겨도 조용히 새어 나가지 않는다.

정본은 `backend/.../taskrun/contract/MarketResearchContract.java` 다. 필드 집합이 어긋나면
그쪽이 `RESULT_SCHEMA_INVALID` 로 막는다 — **여기 목록과 그쪽 `Set.of(...)` 는 같아야 한다.**
"""
from __future__ import annotations

from typing import Any

# ── 계약이 허용하는 값 (자바 `MarketResearchContract` 와 같은 목록) ──────────────
GRADES = ("확정", "실무 신뢰", "추정", "근거 없음")
EVIDENCE_KINDS = ("관측", "계산")
SCORE_STATES = ("FILLED", "PARTIAL", "MISSING", "REPORTED")
SUBJECTS = ("MARKET_SIZE", "GROWTH", "COMPETITOR", "PRICE", "DEMAND",
            "CALCULATION", "NOT_FOUND")

#: 성적표 상태 한글 → 계약 어휘. `—`(못 찾은 것)은 **점수가 아니라 보고**라 따로 있다.
_STATE = {"채워짐": "FILLED", "부분": "PARTIAL", "미확보": "MISSING", "—": "REPORTED"}

#: 성적표 과목 키 → 계약 과목. 순서가 곧 화면 순서다.
_SUBJECT = {
    "1_시장크기": "MARKET_SIZE", "2_성장률": "GROWTH", "3_경쟁사": "COMPETITOR",
    "4_가격": "PRICE", "5_수요": "DEMAND", "6_계산": "CALCULATION",
    "7_못찾은것": "NOT_FOUND",
}

#: 근거 카드 한글 키 → 계약 키. **이 표가 evidence 의 allowlist 다** —
#: 여기 없는 카드 칸(`슬롯`·`채택`·`연도`·`약한_고리` …)은 나가지 않는다.
_EVIDENCE = {
    "카드_id": "id", "종류": "kind", "계량": "metric", "주제": "subject",
    "기간": "period", "값": "value", "단위": "unit",
    "등급": "grade", "등급_근거": "gradeReason",
    "출처_url": "sourceUrl", "kind": "sourceKind", "조회일": "retrievedAt",
    "인용": "quote", "식": "formula", "입력": "inputs",
    "재료_카드_id": "materialIds", "가정": "assumptions",
}

#: 카드가 경계를 담는 칸들. **하나라도 빠뜨리면 §4 위반**이다 — 경계는 값과 같이 옮긴다.
_CAVEAT_KEYS = ("경계", "경계_proxy", "상한_울타리")

#: 「못 찾은 것」 갈래. **사용자의 다음 행동이 다르면 다른 갈래다.**
#:   NOT_YET   더 찾으면 나올 수 있다        CONFIRMED_ABSENT  찾아도 없다 — 종착이다
#:   ASSUMED   식을 가정으로 메웠다          SCREENED_OUT      찾았지만 규격 미달로 걸렀다
#:   DIVERGED  값이 갈렸다 — 그 자체가 결과다
#: 원장 키 목록의 정본은 `research2/schema.py:NOT_FOUND_KEYS` 이고, 여기 표와 갈리면
#: `test_not_found_taxonomy` 가 빨개진다 — 사본을 두는 대신 **갈라지면 터지게** 한다.
_NOT_FOUND = {
    "empty_slots": "NOT_YET",
    "thin_slots": "NOT_YET",
    "retry_hints": "NOT_YET",
    "url_filtered": "NOT_YET",
    "unknown_error_codes": "NOT_YET",
    "unfilled_vars": "ASSUMED",
    "suspect_var": "ASSUMED",
    "independent_topdown_blocked": "CONFIRMED_ABSENT",
    "자료_부재_확정": "CONFIRMED_ABSENT",
    "adapters": "CONFIRMED_ABSENT",
    "off_slot": "SCREENED_OUT",
    "contradictions": "DIVERGED",
    "unit_mismatch": "DIVERGED",
    "range_capped": "DIVERGED",
    "skipped_checks": "DIVERGED",
}

#: ⑦행 한 줄 요약에 쓰는 갈래 이름.
_NOT_FOUND_GROUP_LABEL = {
    "NOT_YET": "아직 못 채운 것", "ASSUMED": "가정으로 메운 변수",
    "CONFIRMED_ABSENT": "찾아도 없는 것", "SCREENED_OUT": "걸러낸 것",
    "DIVERGED": "값이 갈린 것",
}


class ContractDrift(RuntimeError):
    """엔진이 계약 밖 값을 냈다. **조용히 고치지 않는다** — 고치면 어디서 갈라졌는지 잃는다."""


def _text(value: Any, fallback: str) -> str:
    """계약이 **비어 있지 않은 문자열**을 요구하는 자리. 없으면 지어내지 않고 «없다»고 적는다."""
    text = "" if value is None else str(value).strip()
    return text or fallback


def _strings(value: Any) -> list[str]:
    """항상 배열. `null` 로 두면 「없음」과 「안 실었음」이 같아진다."""
    if value is None:
        return []
    items = value if isinstance(value, (list, tuple)) else [value]
    return [str(item).strip() for item in items if str(item).strip()]


def _num(value: Any, digits: int = 0) -> str:
    """숫자를 **사람이 읽는 꼴**로. f-string 이 float 를 그대로 뱉는 자리를 막는다 —
    `TAM 1025336520.0000002` 이 실측이다. 값이 없으면 0 과 섞지 않고 「미확보」다."""
    if value is None:
        return "미확보"
    try:
        number = float(value)
    except (TypeError, ValueError):
        return str(value)
    return f"{number:,.{digits}f}"


def slot_phrase(slot: dict) -> str:
    """`S2` → 「두발 미용업 · 종사자 1인 사업체 비율 (2025, %)」.

    슬롯에 사람이 읽는 이름 칸은 없다. 정체는 `주제 · 계량 (기간, 단위)` 조합이 전부다.
    빈 칸은 지어내지 않고 건너뛴다.
    """
    head = " · ".join(str(slot.get(k)).strip() for k in ("subject", "metric")
                      if str(slot.get(k) or "").strip())
    tail = ", ".join(str(slot.get(k)).strip() for k in ("period", "unit")
                     if str(slot.get(k) or "").strip())
    return f"{head} ({tail})" if head and tail else head or tail


def caveats_of_card(card: dict) -> list[str]:
    """카드 하나가 들고 있는 경계 문장 전부. `proxy_선언` 은 **문장으로 펴서** 싣는다."""
    out: list[str] = []
    for key in _CAVEAT_KEYS:
        out.extend(_strings(card.get(key)))
    declaration = card.get("proxy_선언")
    if isinstance(declaration, dict) and (declaration.get("사유") or declaration.get("대상")):
        out.append(f"proxy 선언 — 대상 {declaration.get('대상')} · 사유 {declaration.get('사유')}")
    return list(dict.fromkeys(out))


# ══════════════════════════════════════════════════════════════
# evidence
# ══════════════════════════════════════════════════════════════
def evidence(cards: list[dict]) -> list[dict]:
    """카드 → `evidence[]`. **id 는 유일해야 한다** — 중복이면 칸의 인용이 어느 것인지 모른다."""
    out, seen = [], set()
    for card in cards:
        kind = card.get("종류")
        grade = card.get("등급")
        if kind not in EVIDENCE_KINDS:
            raise ContractDrift(f"근거 종류가 계약 밖이다: {kind!r}")
        if grade not in GRADES:
            raise ContractDrift(f"등급이 계약 밖이다: {grade!r}")

        item = {contract_key: card.get(card_key)
                for card_key, contract_key in _EVIDENCE.items()}
        item["id"] = _text(card.get("카드_id"), "")
        if not item["id"] or item["id"] in seen:
            raise ContractDrift(f"근거 id 가 비었거나 중복이다: {item['id']!r}")
        seen.add(item["id"])

        # 값은 수이거나 없음이다. 문자열 숫자를 그대로 흘리면 자바에서 400 이 난다.
        value = item.get("value")
        item["value"] = float(value) if isinstance(value, (int, float)) else None
        item["gradeReason"] = _text(item.get("gradeReason"), "등급_근거 미기록")
        item["quote"] = item.get("quote") or None
        item["inputs"] = item.get("inputs") if isinstance(item.get("inputs"), dict) else None
        item["materialIds"] = _strings(item.get("materialIds"))
        item["assumptions"] = _strings(item.get("assumptions"))
        item["caveats"] = caveats_of_card(card)
        for key in ("metric", "subject", "period", "unit",
                    "sourceUrl", "sourceKind", "retrievedAt", "formula"):
            item[key] = str(item[key]) if item.get(key) is not None else None
        out.append(item)
    return out


# ══════════════════════════════════════════════════════════════
# FULL — 성적표 · 시장
# ══════════════════════════════════════════════════════════════
def scorecard(doc: dict) -> list[dict]:
    """7과목 **전부** 싣는다. 빠진 과목은 「미확보」가 아니라 「안 쟀다」로 읽힌다."""
    subjects = doc.get("과목") or {}
    out = []
    for korean, subject in _SUBJECT.items():
        row = subjects.get(korean) or {}
        state = _STATE.get(row.get("상태"))
        if state is None:
            raise ContractDrift(f"{korean} 상태가 계약 밖이다: {row.get('상태')!r}")
        out.append({"subject": subject, "state": state,
                    "detail": _text(_detail(korean, row), "세부 없음")})
    return out


def _detail(korean: str, row: dict) -> str:
    """사람이 읽는 한 줄. **판정을 새로 하지 않고** 성적표가 이미 센 수를 옮기기만 한다."""
    if korean == "1_시장크기":
        return f"TAM 밑동 관측 {row.get('n')}건 · 등급 {row.get('등급')}"
    if korean == "2_성장률":
        return (f"{_num(row.get('값_퍼센트'), 2)}% · 갈래 {row.get('갈래')}"
                " · 단순 증감률이며 CAGR 아님")
    if korean == "3_경쟁사":
        return f"URL 도메인 {row.get('n_url')}곳 — {', '.join(row.get('도메인') or []) or '없음'}"
    if korean == "4_가격":
        return f"표시가격 {row.get('n')}건"
    if korean == "5_수요":
        return f"근거 {row.get('n')}건 · 최고 등급 {row.get('최고_등급')}"
    if korean == "6_계산":
        return f"TAM {_num(row.get('TAM'))}원 · 가정 {row.get('가정수')}개 명시"
    # ⑦행. `건수` 는 이름과 달리 **원본 목록**을 담고 있다(`tools/scorecard.py`). 그대로 join
    # 하면 파이썬 repr 수백 자가 표 한 칸에 쏟아진다 — 갈래별 건수로 접는다.
    # 무엇이 없는지는 `market.notFound` 가 항목까지 적는다. 여기는 한 줄 요약이다.
    per_group: dict[str, int] = {}
    for key, value in (row.get("건수") or {}).items():
        group = _NOT_FOUND.get(str(key))
        if group is None:
            raise ContractDrift(f"「못 찾은 것」 갈래에 없는 키다: {key!r}")
        per_group[group] = per_group.get(group, 0) + _count_of(value)
    return " · ".join(f"{_NOT_FOUND_GROUP_LABEL[g]} {n}건"
                      for g, n in per_group.items() if n) or "보고할 것 없음"


def _count_of(value: Any) -> int:
    """진단 한 칸의 건수. `off_slot` 만 dict 안에 `count` 를 들고 온다."""
    if isinstance(value, dict):
        count = value.get("count")
        return int(count) if isinstance(count, (int, float)) else len(value)
    if isinstance(value, (list, tuple)):
        return len(value)
    return 1 if value else 0


def _figure(estimate: dict | None, unit: str, grade: str | None) -> dict | None:
    """TAM·SAM·성장률 한 칸. `grade` 는 **계산 카드에서** 온다 — 여기서 매기지 않는다."""
    if not isinstance(estimate, dict) or estimate.get("값") is None:
        return None
    if grade not in GRADES:
        raise ContractDrift(f"계산값 등급이 계약 밖이다: {grade!r}")
    return {
        "value": float(estimate.get("값_퍼센트") if unit == "PERCENT_PER_YEAR"
                       else estimate.get("값")),
        "unit": unit,
        "grade": grade,
        "formula": estimate.get("식") or None,
        "assumptions": _strings(estimate.get("가정")),
        "caveats": [],
        "evidenceIds": [f"C-{g.get('fact_id')}" for g in (estimate.get("근거") or [])
                        if isinstance(g, dict) and g.get("fact_id")],
    }


def market(verdict: dict, cards: list[dict], not_found: dict,
           coverage_caveat: str | None, evidence_ids: set[str],
           slots: list[dict] | None = None) -> dict:
    estimates = verdict.get("시장_추정") or {}
    grade_of = {card.get("카드_id"): card.get("등급") for card in cards}

    figures = {
        "tam": _figure(estimates.get("TAM_추정"), "KRW", grade_of.get("C-CALC-TAM")),
        "sam": _figure(estimates.get("SAM_추정"), "KRW", grade_of.get("C-CALC-SAM")),
        "som": None,
        "growth": _figure(estimates.get("성장률_추정"), "PERCENT_PER_YEAR",
                          grade_of.get("C-CALC-성장률")),
    }
    for figure in figures.values():
        if figure is not None:
            figure["evidenceIds"] = [i for i in figure["evidenceIds"] if i in evidence_ids]

    return {
        **figures,
        "price": _price(cards),
        "notFound": _not_found_blocks(not_found, slots),
        "coverageCaveat": coverage_caveat or None,
    }


def _not_found_blocks(not_found: dict, slots: list[dict] | None) -> list[dict]:
    """「못 찾은 것」을 **사람이 읽는 항목 목록**으로. §4: 절대 빼지 않는다.

    `detail` 은 줄바꿈으로 이어 붙인 문장들이다 — 화면이 `\\n` 으로 갈라 목록으로 그린다.
    **자르지 않는다.** `unit_mismatch` 의 note 안에는 「가정이다 — 관측이 아니다」 같은
    경계 문장이 들어 있어서, 잘라내면 경계 제거가 된다.
    """
    by_slot = {str(s.get("slot_id")): s for s in (slots or []) if s.get("slot_id")}
    by_var = {str(s.get("var_id")): s for s in (slots or []) if s.get("var_id")}
    out = []
    for key, value in (not_found or {}).items():
        if str(key) not in _NOT_FOUND:
            raise ContractDrift(f"「못 찾은 것」 갈래에 없는 키다: {key!r}")
        entries = _not_found_entries(str(key), value, by_slot, by_var)
        if entries:
            out.append({"item": str(key), "detail": "\n".join(entries)})
    return out


def _not_found_entries(key: str, value: Any, by_slot: dict, by_var: dict) -> list[str]:
    def with_slot(slot_id: Any, tail: str = "") -> str:
        phrase = slot_phrase(by_slot.get(str(slot_id)) or {})
        return f"{slot_id} — {phrase or '대응 슬롯 정의 없음'}{tail}"

    if key == "empty_slots":
        return [with_slot(v) for v in value or []]
    if key == "thin_slots":
        return [with_slot(v.get("slot_id"),
                          f" · 확인 {v.get('confirmed')}건 / 기준 {v.get('min_facts')}건")
                for v in value or [] if isinstance(v, dict)]
    if key in ("unfilled_vars", "suspect_var"):
        # 변수는 식의 칸이다. 슬롯이 없는 변수(연환산 같은 계수)는 지어내지 않고 그렇게 적는다.
        return [f"{v} — {slot_phrase(by_var[str(v)])}" if str(v) in by_var
                else f"{v} — 대응 슬롯 없음 (식의 계수)" for v in value or []]
    if key == "adapters":
        # `ok` 는 「못 찾은 것」이 아니다. 실어 보내면 잡음이자 거짓이다.
        return [f"{name} — {status}" for name, status in (value or {}).items()
                if str(status) != "ok"]
    if key == "off_slot":
        counts = (value or {}).get("by_reason") or {}
        rows = [f"{reason} {n}건" for reason, n in counts.items() if n]
        if (value or {}).get("unverified_quote"):
            rows.append(f"인용 미검증 {value['unverified_quote']}건")
        return rows
    if key == "contradictions":
        return [f"{v.get('slot_id')} ({v.get('fact_id')}) — {v.get('note')}"
                for v in value or [] if isinstance(v, dict)]
    if key == "unit_mismatch":
        return [f"{v.get('formula_id')} — {v.get('note')}"
                for v in value or [] if isinstance(v, dict)]
    if key == "skipped_checks":
        return [f"{v.get('rule_id')} — 선행 {v.get('skipped_by')} 위반으로 건너뜀"
                for v in value or [] if isinstance(v, dict)]
    # 나머지(`retry_hints`·`independent_topdown_blocked`·`자료_부재_확정` …)는 이미 사람 문장이다.
    if isinstance(value, dict):
        return [f"{k} {v}" for k, v in value.items() if v]
    return _strings(value)


def _price(cards: list[dict]) -> dict | None:
    """관측된 표시가격의 밴드.

    ⚠ **대표값의 성격은 일급 필드다**(`baseKind`·`baseNote`). 자유 dict 안 문자열로 두면
      언젠가 안 그려지고, 그러면 **잠정 대표값이 확정 단가로 읽힌다**.
    ⚠ 종류로 거른다 — `칸 == "PRICE"`. 단위(「원」)로 거르면 **전사 매출 12조**가 밴드에
      들어와 가격 가설에 도장을 찍던 사고가 돌아온다(판 ⑩ 실측 · 백로그 60).
    """
    rows = [card for card in cards
            if card.get("칸") == "PRICE" and isinstance(card.get("값"), (int, float))]
    if not rows:
        return None
    rows.sort(key=lambda card: float(card["값"]))
    values = [float(card["값"]) for card in rows]
    middle = len(values) // 2
    base = values[middle] if len(values) % 2 else (values[middle - 1] + values[middle]) / 2

    ladder = list(GRADES)                       # 확정 → … → 근거 없음. 약한 고리가 이긴다.
    grade = max((card.get("등급") for card in rows),
                key=lambda g: ladder.index(g) if g in ladder else len(ladder))
    if grade not in GRADES:
        raise ContractDrift(f"가격 등급이 계약 밖이다: {grade!r}")

    return {
        "min": values[0], "base": base, "max": values[-1], "currency": "KRW",
        "baseKind": "MEDIAN_PROVISIONAL",
        "baseNote": (f"잠정 대표값(관측 표시가격의 중앙값)이다. 확정 단가가 아니다. "
                     f"관측 {len(values)}건의 중앙값이라 분모를 보고 읽어야 한다."),
        "grade": grade,
        "caveats": sorted({c for card in rows for c in caveats_of_card(card)}),
        "evidenceIds": [str(card.get("카드_id")) for card in rows],
    }


# ══════════════════════════════════════════════════════════════
# BM — 캔버스 9칸 · 판정
# ══════════════════════════════════════════════════════════════
def canvas_cells(items: list, evidence_items: list[dict]) -> list[dict]:
    """`BMCanvasItem` → 계약 칸. **경계는 여기서 기계가 파생한다(층 1).**

    실측(판 ㉜-b): BM 모델은 경계를 최종 문장에 **0/2** 로 싣는다. 그래서 모델에게 다시
    부탁하지 않는다 — 인용한 근거의 경계를 **합집합으로 끌어온다**. LLM 이 관여하지
    않으므로 소실이 구조적으로 불가능하고, 자바 `requireCaveats` 가 한 번 더 막는다.
    """
    caveats_by_id = {item["id"]: item.get("caveats") or [] for item in evidence_items}
    out = []
    for item in items:
        cited = list(item.market_evidence_ids)
        derived: list[str] = []
        for evidence_id in cited:
            derived.extend(caveats_by_id.get(evidence_id, []))
        out.append({
            "canvasCell": item.canvas_cell.value,
            "status": item.status.value,
            "content": _strings(item.content),
            "reason": _text(item.reason, "사유 미기재"),
            "sourceLabels": _strings(item.source_labels),
            "marketEvidenceIds": cited,
            "missingEvidence": _strings(item.missing_evidence),
            "caveats": list(dict.fromkeys(derived)),
        })
    assert_caveats_reached(out, evidence_items)
    return out


def assert_caveats_reached(cells: list[dict], evidence_items: list[dict]) -> None:
    """**층 2 — 불변식을 실패로.** 인용한 근거의 경계가 칸에 없으면 결과를 내지 않는다.

    위 파생이 있으므로 지금은 구조적으로 성립한다. 그래도 검사를 따로 두는 이유는
    판 ㉘ 이 값비싸게 배운 것 때문이다: **「경계는 쓴 곳이 아니라 도달한 곳에서만
    존재한다」.** 파생 코드가 한 줄 바뀌면 소실은 조용히 돌아오고, 조용한 소실은
    출력이 멀쩡해 보인다. 자바 `requireCaveats` 와 **같은 문장을 두 층에서** 막는다.
    """
    caveats_by_id = {item["id"]: set(item.get("caveats") or []) for item in evidence_items}
    for cell in cells:
        want: set[str] = set()
        for evidence_id in cell["marketEvidenceIds"]:
            want |= caveats_by_id.get(evidence_id, set())
        missing = want - set(cell["caveats"])
        if missing:
            raise ContractDrift(
                f"{cell['canvasCell']} 이 인용한 근거의 경계가 칸에 없다: {sorted(missing)}")


def bm(final, analysis) -> dict:
    """`BMFinalResult` + `BMAnalysisResult` → 계약 `bm`. 이름만 바꾼다 — 판정을 다시 안 한다.

    ⚠ **두 물건이 필요하다.** `marketFitStatus`·`consistencyStatus` 는 최종 결과가 아니라
      **핵심 판정**에 있다. 최종 결과에는 그 «요약 문장»만 실려 있어서, 하나만 받으면
      상태 칸을 채울 수 없다(그러려면 요약에서 되짚어야 하고 그건 추측이다).
    """
    return {
        "decision": final.decision.value,
        "confidence": final.confidence,
        "summary": _text(final.summary, "요약 없음"),
        "marketFitStatus": analysis.market_fit_status,
        "marketFitSummary": _text(final.market_fit_summary, "요약 없음"),
        "consistencyStatus": analysis.consistency_status,
        "consistencySummary": _text(final.consistency_summary, "요약 없음"),
        "strengths": _strings(final.strengths),
        "weaknesses": _strings(final.weaknesses),
        "risks": _strings(final.risks),
        "legal": {
            "used": bool(final.legal_context_used),
            "status": final.legal_status,
            "summary": final.legal_summary or None,
            "risks": _strings(final.legal_risks),
            "requiredActions": _strings(final.required_legal_actions),
        },
    }


def summary_lines(doc: dict | None, evidence_ids: set[str]) -> list[dict] | None:
    """칸별 종합 요약(판 ㉛). 카드 밖 id 는 **버린다** — 인용은 원장에 있는 것만."""
    if not doc:
        return None
    out = []
    for line in doc.get("요약") or []:
        out.append({
            "cell": _text(line.get("칸"), "미상"),
            "sentence": _text(line.get("문장"), "요약 없음"),
            "cardIds": [i for i in _strings(line.get("카드_id")) if i in evidence_ids],
        })
    return out or None


# ══════════════════════════════════════════════════════════════
# 봉투
# ══════════════════════════════════════════════════════════════
#: 자바 `ENVELOPE` 와 **같은 집합**. 모드에 해당 없는 칸은 빼는 게 아니라 `null` 이다 —
#: 그래야 봉투를 `exact()` 한 번으로 못박을 수 있다.
ENVELOPE = ("runId", "conceptId", "asOf", "generatedAt", "mode",
            "stages", "degradations",
            "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes")

NOTES_FULL = (
    "등급은 evidence[].grade 에 있다. 값만 떼어 쓰면 추정이 확정처럼 읽힌다.",
    "caveats 를 떨어뜨리지 마라 — 값과 함께 옮겨야 하는 문장이다.",
    "법률 결과는 반영되지 않았다.",
    "SOM 은 이 파이프라인이 산출하지 않는다 — som:null 은 0 이 아니라 «안 쟀다»다.",
)
NOTES_BM = (
    "칸의 caveats 는 인용한 근거에서 기계가 파생한 것이다 — 모델이 쓴 문장이 아니다.",
    "content 만 떼어 쓰면 경계가 사라진다. 값과 같은 자리에 두어라.",
)


def envelope(**fields) -> dict:
    """봉투를 **정확히** 채운다. 빠진 칸은 `null`, 계약 밖 칸은 여기서 막는다."""
    unknown = set(fields) - set(ENVELOPE)
    if unknown:
        raise ContractDrift(f"봉투에 없는 칸: {sorted(unknown)}")
    return {name: fields.get(name) for name in ENVELOPE}
