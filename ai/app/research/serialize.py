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

#: 판 ㊸ — 절 체인이 새로 채우는 과목 셋. **엔진 `doc["과목"]` 에는 없다.**
#: 성적은 「그 절에 실린 사실이 몇 건인가」에서 온다 — 새 판정을 만들지 않는다.
#: ⚠ **판 ㊺ 로 이 줄이 거짓이 됐다 — 고쳐 적는다.**
#:   옛 문장: 「목록 순서가 곧 화면 절 번호다」. **이제 아니다.** 화면 절 번호의 정본은
#:   `frontEnd/.../marketResult.js` 의 **`SECTION_ORDER`**(9칸)이고, 성적표는 여전히
#:   10과목을 보내되 화면이 그중 일곱만 목차에 세운다(성장률·계산은 1절 «안»으로 접혔다).
#:   → **여기 순서를 바꿔도 화면 번호는 안 움직인다.** 화면을 바꾸려면 `SECTION_ORDER` 를 본다.
_SECTION_SUBJECT = {
    "CHANNEL": "어디서 팔리나 — 채널별 비중",
    "UNIT_ECONOMICS": "한 개 팔면 얼마가 남나",
    "REGULATION": "무엇을 지켜야 하나",
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
    # ── 판 ㊸ — 절 배치를 **서버 것으로** 만든다 ──────────────────
    # 지금까지 「이 근거가 어느 과목이냐」는 프론트 `bucketEvidence` 가 다시 풀었고,
    # 코드가 스스로 「분류를 여기서 다시 짜면 두 화면이 같은 근거를 다른 과목이라고
    # 말한다」고 경고를 적어 뒀다. 답을 한 곳으로 옮긴다.
    "_절": "section",          # 절 코드 (MARKET_SIZE · CHANNEL · …)
    "_갈래": "placement",       # 게재 갈래 — 「상한으로만」 경계의 근거
    "_발행사": "issuer",        # 발행사 이름. **두 회사의 표가 하나로 읽히는 것을 막는다**
    "_표키": "tableKey",        # 어느 행이 한 표인가. 없으면 「합 100%」도 「⚠ 아니다」도 못 만든다
    "_원문값": "raw",           # 원문 수 표기(`36,745억원`). 환산값만으로는 원문을 못 되짚는다
}

#: 카드가 경계를 담는 칸들. **하나라도 빠뜨리면 §4 위반**이다 — 경계는 값과 같이 옮긴다.
_CAVEAT_KEYS = ("경계", "경계_proxy", "상한_울타리")

#: `상한_울타리` 는 bool 표식이라 문장이 필요하다 (`caveats_of_card` 참조).
_CEILING_SENTENCE = "⚠ 상한 울타리 — 이 값은 **상한으로만** 읽어야 한다(상위 집계를 밑동으로 썼다)."

#: 계산식 한 항(요인) 한글 키 → 계약 키. **이 표가 factors 의 allowlist 다.**
_FACTOR = {
    "이름": "name", "값": "value", "단위": "unit", "판정": "basis",
    "설명": "note", "울타리": "bound", "반증": "falsifiedIf",
    "출처_수": "sourceCount", "원출처_도메인": "sourceDomains", "경계": "caveats",
}

#: 요인의 판정 어휘. 정본은 `research2/service/verdict.py::FACTOR_BASES` 다 —
#: 사본을 두는 대신 갈라지면 `ContractDrift` 로 터지게 한다.
_FACTOR_BASES = ("관측", "가정", "가설")

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
    "extract_capped": "NOT_YET",
    "fetch_empty": "NOT_YET",
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
        value = card.get(key)
        if key == "상한_울타리":
            # ⚠ 이 칸은 **문장이 아니라 표식**(bool)이다. 그대로 `_strings` 에 넣으면
            #   사용자가 읽는 경계 목록에 `"True"` 한 줄이 섞인다(판 ㉛A 실측 —
            #   사다리 2단이 처음 발동해서야 드러났다). **버리지는 않는다** —
            #   경계 문장이 하나도 없는 카드에서 표식마저 빠지면 울타리가 사라진다.
            if value:
                out.append(_CEILING_SENTENCE)
            continue
        out.extend(_strings(value))
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
                    "sourceUrl", "sourceKind", "retrievedAt", "formula",
                    "section", "placement", "issuer", "tableKey", "raw"):
            item[key] = str(item[key]) if item.get(key) is not None else None
        out.append(item)
    return out


