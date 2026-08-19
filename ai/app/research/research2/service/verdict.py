# -*- coding: utf-8 -*-
"""판정 층 — **서비스 층 4호.** 엔진 관측 × 가설 4 를 나란히 놓고 대조한다.

    python service/verdict.py <run_id> --concept data/concept_beauty-noshow.json

유리벽(채점기·BM 층과 같다):
  · **엔진을 import 하지 않는다**(`blocks/`·`adapters/`). 원장과 규칙만 읽는다
  · **원장에 쓰지 않는다**
  · **LLM 0회.** 대조는 숫자·집합 비교다

도장 어휘는 **네 개뿐**이고 층이 마음대로 늘리지 않는다(설계_v0 §7):

    검증됨 · 미검증 · 판정_불가 · 축_부재

  · **축_부재** = 「재지 않았다」 — 관측 축 자체가 없다
  · **판정_불가** = 「재려고 했는데 비교 기준이 안 섰다」 (예: 대체재 밴드 미형성)
  · **미검증** = 「쟀는데 가설을 뒷받침하지 못한다」
  셋을 섞지 않는다. 섞으면 «측정 안 함»이 «측정했는데 아니다»로 읽힌다.

**「A가 낫다」를 쓰지 않는다.** 이 층은 대조표를 낼 뿐이고, 고르는 것은 사람이다.
판정이 스스로 다음 가설을 만들지도 않는다 — 차별점이 미검증으로 나와도 새 차별점을
제안하지 않는다(재조사 자동 루프 금지와 같은 원리).
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# 채움 축 토글 — **잎 모듈**이라 유리벽을 넘지 않는다(엔진 import 0).
sys.path.insert(0, ROOT)
import fillaxis as _fx                              # noqa: E402
sys.path.insert(0, HERE)

import bm_scorer                                    # 같은 서비스 층 (엔진 아님)

STAMPS = ("검증됨", "미검증", "판정_불가", "축_부재")


def _growth_rules() -> dict:
    """성장률 경계 문구·가정·시차 임계. **코드에 문구를 박지 않는다**(규약 ①)."""
    p = os.path.join(ROOT, "rules", "growth.v1.json")
    return json.load(io.open(p, encoding="utf-8")) if os.path.exists(p) else {}


def _rules() -> dict:
    """규칙 파일만 읽는다 — 엔진 코드가 아니라 `rules/*.json` 이 정본이다(절대 규칙 7)."""
    out = {}
    for name in ("assumptions", "consistency", "series_unit"):
        p = os.path.join(ROOT, "rules", f"{name}.v1.json")
        if os.path.exists(p):
            out[name] = json.load(io.open(p, encoding="utf-8"))
    return out


def load_concept(path: str) -> dict:
    p = path if os.path.isabs(path) else os.path.join(ROOT, path)
    return json.load(io.open(p, encoding="utf-8"))


def _confirmed(led: dict, claim_types: set | None) -> list:
    """그 claim_type 슬롯들의 **확인됨** 행. 판정은 원장에서만 파생된다(절대 규칙 4).

    `claim_types=None` 이면 **거르지 않는다** — 부르는 쪽이 슬롯을 직접 지목할 때 쓴다
    (성장률처럼 «어느 슬롯인가»가 기준인 계산).
    """
    by_slot = {s["slot_id"]: s for s in led["slots"]}
    ids = ({sid for sid, s in by_slot.items() if s.get("claim_type") in claim_types}
           if claim_types is not None else set(by_slot))
    out = []
    for r in led["ledger_rows"]:
        if r["slot_id"] in ids and _fx.filled(r, "verdict._confirmed"):
            f = led["facts"].get(r.get("fact_id")) or {}
            # ⚠ 사실의 단위 필드는 `unit_norm` 이다(`unit` 이 아니다). `unit` 으로 읽으면
            #   전부 None 이 되어 **필터가 조용히 전부 탈락**시킨다 — 실제로 그랬다.
            #   슬롯 단위를 폴백으로 두고, **어디서 온 단위인지 같이 남긴다.**
            slot = by_slot.get(r["slot_id"]) or {}
            unit, src = f.get("unit_norm"), "fact.unit_norm"
            if unit in (None, ""):
                unit, src = slot.get("unit"), "slot.unit(폴백)"
            out.append({"slot_id": r["slot_id"], "fact_id": r.get("fact_id"),
                        "trace_id": f.get("trace_id"), "value": f.get("value_num"),
                        "unit": unit, "unit_src": src, "url": (r.get("url") or "")[:70],
                        "kind": r.get("kind"), "score": r.get("score")})
    return out


#: 사업체 수로 셀 수 있는 단위. 한 곳에 둔다 — 세 자리에 흩어져 있으면 하나만 고치게 된다.
_COUNT_UNITS = ("개", "곳", "명")


def _pick_base(led: dict, claim_types: set) -> tuple:
    """사업체 수 기준값을 고른다. (값, 근거목록, 주의)

    ⚠ **`max()` 를 쓰지 않는다.** 예전에는 claim_type 버킷에서 **최댓값**을 집었다.
    그건 버킷에 후보가 하나뿐일 때만 우연히 맞는다 — 슬롯이 늘면 **조용히 엉뚱한 값**을
    고른다. 판 ④ 에서 실제로 걸린 자리다: 연도별 사업체 수를 같은 버킷에 넣으면
    최댓값이 계속 큰 연도를 가리키고, 아무도 그 사실을 모른다.

    지금은 **후보가 둘 이상이면 시끄럽게 말한다.** 값은 `slot_id` 사전순으로 정해
    실행마다 흔들리지 않게 하고(결정론), 「여럿 중에 골랐다」는 사실을 주의로 내보낸다.
    조용히 하나를 집는 것이 곧 오집이다.

    ⚠ **`formula_id` 로 고르지 않는다.** 식 이름은 **컨셉마다 다르다**
    (`beauty-*` 는 `F_TAM`, `unified-01` 은 `F_SAM_TD`). 식 이름을 코드에 박으면
    이 층이 특정 컨셉 전용이 된다 — 서비스 층은 원장이 무엇이든 돌아야 한다.
    버킷을 가르는 일은 **슬롯의 `claim_type`**(= 전용 축)이 맡는다.
    """
    cands = [c for c in _confirmed(led, claim_types)
             if c["value"] is not None and (c["unit"] or "") in _COUNT_UNITS]
    if not cands:
        return None, [], ""
    cands = sorted(cands, key=lambda c: c["slot_id"])
    if len(cands) > 1:
        return (cands[0]["value"], cands,
                f"⚠ {sorted(claim_types)} 에 사업체 수 확인됨이 {len(cands)}건"
                f"({[c['slot_id'] for c in cands]}) — slot_id 사전순 첫 번째"
                f"({cands[0]['slot_id']})를 썼다. 축이 섞였는지 확인할 것")
    return cands[0]["value"], cands, ""


#: 계산식 한 항의 **판정**. 셋뿐이고 층이 마음대로 늘리지 않는다(도장 어휘와 같은 규율).
#:   관측 — 원장에 출처가 있는 사실에서 왔다
#:   가정 — `rules/assumptions.v1.json` 이 값과 근거를 함께 선언한 것이다
#:   가설 — **우리가 정한 값**이다(가격 등). 시장에서 관측한 것이 아니다
FACTOR_BASES = ("관측", "가정", "가설")

#: 세그먼트 비중을 못 구했을 때의 **한 문장**. 사유·출처·요인 설명 세 자리가 이것을 함께
#: 쓴다 — 세 곳에 각자 쓰면 화면마다 다른 말이 뜬다(판 ⑩ 「자를 둘 두지 않는다」).
SEG_MISSING = ("세그먼트 비중 미확보 — `rules/assumptions.v1.json::by_role.세그먼트비중` 에 "
               "값이 없다(판 ㊴ 에서 남의 업종 값 0.19 를 지웠다). **1.0 으로 때우지 않는다** "
               "— 모르는 것을 «시장 전체» 로 읽으면 목표 고객 수가 조용히 부푼다")

#: 단가는 `rules` 가 아니라 **컨셉의 가격 가설**에서 온다. 그래서 rules 의 `basis`
#: (요금제 관측 참조)를 붙이면 다른 값의 근거를 이 값에 다는 셈이 된다.
_PRICE_NOTE = "우리 가격 가설이지 관측된 시장 단가가 아니다"


def _요인(이름, 값, 판정, 단위=None, 설명=None, 울타리=None, 반증=None, rows=None) -> dict:
    """계산식의 한 항을 **레코드**로 편다. 문장이 아니라 표의 한 줄이다.

    ⚠ **설명을 자르지 않는다.** 옛 `_SEG_WARN` 은 `basis[:100]` 으로 잘라 문장 한가운데가
    끊긴 채로 화면까지 보냈다("… 두발 미"). 자를 자리는 화면이지 판정 층이 아니다.

    ⚠ **여기서 판정하지 않는다.** `판정` 은 부르는 쪽이 이미 아는 사실이다 — 값이
    `rules/` 에서 왔으면 가정이고 원장에서 왔으면 관측이다. 이 함수가 추론하면 같은
    물음을 두 곳이 각자 풀게 된다.
    """
    if 판정 not in FACTOR_BASES:
        raise ValueError(f"요인 판정이 어휘 밖이다: {판정!r}")
    기반 = _대조_기반(list(rows or []), 가정=(판정 != "관측"))
    return {"이름": 이름, "값": 값, "단위": 단위, "판정": 판정,
            "설명": 설명, "울타리": 울타리, "반증": 반증,
            "출처_수": 기반["출처_수"], "원출처_도메인": 기반["원출처_도메인"],
            "경계": 기반["경계"]}


def _역할_요인(이름, 역할, 값, by_role: dict, 판정="가정", 설명=None) -> dict:
    """`rules/assumptions.v1.json::by_role` 한 칸을 요인 한 줄로 편다.

    `_관측된_상한` 이 있으면 **울타리**로 싣는다 — 「가정이지만 이 선은 못 넘는다」는
    관측이고, 가정과 같은 칸에 두면 둘이 구분되지 않는다.
    """
    a = by_role.get(역할) or {}
    상한, 출처 = a.get("_관측된_상한"), a.get("_상한_출처")
    return _요인(이름, 값, 판정, 단위=a.get("unit"),
                설명=a.get("basis") if 설명 is None else 설명,
                울타리=(f"관측 상한 {상한} · {출처}" if 상한 is not None else None),
                반증=a.get("falsified_if"))


def _가정_문장(요인들: list) -> list:
    """요인 표의 **문장 투영**. 표를 못 읽는 소비자(성적표·카드·보고서)를 위해 남긴다.

    ⚠ **문장을 따로 손으로 쓰지 않는다.** 두 벌을 각자 쓰면 표와 문장이 갈라지고,
    갈라지는 순간 어느 쪽이 정본인지 알 수 없게 된다(판 ⑩ 「자를 둘 두지 않는다」).
    """
    out = []
    for f in 요인들:
        if f["판정"] == "관측":
            continue
        # 조사를 붙이지 않는다 — 「0.1 는/은」은 숫자를 읽어야 정해지고, 그 규칙을
        # 여기 넣으면 판정 층이 한국어 형태론을 갖게 된다. 값 뒤는 줄표로 끊는다.
        꼬리 = " · ".join(x for x in (f.get("설명"), f.get("울타리")) if x)
        # 값이 없는 요인은 「None」이라고 적지 않는다 — 파이썬 표현이 문장으로 새 나가면
        # 읽는 쪽이 «값이 0이다»/«버그다» 로 읽는다. 없는 것은 **미확보**라고 적는다.
        값 = "미확보" if f["값"] is None else f["값"]
        머리 = f"{f['이름']} {값}{(' ' + f['단위']) if f.get('단위') else ''} — {f['판정']}이다"
        out.append(f"{머리}. {꼬리}" if 꼬리 else 머리)
    return out


def _가정수(요인들: list) -> int:
    """가정의 개수는 **요인 표에서 센다.** 문장 수를 세면 한 요인이 세 문장이던 시절의
    수(6)와 실제 가정 수(4)가 갈라진다 — 실제로 갈라져 있었다."""
    return len([f for f in 요인들 if f["판정"] != "관측"])


def _점추정_가능(요인들: list) -> tuple:
    """★ 판 ㊳ — **관측이 아닌 항을 곱해 만든 값은 값 자리에 앉지 못한다.**

    실측: 계열 C 가 KOSIS 거래액 38.04조에 `추정점유율 0.3`(출처 0건)을 곱해
    11.41조를 만들었고, 화면은 그것을 「전체 시장(TAM) 11.41조원」으로 그렸다.
    같은 원장의 엔진 §2 는 같은 칸을 「추정 불가」로 적어 두었다 — 한 화면에
    **「TAM 추정 불가」와 「TAM 11.41조」가 서로를 모른 채 나란히** 떴다.

    규칙을 하나로 줄인다 — **항이 전부 관측일 때만 값을 낸다.**

    ⚠ **요인 표는 지우지 않는다.** 값을 못 내는 것과 아무것도 모르는 것은 다르다.
    「거래액 38.04조까지는 관측했고 점유율이 없어 환산을 못 한다」가 사용자에게 줄 답이다.
    """
    비관측 = [f for f in 요인들 if f["판정"] != "관측"]
    if not 비관측:
        return True, ""
    항 = " · ".join(f"{f['이름']}({f['판정']})" for f in 비관측)
    return False, (f"관측이 아닌 항이 {len(비관측)}개 섞여 있어 값을 내지 않는다 — {항}. "
                   f"관측에 가정을 곱하면 지어낸 수가 관측처럼 보인다")


def _엔진_결론(엔진_판정: list, target: str) -> str:
    """엔진이 같은 칸에 뭐라고 적었는지. **판정 층의 사유에 붙여 둘을 한 줄에 세운다.**

    이 값은 판 ㊳ 이전까지 `엔진_§2` 키에 복사만 되고 **읽는 코드가 0곳**이었다 —
    즉 엔진의 「추정 불가」 판정은 봉투에도 화면에도 닿지 않고 사라졌다.
    """
    for x in (엔진_판정 or []):
        if x.get("target") == target:
            st, bd = x.get("status"), x.get("badge")
            return f" (엔진 §2: {st or bd or '기록 없음'})"
    return ""


# `_SEG_WARN` 은 `_역할_요인` 이 대신한다(판 ㉞). 그 함수가 하던 일 셋 — 값·상한 울타리·
# 원 근거 서술 — 은 이제 요인 한 줄의 `값`·`울타리`·`설명` 이고, **잘리지 않는다.**


_YEAR = re.compile(r"(?:19|20)\d{2}")


def _growth_units() -> tuple:
    """증감률의 재료가 되는 **LEVEL 단위**. 규칙 파일이 단일 원천이다(규약 ①)."""
    g = _growth_rules() or {}
    a = (g.get("허용_단위") or {}).get("class") or list(_COUNT_UNITS)
    ex = set((g.get("허용_단위") or {}).get("제외_class") or [])
    return tuple(u for u in a if u not in ex)


def judge_growth(led: dict) -> dict | None:
    """성장률 — **연도별 사업체 수 관측 2건의 단순 증감률.** LLM 0회.

    관측은 엔진이 하고 계산은 이 층이 한다(결정 2 선례). 엔진에 성장 연산을 넣지 않은
    이유는 `blocks/b_estimate.py` 의 템플릿이 `mul·div·pick` 셋뿐이라 CAGR 을 표현할 수
    없고, 그걸 늘리는 것은 이 판의 범위가 아니기 때문이다(백로그: T6 템플릿).

    ⚠ **CAGR 이 아니다.** 두 해를 잇는 **한 구간의 증감률**이고, 그 사실을 산출물에
    문자열로 박아 내보낸다 — 「성장률」이라는 말만 보고 연평균으로 읽으면 곱셈에서 틀린다.
    """
    by_slot = {s["slot_id"]: s for s in led["slots"]}
    obs = []
    for c in _confirmed(led, {"GROWTH"}):
        # ⚠ **여기서 `_COUNT_UNITS` 를 쓰지 않는다.** 그것은 「사업체 수」 단위라
        # **계열 A 전제**다 — 계열 C 의 거래액(원)·B 의 인구(명)가 확인됨인데도 걸렸다
        # (판 ㉓ 실측). 허용 목록은 `rules/growth.v1.json` 에 값으로 있다(규약 ①).
        if c["value"] is None or (c["unit"] or "") not in _growth_units():
            continue
        m = _YEAR.search(str((by_slot.get(c["slot_id"]) or {}).get("period") or ""))
        if m:
            obs.append({**c, "year": int(m.group(0))})
    if len(obs) < 2:
        return {"값": None, "사유": (f"성장률 축(GROWTH) 확인됨 {len(obs)}건 — "
                                  f"두 해가 있어야 구간을 만든다"),
                "근거": obs or None}

    obs.sort(key=lambda x: x["year"])
    a, b = obs[0], obs[-1]
    # 단위가 다른 두 해를 이으면 증감률이 무의미하다 — **조용히 계산하지 않는다.**
    if ((_growth_rules() or {}).get("허용_단위") or {}).get("두_관측_단위_일치_강제") \
            and (a["unit"] or "") != (b["unit"] or ""):
        return {"값": None, "사유": f"두 관측의 단위가 다르다({a['unit']} vs {b['unit']}) — "
                                  f"구간을 만들 수 없다", "근거": obs}
    if a["year"] == b["year"] or not a["value"]:
        return {"값": None, "사유": "연도가 갈리지 않는다(같은 해) — 구간을 만들 수 없다",
                "근거": obs}
    span = b["year"] - a["year"]
    rate = (b["value"] - a["value"]) / a["value"]
    g = _growth_rules()
    가정 = list(g.get("가정") or [])
    out = {
        "식": "증감률 = (나중 해 값 − 이전 해 값) ÷ 이전 해 값",
        "입력": {f"{a['year']}": a["value"], f"{b['year']}": b["value"]},
        "값": rate,
        "값_퍼센트": round(rate * 100, 4),
        "구간_년": span,
        # **경계 표시 — 지우지 않는다.** 이 문자열이 없으면 연평균으로 오독된다.
        # 문구는 `rules/growth.v1.json` 에서 온다(규약 ①) — 실행 기록에 값째로 남는다.
        "표시": (g.get("표시") or {}).get("형식", "").format(
            span=span, y0=a["year"], y1=b["year"]),
        "가정": 가정,
        # 성장률의 항은 **둘 다 관측**이다. 그래서 요인 표에 가정 행이 없고,
        # `가정` 의 문장들은 항이 아니라 **해석**에 붙는다(연평균이 아니다·과거다).
        # 둘을 같은 칸에 두면 「관측 2건의 산술」이 「지어낸 값」과 같은 무게로 읽힌다.
        "요인": [_요인(f"{x['year']}년", x["value"], "관측", 단위=x["unit"], rows=[x])
               for x in (a, b)],
        "해석_경계": 가정,
        # 코드가 따로 세지 않는다 — 가정 목록의 길이가 곧 개수다.
        "assumption_count": len(가정),
        # 입력 사실 2건의 trace 를 **반드시** 싣는다 — 어느 관측에서 나왔는지 되짚을 수 있어야 한다
        "근거": [{"slot_id": x["slot_id"], "fact_id": x["fact_id"], "trace_id": x["trace_id"],
                "year": x["year"], "value": x["value"], "unit": x["unit"],
                "kind": x["kind"], "score": x["score"], "url": x["url"]} for x in (a, b)],
        # 관측 2건이 **같은 표에서** 나온 경우가 흔하다(S20·S21 은 둘 다 kosis).
        # 「2건」만 보면 교차된 것처럼 읽힌다 — 도메인을 같이 낸다.
        "대조_기반": _대조_기반([a, b]),
    }
    if len(obs) > 2:
        out["주의"] = (f"GROWTH 확인됨이 {len(obs)}건이라 **가장 이른 해와 가장 늦은 해**만 썼다"
                     f"({a['year']}→{b['year']}). 중간 해는 계산에 들어가지 않았다")

    # ── 시차 경고 — **자동 부착.** 사람이 매번 붙이면 빠뜨린다 ──────
    # 기준일은 «오늘»이 아니라 원장의 `reference_date` 다. 오늘로 재면 같은 원장이
    # 날짜가 바뀔 때마다 다른 문서를 낸다(`rules/scoring.reference_date` 와 같은 이유).
    warn = g.get("시차_경고") or {}
    as_of = str(led.get("reference_date") or "")
    m_as = _YEAR.search(as_of)
    if m_as and warn.get("문구"):
        gap = int(m_as.group(0)) - b["year"]
        out["시차_년"] = gap
        if gap > int(warn.get("임계_년", 2)):
            out["시차_경고"] = warn["문구"].format(y1=b["year"], as_of=as_of, gap=gap)
    return out


# ══════════════════════════════════════════════════════════════
# 5. 시장 크기 — **관측 층위를 낸다. 계산하지 않는다.**
#
# ★ 판 ㊳ 에서 계열별 계산식(T2·T7)과 `_judge_market_t7`·`_pick_money` 를 들어냈다.
#   두 식 다 관측 하나에 가정 여럿을 곱했고, 가정 곱셈을 막자 **어느 식을 골라도
#   값이 안 나왔다** — 갈림길이 아무것도 안 가르면서 계열이 틀리면 틀린 사유만 냈다.
#   고객 단위 정합 검사(`계열_고객_단위`)는 하네스·게이트에 **그대로 살아 있다.**
# ══════════════════════════════════════════════════════════════
_STALE_YEARS = 3


def _market_observations(led: dict) -> list:
    """시장 크기 **관측을 층위 그대로** 모은다. 금액·개수를 가리지 않는다.

    ★ 판 ㊳ — 여기가 계열 A~E 를 대체한다. 예전에는 계열이 계산식을 골랐고
    (T2 = 사업체수 × 세그먼트비중 × 침투율 × 단가 × 12, T7 = 거래액 × 점유율),
    두 식 모두 **관측 하나에 가정 여럿을 곱하는 것**이었다. 가정 곱셈을 막고 나니
    **어느 식을 고르든 값이 안 나온다** — 갈림길이 아무것도 안 가르면서, 계열이
    틀리면 **틀린 사유**만 냈다(실측: 「점유율 관측이 없다」가 「전국 사업체 수 0건」으로).

    그래서 식을 고르지 않는다. **관측한 것을 층위 그대로 낸다.**
    """
    by_slot = {s["slot_id"]: s for s in led["slots"]}
    as_of = _YEAR.search(str(led.get("reference_date") or ""))
    올해 = int(as_of.group(0)) if as_of else None
    본: dict = {}
    for c in _confirmed(led, {"TAM", "SAM"}):
        if c.get("value") is None:
            continue
        s = by_slot.get(c["slot_id"]) or {}
        m = _YEAR.search(str(s.get("period") or ""))
        연도 = int(m.group(0)) if m else None
        # ⚠ **같은 사실이 여러 슬롯에 채택된다.** 실측: KOSIS 거래액 한 건이 TAM 슬롯과
        #   SAM 슬롯에 동시에 앉아 TAM=SAM 이 됐다. 층위로 두 번 세면 **없는 층이 생긴다** —
        #   fact_id 로 접고, 몇 칸에 앉았는지는 정보로 남긴다.
        #   ⚠ `fact_id` 로는 안 접힌다 — 슬롯마다 새 id 가 붙기 때문이다(실측: F001·F002 가
        #     같은 KOSIS 표·같은 해·같은 수인데 id 만 달랐다). **값·단위·연도**가 같으면 같은 층이다.
        키 = (c.get("value"), c.get("unit"), 연도)
        if 키 in 본:
            본[키]["앉은_슬롯"].append(c.get("slot_id"))
            continue
        본[키] = {
            "이름": s.get("subject") or s.get("metric") or c["slot_id"],
            "계량": s.get("metric"),
            "값": c["value"], "단위": c.get("unit"),
            "연도": 연도,
            "낡음": bool(올해 and 연도 and (올해 - 연도) > _STALE_YEARS),
            "등급": c.get("등급"), "앉은_슬롯": [c.get("slot_id")], "행": c,
        }
    # 큰 층위부터 — 사람이 「상한 → 우리 자리」 순으로 읽는다.
    return sorted(본.values(), key=lambda x: (str(x["단위"]), -(x["값"] or 0)))


#: 시장 크기 절이 관측을 어떻게 읽어야 하는지. **계산식이 아니다.**
_LAYER_NOTE = ("이 파이프라인은 시장 크기를 **식으로 계산하지 않는다.** "
               "아래는 계산식의 항이 아니라 **관측한 층위 목록**이다 — "
               "큰 층위는 상한이고, 우리 사업은 그 안의 일부다")


def judge_market(led: dict, hyp: dict, concept: dict | None = None) -> dict:
    """시장 크기 — **관측 층위를 낸다. 계산하지 않는다.**

    ⚠ `concept` 은 더 이상 읽지 않는다(계열 분기 폐지). 인자는 부르는 쪽 호환으로 남긴다.
    고객 단위 정합 검사(`계열_고객_단위`)는 **하네스·게이트에 그대로 살아 있다** —
    그것은 계산 경로가 아니라 슬롯 설계 검사라 이 폐지와 무관하다.
    """
    head = {x.get("target"): x for x in (led["report"].get("headline_numbers") or [])}
    out = {"엔진_§2": [{"target": t, **{k: head.get(t, {}).get(k)
                                      for k in ("value", "badge", "status")}}
                     for t in ("TAM", "SAM", "SOM") if t in head],
           "_방식": "관측 층위 나열 (판 ㊳ — 계열별 계산식 폐지)"}

    층 = _market_observations(led)
    if not 층:
        out["TAM_추정"] = None
        out["사유"] = ("시장 크기 관측 0건 — 낼 층위가 없다"
                      + _엔진_결론(out["엔진_§2"], "TAM"))
        out["SAM_추정"] = None
        out["SAM_사유"] = "시장 크기 관측 0건"
        return out

    요인 = [_요인(x["이름"], x["값"], "관측", 단위=x["단위"],
                설명=" · ".join(y for y in (
                    f"{x['연도']}년" if x["연도"] else None,
                    "⚠ 3년 넘은 관측 — 낡음" if x["낡음"] else None,
                    f"등급 {x['등급']}" if x.get("등급") else None,
                    (f"⚠ 같은 관측이 슬롯 {len(x['앉은_슬롯'])}칸"
                     f"({', '.join(str(s) for s in x['앉은_슬롯'])})에 함께 앉아 있다 — "
                     f"층이 늘어난 것이 아니다")
                    if len(x.get("앉은_슬롯") or []) > 1 else None) if y),
                rows=[x["행"]])
           for x in 층]

    out["시장_관측"] = [{k: v for k, v in x.items() if k != "행"} for x in 층]
    out["TAM_추정"] = {
        "식": None,                       # **식이 없다.** 계산하지 않으므로.
        "입력": {},
        "값": None,
        "assumption_count": 0,            # 가정을 하나도 안 쓴다
        "요인": 요인,
        "가정": [],
        "해석_경계": [_LAYER_NOTE],
        "값_불가_사유": (
            f"시장 크기를 계산하지 않는다 — 관측 {len(층)}개 층위를 그대로 낸다. "
            "예전에는 관측 하나에 가정(점유율·침투율·세그먼트비중)을 곱해 값을 만들었고, "
            "그 값이 화면에 관측처럼 떴다"
            + _엔진_결론(out["엔진_§2"], "TAM")),
        "관측된_밑동": {"이름": 층[0]["이름"], "값": 층[0]["값"], "단위": 층[0]["단위"]},
        "근거": [x["행"] for x in 층],
        "대조_기반": _대조_기반([x["행"] for x in 층]),
    }
    # SAM 은 **조사 범위를 좁히는 축**이 정의돼야 낼 수 있다. 지금은 그 축이 없다.
    out["SAM_추정"] = None
    out["SAM_사유"] = ("조사 범위를 좁히는 축(지역·세그먼트)이 관측으로 정의되지 않았다 — "
                      "TAM 층위를 그대로 SAM 이라 부르지 않는다"
                      + _엔진_결론(out["엔진_§2"], "SAM"))
    return out


# ══════════════════════════════════════════════════════════════
# 6. 수익 방식 + 가격
# ══════════════════════════════════════════════════════════════
def _trust_rules() -> dict:
    p = os.path.join(ROOT, "rules", "trust_labels.v1.json")
    return json.load(io.open(p, encoding="utf-8")) if os.path.exists(p) else {}


def _대조_기반(rows: list, 가정: bool = False) -> dict:
    """그 값이 **몇 건 · 몇 화자**에서 나왔는가. 문구는 규칙 파일에서 온다(규약 ①).

    ⚠ **건수와 화자 수는 다른 수다.** 판 ⑩ 의 밴드는 3건이었지만 도메인은 `gongbiz.kr`
    하나였다 — 3중 확인이 아니라 **1중**이다. 두 수를 나란히 싣지 않으면 읽는 쪽이
    건수를 화자 수로 읽는다. 그래서 이 칸은 **항상 둘 다** 낸다.
    """
    cfg = (_trust_rules().get("대조_기반") or {})
    문구 = cfg.get("경계_문구") or {}
    doms = sorted({(str(r.get("url") or "").split("/")[2] if "//" in str(r.get("url") or "")
                    else "") for r in rows} - {""})
    out = {"출처_수": len(rows), "원출처_도메인": doms, "경계": []}
    if 가정:
        out["경계"].append(문구.get("가정", "관측이 아니라 가정이다 — 출처 0건"))
    elif len(doms) <= 1 and rows:
        out["경계"].append(문구.get("단일_도메인", "").format(n=len(doms), k=len(rows)))
    return out


def _price_band_rows(led: dict) -> list:
    """가격 밴드 재료. **채택 기준은 `rules/consistency.v1.json::price_band` 하나다.**

    ⚠ 이 함수가 있는 이유: 예전에는 여기가 「확인됨」만 세고 `blocks/c_chain.py::_price_band`
    도 따로 「확인됨」만 셌다. 같은 값을 두 곳이 각자 세고 있었고, 한쪽 기준이 바뀌자
    **게이트는 「가격 밴드 충족」이라 하고 캔버스는 「판정_불가」라 하는 자기모순 문서**가
    나왔다(판 ⑩ 실측). 판 ① 의 채널 축에서 같은 병을 이미 한 번 앓았다 —
    **자를 둘 두지 않는다.** 규칙 파일이 정본이고 여기는 읽기만 한다.
    """
    cfg = (_rules().get("consistency") or {}).get("price_band") or {}
    ctypes = set(cfg.get("claim_types") or ("PRICE", "COMP"))
    accepts = [a for a in (cfg.get("accept") or [{"id": "confirmed", "enabled": True,
                                                  "labels": ["확인됨"]}]) if a.get("enabled")]
    by_slot = {s["slot_id"]: s for s in led["slots"]}
    ids = {sid for sid, s in by_slot.items() if s.get("claim_type") in ctypes}
    out = []
    for r in led["ledger_rows"]:
        if r["slot_id"] not in ids:
            continue
        f = led["facts"].get(r.get("fact_id")) or {}
        for a in accepts:
            if a.get("labels") and r.get("label") not in a["labels"]:
                continue
            if a.get("kinds") and r.get("kind") not in a["kinds"]:
                continue
            if a.get("min_score") is not None and (r.get("score") or 0) < a["min_score"]:
                continue
            if a.get("require_quote_verified") and f.get("quote_verified") is not True:
                continue
            v = f.get("value_num")
            if v is not None:
                out.append({"fact_id": r.get("fact_id"), "slot_id": r["slot_id"],
                            "value": v, "unit": f.get("unit_norm"),
                            "kind": r.get("kind"), "score": r.get("score"),
                            "label": r.get("label"), "url": r.get("url"),
                            "_채택_갈래": a.get("id")})
            break
    return out


def judge_price(led: dict, hyp: dict) -> dict:
    h = hyp.get("6_수익_가격") or {}
    price = h.get("제안값_krw_월")
    r7 = (led["violations"].get("R7") or {})
    band = [c for c in _price_band_rows(led) if c["value"] is not None]

    if r7.get("status") == "not_applicable" or len(band) < 2:
        return {"가설": "6_수익_가격", "가설값": price, "도장": "판정_불가",
                "why": r7.get("detail") or ("대체재 가격 밴드가 서지 않았다 — "
                                           "`consistency.price_band.accept` 를 통과한 화폐 사실이 2건 미만"),
                "근거": band, "밴드": None,
                "대조_기반": _대조_기반(band)}
    lo, hi = min(c["value"] for c in band), max(c["value"] for c in band)
    inside = price is not None and lo <= price <= hi
    return {"가설": "6_수익_가격", "가설값": price,
            "도장": "검증됨" if inside else "미검증",
            "why": f"대체재 밴드 [{lo:,.0f}, {hi:,.0f}] 의 " + ("안" if inside else "밖"),
            "근거": band, "밴드": [lo, hi],
            "대조_기반": _대조_기반(band)}


# ══════════════════════════════════════════════════════════════
# 7. 채널 — 관측은 엔진, **계산은 이 층**(결정 2)
# ══════════════════════════════════════════════════════════════
#: 채널 판정에 **어느 갈래에서든** 붙는 뒷문장. 침묵이 승인으로 읽히는 것을 막는다.
#  경계 표시라 분기 밖으로 빼 둔다 — 가지를 하나 더 치다가 빠뜨리는 종류의 문장이다.
CHANNEL_TAIL = "채널 없이 BM 이 성립한다는 뜻이 아니다."


def _channel_rules() -> dict:
    return json.load(io.open(os.path.join(ROOT, "rules", "channel_assumption.v1.json"),
                             encoding="utf-8"))


def _channel_assumption(som_hyp: dict) -> dict:
    """관측 0건일 때의 **가정 승격**. LLM 0회 · 원장 쓰기 0.

    값·밴드·환율·경계 문구는 전부 `rules/channel_assumption.v1.json` 에서 온다(규약 ①).
    **이것은 관측이 아니다.** `_출처` 는 「원장 밖 참고」로 실어 보내고 사실인 척하지 않는다 —
    S15 는 확인됨 0건이고 그 판정은 여기서 바뀌지 않는다.
    """
    r = _channel_rules()
    lo, hi = r["채택_밴드_usd"]
    fx = r["환율_krw_per_usd"]
    rate, f_lo, f_hi = fx["value"], *fx["range"]
    krw = [lo * rate, hi * rate]
    out = {
        "_이것은_관측이_아니다": r["_이것은_관측이_아니다"],
        "경계": [r["경계"]["1"], r["경계"]["2"]],
        "식": "CAC(원) = 해외 벤치마크 CAC(USD) × 환율 가정",
        "입력": {"CAC 밴드(USD)": [lo, hi], "환율(원/USD)": rate},
        "값": {"CAC 밴드(원)": krw,
              "환율 range 적용": [lo * f_lo, hi * f_hi]},
        "assumption_count": r["assumption_count"],
        "가정": list(r["가정"]),
        "밴드_선택_사유": r["_채택_사유"],
        "제외한_값": r["_왜_다른_숫자를_안_썼나"],
        "참고_출처": r["_출처"],
        "등급": "추정",
    }
    # 필요 마케팅비 — 목표 고객 수가 있을 때만. 없으면 **계산하지 않고 사유를 남긴다.**
    targets = (som_hyp or {}).get("_목표_고객수")
    if targets:
        out["필요_마케팅비_추정"] = {
            "식": "필요 마케팅비 = 목표 고객 수 × CAC",
            "입력": {"목표 고객 수": targets, "CAC 밴드(원)": krw},
            "값": [targets * krw[0], targets * krw[1]],
            "가정": ["목표 고객 수 자체가 SOM 가정 사슬에서 온 값이다 — 가정 위의 가정이다",
                   "이탈·재획득 비용은 반영하지 않았다"]}
    else:
        out["필요_마케팅비_사유"] = "목표 고객 수가 없다(SOM 추정 부재) — 곱하지 않는다"
    return out


def judge_channel(led: dict, hyp: dict, som_hyp: dict) -> dict:
    """**축_부재와 미검증을 슬롯 유무로 가른다.**

    옛 구현은 CAC 확인됨이 0건이면 무조건 축_부재를 찍었다 — 슬롯을 보지 않았다.
    그래서 CHANNEL 슬롯을 넣고 못 채운 원장까지 «재지 않았다»로 읽혔고, 성적표는
    미충족(재려다 못 채웠다)인데 이 층은 축_부재라고 말하는 **자기모순 문서**가 나왔다.
    둘을 섞지 않는다는 규칙(설계_v0 §7)은 성적표에만 걸리는 규칙이 아니다.
    """
    h = hyp.get("7_채널") or {}
    slot_ids = sorted(s["slot_id"] for s in led["slots"]
                      if s.get("claim_type") == "CHANNEL")
    cac = [c for c in _confirmed(led, {"CHANNEL"})
           if c["value"] is not None and (c["unit"] or "") in ("원", "KRW")]
    base = {"가설": "7_채널", "가설값": h.get("주_채널_가정"), "근거": cac,
            "채널_슬롯": slot_ids}
    if not slot_ids:
        # «재지 않았다» — 이 원장에 채널을 재는 슬롯 자체가 없다
        return {**base, "도장": "축_부재",
                "why": ("채널 축이 관측된 적이 없다(CHANNEL 슬롯 0개). " + CHANNEL_TAIL),
                "추정": None}
    if not cac:
        # «재려다 못 채웠다» — 슬롯은 있고 확인됨이 0건이다. 축_부재로 내리지 않는다.
        # 관측이 없으므로 **가정을 선언하고 계산한다**(judge_market 의 세그먼트비중 승격과 같은 자리).
        # ⚠ 도장은 **미검증 그대로**다. 가정 승격은 계산을 채우는 것이지 판정을 올리는 것이 아니다.
        return {**base, "도장": "미검증",
                "why": (f"채널 슬롯 {slot_ids} 은 있으나 CAC 확인됨 0건 — "
                        f"재려다 못 채운 것이지 재지 않은 것이 아니다. " + CHANNEL_TAIL),
                "추정": _channel_assumption(som_hyp)}

    # 필요 마케팅비 = 목표 고객 수 × CAC. **엔진은 CAC 만 관측하고 곱셈은 여기서 한다.**
    unit_cac = min(c["value"] for c in cac)
    targets = som_hyp.get("_목표_고객수")
    calc = None
    if targets:
        calc = {"식": "필요 마케팅비 = 목표 고객 수 × CAC",
                "입력": {"목표 고객 수": targets, "CAC(원)": unit_cac},
                "값": targets * unit_cac,
                "가정": ["CAC 는 관측된 값 중 최솟값을 썼다 — 낙관 방향이다",
                       "이탈·재획득 비용은 반영하지 않았다"]}
    return {**base, "도장": "미검증",
            "why": ("CAC 관측은 있으나 이 채널이 가설대로 작동한다는 근거는 아니다. "
                    + CHANNEL_TAIL),
            "추정": calc}


# ══════════════════════════════════════════════════════════════
# 8. 차별점 — 축별로 따로 찍는다. **미검증이어도 새 축을 제안하지 않는다**
# ══════════════════════════════════════════════════════════════
def judge_diff(led: dict, hyp: dict, concept: dict) -> dict:
    h = hyp.get("8_차별점") or {}
    comp = _confirmed(led, {"COMP", "COMPARABLE"})
    exposure = concept.get("_노출_기록")
    axes = []
    for i, ax in enumerate(h.get("비교축") or []):
        row = {"축": ax.get("축"), "우리_값": ax.get("우리_값"),
               "도장": "축_부재",
               "why": "이 축은 기능 유무라 수치 관측 대상이 아니다 — 슬롯을 만들지 않았다",
               "근거": []}
        if "요금" in (ax.get("축") or ""):
            band = [c for c in comp if (c["unit"] or "") == "원"]
            row.update({"도장": "미검증" if band else "판정_불가",
                        "why": ("경쟁 요금 관측은 있으나 우리 요금 체계가 다르다는 판정은 "
                                "사람이 한다" if band else "경쟁 요금 확인됨 0건"),
                        "근거": band})
        if i == 0 and exposure:
            # 규칙 6 의 잔여 위험을 판정에 **꼬리표로** 남긴다 — 지우지 않는다
            row["_노출_꼬리표"] = exposure
        axes.append(row)
    return {"가설": "8_차별점", "축": axes,
            "도장": "미검증" if any(a["도장"] == "미검증" for a in axes) else "축_부재",
            "why": "축별 도장이 정본이다. 묶음 도장은 요약일 뿐이다"}


# ══════════════════════════════════════════════════════════════
# 9. SOM — 계산 과정과 가정을 **같이** 나른다
# ══════════════════════════════════════════════════════════════
def judge_som(led: dict, hyp: dict) -> dict:
    h = hyp.get("9_SOM_초기점유") or {}
    rate, price = h.get("가정_침투율"), (hyp.get("6_수익_가격") or {}).get("제안값_krw_월")
    # 기준은 예전과 같은 버킷(TAM+SAM)에서 오되 **최댓값이 아니라 결정론적 선택**이고,
    # 후보가 여럿이면 주의가 함께 나간다. 전국·서울이 뒤섞이는 것 자체는 원장 설계 문제라
    # 여기서 조용히 정리하지 않고 **드러낸다**.
    base, counts, warn = _pick_base(led, {"TAM", "SAM"})
    head = {x.get("target"): x for x in (led["report"].get("headline_numbers") or [])}
    som = head.get("SOM") or {}

    if not counts:
        return {"가설": "9_SOM_초기점유", "도장": "판정_불가",
                "why": "사업체 수 확인됨 0건 — 분해의 첫 항이 없다",
                "추정": None, "엔진_SOM": som.get("value"), "badge": som.get("badge")}

    # 세그먼트비중 — **가정이다.** 예전에는 「TAM/SAM 확인됨 중 단위가 % 인 것」을
    # 관측으로 받아 100 으로 나눠 썼는데, 그 조건은 **아무 %나 통과시킨다**(위험 ②).
    # 판 ④ 에서 성장률(%)·비율(%) 관측이 들어오면 그중 하나가 조용히 세그먼트비중이 된다.
    # 「1인 비율」은 어느 국가통계에도 없음이 실측으로 확정됐으므로(kosis-probe-04)
    # 이 자리는 **가정으로 고정**하고, 관측으로 승격하는 문은 닫는다.
    # ⚠ 판 ㊴ — 그 가정값(0.19, 두발 미용업 구성비)마저 지웠다. 문은 여전히 닫혀 있고,
    #   이제 **가정도 없다** — 그래서 이 자리는 「모른다」가 정답이다.
    by_role = (_rules().get("assumptions") or {}).get("by_role") or {}
    a = by_role.get("세그먼트비중") or {}
    seg = a.get("value")
    # ★ 판 ㊴ — **`seg = 1.0 if seg is None else seg` 를 없앴다.**
    #   그 한 줄은 「세그먼트 비중을 모른다」를 「세그먼트 = 시장 전체」로 읽었다. 값이
    #   0.19 이던 시절 기준으로 목표 고객 수가 **5배** 부풀고, 그 수가 판정 층을 지나
    #   `judge_channel` 의 필요 마케팅비까지 그대로 곱해진다. 가정값이 없다는 것은
    #   «전부» 가 아니라 «못 잰다» 다(절대규칙 5 — 실패는 값이다).
    seg_src = ("가정(rules/assumptions.v1.json) — 관측 아님" if seg is not None
               else SEG_MISSING)

    # 침투율은 **원장이 아니라 컨셉의 SOM 가설**에서 온다 — rules 의 침투율과 다른 값이다.
    # 그래서 rules 의 basis 를 붙이지 않고 출신을 그대로 적는다.
    요인 = [
        _요인("사업체 수", base, "관측", 단위="개", rows=counts,
            설명=f"슬롯 {counts[0]['slot_id']} 의 확인됨"),
        _역할_요인("세그먼트비중", "세그먼트비중", seg, by_role,
                 설명=(None if seg is not None else SEG_MISSING)),
        _요인("침투율", rate, "가정", 단위="비율",
            설명="관측 근거가 없는 순수 가정 — 컨셉의 9_SOM_초기점유 가설에서 왔다"),
        _요인("월 구독가", price, "가설", 단위="원", 설명=_PRICE_NOTE),
        _요인("개월", 12, "가정", 단위="개월", 설명="이탈 없는 12개월 만액 결제"),
    ]
    # ★ 세그먼트 비중이 없으면 **목표 고객 수부터 내지 않는다.** 이 수는 여기서 끝나지
    #   않고 `build` 가 `som_hyp["_목표_고객수"]` 로 `judge_channel` 에 넘긴다 — 여기서
    #   때우면 마케팅비 추정까지 같은 배수로 부푼다. 없으면 None 을 그대로 흘린다.
    targets = (base * seg * (rate or 0)) if seg is not None else None
    # ★ 판 ㊳ — TAM·SAM 과 **같은 잠금**. SOM 도 관측 하나에 가정 넷을 곱한다.
    #   봉투에는 안 실리지만(`serialize.market` 이 som=None 고정) 캔버스·BM 층으로는 간다 —
    #   한쪽에서만 정직하면 두 산출물이 갈린다.
    som_가능, som_사유 = _점추정_가능(요인)
    if seg is None:
        som_가능 = False
        som_사유 = f"{SEG_MISSING} — SOM 을 계산하지 않는다. " + som_사유
    calc = {"식": "SOM(연) = 사업체 수 × 세그먼트비중 × 침투율 × 월 구독가 × 12",
            "입력": {"사업체 수": base, "세그먼트비중": seg, "세그먼트비중_출처": seg_src,
                   "침투율": rate, "월 구독가": price, "개월": 12},
            "목표 고객 수": targets,
            "값": (targets * price * 12) if (som_가능 and price and targets) else None,
            "값_불가_사유": (None if som_가능 else som_사유),
            "assumption_count": _가정수(요인),
            "요인": 요인,
            "가정": _가정_문장(요인),
            "해석_경계": [],
            "근거": counts,
            **({"선택_주의": warn} if warn else {})}
    return {"가설": "9_SOM_초기점유",
            # 세그먼트 비중이 없으면 **아무것도 계산하지 못했다.** 「가정으로 계산한 값」
            # 이라고 적으면 없는 계산을 있다고 말하는 셈이다 — 도장도 같이 내린다.
            "도장": ("미검증" if seg is not None else "판정_불가"),
            "why": ("가정으로 계산한 값이다 — 관측이 뒷받침한 것이 아니다" if seg is not None
                    else f"{SEG_MISSING} — 분해의 둘째 항이 없어 목표 고객 수부터 못 낸다. "
                         f"요인 표는 그대로 낸다(사업체 수까지는 관측했다)"),
            "추정": calc, "엔진_SOM": som.get("value"), "badge": som.get("badge")}


# ══════════════════════════════════════════════════════════════
def build(run_id: str, concept_path: str) -> dict:
    led = bm_scorer.load_ledger(run_id)
    concept = load_concept(concept_path)
    hyp = concept.get("_hypotheses_v2") or {}

    som = judge_som(led, hyp)
    som_hyp = {"_목표_고객수": (som.get("추정") or {}).get("목표 고객 수")}
    out = {"run_id": led["run_id"], "concept": concept.get("name"),
           "concept_path": concept_path,
           # 성장률은 시장 추정과 **같은 칸**에 둔다 — 가설 4개(판정)를 늘리지 않는다.
           "시장_추정": {**judge_market(led, hyp, concept), "성장률_추정": judge_growth(led)},
           "_규칙": "도장 4개(검증됨·미검증·판정_불가·축_부재)만 쓴다. 판단문 없음.",
           "판정": {
               "6_수익_가격": judge_price(led, hyp),
               "7_채널": judge_channel(led, hyp, som_hyp),
               "8_차별점": judge_diff(led, hyp, concept),
               "9_SOM_초기점유": som,
           }}
    bad = [k for k, v in out["판정"].items() if v["도장"] not in STAMPS]
    if bad:
        raise SystemExit(f"도장 어휘 밖: {bad}")     # fail-closed
    return out


def render(v: dict) -> str:
    L = [f"# 판정 — {v['concept']} ({v['run_id']})", "",
         "| 가설 | 도장 | 사유 |", "|---|---|---|"]
    for k, x in v["판정"].items():
        L.append(f"| {k} | **{x['도장']}** | {x.get('why', '')} |")
    for k, x in v["판정"].items():
        if x.get("추정"):
            c = x["추정"]
            L += ["", f"## {k} — 계산", f"- 식: `{c['식']}`",
                  f"- 입력: {json.dumps(c['입력'], ensure_ascii=False)}",
                  f"- 값: {c.get('값')}", "- 가정:"] + [f"  - {a}" for a in c["가정"]]
        if k == "8_차별점":
            L += ["", "## 차별점 — 축별", "| 축 | 도장 | 사유 |", "|---|---|---|"]
            for a in x["축"]:
                L.append(f"| {a['축']} | {a['도장']} | {a['why']} |")
                if a.get("_노출_꼬리표"):
                    L.append(f"| ↳ 꼬리표 | | {a['_노출_꼬리표']} |")
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--concept", default="data/concept_beauty-noshow.json")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    v = build(a.run_id, a.concept)
    print(json.dumps(v, ensure_ascii=False, indent=2) if a.json else render(v))
    return 0


if __name__ == "__main__":
    sys.exit(main())
