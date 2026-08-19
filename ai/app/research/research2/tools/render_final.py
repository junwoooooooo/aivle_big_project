# -*- coding: utf-8 -*-
"""**사람이 읽는 시장조사 보고서를 낸다.** LLM 0회 · 0원. (판 ㊹ 5단계)

    python tools/render_final.py runs-generated/p41-merged/publish.json \
           --concept data/concept_hmr-product.json \
           --out ../../../../docs/market-research-redesign/OUR_REPORT.md

## 왜 LLM 을 안 부르나

A단계 실측(같은 재료·모델만 바꿈)에서 집필층이 낸 결함이 넷이었다 —
① 오뚜기 전사 실적을 「당사의 매출액」으로 결론까지 냄 ② 「배달 음식보다 저렴」이라는
**방향이 반대인 확신** ③ 재료에 있는 결정적 값 셋을 통째로 유기 ④ 지어낸 수.
`gpt-4o` 로 올려도 ①②③이 남았다.

그래서 **이 층은 셈과 조건문뿐**이다. 문장은 `judge_lines` 가 기계로 만들고, 표는 원장 값을
그대로 옮긴다. **모르는 것은 안 쓴다.**

## 구조

절마다 ⓐ 판단 한 문장(+계산식) ⓑ 절 머리 표 ⓒ 접힌 서랍 라벨.
`headline.py` 가 ⓑⓒ를, `judge_lines.py` 가 ⓐ를 정한다 — 이 파일은 **엮기만** 한다.

⚠ **경계 문장은 접지 않는다.** 판단 아래에 그대로 편다(`AssumptionLedger.test.jsx` 규율).
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for _p in (ROOT, HERE, os.path.join(ROOT, "adapters")):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import headline as H            # noqa: E402
import judge_lines as J         # noqa: E402
import prescribe as PRESCRIBE   # noqa: E402
import promote_cards as PROMOTE  # noqa: E402
import publish_gate as PG       # noqa: E402

제목 = [
    ("MARKET_SIZE", "1 · 시장 크기 — 얼마나 큰가"),
    ("PRICE", "2 · 가격 — 내 값은 어디에 서는가"),
    ("COMPETITOR", "3 · 경쟁 지형 — 그 자리에 누가 있나"),
    ("CHANNEL", "4 · 채널 — 어디서 팔리나"),
    ("DEMAND", "5 · 수요 — 우리 고객이 실재하는가"),
    ("UNIT_ECONOMICS", "6 · 원가와 수익성 — 이 사업이 남기는가"),
    ("REGULATION", "7 · 규제 — 팔기 전에 확인할 것"),
]


_잘림꼬리 = (",", "(", "[", "~", "-", "및", "또는", "의", "과", "와")


def _인용에서_값(q: str) -> str | None:
    """인용에서 **기준치 한 조각**을 끌어온다. 못 하면 `None` — **잘린 값을 내지 않는다.**

    ## ⚠ 이 함수가 없으면 규제 기준이 1,000배 틀린 채로 표에 박힌다

    실측(판 ㊹ 6단계 재판정): 「바실러스 세레우스 **1g당 1,000 이하**」가 표에
    **「1g당 1」** 로 앉았다. 앞선 구현이 `[^,.;]` 로 잘라서 **`1,000` 의 콤마에서 끊긴** 것이다.
    아래 인용 블록에 온전한 문장이 있어 검산하면 복구되지만, **표만 읽는 사업가에게 이것은
    틀린 규제 기준**이다. 이 제품이 파는 것이 「검증된 값」이라는 보증이라,
    **잘린 값 하나가 열 전체의 보증을 깬다.**

    그래서 두 겹이다 —
      ① **자릿점이 든 수를 한 덩어리로** 본다(`1,000` 이 쪼개지지 않는다)
      ② 끝이 콤마·여는 괄호·조사로 끝나면 **미완성으로 보고 통째로 버린다**
    """
    import re                                                      # noqa: PLC0415
    # 숫자(자릿점 포함) 앞뒤로 40자. 문장 끝(`.` `;`)에서만 끊고 **콤마로는 안 끊는다**
    m = re.search(r"[^.;]{0,40}\d[\d,]*(?:\.\d+)?[^.;]{0,40}", q)
    if not m:
        return None
    got = " ".join(m.group(0).split()).strip(" ,")
    if not got or got.endswith(_잘림꼬리) or not any(ch.isdigit() for ch in got):
        return None                      # **미완성이면 안 낸다**
    # 여는 괄호만 있고 닫는 괄호가 없으면 그 앞에서 끊는다
    if got.count("(") > got.count(")"):
        got = got[:got.rfind("(")].strip(" ,")
    return got or None


def _표(카드들: list) -> list[str]:
    """절 머리 표. **정체(누구의 숫자인가)를 반드시 한 열로 낸다.**

    ⚠ 이 열이 없어서 A단계 산출이 **오뚜기 전사 실적을 「당사의 매출액」**으로 썼다.
      값 옆에 주인이 없으면 사업가가 남의 수를 자기 수로 읽는다.
    """
    if not 카드들:
        return ["이 절은 **절 머리에 세울 것을 한 건도 못 구했습니다.**", ""]
    줄 = ["| 무엇의 수 | 값 | 연도 | 누구의 숫자인가 | 출처 |",
          "|---|---|---|---|---|"]
    for c in 카드들:
        출처 = c.get("출처_url") or ""
        값 = str(c.get("_원문값") or "").strip()
        # ⚠ **값 자리에 이름표가 앉는 절이 있다** — 규제가 그렇다. 실측: 「값」 열에
        #   「황색포도상구균」이 찍히고 진짜 기준치(「1g당 100 이하」)는 인용에만 있었다.
        #   그럴 때는 **인용에서 기준치를 끌어온다** — 지어내지 않고 옮긴다.
        if not any(ch.isdigit() for ch in 값):
            값 = _인용에서_값(str(c.get("인용") or "")) or 값
        줄.append(f"| {c.get('주제')} | {값 or '—'} | "
                  f"{c.get('기간') or '연도 없음'} | {H.정체(c)} | "
                  f"{('[원문](' + 출처 + ')') if 출처 else '—'} |")
    return 줄 + [""]


def _인용(카드들: list) -> list[str]:
    out = []
    for c in 카드들:
        q = (c.get("인용") or "").strip()
        if q:
            out.append(f"> {q[:220]}")
    return (["", *out, ""] if out else [])


def build(publish: dict, concept: dict) -> str:
    카드 = PROMOTE.build(publish)
    머리 = H.build(publish, 카드)
    정가 = float(((concept.get("_hypotheses_v2") or {}).get("6_수익_가격") or {})
                .get("제안값_krw_월") or 0)
    판단 = J.절_판단(머리, 정가)
    # ⚠ `judge_lines.build` 는 **한 겹 싸서** 돌려준다(`{"가격": {...}}`) — 벗기지 않으면
    #   `갈래` 가 늘 비어 2절 판단이 통째로 사라진다(실측: 1차 렌더에서 2절이 표만 남았다).
    가격 = (J.build(publish, concept) or {}).get("가격") or {}

    실린 = sum(len(v["머리"]) for v in 머리.values())
    서랍 = sum(len(v["서랍"]) for v in 머리.values())

    L = [f"# 시장조사 보고서 — {concept.get('name') or ''}",
         "",
         "> 이 보고서는 **수집한 문서를 절 단위로 다시 읽어** 만들었습니다. "
         "값마다 출처와 연도, 그리고 **누구의 숫자인지**를 붙였습니다.",
         f"> 절 머리에 **{실린}건**, 접힌 서랍에 **{서랍}건**. "
         "**버린 것은 「값이 아닌 것」뿐입니다.**",
         "> 판단 문장은 **기계가 계산**했고, 계산식을 함께 적었습니다 — 손으로 검산할 수 있습니다.",
         ""]

    for code, 제 in 제목:
        v = 머리.get(code) or {"머리": [], "서랍": [], "서랍_라벨": "", "묻는_것": ""}
        L += [f"## {제}", ""]
        if v.get("묻는_것"):
            L += [f"*{v['묻는_것']}*", ""]

        # ⓐ 판단 — 절 머리 표보다 **위**에 둔다. 사업가가 표를 읽으러 오는 게 아니다.
        if code == "PRICE" and 가격.get("갈래"):
            for g in 가격["갈래"]:
                if g.get("문장"):
                    L += [f"**{g['무엇']}** — {g['문장']}", "",
                          f"`{g.get('계산') or ''}`", ""]
                else:
                    L += [f"**{g['무엇']}** — 못 씁니다. {g.get('왜_못_쓰나')}", ""]
            if 가격.get("결론"):
                L += [f"⇒ {가격['결론']}", ""]
        j = 판단.get(code)
        if j and j.get("문장"):
            L += [f"**{j['문장']}**", ""]
            if j.get("계산"):
                L += [f"`{j['계산']}`", ""]
            for b in j.get("경계") or []:
                # ⚠ 경계 문자열이 이미 ⚠ 로 시작하면 **또 붙이지 않는다**(「⚠ ⚠」가 나왔다)
                L += [b if str(b).lstrip().startswith("⚠") else f"⚠ {b}", ""]
        elif j:
            L += [f"이 절은 판단을 세우지 못했습니다 — {j.get('왜_못_쓰나')}", ""]

        L += _표(v["머리"])
        L += _인용(v["머리"])
        if v["서랍"]:
            L += [f"<details><summary>이 절에서 더 나온 것 {len(v['서랍'])}건 — "
                  f"{v['서랍_라벨']}</summary>", ""]
            L += _표(v["서랍"][:40])
            if len(v["서랍"]) > 40:
                L += [f"*… 그리고 {len(v['서랍']) - 40}건 더*", ""]
            L += ["</details>", ""]

    # ── 8절 — 못 구한 것 ──────────────────────────────────
    L += ["## 8 · 못 구한 것 — 다음에 채울 자리", ""]
    try:
        처방 = PRESCRIBE.build(publish, concept, 가격) or []
    except Exception as e:                      # noqa: BLE001 — 보고서를 죽이지 않는다
        처방, L = [], L + [f"*(처방 층이 돌지 않았습니다 — {type(e).__name__})*", ""]
    if 처방:
        for p in 처방:
            # `prescribe` 의 칸 이름은 `절말 · 왜 · 어디서` 다. 다른 이름을 넘겨짚으면
            # **빈 제목**이 찍힌다(실측: 1차 렌더의 8절이 「- **** — …」였다).
            L.append(f"- **{p.get('절말') or p.get('절') or ''}** — "
                     f"{p.get('왜') or ''} {p.get('어디서') or ''}".rstrip())
        L.append("")
    else:
        L += ["이번 실행은 「못 구한 것」 목록을 만들지 못했습니다.", ""]

    # ── 9절 — 이 조사가 말하는 것 ─────────────────────────
    #
    # ⚠ **절 요약 나열이 아니라 «대차대조»다.** 실측(판 ㊹ 6단계 판정): 목표 보고서가
    #   마지막까지 이긴 자리가 여기였다 — 목표는 「지지 5 / 흔듦 5」로 갈라 놓는데
    #   우리는 절 판단을 그냥 이어 붙였다. **사업가가 사는 것은 그 갈래다.**
    #   방향은 각 판단이 **이미 계산한 부호**에서 오고, 못 정한 것은 「못 정함」에 남는다 —
    #   억지로 한쪽에 밀어 넣지 않는다.
    L += ["## 9 · 이 조사가 말하는 것", ""]
    지지, 흔듦, 할일, 못정 = [], [], [], []
    if 가격.get("결론"):
        결 = 가격["결론"]
        흔 = ("설 자리가 없다" in 결 or "설 자리가 좁" in 결 or "어긋난다" in 결)
        (흔듦 if 흔 else 지지).append(
            ("가격", 결,
             "값만으로는 설 자리가 좁다 — 값이 아닌 이유가 서야 한다" if 흔
             else "값으로 설 자리가 있다"))
    for code, 제 in 제목:
        j = 판단.get(code)
        if not (j and j.get("문장")):
            continue
        이름 = 제.split(" · ")[1].split(" — ")[0]
        방 = j.get("방향")
        (지지 if 방 == "지지" else 흔듦 if 방 == "흔듦"
         else 할일 if 방 == "해야 할 일" else 못정).append(
            (이름, j["문장"], j.get("왜그쪽") or ""))

    for 제목말, 무리 in (("이 사업안을 **미는** 것", 지지),
                     ("이 사업안을 **흔드는** 것", 흔듦),
                     ("**해야 할 일** — 미는 것도 흔드는 것도 아니다", 할일),
                     ("어느 쪽인지 이 조사가 **못 정한** 것", 못정)):
        if not 무리:
            continue
        L += [f"### {제목말} — {len(무리)}건", ""]
        for 이름, 문장, 왜 in 무리:
            L.append(f"- **{이름}** — {문장}" + (f"\n  ↳ {왜}" if 왜 else ""))
        L.append("")

    if not (지지 or 흔듦 or 할일 or 못정):
        L += ["**이번 실행은 판단을 하나도 세우지 못했습니다.** "
              "비교쌍이 서지 않은 절이 많다는 뜻이고, 그 자체가 결과입니다.", ""]
    else:
        L += [f"**미는 것 {len(지지)} · 흔드는 것 {len(흔듦)}"
              + (f" · 해야 할 일 {len(할일)}" if 할일 else "")
              + (f" · 못 정한 것 {len(못정)}" if 못정 else "") + ".** "
              "이 저울은 **수를 센 것이지 성패를 말한 것이 아닙니다** — "
              "빠진 절이 있고, 값마다 「누구의 숫자인가」가 다릅니다.", ""]
    L += ["⚠ 이 조사는 **가격을 낼 것인가**를 답하지 못합니다 — 설계대로 시장 인터뷰의 몫입니다.",
          "⚠ 재무 자문이 아니며, 외부 시장 데이터를 전부 반영하지 못했습니다.", ""]
    return "\n".join(L)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    pub = json.load(io.open(a.publish, encoding="utf-8"))
    cpt = json.load(io.open(a.concept if os.path.isabs(a.concept)
                            else os.path.join(ROOT, a.concept), encoding="utf-8"))
    md = build(pub, cpt)
    io.open(a.out, "w", encoding="utf-8").write(md + "\n")
    print(f"\n→ {os.path.normpath(a.out)}  ({len(md):,}자 · LLM 0회 · 0원)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