#: 경쟁사 지표로 읽는 계량. **프론트 `COMP_METRICS` 에서 옮겨 왔다** — 그 자리에
#: 「봉투에 과목 필드가 생기면 이 표는 없어진다」고 적혀 있었고, 지금이 그때다.
_COMPETITOR_METRICS = ("가입 매장 수", "누적 가입자 수", "매출액", "이용 요금", "월 활성 사용자")


def assign_sections(items: list[dict], market: dict | None) -> None:
    """**절이 안 붙은 근거**(슬롯 카드)에 절을 붙인다. 제자리에서 고친다.

    승격 카드(`promote_cards`)는 이미 `section` 을 들고 온다 — 건드리지 않는다.
    여기서 채우는 것은 슬롯 기반 카드뿐이고, 셈은 **프론트 `bucketEvidence` 가 하던 그대로**다.
    옮긴 이유는 규칙을 바꾸려는 게 아니라 **답이 나는 자리를 하나로 만들려는** 것이다.

    ⚠ `market` 을 알아야 풀 수 있어 `evidence()` 안에서 못 한다 — market 이 evidence 뒤에
    만들어지기 때문이다. 그래서 조립하는 쪽이 둘 다 손에 쥔 뒤 부른다.
    """
    m = market or {}

    def ids(*names) -> set:
        out = set()
        for name in names:
            figure = m.get(name)
            if isinstance(figure, dict):
                out |= set(_strings(figure.get("evidenceIds")))
        return out

    size, grow, price = ids("tam", "sam", "som"), ids("growth"), ids("price")
    for item in items:
        if item.get("section"):
            continue
        if item.get("kind") == "계산":
            item["section"] = "CALCULATION"
        elif item.get("metric") in _COMPETITOR_METRICS:
            item["section"] = "COMPETITOR"
        elif item["id"] in price:
            item["section"] = "PRICE"
        elif item["id"] in size:
            item["section"] = "MARKET_SIZE"
        elif item["id"] in grow:
            item["section"] = "GROWTH"
        else:
            # **「그 밖」은 수요가 아니다.** 지금까지 프론트가 그렇게 했고 그대로 옮기지만,
            # 이것은 분류가 아니라 **나머지 통**이다. 다음 판이 볼 자리로 적어 둔다.
            item["section"] = "DEMAND"


# ══════════════════════════════════════════════════════════════
# FULL — 성적표 · 시장
# ══════════════════════════════════════════════════════════════
def scorecard(doc: dict, section_counts: dict | None = None,
              threshold: int = 3) -> list[dict]:
    """10과목 **전부** 싣는다. 빠진 과목은 「미확보」가 아니라 「안 쟀다」로 읽힌다.

    `section_counts` 는 절 체인이 센 「그 절에 실린 사실 수」다. 없으면(체인이 안 돈 실행)
    새 세 과목은 **`MISSING` + 「이 실행은 절 조사를 돌리지 않았다」**로 나간다 —
    **0건과 「안 쟀다」를 같은 말로 만들지 않는다.**
    """
    subjects = doc.get("과목") or {}
    out = []
    for korean, subject in _SUBJECT.items():
        if subject == "NOT_FOUND":
            out.extend(_section_rows(section_counts, threshold))
        row = subjects.get(korean) or {}
        state = _STATE.get(row.get("상태"))
        if state is None:
            raise ContractDrift(f"{korean} 상태가 계약 밖이다: {row.get('상태')!r}")
        out.append({"subject": subject, "state": state,
                    "detail": _text(_곁들임(_detail(korean, row), subject, section_counts),
                                    "세부 없음")})
    return out


