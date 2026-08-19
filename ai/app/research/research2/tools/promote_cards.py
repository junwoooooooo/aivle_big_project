# -*- coding: utf-8 -*-
"""**절 사실 → 근거 카드.** LLM 0회 · 0원. (판 ㊸ 2단계)

    python tools/promote_cards.py runs-generated/p43-wire/publish.json \
           --concept data/concept_hmr-product.json

봉투의 `evidence[]` 는 **슬롯 기반 카드 15장**인데 판 ㊷ 체인이 싣는 사실은 **132건**이고
**둘은 겹치지 않는다**(판 ㊸ 0단계 실측). 승격하지 않으면

- 화면의 채널·원가·수익성·규제 세 과목이 **빈 채로 태어나고**
- 9절 문장이 인용한 수를 사업가가 검산하러 가면 `evidenceById` 에 **없다**

**한글 카드 키로 내보낸다.** 그래야 `serialize.evidence()` 의 번역표 한 곳을 그대로 지나간다 —
계약 키를 여기서 또 쓰면 「같은 물음을 두 곳이 각자 푼다」가 한 번 더 생긴다.

⚠ **등급을 새로 만들지 않는다.** `rules/fill.v2.json` 의 `등급표[kind]` 를 그대로 쓴다.
   상향은 하지 않는다 — 상향은 독립 화자 ≥2 를 요구하는데 이 체인은 화자를 못 센다.
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import publish_gate as PG                                           # noqa: E402
import synthesize as SY                                            # noqa: E402
from a_desk import kind_of                                         # noqa: E402


def _규칙() -> tuple:
    """⚠ `whitelist`·`fill` 은 **버전을 손으로 박지 않는다** — `load_rules()` 가 고른다.
    여기서 `whitelist.v8.json` 이라고 적으면 다음 판이 v9 를 올릴 때 이 자리만 옛 표를 본다."""
    from runlog import load_rules                                  # noqa: PLC0415
    R = load_rules()
    P = json.load(io.open(os.path.join(ROOT, "rules", "promote.v1.json"), encoding="utf-8"))
    return P, R["fill"], R["whitelist"]


def _등급(kind: str, 표: dict) -> str:
    for lv, kinds in 표.items():
        if not lv.startswith("_") and kind in kinds:
            return lv
    return 표.get("_기본") or "추정"


def _채택(it: dict, r: dict, kind: str, 불가: dict) -> str:
    """**채택 4요건**(`rules/fill.v2.json`)을 그대로 검사한다. 통과면 빈 문자열.

    ⚠ 등급표만 가져오고 요건을 안 보면 **「채택 불가」가 「확정」으로 화면에 앉는다.**
    등급표는 요건을 통과한 사실에만 붙는 이름표였고, 승격이 그 전제를 건너뛰고 있었다.

    `채택_불가_부류` 는 **등급을 낮게 주는 것이 아니라 받지 않는다**고 규칙이 적어 뒀다 —
    커뮤니티 추측은 「관측 존재」에서 이미 탈락하므로 낮은 등급으로도 실으면 안 된다.
    """
    if kind in ((불가 or {}).get("kinds") or {}):
        return f"채택 불가 부류({kind})"
    if not (r.get("url") or "").strip():
        return "url 없음"
    if not r.get("조회일"):
        # **백필 금지.** 오늘 날짜를 넣는 것은 지어내기다 — 규칙 파일이 그렇게 적어 뒀다.
        return "retrieved_at 없음"
    if not it.get("quote_verified"):
        return "인용 대조 실패"
    return ""


#: 수 자체에 배율이 붙는 말. `_수값` 이 이미 아는 것과 같은 집합이다.
_배율말 = ("조", "억", "만", "천")


def _값(number_raw: str, unit_raw: str, 환산: dict) -> tuple:
    """(값, 단위). **화폐만 환산한다** — 모르는 단위는 원문 표기를 그대로 단위로 쓴다."""
    n_raw = str(number_raw or "")
    u = str(unit_raw or "").strip()
    conv = 환산.get(u)

    # ⚠ **배율을 두 번 곱하지 않는다.** 발췌가 `8조 9,854` + `억원` 으로 갈라 오면
    #   앞쪽을 8.0e12 로 읽고 뒤쪽 배수 1e8 을 또 곱해 **8.0e20 원**(80,000경)이 됐다.
    #   실측(유료 스모크 2026-08-15): `sec-0002`·`sec-0003` 두 장이 그렇게 나갔고,
    #   그 절이 9절 합성의 입력이라 **첫 화면 결론에 섞였다.**
    #
    #   고치는 법은 「어느 배율이 맞나」를 고르는 것이 아니라 **가르지 않는 것**이다 —
    #   `8조 9,854억원` 은 한 덩어리로 읽으면 8조 + 9,854억 = 8.9854e12 로 정확하다.
    #   `_수값` 은 원래 그런 표기(`2조 7,421억`)를 읽으라고 만든 것이다.
    if conv and any(말 in n_raw for 말 in _배율말) and any(말 in u for 말 in _배율말):
        n = SY._수값(n_raw + u)
        return (None, conv["단위"]) if n is None or n < 0 else (n, conv["단위"])

    # ⚠ `_수값` 은 못 읽으면 `None` 이 아니라 **`-1`** 을 돌려준다. 그대로 흘리면
    #    「못 읽었다」가 **「마이너스 1원」**이 되어 화면에 값처럼 앉는다.
    n = SY._수값(n_raw)
    if n is None or n < 0:
        return None, (u or None)
    if conv:
        return n * conv["배수"], conv["단위"]
    return n, (u or None)


def _원문값(n, u) -> str:
    """원문 표기 그대로. **단위 중복만 접는다.**

    ⚠ 실측(판 ㊹ 6단계): 「5.3억원원」·「80%%」·「3조 5,340억 원원」이 표에 앉았다.
      추출이 `number_raw` 에 단위를 이미 넣은 경우가 있는데 무조건 이어 붙였다.
      **값을 고치는 게 아니라 겹친 꼬리만 안 붙인다.**
    """
    n, u = str(n or "").strip(), str(u or "").strip()
    if not u or n.endswith(u) or n.replace(" ", "").endswith(u.replace(" ", "")):
        return n
    return f"{n}{u}"


#: 절 하나가 봉투에 싣는 **서랍(`밖`) 카드 상한.** 절 머리는 이 상한을 안 받는다.
#:
#: ★ **판 ㊺ — 왜 상한이 필요한가.** 발췌가 `gpt-5.6-luna` 가 된 뒤 근거가 봉투 계약을
#: 넘겼다. 실측(원장 `0c54ffb5…`):
#:
#:     봉투 상한 (`ai/main.py:36`)          2.00 MiB
#:     재질문 4절                            5.52 MiB   evidence 5,640
#:     재질문을 «전부 꺼도»                   2.31 MiB   evidence 2,666   ← 그래도 넘는다
#:
#: 그래서 이것은 재질문을 줄여 풀 수 있는 문제가 아니다. 그리고 넘으면 조용히 잘리는 게
#: 아니라 **실행 전체가 `PAYLOAD_TOO_LARGE` 로 죽는다** — 이미 지불한 수집을 통째로 잃는다.
#: 같은 이유로 BM 도 그 앞에서 `context_length_exceeded` 로 죽었다.
#:
#: ⚠ **절 머리는 한 건도 접지 않는다.** 화면 본문이 그것이고, 사업가가 읽는 것도 그것이다.
#:   접히는 것은 「더 있다」를 보여주는 참고뿐이고, **몇 건 중 몇 건인지 반드시 알린다**
#:   (`pipeline._sections` 가 `DRAWER_SAMPLED` 로 화면까지 올린다).
#: ⚠ **원장에서 지우는 것이 아니다.** 사실은 그대로 있고 봉투에만 표본이 실린다 —
#:   「버리는 자리는 질문과 게재뿐」 규율에서 이것은 **게재**다.
서랍_상한 = 20


def _접기(카드: list, 표: dict, 상한: int, 생략: dict | None) -> list:
    """절마다 서랍 카드를 `상한` 건까지만 남긴다. **절 머리는 그대로 통과시킨다.**

    남길 것을 고르는 순서는 ① 값을 읽은 것 ② 등급이 높은 것 ③ 원래 차례다.
    값이 없는 서랍 카드는 화면에서 「참고」 이상이 못 되므로 먼저 접힌다.
    """
    순위 = {lv: i for i, lv in enumerate(k for k in 표 if not k.startswith("_"))}
    머리 = [c for c in 카드 if c["_갈래"] != "밖"]
    서랍: dict = {}
    for c in 카드:
        if c["_갈래"] == "밖":
            서랍.setdefault(c["_절"], []).append(c)

    남김 = []
    for 절, 목록 in 서랍.items():
        고른 = sorted(enumerate(목록),
                     key=lambda t: (t[1]["값"] is None,
                                    순위.get(t[1]["등급"], len(순위)), t[0]))[:상한]
        남김 += [c for _, c in 고른]
        if 생략 is not None and len(목록) > 상한:
            생략[절] = {"전체": len(목록), "실음": 상한}
    # 원래 차례를 되살린다 — id 는 `build` 가 이미 붙였고, 뒤섞이면 화면 순서가 흔들린다.
    자리 = {id(c): i for i, c in enumerate(카드)}
    return sorted(머리 + 남김, key=lambda c: 자리[id(c)])


def build(publish: dict, concept: dict | None = None, *,
          서랍상한: int = 0, 생략: dict | None = None) -> list:
    """실린 사실 → **한글 카드 목록**. 사실이 아닌 것(`OFF_TOPIC`)만 안 온다.

    ⚠ 서랍(`밖`)도 온다 — **버리지 않는 것이 이 모듈의 전부**다. 대신 카드마다
      「어떻게 읽어야 하는지」가 `경계` 로 붙고, 화면이 접는다.

    `서랍상한` 을 주면 절마다 서랍을 그만큼만 남긴다(0 이면 전량 — 종전 그대로).
    `생략` 에 dict 를 주면 **절별로 몇 건 중 몇 건을 실었는지** 채워 준다 —
    부르는 쪽은 그것을 반드시 사용자에게 알린다."""
    P, F, WL = _규칙()
    # ⚠ **한 번만 읽는다.** `PG._rules()` 는 캐시가 없어 항목마다 부르면 JSON 을
    #   수천 번 읽는다(실측 원장 기준 2,876회).
    PGR = PG._rules()
    표, 환산 = F.get("등급표") or {}, P["단위_환산"]
    불가 = F.get("채택_불가_부류") or {}
    갈래경계, 앞머리 = P["갈래_경계"], P["id_앞머리"]

    카드, 거부 = [], []
    for r in publish.get("문서별") or []:
        url = r.get("url") or ""
        kind, 어떻게 = kind_of(url, WL)
        등급 = _등급(kind, 표)
        for it in r.get("items") or []:
            # **서랍(`밖`)도 카드가 된다** (판 ㊹ 3단계) — 안 그러면 「버리지 않는다」가
            # 화면에서 거짓이 된다. 어떻게 읽을지는 아래 `경계` 가 붙인다.
            if not PG.실었나(it):
                continue
            사유 = _채택(it, r, kind, 불가)
            if 사유:
                # **떨어뜨리되 지우지 않는다**(절대규칙 5 — 실패는 값이다).
                거부.append({"주제": it.get("subject"), "사유": 사유, "url": url})
                continue
            갈래 = it["게재"]
            값, 단위 = _값(it.get("number_raw"), it.get("unit_raw"), 환산)

            # 경계 — **값과 한 몸이다.** 갈래가 말하는 「이 수를 어떻게 읽나」를 옮긴다.
            경계 = []
            문장 = 갈래경계.get(갈래)
            발 = str(it.get("게재_발행사") or "").strip()
            if 갈래 == "COMPETITOR_FIRM" and not 발:
                # ⚠ **발행사를 모르면 「한 회사」라고 단정하지 않는다.** 실측(2026-08-15):
                #    산업 전체 합계인 「식품제조업 매출액 188.8조」에 「시장 전체가 아니라
                #    한 회사의 수다」가 붙었다. **틀린 경계는 없는 것보다 나쁘다** —
                #    사업가가 그 수의 정체를 반대로 읽는다.
                #    이 실행에서 COMPETITOR_FIRM 승격 7건이 **전부** 발행사 미상이었다.
                문장 = 갈래경계.get("COMPETITOR_FIRM_발행사_미상") or 문장
            if 문장:
                # ⚠ `**한 회사**` 안쪽을 갈아끼운다. 「한 회사」만 바꾸면 굵게 표시가 겹쳐
                #    `****오뚜기** 한 회사**` 가 된다(실측).
                경계.append(문장.replace("**한 회사**", f"**{발} 한 회사**")
                          if 발 and 갈래 == "COMPETITOR_FIRM" else 문장)
            tc = str(it.get("table_context") or "").strip()
            if tc:
                경계.append(P["표_경계"].replace("{표}", tc))

            카드.append({
                "카드_id": f"{앞머리}{len(카드) + 1:04d}",
                "종류": "관측",
                "계량": str(it.get("subject") or "")[:60],
                "주제": str(it.get("subject") or ""),
                # **연도만 있고 기간이 없다.** 없는 것을 지어내지 않는다.
                "기간": str(it.get("year") or "") or None,
                "값": 값, "단위": 단위,
                "등급": 등급, "등급_근거": f"등급표:{kind}({어떻게}) · 인용 본문 대조 통과",
                "출처_url": url, "kind": kind,
                # ⚠ **오늘 날짜를 적지 않는다.** 원장의 `a3_document.retrieved_at` 을
                #    되찾아 온다 — 지어내는 것(백필)이 아니라 있는 것을 옮기는 것이다.
                #    엔진의 채택 4요건(`fill.v2.json`)이 `retrieved_at` 을 요구하고,
                #    없으면 「채택 불가」다. 없이 「확정」을 붙이면 등급이 거짓이 된다.
                "조회일": r.get("조회일"),
                "인용": it.get("quote") or None,
                "경계": 경계,
                # 승격에서만 붙는 칸 — `serialize._EVIDENCE` 밖이라 봉투로 새지 않는다.
                # ⚠ `it["section"]` 을 그대로 쓰지 않는다 — 게재 판정 뒤의 재배정이 빠져
                #    **보고서와 화면이 같은 사실을 다른 절에 넣는다.** 정본은 `PG.절()`.
                "_절": PG.절(it), "_갈래": 갈래,
                "_발행사": (it.get("게재_발행사") or None),
                "_표키": (f"{r.get('trace_id')}|{tc}|{it.get('year') or ''}" if tc else None),
                # ⚠ **단위가 이미 수 안에 있으면 또 붙이지 않는다.** 실측: 「5.3억원원」·
                #   「80%%」·「3조 5,340억 원원」이 표에 앉았다 — 값이 깨져 보인다.
                "_원문값": _원문값(it.get("number_raw"), it.get("unit_raw")),
                # 줄 세우기용. `serialize._EVIDENCE` 밖이라 봉투로는 안 나가고,
                # **순서로만** 화면에 전달된다 — 화면이 중요도를 다시 풀지 않게 하려는 것이다.
                "_표지": PG.표지적중(it, PGR),
            })

    # ★ 판 ㊺ — **절 안에서 「그 절이 묻는 것에 답하는 것」이 먼저 선다.**
    #   왜 갈래·등급만으로는 모자란지는 `PG.표지적중` 에 실측과 함께 적었다.
    #   ⚠ **아무것도 버리지 않는다.** 순서만 바꾼다.
    #   ⚠ 안정 정렬이라 같은 등급 안에서는 **원장에 실린 차례**가 그대로 남는다.
    갈래순 = {"OURS_SEGMENT": 0, "OURS_UMBRELLA": 1, "SUBSTITUTE": 2,
             "COMPETITOR_FIRM": 3, "밖": 4}
    등급순 = {lv: i for i, lv in enumerate(k for k in 표 if not k.startswith("_"))}
    카드.sort(key=lambda c: (갈래순.get(c["_갈래"], 5),
                           0 if c["_표지"] else 1,
                           등급순.get(c["등급"], 9)))
    if 거부:
        from collections import Counter                             # noqa: PLC0415
        print("승격 거부 —", " · ".join(f"{k} {v}" for k, v in
                                    Counter(x["사유"] for x in 거부).most_common()))
    if 서랍상한 > 0:
        전 = len(카드)
        카드 = _접기(카드, 표, 서랍상한, 생략)
        if len(카드) != 전:
            print(f"서랍 접기 — {전} → {len(카드)}장 (절마다 최대 {서랍상한}건 · 절 머리는 전량)")
    return 카드


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish")
    ap.add_argument("--concept", default="")
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    d = json.load(io.open(a.publish, encoding="utf-8"))
    카드 = build(d)

    from collections import Counter
    print(f"승격 {len(카드)}장 (LLM 0회)\n")
    print("등급 —", " · ".join(f"{k} {v}" for k, v in Counter(c["등급"] for c in 카드).most_common()))
    print("출처 —", " · ".join(f"{k} {v}" for k, v in Counter(c["kind"] for c in 카드).most_common()))
    print("절   —", " · ".join(f"{k} {v}" for k, v in Counter(c["_절"] for c in 카드).most_common()))
    없 = sum(1 for c in 카드 if c["값"] is None)
    print(f"\n값을 못 읽은 것 {없}장 (값 null — **지어내지 않는다**)")
    print(f"경계가 붙은 것 {sum(1 for c in 카드 if c['경계'])}장")

    out = a.out or os.path.join(os.path.dirname(a.publish), "promoted.json")
    io.open(out, "w", encoding="utf-8").write(
        json.dumps({"카드": 카드}, ensure_ascii=False, indent=1))
    print(f"\n기록: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
