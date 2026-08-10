# -*- coding: utf-8 -*-
"""골든 관통 — **정답 관측을 주입해 남은 문을 한 번에 다 센다** (판 ⑱ 개정). LLM 0회.

    python tools/golden_probe.py --run pet-treat-07 --concept data/concept_pet-treat.json

왜 있는가: 판 ⑬~⑱ 이 **한 판에 한 겹씩** 벗겼다(판정층 → 라우팅 → 축 → 표기 → 단위).
매번 「이게 마지막 겹」이라 믿었고 매번 틀렸다. **겹을 하나씩 세는 방식 자체가 비싸다.**
정답을 넣어 보면 **막는 층이 한 번에 다 드러난다.**

무엇을 넣나 — **세 벌을 같이 넣어 양방향으로 잰다:**

    golden   정답      017(애완용품) · 백만원 · 올바른 표기 · 프로브 참값
    bad_axis 오답 ①    합계(전체 온라인쇼핑) — 판 ⑮ 의 274조
    bad_unit 오답 ②    배수 미적용 — 판 ⑰ 의 100만 배 축소

**정답이 통과하고 오답이 막혀야** 그 층이 제 일을 하는 것이다.
정답이 막히면 **그 층이 남은 문**이고, 오답이 통과하면 **가드가 뚫린 것**이다.

⚠ **이것은 원장을 쓰지 않는다.** 합성 사실을 메모리에서 심사에 통과시켜 볼 뿐이다.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "service")):
    sys.path.insert(0, p)

import a_desk as A4                                              # noqa: E402
from runlog import load_rules                                    # noqa: E402
from schema import Document, Fact, Slot                          # noqa: E402

#: 프로브 참값 (`runs/kosis-probe-yearaxis`) — 애완용품 거래액, 단위 백만원
GOLDEN = {2023: 2589262.0, 2024: 2792575.0}
MILLION = 1_000_000

#: KOSIS 응답을 흉내 낸 본문. **표기는 실제 응답 그대로** — 「애완용품」이라 적힌다.
BODY = ('{{"C1_NM": "애완용품", "C1": "017", "ITM_NM": "거래액", '
        '"DT": "{dt}", "UNIT_NM": "백만원", "PRD_DE": "{yr}"}}')


def mk(slot: Slot, dt: float, yr: int, *, scaled: bool, subject_nm: str = "애완용품"):
    body = BODY.format(dt=int(dt), yr=yr).replace("애완용품", subject_nm)
    doc = Document(slot_id=slot.slot_id, trace_id=f"{slot.slot_id}-g", url="https://kosis.kr/g",
                   text=body, content_status="usable")
    f = Fact(fact_id="F900", slot_id=slot.slot_id, var_id=slot.var_id, trace_id=doc.trace_id,
             url=doc.url, quote=f'"DT": "{int(dt)}"',
             value_num=(dt * MILLION) if scaled else dt,
             unit_norm="원", year=yr, dedup_key="g", match_key="g",
             quote_verified=True, content_status="usable", channel="kosis_api")
    return f, doc


def trace(slot: Slot, f: Fact, doc: Document, rules: dict, ref_year: int,
          raw: tuple | None = None) -> dict:
    """한 사실을 심사 전 층에 통과시켜 보고, **막힌 층과 사유**를 돌려준다.

    `raw=(number_raw, unit_raw)` 를 주면 **단위층(`parse_number`)부터** 태운다.
    ⚠ **이것이 없으면 계기가 거짓말을 한다** — 판 ⑲ 실측: 이미 파싱된 값만 넣었더니
    `bad_unit` 이 `value_range` 를 통과해 「가드가 뚫렸다」로 보였다. 실제로는 그 오답이
    **단위층에서 먼저 걸린다**(판 ⑱ `unknown_multiplier`). **계기는 실제 경로를 태워야 한다.**
    """
    steps = []
    if raw is not None:
        v, un, _ = A4.parse_number(raw[0], raw[1], rules["units"])
        steps.append({"층": "단위(parse_number)", "통과": v is not None,
                      "사유": f"{raw[0]} + {raw[1]} → value={v} unit={un}"})
        if v is None:
            return {"막힌_층": "단위(parse_number)", "단계": steps}
        f.value_num, f.unit_norm = v, un
    off = A4.off_slot_reason(f, slot, doc, rules)
    steps.append({"층": "off_slot(4겹)", "통과": off is None, "사유": off,
                  "표기_다리": list(getattr(f, "표기_다리", []) or [])})
    if off is None:
        led = A4.grade([f], {slot.slot_id: slot}, {doc.trace_id: doc}, rules, ref_year)
        row = led.rows[0]
        steps.append({"층": "등급", "통과": row.label == "확인됨",
                      "사유": f"label={row.label} score={row.score} kind={row.kind}",
                      "reasons": row.reasons[:3]})
    return {"막힌_층": next((s["층"] for s in steps if not s["통과"]), None), "단계": steps}


# ══════════════════════════════════════════════════════════════════════
# 기준 v2 오염 반례 (판 ㉙ S3) — **채택/등급 두 축**을 잰다
#
#   위의 사례들은 「값이 심사를 통과하는가」(단위·축·표기)를 잰다.
#   여기부터는 「**채워도 되는가**(채택)」와 「**어떻게 표기되는가**(등급)」를 잰다.
#   판 ㉙ 이 화이트리스트 하드 드롭을 여는 판이므로, **여는 것과 같은 판에서**
#   무엇이 막혀야 하는지를 기계로 못박아 둔다. 가드가 개방보다 먼저다.
# ══════════════════════════════════════════════════════════════════════
import copy as _copy                                              # noqa: E402

#: 각 반례의 `기대`. **불일치면 종료 코드 ≠ 0** — 사람 눈에 기대지 않는다.
V2_BODY = ('반려동물 애완용품 거래액은 2,792,575 백만원이다. '
           '{"C1_NM": "애완용품", "ITM_NM": "거래액", "UNIT_NM": "백만원"}')


def v2_case(slot: Slot, rules: dict, ref: int, *, url: str | list, quote: str,
            retrieved_at: str | None, body: str = V2_BODY, n_dup: int = 1) -> dict:
    """원문 층부터 태워 `채택`·`등급`·사유를 돌려준다.

    ⚠ **인용 대조는 흉내 내지 않는다** — 진짜 `normalize()` 를 태운다. 대조기가 관대해지면
      `poison_quote_soft` 가 여기서 뚫려야 하고, 손으로 `quote_verified=False` 를 넣으면
      그 관대함을 **영원히 못 본다**(판 ⑲ 「계기가 층을 건너뛰면 진단을 속인다」).
    """
    from schema import Finding, FindingItem
    # url 을 목록으로 주면 **서로 다른 도메인**을 만든다 — 화자 수를 재는 사례용.
    urls = url if isinstance(url, list) else [
        (url if i == 0 else url.rstrip("/") + f"/p{i}") for i in range(n_dup)]
    docs, findings = {}, []
    for i in range(len(urls)):
        tid = f"{slot.slot_id}-v2-{i}"
        docs[tid] = Document(slot_id=slot.slot_id, trace_id=tid, url=urls[i],
                             text=body, content_status="usable", retrieved_at=retrieved_at)
        findings.append(Finding(
            slot_id=slot.slot_id, trace_id=tid, status="found",
            findings=[FindingItem(quote=quote, number_raw="2792575",
                                  unit_raw="백만원", url=docs[tid].url)]))
    facts = A4.normalize(findings, docs, {slot.slot_id: slot}, rules)
    if not facts:
        return {"채택": False, "등급": None, "사유": ["사실이 만들어지지 않았다"],
                "label": None, "note": "normalize 0건"}
    led = A4.grade(facts, {slot.slot_id: slot}, docs, rules, ref)
    r = led.rows[0]
    return {"채택": r.채택, "등급": r.등급, "등급_근거": r.등급_근거,
            "사유": r.채택_불가_사유, "label": r.label, "score": r.score,
            "kind": r.kind, "kind_by": r.kind_by, "cross": r.cross}


def v2_cases(s1: Slot, rules: dict, ref: int) -> dict:
    """오염 반례 6종 + 정답 1종. 각 사례는 `기대` 를 달고 다닌다."""
    #: 값 범위·주제어 가드에 걸리지 않게 슬롯 사본을 쓴다 — 재는 것은 **v2 축**이다.
    s = _copy.deepcopy(s1)
    s.value_range = [1.0, 1e18]
    s.must_contain = ["애완용품"]
    GOV = "https://kosis.kr/v2"
    UNLISTED = "https://smartstore.example-shop.co/goods/12345"
    #: **등재 거부** 도메인 — 화이트리스트 `rejected` 에 「포털 재게시본 · 발행자 불명」으로
    #  적혀 있다. 판 ㉙ 전까지 그 결정에는 **실효가 없었다**(백로그 30).
    REJECTED = "https://v.daum.net/v/20260101000000"
    COMMUNITY = "https://cafe.naver.com/petfood/12345"
    OK_Q, TODAY = "2,792,575 백만원", "2026-08-09T21:00:00"

    out = {}
    out["poison_quote"] = {
        "무엇": "인용이 본문에 없다",
        "기대": {"채택": False, "label": "미검증", "사유_포함": "인용 대조 실패"},
        "실측": v2_case(s, rules, ref, url=GOV, quote='"9,999,999 백만원"',
                       retrieved_at=TODAY)}
    out["poison_quote_soft"] = {
        "무엇": "본문과 **거의 같은** 인용(공백·조사·숫자 표기) — 대조기가 관대해지면 여기서 뚫린다",
        "기대": {"채택": False, "label": "미검증", "사유_포함": "인용 대조 실패"},
        "실측": v2_case(s, rules, ref, url=GOV, quote='"2792575백만 원"',
                       retrieved_at=TODAY)}
    out["poison_no_retrieved"] = {
        "무엇": "4요건 중 **조회일만** 없다. 백필 금지이므로 채택 불가로 남아야 한다",
        "기대": {"채택": False, "사유_포함": "retrieved_at 없음"},
        "실측": v2_case(s, rules, ref, url=GOV, quote=OK_Q, retrieved_at=None)}
    out["poison_grade_up"] = {
        "무엇": "미등재 2건이 **같은 도메인** — 도메인 수는 화자 수가 아니다",
        "기대": {"채택": True, "등급": "추정"},
        "실측": v2_case(s, rules, ref, url=UNLISTED, quote=OK_Q, retrieved_at=TODAY, n_dup=2)}
    out["golden_downgrade"] = {
        "무엇": ("suffix 강등(`*.or.kr` 협회·공공 일반 페이지) — **발행 주체가 확인되므로 채택된다.** "
               "판 ㉙ ⓓ 실측이 반증한 자리: 처음엔 `aggregate` 를 통째로 막았다가 "
               "`foodtoday.or.kr`(조사 주체·조사명 명시)이 「추적 불가」로 죽는 것을 보고 정정했다"),
        "기대": {"채택": True, "등급": "추정"},
        "실측": v2_case(s, rules, ref, url="https://foodtoday.or.kr/news/article.html?no=1",
                       quote=OK_Q, retrieved_at=TODAY)}
    out["poison_republish"] = {
        "무엇": "원출처 불명 재게시(등재 거부 도메인) — **등급 문제가 아니라 채택 요건 미달**(결정 ②)",
        "기대": {"채택": False, "사유_포함": "추적 불가"},
        "실측": v2_case(s, rules, ref, url=REJECTED, quote=OK_Q, retrieved_at=TODAY)}
    out["poison_community"] = {
        "무엇": "비관측(커뮤니티 추측) — v2 대전제 「관측 존재」에서 이미 탈락한다",
        "기대": {"채택": False, "사유_포함": "비관측"},
        "실측": v2_case(s, rules, ref, url=COMMUNITY, quote=OK_Q, retrieved_at=TODAY)}
    out["golden_unlisted"] = {
        "무엇": "미등재 도메인 + 4요건 충족 — **이 뒤집힘이 개방의 회귀 증명**",
        "기대": {"채택": True, "등급": "추정"},
        "실측": v2_case(s, rules, ref, url=UNLISTED, quote=OK_Q, retrieved_at=TODAY)}
    out["golden_cross_up"] = {
        "무엇": "미등재 **두 도메인**이 같은 값 — 상향 1단계(추정 → 실무 신뢰)가 살아 있는가",
        "기대": {"채택": True, "등급": "실무 신뢰"},
        "실측": v2_case(s, rules, ref, quote=OK_Q, retrieved_at=TODAY,
                       url=[UNLISTED, "https://other-shop.example.org/item/9"])}
    out["golden_gov"] = {
        "무엇": "정답 — 정부 통계 + 4요건 충족",
        "기대": {"채택": True, "등급": "확정"},
        "실측": v2_case(s, rules, ref, url=GOV, quote=OK_Q, retrieved_at=TODAY)}
    return out


def judge(cases: dict) -> list[str]:
    """`기대` 와 `실측` 을 대조한다. **사람 눈에 기대지 않는다.**"""
    bad = []
    for name, c in cases.items():
        exp, got = c["기대"], c["실측"]
        for k, want in exp.items():
            if k == "사유_포함":
                if not any(want in s for s in (got.get("사유") or [])):
                    bad.append(f"{name}: 사유에 '{want}' 없음 (실측 {got.get('사유')})")
            elif got.get(k) != want:
                bad.append(f"{name}: {k} 기대 {want!r} · 실측 {got.get(k)!r}")
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--concept", required=True)
    a = ap.parse_args()

    rules = load_rules()
    res = json.load(io.open(os.path.join(ROOT, "runs", a.run, "result.json"), encoding="utf-8"))
    slots = {s["slot_id"]: Slot(**{k: v for k, v in s.items() if not k.startswith("_")})
             for s in res["input"]["slots"]}
    s1 = slots["S1"]
    ref = int(str(res.get("reference_date", "2026"))[:4])

    cases = {}
    # ── 정답 ────────────────────────────────────────────────
    f, d = mk(s1, GOLDEN[2024], 2024, scaled=True)
    cases["golden(017·백만원·정표기)"] = trace(s1, f, d, rules, ref)
    # ── 오답 ① 합계 축 (판 ⑮ 274조) ─────────────────────────
    f, d = mk(s1, 274944156.0, 2025, scaled=True, subject_nm="합계")
    cases["bad_axis(합계 274조)"] = trace(s1, f, d, rules, ref)
    # ── 오답 ② 배수 미적용 (판 ⑰ 100만 배 축소) ─────────────
    # **단위층부터 태운다** — 「백만원」은 판 ⑱ 에서 등재됐으므로 여기서 **정답이 된다.**
    # 즉 이 오답은 **더 이상 발생할 수 없다**는 것이 확인돼야 한다.
    f, d = mk(s1, GOLDEN[2024], 2024, scaled=False)
    cases["bad_unit(원시 「백만원」 — 단위층부터)"] = trace(
        s1, f, d, rules, ref, raw=(str(int(GOLDEN[2024])), "백만원"))
    # ── 오답 ③ **모르는 배수** — 단위층이 명시 실패해야 한다 ────
    f, d = mk(s1, GOLDEN[2024], 2024, scaled=False)
    cases["bad_unit2(모르는 배수 「경원」)"] = trace(
        s1, f, d, rules, ref, raw=(str(int(GOLDEN[2024])), "십경원"))

    # ── 진단 우회 — **뒤 층을 계속 세기 위해서다** ───────────────
    # 정답이 앞 층(`value_range`)에서 막히면 그 뒤 층을 못 본다. 그러면 「남은 문 전체
    # 목록」이라는 이 도구의 목적을 못 이룬다. 그래서 **슬롯 사본**의 range 를 넓혀
    # 뒤 층을 마저 추적한다.
    # ⚠ **이것은 수리가 아니다.** 원본 슬롯도 규칙도 건드리지 않는다 —
    #   「가드를 껐을 때 뒤에 무엇이 더 있나」를 보는 **진단**이고, 산출물에도 그렇게 적힌다.
    import copy as _copy
    wide = _copy.deepcopy(s1)
    wide.value_range = [1.0, 1e18]
    f, d = mk(wide, GOLDEN[2024], 2024, scaled=True)
    cases["golden(우회: value_range 무시 — 진단용)"] = trace(wide, f, d, rules, ref)
    f, d = mk(wide, GOLDEN[2023], 2023, scaled=True)
    cases["golden 2023(우회 · 성장률 2년차)"] = trace(wide, f, d, rules, ref)

    v2 = v2_cases(s1, rules, ref)
    bad = judge(v2)

    out = {"_규칙": ("정답이 통과하고 오답이 막혀야 그 층이 제 일을 하는 것이다. "
                   "정답이 막히면 **그 층이 남은 문**이고, 오답이 통과하면 **가드가 뚫린 것**이다."),
           "run": a.run, "slot": s1.slot_id, "v2_반례": v2, "v2_불일치": bad,
           "slot_조건": {"must_contain": s1.must_contain, "value_range": s1.value_range,
                       "unit": s1.unit, "period": s1.period,
                       "period_min": s1.period_min, "period_max": s1.period_max},
           "사례": cases}
    p = os.path.join(ROOT, "runs", a.run, "golden_probe.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))

    print(f"슬롯 {s1.slot_id} · must_contain={s1.must_contain} · range={s1.value_range}\n")
    for name, c in cases.items():
        mark = "통과" if c["막힌_층"] is None else f"막힘 → {c['막힌_층']}"
        print(f"  [{name}] {mark}")
        for st in c["단계"]:
            print(f"      {'OK ' if st['통과'] else 'X  '} {st['층']:<14}{str(st['사유'])[:95]}")
    print("\n기준 v2 오염 반례 — 채택/등급 두 축")
    for name, c in v2.items():
        g = c["실측"]
        print(f"  [{name}] 채택={g.get('채택')} 등급={g.get('등급')} "
              f"label={g.get('label')} kind={g.get('kind')}/{g.get('kind_by')}")
        if g.get("사유"):
            print(f"      사유 {g['사유']}")
    if bad:
        print("\n⚠ 기대 불일치 — **가드가 뚫렸거나 정답이 막혔다**:")
        for b in bad:
            print("  X  " + b)

    print(f"\n기록: {p}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