def _곁들임(detail: str, subject: str, counts: dict | None) -> str:
    """슬롯 판정 줄에 **절 조사가 실은 사실 수**를 덧붙인다.

    ⚠ **판정을 바꾸지 않는다.** `state` 는 슬롯 카드가 정한 그대로다 — 이 함수는
    **말을 맞출 뿐**이다.

    왜 필요한가(유료 스모크 실측 2026-08-15). 성적표 수요 줄이

        5  수요  [미확보]  근거 0건 · 최고 등급 None        근거 13건 ▾

    로 나갔다. 「0건」은 슬롯 카드의 수고 「13건」은 절 조사가 실은 수인데, 화면은 두
    모집단을 모른 채 **한 줄에 두 수**를 찍었다. 사용자에게는 동시에 참일 수 없는 말이라
    어느 쪽을 믿어도 손해다 — 배지를 믿으면 실린 13건을 버리고, 표를 믿으면 배지를
    화면 고장으로 읽는다.

    두 모집단을 **성적표에서 합치는 것은 설계 결정**이라 이 판에서 하지 않는다.
    여기서는 **모순으로 보이지 않게 이름을 붙일 뿐**이다.
    """
    n = int((counts or {}).get(subject) or 0)
    if not n:
        return detail
    return (f"{detail} · 절 조사가 실은 **정황 근거 {n}건**은 아래에 있다 "
            f"— 위 판정을 세운 직접 근거는 아니다")


def _section_rows(counts: dict | None, threshold: int) -> list[dict]:
    """절 체인이 채우는 세 과목의 성적. **새 판정을 만들지 않는다** — 건수를 옮긴다."""
    out = []
    for code, what in _SECTION_SUBJECT.items():
        if counts is None:
            out.append({"subject": code, "state": "MISSING",
                        "detail": "이 실행은 절 조사를 돌리지 않았다 — 0건이 아니라 «안 쟀다»다"})
            continue
        n = int(counts.get(code) or 0)
        state = "FILLED" if n >= threshold else "PARTIAL" if n else "MISSING"
        detail = (f"{what} — 실린 사실 {n}건" if n else
                  f"{what} — **한 건도 못 구했다.** 8절 처방을 보라")
        out.append({"subject": code, "state": state, "detail": detail})
    return out


def _what(row: dict, limit: int = 4) -> str:
    """★ 판 ㊳ — **무엇을 세었는지** 한 줄에 붙인다.

    건수만 보이면 「가격 확인됨 5건」이 참말처럼 읽힌다. 그 5건이 배달비·편의점 도시락이면
    거짓말이고, 지금까지 그 사실은 화면 어디에도 없었다. 관련성 판정은 여기서 하지
    않는다 — **이름을 드러내 사람이 알아보게** 한다.
    """
    items = [str(x) for x in (row.get("셈한_것") or []) if str(x).strip()]
    if not items:
        return ""
    head = ", ".join(items[:limit])
    more = f" 외 {len(items) - limit}종" if len(items) > limit else ""
    return f" — {head}{more}"


def _detail(korean: str, row: dict) -> str:
    """사람이 읽는 한 줄. **판정을 새로 하지 않고** 성적표가 이미 센 수를 옮기기만 한다."""
    if korean == "1_시장크기":
        # ⚠ `등급` 은 리스트다. 그대로 f-string 에 넣으면 화면에 `['확정']` 이라는
        #   **파이썬 코드 표기**가 뜬다(화면 감사 04). 사람 문장으로 편다.
        등급 = ", ".join(_strings(row.get("등급"))) or "없음"
        층 = row.get("층위")
        겹 = (f"관측 {row.get('n')}건 → **층위 {층}개**"
              if 층 is not None and 층 != row.get("n") else f"관측 {row.get('n')}건")
        return f"시장 크기 {겹} · 등급 {등급}{_what(row)}"
    if korean == "2_성장률":
        return (f"{_num(row.get('값_퍼센트'), 2)}% · 갈래 {row.get('갈래')}"
                " · 단순 증감률이며 CAGR 아님")
    if korean == "3_경쟁사":
        return f"URL 도메인 {row.get('n_url')}곳 — {', '.join(row.get('도메인') or []) or '없음'}"
    if korean == "4_가격":
        return f"표시가격 {row.get('n')}건{_what(row)}"
    if korean == "5_수요":
        # ⚠ **파이썬 `None` 을 한국어 문장에 넣지 않는다.** 등급이 없으면 없다고 말한다 —
        #   실측(유료 스모크): 「최고 등급 None」 이 그대로 화면에 앉았다.
        등급 = row.get("최고_등급")
        꼬리 = f" · 최고 등급 {등급}" if 등급 else " · 등급을 매길 근거가 없다"
        return f"근거 {row.get('n')}건{꼬리}{_what(row)}"
    if korean == "6_계산":
        # 값이 없으면 **없다고 말한다.** 예전에는 `TAM None원 · 가정 1개 명시` 로 나갔다.
        if row.get("TAM") is None:
            rng = row.get("범위") or None
            if rng:
                lo, hi = rng.get("하한"), rng.get("상한")
                폭 = f"{_num(lo)} ~ {_num(hi)}원" if lo is not None else f"{_num(hi)}원 이하"
                return (f"점 추정은 못 했고 **범위**만 냈다 — {폭}"
                        f" (관측 점유율 {rng.get('근거_점유율_수')}건)")
            층 = row.get("층위") or 0
            if 층:
                return (f"시장 크기를 계산하지 않는다 — 관측 {층}개 층위를 그대로 낸다 "
                        f"(가정을 곱해 값을 만들지 않는다)")
            return "시장 크기 관측 0건 — 낼 층위가 없다"
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


