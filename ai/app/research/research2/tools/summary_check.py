# -*- coding: utf-8 -*-
"""문장↔카드 대조 — **요약이 카드 밖으로 못 나가게 한다** (판 ㉛). LLM 0회 · 원장 쓰기 0회.

    python tools/summary_check.py --run <id> --concept <c>     # 실제 요약 검사
    python tools/summary_check.py --golden                     # 오염 반례(LLM 0회)

세 가지를 본다 (`rules/summary.v1.json 검사`):

  **① 숫자 출처** — 문장의 모든 수가 그 문장이 가리킨 카드의 값에서 와야 한다.
      카드 밖 숫자는 되짚을 곳이 없다 = 지어내기(금지선의 문장 층 적용).
  **② 해석 어휘** — 「유망하다」·「경쟁력 있다」류. 층은 «A가 낫다»를 쓰지 않는다.
  **③ 카드 id 실재** — 가리킨 id 가 실제 카드여야 한다(환각 id 차단).

⚠ **통과 사례만으로는 검사의 존재 증명이 안 된다**(관리자 확인 조건 ②).
   그래서 `--golden` 이 **막혀야 하는 문장**을 넣어 막히는 것을 본다. 종료 코드로 답한다.
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

#: 문장 속 수. 천단위 쉼표·소수점을 포함한다.
NUM = re.compile(r"\d[\d,]*(?:\.\d+)?")


def rules() -> dict:
    return json.load(io.open(os.path.join(ROOT, "rules", "summary.v1.json"), encoding="utf-8"))


def _nums_of_card(c: dict) -> set:
    """그 카드가 정당화하는 수의 집합. **표기 변형을 넉넉히 편다.**

    좁게 잡으면 「2조 9,574억」 같은 정직한 표기가 막힌다. 넓게 잡으면 검사가 헐거워진다.
    여기서는 **값에서 파생 가능한 표기**만 연다 — 카드에 없는 수는 어떤 변형으로도 안 나온다.
    """
    out: set[str] = set()
    for key in ("값", "값_퍼센트", "연도"):
        v = c.get(key)
        if v is None:
            continue
        out |= _variants(v)
    for v in (c.get("입력") or {}).values():
        if isinstance(v, (int, float)):
            out |= _variants(v)
    # 카드의 **기간 문자열** 안 연도도 카드가 정당화한다(규칙 `허용_예외`: 「연도 — 단
    # 카드의 연도와 일치할 때만」). 판 ㉛ 실측: 이걸 안 펴면 「2024년 …」이 위반으로 잡혀
    # **정직한 문장이 막힌다**. ⚠ 카드에 없는 연도는 여전히 막힌다.
    for key in ("기간", "연도"):
        v = c.get(key)
        if v is None:
            continue
        for y in re.findall(r"(?<!\d)(?:19|20)\d{2}(?!\d)", str(v)):
            out.add(y)
    # **경계 문장 안의 수도 카드 내용이다.** 판 ㉛ 실측: 검사 ④ 가 「외식업 214곳 표본이다」를
    # 그대로 베끼라고 요구하는데 검사 ① 이 그 안의 「214」를 카드 밖 숫자로 잡았다 —
    # **두 검사가 서로 모순**이었다. 경계는 카드가 들고 있는 문장이므로 그 수도 카드가 낸 것이다.
    for key in ("경계", "경계_proxy", "상한_울타리", "가정"):
        v = c.get(key)
        if not v:
            continue
        for t in (v if isinstance(v, list) else [v]):
            for m in NUM.finditer(str(t)):
                out.add(m.group(0))
                out.add(m.group(0).replace(",", ""))
    return out


def _variants(v) -> set:
    out = set()
    try:
        f = float(v)
    except (TypeError, ValueError):
        return out
    for x in {f, round(f, 4), round(f, 2), round(f, 1), round(f)}:
        # ⚠ `.10g` 는 **큰 수를 지수표기로 만든다** — `57219879301.2` → `5.72198793e+10`.
        #   그래서 카드 값 그 자체가 「카드에 없는 수」로 잡혔다(판 ㉛ 실측 · ledger-05).
        #   고정 표기로 적고 꼬리 0 만 떼어 낸다.
        s = f"{x:.4f}".rstrip("0").rstrip(".")
        out.add(s.replace("-", ""))
        try:
            iv = int(round(float(x)))
            out.add(f"{iv:,}")
            # 쉼표 + 소수 혼합 표기(「57,219,879,301.2」)도 사람이 쓰는 형태다
            frac = abs(float(x)) - abs(iv)
            if frac:
                out.add(f"{int(abs(float(x))):,}" + f"{abs(float(x)) % 1:.4f}"
                        .rstrip("0").rstrip(".").lstrip("0"))
        except (ValueError, OverflowError):
            pass
    # 억·조 단위 축약 — 사람이 읽는 표기는 대개 이쪽이다
    for div, _n in ((1e12, "조"), (1e8, "억"), (1e4, "만")):
        if abs(f) >= div:
            q = f / div
            for x in {round(q, 2), round(q, 1), round(q)}:
                s = f"{x:.10g}"
                if s.endswith(".0"):
                    s = s[:-2]
                out.add(s)
                try:
                    out.add(f"{int(round(float(x))):,}")
                except (ValueError, OverflowError):
                    pass
    # **복합 표기** — 「2조 9,574억 원」처럼 몫과 나머지를 이어 쓰는 한국어 형태.
    #   판 ㉛ 실측: 이걸 안 펴면 **정직한 문장이 막힌다**(골든 `ok_quote_number`).
    #   ⚠ 느슨해지는 것이 아니다 — 여기서 나오는 수는 **카드 값에서 정확히 유도된 것뿐**이다.
    #     「연 12.4% 성장」 같은 카드 밖 수는 어떤 분해로도 나오지 않는다.
    n = abs(f)
    for big, small in ((1e12, 1e8), (1e8, 1e4)):
        if n >= big:
            head = int(n // big)
            rest = (n - head * big) / small
            for x in {round(rest, 2), round(rest, 1), round(rest)}:
                t = f"{x:.10g}"
                if t.endswith(".0"):
                    t = t[:-2]
                out.add(t)
                try:
                    out.add(f"{int(round(float(x))):,}")
                except (ValueError, OverflowError):
                    pass
            out.add(str(head))
            out.add(f"{head:,}")
    return out


def _isnum(x: str) -> bool:
    try:
        float(str(x).replace(",", ""))
        return True
    except ValueError:
        return False


def check(cards: list, sentences: list, r: dict | None = None) -> list:
    """위반 목록. 비어 있으면 통과."""
    r = r or rules()
    chk = r["검사"]
    banned = chk["해석_어휘"]["금지"]
    by_id = {c["카드_id"]: c for c in cards}
    n_cards = {str(len(cards))}
    bad = []

    for i, s in enumerate(sentences):
        text = s.get("문장") or ""
        ids = list(s.get("카드_id") or [])

        # ③ 카드 id 실재
        for cid in ids:
            if cid not in by_id:
                bad.append({"검사": "카드_id 실재", "문장": i, "상세": f"없는 카드 {cid}"})

        # ② 해석 어휘
        for w in banned:
            if w in text:
                bad.append({"검사": "해석 어휘", "문장": i,
                            "상세": f"금지 낱말 '{w}' — 층은 «A가 낫다»를 쓰지 않는다"})

        # ① 숫자 출처
        allowed: set = set(n_cards)
        for cid in ids:
            if cid in by_id:
                allowed |= _nums_of_card(by_id[cid])
        for m in NUM.finditer(text):
            tok = m.group(0)
            if tok.rstrip("0").rstrip(".") in ("", "0"):
                continue
            # 꼬리 `.0` 은 **표기 차이지 다른 수가 아니다** — 카드 값 19800.0 을 모델이
            # 「19800.0」으로 쓰면 막히던 자리(판 ㉛ 실측). 정규화는 양쪽에 똑같이 건다.
            norm = tok.replace(",", "")
            if norm.endswith(".0"):
                norm = norm[:-2]
            if tok in allowed or norm in allowed:
                continue
            # **값으로 견준다** — 표기 변형을 하나하나 세는 대신 수 자체를 비교한다.
            #   `1025336520.0000002`(부동소수 잡음)처럼 어떤 포맷 규칙으로도 못 맞추는
            #   형태가 실제로 나왔다(판 ㉛). ⚠ 느슨해지지 않는다 — **값이 같아야** 통과다.
            try:
                fv = float(norm)
                if any(abs(fv - float(x.replace(",", ""))) < 1e-6
                       for x in allowed if _isnum(x)):
                    continue
            except ValueError:
                pass
            # 「2,957,408」처럼 쉼표 표기가 카드 값과 같은 경우
            if f"{norm}" in {a.replace(',', '') for a in allowed}:
                continue
            bad.append({"검사": "숫자 출처", "문장": i,
                        "상세": f"카드에 없는 수 '{tok}' — 되짚을 곳이 없다",
                        "가리킨_카드": ids})

        # ⑤ 카드 대응 — **근거 없는 주장은 표현이 무엇이든 막는다**
        #   낱말 목록(②)은 1차 그물이고, 진짜 방어는 여기다. 해석문의 정체는 낱말이 아니라
        #   **어떤 카드도 그렇게 말하지 않는다**는 것이다 — 그래서 대응 실패로 잡으면
        #   「나쁘지 않다」처럼 목록을 빠져나가는 표현도 같이 잡힌다(관리자 판정 ⓐ).
        cm = chk.get("카드_대응") or {}
        if cm and ids:
            stripped = text
            for cid in ids:
                c = by_id.get(cid) or {}
                for k in ("계량", "주제", "단위", "등급", "칸", "식"):
                    if c.get(k):
                        stripped = stripped.replace(str(c[k]), " ")
                for k in ("경계", "경계_proxy", "상한_울타리", "가정"):
                    v = c.get(k)
                    for t in (v if isinstance(v, list) else [v]) if v else []:
                        stripped = stripped.replace(str(t), " ")
            stripped = NUM.sub(" ", stripped)
            allowed_pred = cm.get("허용_서술") or []
            # 서술어가 하나도 안 남으면(순수 수치 나열) 통과. 남았는데 허용 밖이면 주장이다.
            hits = [w for w in (cm.get("_주장_표지") or []) if w in stripped]
            if not any(p in stripped for p in allowed_pred) and hits:
                bad.append({"검사": "카드 대응", "문장": i,
                            "상세": f"카드가 말하지 않은 주장 — 표지 {hits}"})

    # ── ④ 경계 동행 — **요약 전체**에서 본다 ─────────────────────────
    #   §4 「경계 표시를 절대 제거하지 않는다」의 문장 층 적용.
    #   판 ㉘: **경계는 쓴 곳이 아니라 도달한 곳에서만 존재한다.** 원장·canvas 까지
    #   도달시켜 놓고 요약이 떨어뜨리면 **사람이 읽는 마지막 자리**에서 사라진다.
    bd = chk.get("경계_동행") or {}
    if bd:
        whole = " ".join(x.get("문장") or "" for x in sentences)
        cited = {cid for x in sentences for cid in (x.get("카드_id") or [])}
        for cid in sorted(cited):
            c = by_id.get(cid) or {}
            for k in (bd.get("필드") or []):
                v = c.get(k)
                if not v:
                    continue
                txts = v if isinstance(v, list) else [v]
                for t in txts:
                    probe = str(t)[:12]
                    if probe and probe not in whole:
                        bad.append({"검사": "경계 동행", "문장": -1,
                                    "상세": f"{cid} 의 `{k}` 가 요약에 없다 — "
                                            f"「{str(t)[:40]}…」"})
    return bad


# ══════════════════════════════════════════════════════════════════════
# 오염 반례 — **막혀야 하는 것이 막히는지** 본다
# ══════════════════════════════════════════════════════════════════════
GOLDEN_CARDS = [
    {"카드_id": "C-F001", "종류": "관측", "값": 2957408000000.0, "단위": "원",
     "등급": "확정", "연도": 2024},
    {"카드_id": "C-CALC-TAM", "종류": "계산", "값": 887222400000.0, "단위": "원",
     "등급": "추정", "입력": {"시장 거래액": 2957408000000.0, "추정점유율": 0.3}},
]

GOLDEN = [
    {"id": "ok_quote_number", "기대": "통과",
     "문장": {"문장": "애완용품 거래액은 2024년 2조 9,574억 원으로 관측됐다.",
            "카드_id": ["C-F001"]}},
    {"id": "poison_number_not_in_card", "기대": "막힘 · 숫자 출처",
     "_무엇": "**카드에 없는 숫자**가 요약에 등장 — 관리자 확인 조건 ② 의 반례 1",
     "문장": {"문장": "애완용품 거래액은 2024년 2조 9,574억 원이고 연 12.4% 성장하고 있다.",
            "카드_id": ["C-F001"]}},
    {"id": "poison_interpretation", "기대": "막힘 · 해석 어휘",
     "_무엇": "**해석 문장**(유망하다·경쟁력 있다류) — 관리자 확인 조건 ② 의 반례 2",
     "문장": {"문장": "시장 규모로 볼 때 이 분야는 유망하며 진입 시 경쟁력이 있다.",
            "카드_id": ["C-F001"]}},
    {"id": "poison_ghost_card", "기대": "막힘 · 카드_id 실재",
     "문장": {"문장": "거래액은 2조 9,574억 원이다.", "카드_id": ["C-F999"]}},
    {"id": "poison_grade_leak_number", "기대": "막힘 · 숫자 출처",
     "_무엇": "추정 카드의 값을 **다른 숫자로 바꿔** 단정하는 문장",
     "문장": {"문장": "따라서 TAM 은 정확히 9,000억 원이다.", "카드_id": ["C-CALC-TAM"]}},
    {"id": "poison_boundary_dropped", "기대": "막힘 · 경계 동행",
     "_무엇": "카드가 든 **경계를 요약이 떨어뜨린다** — §4 의 문장 층 위반",
     "카드": [{"카드_id": "C-B1", "종류": "관측", "값": 12035007218975.0, "단위": "원",
              "등급": "확정", "경계": "전사 매출 — 시장 매출 아님. 용도는 DART 경로 검증이다."}],
     "문장": {"문장": "네이버 매출은 12,035,007,218,975원이다.", "카드_id": ["C-B1"]}},
    {"id": "ok_boundary_carried", "기대": "통과",
     "카드": [{"카드_id": "C-B1", "종류": "관측", "값": 12035007218975.0, "단위": "원",
              "등급": "확정", "경계": "전사 매출 — 시장 매출 아님. 용도는 DART 경로 검증이다."}],
     "문장": {"문장": "네이버 매출은 12,035,007,218,975원이다(전사 매출 — 시장 매출 아님).",
            "카드_id": ["C-B1"]}},
    {"id": "poison_claim_no_wordlist", "기대": "막힘 · 카드 대응",
     "_무엇": ("**낱말 목록을 빠져나가는 해석문.** 「유망」·「경쟁력」이 없어서 검사 ② 는 통과하지만, "
             "어떤 카드도 그렇게 말하지 않으므로 검사 ⑤ 가 잡는다 — 관리자 판정 ⓐ 의 요지"),
     "문장": {"문장": "거래액 규모로 볼 때 이 시장은 나쁘지 않으며 진입 가능성이 있다고 보인다.",
            "카드_id": ["C-F001"]}},
    {"id": "ok_calc_with_grade", "기대": "통과",
     "문장": {"문장": "TAM 은 8,872억 원으로 산출됐다(등급 추정 — 추정점유율 0.3 은 가정이다).",
            "카드_id": ["C-CALC-TAM"]}},
]


def run_golden() -> int:
    r = rules()
    fails = []
    print("오염 반례 — **막혀야 하는 것이 막히는가**")
    for g in GOLDEN:
        bad = check(g.get("카드") or GOLDEN_CARDS, [g["문장"]], r)
        got = "통과" if not bad else "막힘 · " + bad[0]["검사"]
        ok = got == g["기대"]
        print(f"  [{'OK ' if ok else 'X  '}] {g['id']:<28}기대 {g['기대']:<18}실측 {got}")
        if bad:
            print(f"        └ {bad[0]['상세']}")
        if not ok:
            fails.append(g["id"])
    if fails:
        print(f"\n실패 {fails} — **검사가 제 일을 못 한다**")
        return 1
    print("\n통과 — 반례 전부 기대대로")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--golden", action="store_true")
    ap.add_argument("--summary", default="", help="요약 JSON 경로")
    a = ap.parse_args()
    if a.golden or not a.summary:
        return run_golden()
    d = json.load(io.open(a.summary, encoding="utf-8"))
    bad = check(d["카드"], d["요약"])
    print(f"문장 {len(d['요약'])}개 · 카드 {len(d['카드'])}장 → 위반 {len(bad)}건")
    for b in bad:
        print("  X ", b)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