def _factors(items: Any) -> list[dict]:
    """계산식의 항들. **번역만 한다** — 판정도 집계도 여기서 하지 않는다.

    ⚠ `note` 를 자르지 않는다. 자르면 문장 한가운데가 끊긴 채 화면까지 간다 —
    실제로 그랬다(`basis[:100]` → 「… 두발 미」).
    """
    out = []
    for item in (items or []):
        if not isinstance(item, dict):
            raise ContractDrift(f"요인이 dict 가 아니다: {item!r}")
        basis = item.get("판정")
        if basis not in _FACTOR_BASES:
            raise ContractDrift(f"요인 판정이 계약 밖이다: {basis!r}")
        value = item.get("값")
        row = {
            "name": _text(item.get("이름"), "이름 미기재"),
            "value": float(value) if isinstance(value, (int, float)) else None,
            "unit": _text(item.get("단위"), "") or None,
            "basis": basis,
            "note": _text(item.get("설명"), "") or None,
            "bound": _text(item.get("울타리"), "") or None,
            "falsifiedIf": _text(item.get("반증"), "") or None,
            "sourceCount": int(item.get("출처_수") or 0),
            "sourceDomains": _strings(item.get("원출처_도메인")),
            "caveats": _strings(item.get("경계")),
        }
        out.append(row)
    return out


def _figure(estimate: dict | None, unit: str, grade: str | None) -> dict | None:
    """TAM·SAM·성장률 한 칸. `grade` 는 **계산 카드에서** 온다 — 여기서 매기지 않는다.

    ⚠ `assumptions` 는 **`해석_경계`** 다. 항에 붙는 문장은 `factors` 가 값·근거·울타리·
    반증까지 들고 나가므로, 같은 말을 문장으로 한 번 더 실으면 화면에 두 벌이 뜬다.
    남는 것은 **표가 말할 수 없는 것**뿐이다(예: 「연평균이 아니다」·「과거 관측이다」).
    요인이 없는 옛 판정 출력은 `가정` 을 그대로 쓴다 — 그때는 표가 아예 없다.
    """
    if not isinstance(estimate, dict):
        return None
    # ★ 판 ㊳ — **값이 없어도 분해표는 남긴다.**
    #   값을 못 내는 것과 아무것도 모르는 것은 다르다. 「거래액 38.04조까지는 관측했고
    #   점유율이 없어 시장 크기로 환산하지 못한다」가 사용자에게 줄 답이다.
    #   예전에는 여기서 통째로 None 을 돌려줘 요인 표·근거·식이 전부 사라졌다.
    #   ⚠ 계약은 이미 이것을 허용한다 — `value` 는 nullableNumber 이고
    #     `GRADES` 에 「근거 없음」이 있다(Java `MarketResearchContract.java:38`).
    if estimate.get("값") is None:
        if not estimate.get("요인"):
            return None            # 표도 없으면 정말 할 말이 없다
        grade = grade or "근거 없음"
    if grade not in GRADES:
        raise ContractDrift(f"계산값 등급이 계약 밖이다: {grade!r}")
    factors = _factors(estimate.get("요인"))
    raw = (estimate.get("값_퍼센트") if unit == "PERCENT_PER_YEAR"
           else estimate.get("값"))
    # **왜 값이 없는지**는 경계 문장으로 나간다. 빈 칸만 보내면 화면이 「조사를 안 했다」로 읽는다.
    사유 = [s for s in (estimate.get("값_불가_사유"),) if s]
    밑동 = estimate.get("관측된_밑동") or None
    if 밑동 and 밑동.get("값") is not None:
        사유.append(f"관측한 데까지: {밑동.get('이름')} {_num(밑동.get('값'))}"
                   f"{(' ' + 밑동['단위']) if 밑동.get('단위') else ''}")
    # ★ 판 ㊳ — 점 추정을 못 내도 **범위**는 낸다. 계약에 범위 칸이 없으므로 경계 문장으로.
    rng = estimate.get("범위") or None
    if rng:
        lo, hi = rng.get("하한"), rng.get("상한")
        폭 = (f"{_num(lo)} ~ {_num(hi)}" if lo is not None
              else f"{_num(hi)} 이하")
        사유.append(f"관측 점유율로 만든 범위: {폭}")
        사유.extend(_strings(rng.get("읽는_법")))
    return {
        "value": float(raw) if isinstance(raw, (int, float)) else None,
        "unit": unit,
        "grade": grade,
        "formula": estimate.get("식") or None,
        "factors": factors,
        "assumptions": _strings(사유 + list(estimate.get("해석_경계") or [])) if factors
                       else _strings(사유 + list(estimate.get("가정") or [])),
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
    if key == "extract_capped":
        # **본문은 받았는데 묻지 않은 문서다.** 「찾아도 없다」가 아니라 「안 봤다」 —
        # 이 문장이 흐려지면 성적표의 미확보가 자료 부재로 읽힌다.
        return [with_slot(v.get("slot_id"), f" · {v.get('trace_id')} — {v.get('why')}")
                for v in value or [] if isinstance(v, dict)]
    if key == "fetch_empty":
        # 200 을 받고도 본문이 0자다. 「없는 자료」가 아니라 **못 가져온 자료**다.
        return [with_slot(v.get("slot_id"), f" · {v.get('url')} — {v.get('why')}")
                for v in value or [] if isinstance(v, dict)]
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
#: 사용자가 채운 계획 키 → 그것이 채우는 캔버스 칸.
#: 정본은 `pipeline.PLAN_KEYS` + `constraint` 이고, 여기는 **이름만** 바꾼다.
USER_PLAN_CELL = {
    "key_activities": "KEY_ACTIVITIES",
    "key_resources": "KEY_RESOURCES",
    "key_partners": "KEY_PARTNERS",
    "customer_relationship": "CUSTOMER_RELATIONSHIPS",
    "constraint": "COST_STRUCTURE",
}

#: 사용자가 쓴 칸에 붙는 경계. 「꽉 찬 캔버스」가 「검증된 캔버스」로 읽히지 않게 한다.
USER_PLAN_CAVEAT = "사용자가 입력한 실행 계획이다 — 관측이 아니다."


#: 사용자가 쓴 칸의 출처 라벨. 계약 화이트리스트 7종 안에 있어야 한다 —
#: `user_input` 같은 새 라벨은 `analyze.validate_canvas_source_labels` 가 지우고
#: 자바 계약이 거부한다. 둘 다 이미 「관측이 아닌 입력」 축이다.
_USER_PLAN_LABEL = {"COST_STRUCTURE": "execution_constraints"}


def canvas_cells(items: list, evidence_items: list[dict],
                 user_planned: dict[str, list[str]] | None = None) -> list[dict]:
    """`BMCanvasItem` → 계약 칸. **경계는 여기서 기계가 파생한다(층 1).**

    실측(판 ㉜-b): BM 모델은 경계를 최종 문장에 **0/2** 로 싣는다. 그래서 모델에게 다시
    부탁하지 않는다 — 인용한 근거의 경계를 **합집합으로 끌어온다**. LLM 이 관여하지
    않으므로 소실이 구조적으로 불가능하고, 자바 `requireCaveats` 가 한 번 더 막는다.

    `user_planned` 는 **사용자가 직접 채운 칸**의 이름이다. 그 칸이 시장 근거를 하나도
    인용하지 않았다면 도장을 `PLAN` 으로 **내리고** 경계를 붙인다 — 사유는 아래.
    """
    caveats_by_id = {item["id"]: item.get("caveats") or [] for item in evidence_items}
    planned = dict(user_planned or {})
    out = []
    for item in items:
        cited = list(item.market_evidence_ids)
        derived: list[str] = []
        for evidence_id in cited:
            derived.extend(caveats_by_id.get(evidence_id, []))
        cell = {
            "canvasCell": item.canvas_cell.value,
            "status": item.status.value,
            "content": _strings(item.content),
            "reason": _text(item.reason, "사유 미기재"),
            "sourceLabels": _strings(item.source_labels),
            "marketEvidenceIds": cited,
            "missingEvidence": _strings(item.missing_evidence),
            "caveats": list(dict.fromkeys(derived)),
        }
        _stamp_user_plan(cell, planned)
        out.append(cell)
    assert_caveats_reached(out, evidence_items)
    return out


def _stamp_user_plan(cell: dict, planned: dict[str, list[str]]) -> None:
    """사용자가 쓴 칸을 **계획으로 고정하고, 쓴 내용을 잃지 않게 한다.**

    두 가지를 한다.

    <b>① 도장을 내린다.</b> 프롬프트 §9 만 파트너 칸에 「입력 또는 시장분석에 **실제 파트너
    정보**가 있을 때만 작성」이라 적고 PLAN 을 지시하지 않는다. 그래서 사용자가 파트너를
    적으면 모델이 「입력 근거로 확인됨」(VERIFIED)으로 올릴 수 있다. 사용자가 쓴 것은
    **필요한 유형이지 계약된 상대가 아니다**(견본의 `_key_partners_주의`).

    <b>② 비었으면 사용자가 쓴 그대로 채운다.</b> 실측(실스택 스모크): 입력을 다 받고도
    모델이 `CUSTOMER_RELATIONSHIPS` 와 `COST_STRUCTURE` 를 `content=[]` 로 냈다 — payload
    에는 글자 그대로 있었다. 계획 칸에서 모델이 할 일은 **창업자가 쓴 계획을 다시 쓰는
    것이 아니다.** 사용자의 문장을 LLM 왕복에 맡기면 조용히 사라진다.

    ⚠ 모델이 쓴 내용이 있으면 **덮지 않는다.** 정리해 놓은 것을 뭉개지 않는다.
    ⚠ 근거를 인용한 칸은 아예 건드리지 않는다 — 시장 근거가 붙었다면 판정은 모델과
      근거의 몫이다.
    ⚠ **올리지 않는다.** VERIFIED→PLAN 은 안전한 방향이고 그 반대는 하지 않는다.
    """
    name = cell["canvasCell"]
    if name not in planned or cell["marketEvidenceIds"]:
        return
    cell["status"] = "PLAN"
    if not cell["content"]:
        cell["content"] = list(planned[name])
        # content 가 있으면 출처 라벨도 있어야 한다(자바 계약 :242). 새 라벨을 만들지
        # 않고 화이트리스트 7종 중 뜻이 맞는 것을 쓴다.
        if not cell["sourceLabels"]:
            cell["sourceLabels"] = [_USER_PLAN_LABEL.get(name, "concept_snapshot")]
    if USER_PLAN_CAVEAT not in cell["caveats"]:
        # 경계는 상위집합이면 된다(자바 `requireCaveats` 가 `containsAll`) — 더해도 안 깨진다.
        cell["caveats"].append(USER_PLAN_CAVEAT)


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


def bm(final, analysis, decision: str, gate_reasons: list[dict], handoff=None) -> dict:
    """`BMFinalResult` + `BMAnalysisResult` → 계약 `bm`. 이름만 바꾼다 — 판정을 다시 안 한다.

    ⚠ **두 물건이 필요하다.** `marketFitStatus`·`consistencyStatus` 는 최종 결과가 아니라
      **핵심 판정**에 있다. 최종 결과에는 그 «요약 문장»만 실려 있어서, 하나만 받으면
      상태 칸을 채울 수 없다(그러려면 요약에서 되짚어야 하고 그건 추측이다).

    `decision` 은 **게이트가 내린 뒤의** 값을 받는다. 여기서 다시 재지 않는 이유는 위와 같다 —
    이 함수는 이름만 바꾸는 자리다. 판정은 `app.validation.gate` 가 하고 부른 쪽이 넘긴다.
    """
    return {
        "decision": decision,
        "gateReasons": [dict(reason) for reason in gate_reasons],
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
        "financialHandoff": None if handoff is None else {
            "conceptId": handoff.concept_id,
            "revenueModel": handoff.revenue_model,
            "priceMin": handoff.price_min, "priceBase": handoff.price_base,
            "priceMax": handoff.price_max, "tam": handoff.tam, "sam": handoff.sam,
            "som": handoff.som, "marketGrowthRate": handoff.market_growth_rate,
            "expectedRevenue": handoff.expected_revenue, "unitCost": handoff.unit_cost,
            "fixedCostItems": list(handoff.fixed_cost_items),
            "variableCostItems": list(handoff.variable_cost_items),
            "missingFinancialInputs": _strings(handoff.missing_financial_inputs),
            "handoffStatus": handoff.handoff_status,
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
            "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes",
            # ── 판 ㊸ — 사람 보고서의 2·8·9절 ─────────────────────
            "judgment", "prescriptions", "synthesis",
            # ── 판 ㊻ — **사람이 읽는 보고서 글** ─────────────────
            # 사실(evidence[])만 실으면 화면은 표밖에 못 그린다. 목표 보고서는 절마다
            # 여는 문장·소제목·표·설명 문단이 있고, 그 «글»이 여기로 실린다.
            # **`null` 이어도 나머지 화면은 그대로 돈다** — 프론트가 물러설 수 있게.
            "report")


#: 보고서 글이 설 수 있는 절. 앞 일곱은 **evidence 의 `_절` 과 같은 어휘다** — 새 이름을
#: 만들면 화면이 글과 근거를 같은 절에 못 세운다.
#: 뒤 둘은 보고서의 꼬리다 — `GAPS` 8절(못 구한 것) · `SYNTHESIS` 9절(이 조사가 말하는 것).
#: ⚠ 봉투의 `prescriptions`·`synthesis` 와 **다른 물건이다.** 저쪽은 기계가 원장에서 뽑은
#:   값이고 이쪽은 모델이 쓴 «글»이다. 한쪽으로 합치면 「기계가 판정했다」가 흐려진다.
REPORT_SECTIONS = ("MARKET_SIZE", "PRICE", "COMPETITOR", "CHANNEL", "DEMAND",
                   "UNIT_ECONOMICS", "REGULATION", "GAPS", "SYNTHESIS")


def report(doc: dict | None) -> dict | None:
    """`write_report.build()` 산출 → 계약. **번역만 한다 — 세지도 자르지도 않는다.**

    `unverifiedNumbers`(유령 수)·`conceptLeaks`(컨셉 누출)는 도구가 이미 센 값이다.
    ⚠ **이 둘을 떨어뜨리지 마라.** 화면이 이걸로 「이 글의 수 몇 개는 조사 결과가 아니다」를
      경고한다 — 경계 표시다.
    ⚠ `lead` 도 경계 표시다 — 재료가 무엇이고 누가 썼는지가 거기 적혀 있다.
      **글만 떼어 그리면 모델이 쓴 문장이 조사 결과로 읽힌다.**
    """
    if not doc:
        return None
    sections = []
    for row in doc.get("절") or []:
        code, markdown = row.get("section"), (row.get("본문") or "").strip()
        if not markdown:
            continue
        if code not in REPORT_SECTIONS:
            raise ContractDrift(f"보고서 절이 계약 밖이다: {code!r}")
        sections.append({"subject": code, "markdown": markdown})
    if not sections:
        return None
    return {"writtenBy": _text(doc.get("쓴_모델"), "모델 미상"),
            "unverifiedNumbers": int(doc.get("유령_수") or 0),
            "conceptLeaks": int(doc.get("컨셉_누출_수") or 0),
            "lead": (doc.get("머리말") or "").strip() or None,
            "tail": (doc.get("꼬리말") or "").strip() or None,
            "sections": sections}


def judgment(doc: dict | None) -> dict | None:
    """2절 **가격 판단**. `judge_lines.build()` 산출 → 계약.

    ⚠ **계산식만 실으면 반쪽이다.** 사업가가 사는 것은 「1.37배」가 아니라 「그래서 어느 쪽으로
    팔라」이고, 그 문장은 기계가 계산된 부호에서 뽑는다. `conclusion` 을 빼지 마라.
    """
    price = (doc or {}).get("가격") or {}
    if not price:
        return None
    lines = []
    for g in price.get("갈래") or []:
        lines.append({
            "what": _text(g.get("무엇"), "무엇 없음"),
            "sentence": g.get("문장") or None,
            "formula": g.get("계산") or None,
            # **못 쓴 이유도 값이다**(절대규칙 5). 침묵을 「해당 없음」으로 읽히게 두지 않는다.
            "silentBecause": g.get("왜_못_쓰나") or None,
            # ⚠ **연도를 뗀 근거는 오늘 값처럼 읽힌다.** 실측: 「배달 한 끼 8,244원」의
            #    자장면값이 2018년인데 결론 문장에는 그 사실이 없었다.
            "sources": [{"raw": f"{s.get('number_raw')}{s.get('unit_raw') or ''}",
                         "subject": _text(s.get("subject"), "무엇의 수인지 미상"),
                         "period": str(s["year"]) if s.get("year") else None,
                         "url": _text(s.get("_url"), "")}
                        for s in (g.get("근거") or [])],
        })
    정가 = price.get("정가")
    return {"price": float(정가) if isinstance(정가, (int, float)) else None,
            "lines": lines,
            "conclusion": price.get("결론") or None}


def prescriptions(rows: list | None) -> list[dict] | None:
    """8절 **처방** — 「무엇을 못 구했나 / 왜 / 어디서」."""
    if not rows:
        return None
    return [{"section": _text(r.get("절"), "UNKNOWN"),
             "kind": _text(r.get("갈래"), "UNKNOWN"),
             "kindLabel": _text(r.get("갈래말"), "갈래 없음"),
             "what": _text(r.get("진단"), "진단 없음"),
             "why": _text(r.get("왜"), "사유 없음"),
             "where": _text(r.get("어디서"), "어디서 구할지 미기록")}
            for r in rows]


def synthesis(doc: dict | None) -> list[dict] | None:
    """9절 **지지 / 흔듦**. 검사에서 **버려진 문장은 안 싣는다** — 버린 것을 화면에 올리면
    「검사를 했다」가 「검사를 통과했다」로 읽힌다."""
    lines = [x for x in ((doc or {}).get("문장") or []) if x.get("문장")]
    if not lines:
        return None
    return [{"key": _text(x.get("키"), "키 없음"),
             "stance": _text(x.get("갈래"), "미상"),
             "sentence": _text(x.get("문장"), ""),
             "what": _text(x.get("무엇"), ""),
             "sources": [{"raw": f"{s.get('number_raw')}{s.get('unit_raw') or ''}",
                          "subject": _text(s.get("subject"), "무엇의 수인지 미상"),
                          "period": str(s["year"]) if s.get("year") else None}
                         for s in (x.get("근거") or [])]}
            for x in lines]

NOTES_FULL = (
    "등급은 evidence[].grade 에 있다. 값만 떼어 쓰면 추정이 확정처럼 읽힌다.",
    "caveats 를 떨어뜨리지 마라 — 값과 함께 옮겨야 하는 문장이다.",
    "법률 결과는 반영되지 않았다.",
    "SOM 은 이 파이프라인이 산출하지 않는다 — som:null 은 0 이 아니라 «안 쟀다»다.",
)
NOTES_BM = (
    "칸의 caveats 는 인용한 근거에서 기계가 파생한 것이다 — 모델이 쓴 문장이 아니다.",
    "content 만 떼어 쓰면 경계가 사라진다. 값과 같은 자리에 두어라.",
    "gateReasons[].cause 가 UNCOLLECTED 이면 컨셉을 고쳐도 안 고쳐진다 — 재수집이 답이고, "
    "그래도 없으면 「미확보」로 확정하고 멈춘다.",
)


def envelope(**fields) -> dict:
    """봉투를 **정확히** 채운다. 빠진 칸은 `null`, 계약 밖 칸은 여기서 막는다."""
    unknown = set(fields) - set(ENVELOPE)
    if unknown:
        raise ContractDrift(f"봉투에 없는 칸: {sorted(unknown)}")
    return {name: fields.get(name) for name in ENVELOPE}
