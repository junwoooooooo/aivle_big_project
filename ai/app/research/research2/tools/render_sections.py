# -*- coding: utf-8 -*-
"""**9절 보고서를 정적 HTML 한 장으로** 뽑는다. LLM 0회. (판 ㊴ 항목 2)

    python tools/render_sections.py runs-generated/p39-secFULL/publish.json

**왜 화면이 아니라 HTML 한 장인가** — 봉투 계약을 바꾸는 일(AI·Java·픽스처 한 커밋)은
1.5일이고 되돌리기가 비싸다. 그 앞에 **「배선해도 되는 물건인가」를 눈으로 가르는 자**를 둔다.
실제로 이번 판의 결함 셋(병원 문서가 최다 기여·채널 4건·왕관 사실 탈락)은 전부
스크립트를 짜야 보였다 — 이 한 장이 있었으면 열자마자 보였을 것들이다.

⚠ **내부 판정용이다.** 항목 6 화면의 원형으로 쓰되, 여기 있는 진단 표는 사용자에게 안 간다.
다만 **영문 갈래 이름은 여기서도 안 쓴다** — 기계 말이 사용자 문구로 새는 것이
이 제품의 실측 결함이고, 원형에서부터 막는다.

`tools/render_report.py` 는 옛 `result.json` 을 읽는다 — **재사용하지 않는다.**
"""
from __future__ import annotations

import argparse, html, io, json, os, re, sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import publish_gate as PG          # ⚠ 절 배정 규칙의 정본은 PG.절() 하나다

SECTIONS = [
    ("MARKET_SIZE", "1. 시장 크기", "시장·카테고리의 규모와 성장률"),
    ("PRICE", "2. 가격의 자리", "우리 가격이 놓일 지형"),
    ("COMPETITOR", "3. 경쟁 지형", "누가 얼마나 팔고 있나"),
    ("CHANNEL", "4. 채널", "어디서 팔리나"),
    ("DEMAND", "5. 수요", "사람들이 얼마나·왜 쓰나"),
    ("UNIT_ECONOMICS", "6. 원가·수익성", "한 개 팔면 얼마가 남나"),
    ("REGULATION", "7. 규제", "무엇을 지켜야 하나"),
]
#: 기계 말 → 사용자 말. **영문 갈래 이름이 화면에 새지 않게 한다.**
갈래말 = {
    "OURS_SEGMENT": ("우리 시장", ""),
    "OURS_UMBRELLA": ("상한으로만", "우리보다 넓은 범주의 수다 — 우리 시장은 이보다 작다"),
    "SUBSTITUTE": ("비교 — 대체 수단", "우리가 아니라 손님이 대신 고르는 것의 값이다"),
    "COMPETITOR_FIRM": ("경쟁사", ""),
}
사유말 = {
    "컨셉이 말하지 않은 대상": "이 사업과 관계없는 주제였습니다",
    "값이 인용 안에 없다": "인용한 문장에 그 숫자가 없었습니다",
    "대체재 산업의 내부 경제": "배달앱과 입점 식당 사이의 돈이라 이 사업과 무관합니다",
    "우리 고객이지만 우리 주제가 아니다": "1인 가구 이야기지만 먹는 것과 무관했습니다",
    "값이 없다": "숫자가 비어 있었습니다",
    "국내 값이 아니다": "외국 통화라 국내 시장이 아닙니다",
    "대상은 맞으나 지불성이 없다": "값이 아니라 서술이었습니다",
    "입점업체용 문서의 수": "가게 사장님용 안내문의 숫자라 손님이 내는 값이 아닙니다",
    "우리 지역이 아니다": "해외 시장이었습니다",
    "회사·채널 이름만 겹친다": "회사 이름만 같을 뿐 다른 이야기였습니다",
    "공시 문서의 수": "",
}

CSS = """
:root{--fg:#1a1a1a;--dim:#6b7280;--line:#e5e7eb;--ours:#0f766e;--cap:#b45309;
      --sub:#4338ca;--comp:#9d174d;--bg:#fff;--card:#fafafa}
*{box-sizing:border-box}
body{margin:0;padding:2rem 1.25rem 5rem;background:var(--bg);color:var(--fg);
     font:15px/1.65 -apple-system,'Malgun Gothic',sans-serif;max-width:60rem;margin-inline:auto}
h1{font-size:1.6rem;margin:0 0 .35rem}
.sub{color:var(--dim);margin:0 0 2rem;font-size:.9rem}
.warn{background:#fffbeb;border:1px solid #fcd34d;border-radius:.5rem;padding:.9rem 1.1rem;
      margin:0 0 2rem;font-size:.9rem}
section{border-top:1px solid var(--line);padding:1.6rem 0}
h2{font-size:1.1rem;margin:0 0 .2rem}
.hint{color:var(--dim);font-size:.85rem;margin:0 0 1rem}
table{width:100%;border-collapse:collapse;font-size:.9rem}
td,th{padding:.45rem .5rem;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
th{color:var(--dim);font-weight:600;font-size:.8rem}
.v{font-weight:700;white-space:nowrap}
.tag{display:inline-block;font-size:.72rem;padding:.1rem .45rem;border-radius:.7rem;
     border:1px solid currentColor;white-space:nowrap}
.t-ours{color:var(--ours)} .t-cap{color:var(--cap)} .t-sub{color:var(--sub)} .t-comp{color:var(--comp)}
.note{color:var(--cap);font-size:.82rem;margin:.5rem 0 0}
.empty{background:var(--card);border-radius:.5rem;padding:1rem 1.1rem;color:var(--dim);font-size:.9rem}
.empty b{color:var(--fg)}
.judge{background:var(--card);border-left:3px solid #2563eb;border-radius:.4rem;
  padding:.9rem 1.1rem;margin:0 0 1rem}
.judge ul{margin:.6rem 0 0;padding-left:1.1rem}
.judge li{margin:.35rem 0;line-height:1.55}
.judge code{background:rgba(37,99,235,.09);border-radius:.25rem;padding:.05rem .35rem;
  font-size:.82rem;color:var(--dim)}
.judge li.mute{color:var(--dim)}
.concl{margin:.8rem 0 0;padding-top:.7rem;border-top:1px solid var(--line);line-height:1.6}
a{color:inherit;text-decoration:none;border-bottom:1px dotted var(--dim)}
.src{color:var(--dim);font-size:.78rem}
.diag{margin-top:3rem;border-top:3px double var(--line);padding-top:1.5rem}
.diag h2{font-size:1rem}
@media(prefers-color-scheme:dark){:root{--fg:#e5e7eb;--dim:#9ca3af;--line:#374151;--bg:#111827;
  --card:#1f2937;--ours:#5eead4;--cap:#fcd34d;--sub:#a5b4fc;--comp:#f9a8d4}
  .warn{background:#1f2937;border-color:#78350f}}
"""


def _esc(s) -> str:
    return html.escape(str(s or ""))


def _강조(s) -> str:
    """`**...**` 만 굵게. **이스케이프 뒤에** 바꾼다 — 순서를 바꾸면 태그가 새어 나간다.

    판단·처방 문구는 원장(JSON)에도 그대로 남아야 해서 마크다운으로 적혀 있다.
    화면에서는 별표가 글자로 보이면 안 된다.
    """
    return re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", _esc(s))


def _구성비_경고(rows: list) -> list:
    """한 절에 **구성비 표가 섞여 들어올 때** 무엇의 구성비이고 합이 얼마인지 적는다.

    **합이 100이 아닌 구성비 표에는 그 사실이 적혀 있어야 한다** — 없으면 반쪽 표가
    빈칸보다 나쁘다. 빈칸은 「못 구했다」라고 말하지만 반쪽 표는 아무 말도 안 한다.
    그리고 성질이 다른 두 구성비(회사의 **매출처** 구성 vs 편의점이 파는 **상품** 구성)가
    같은 칸에 나란히 있으면 같은 표의 연장으로 읽힌다 — 실측으로 잡힌 혼동이다.
    """
    묶 = defaultdict(list)
    for it, _ in rows:
        if str(it.get("unit_raw") or "").strip() != "%":
            continue
        tc = str(it.get("table_context") or "").strip()
        if tc:
            묶[(tc, str(it.get("year") or ""))].append(it)
    if len(묶) < 2 and all(len(v) < 3 for v in 묶.values()):
        return []
    줄 = []
    for (tc, yr), v in sorted(묶.items(), key=lambda x: -len(x[1])):
        if len(v) < 2:
            continue
        s = sum(float(re.sub(r"[^0-9.]", "", str(x["number_raw"])) or 0) for x in v)
        온전 = 90 <= s <= 110
        # ⚠ **구성비 표에만 경고한다.** 실측 오발: 「배달 비용을 둘러싼 갈등 — 2행 합 116.1%
        # ⚠ 1위를 판단하면 안 된다」 — 이 둘은 설문 **응답률**(「…한 비율」)이지 한 표를 나눠
        # 가진 몫이 아니다. **무의미한 경고가 진짜 경고(BGF 81.5%)의 신뢰를 깎는다.**
        # 「비율」은 표지로 안 쓴다 — 설문 응답률도 비율이라 부른다.
        말 = tc + " " + " ".join(str(x.get("subject") or "") for x in v)
        if not (온전 or any(w in 말 for w in ("비중", "구성비", "구성 비", "점유율"))):
            continue
        줄.append(f'<li>「{_esc(tc)}」 {_esc(yr)} — {len(v)}행 · 합 {s:.1f}%'
                  + ("" if 온전 else
                     ' <b>⚠ 100%가 아니다 — 이 표의 나머지 행은 여기 없다.</b> '
                     '이것만 보고 1위를 판단하면 안 된다') + "</li>")
    if not 줄:
        return []
    return ['<p class="note"><b>이 칸의 구성비는 표가 여럿이다 — 표를 섞어 견주면 안 된다.</b>'
            f'<ul>{"".join(줄)}</ul></p>']


def _row(it: dict, url: str) -> str:
    라벨, _ = 갈래말[it["게재"]]
    cls = {"OURS_SEGMENT": "t-ours", "OURS_UMBRELLA": "t-cap",
           "SUBSTITUTE": "t-sub", "COMPETITOR_FIRM": "t-comp"}[it["게재"]]
    # **꼬리표에 회사 이름을 남긴다.** 「경쟁사」만으로는 두 회사의 표가 한 회사처럼 읽힌다 —
    # 실측: 오뚜기 매출처 구성비와 BGF 상품 구성비가 똑같이 「경쟁사 | kind.krx.co.kr」였다.
    if it.get("게재_발행사"):
        라벨 = f'{라벨}({it["게재_발행사"]})'
    dom = (url or "").split("/")[2] if "//" in (url or "") else "정부 통계"
    출처 = (f'<a href="{_esc(url)}">{_esc(dom)}</a>' if url else "정부 통계 API")
    # 값이 이미 단위로 끝나면 또 붙이지 않는다 — 실측: 「3조 5,340억 원」 + 「원」 = 「원원」
    단위 = "" if str(it["number_raw"]).rstrip().endswith(str(it["unit_raw"] or "\0")) else it["unit_raw"]
    return (f'<tr><td class="v">{_esc(it["number_raw"])}{_esc(단위)}</td>'
            f'<td>{_esc(it["subject"])}</td>'
            f'<td class="src">{_esc(it["year"] or "연도 없음")}</td>'
            f'<td><span class="tag {cls}">{_esc(라벨)}</span></td>'
            f'<td class="src">{출처}</td></tr>')


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish", help="publish_gate.py 가 낸 publish.json")
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    d = json.load(io.open(a.publish, encoding="utf-8"))
    실림, 사유 = defaultdict(list), defaultdict(Counter)
    전체 = 0
    for r in d["문서별"]:
        for it in r["items"]:
            if not it.get("게재"):
                continue
            전체 += 1
            if PG.버렸나(it):
                sec = it["section"]
                사유[sec][it["게재_사유"].split("(")[0]] += 1
                continue
            # `게재_제자리` 는 「출처는 그 회사인데 자리는 이 절」이다 (판 ㊷ R2) —
            # 공시 표의 채널 구성·가동률·품목 단가가 여기로 온다. 꼬리표는 「경쟁사」 그대로다.
            sec = PG.절(it)
            실림[sec].append((it, r.get("url") or ""))

    n실림 = sum(len(v) for v in 실림.values())
    # ⚠ charset 이 없으면 브라우저가 CP949 로 짐작해 전부 깨진다. 서버가 안 붙여 준다.
    P = ['<!doctype html><html lang="ko"><head><meta charset="utf-8">'
         '<meta name="viewport" content="width=device-width,initial-scale=1">'
         "<title>시장조사 9절 보고서 (초안)</title>",
         CSS.join(("<style>", "</style>")), "</head><body>",
         "<h1>시장조사 — 9절 보고서 (초안)</h1>",
         f'<p class="sub">사업안: 프리미엄 냉동 간편식 · 원장 {_esc(d["source_run"])[:8]}… · '
         f'문서 {d["문서"]}건에서 뽑은 사실 {전체}건 중 <b>{n실림}건</b>을 실었다</p>',
         '<div class="warn"><b>이 문서는 판정용 초안이다.</b> 값마다 출처가 붙어 있고, '
         '못 구한 칸은 비운 채로 이유를 적었다. 추정으로 채운 숫자는 하나도 없다. '
         '「상한으로만」이 붙은 값은 우리보다 넓은 범주의 수이므로 우리 시장은 그보다 작다.</div>']

    # 판단 문장(3단계)·8절 처방(5단계)이 옆에 있으면 같이 싣는다. 없으면 조용히 넘어간다.
    def _옆(name):
        p = os.path.join(os.path.dirname(a.publish), name)
        return json.load(io.open(p, encoding="utf-8")) if os.path.exists(p) else None

    판단, 처방, 합성 = _옆("judgments.json"), _옆("prescribe.json"), _옆("synthesis.json")

    for code, title, hint in SECTIONS:
        rows = 실림.get(code, [])
        P.append(f'<section><h2>{title}</h2><p class="hint">{hint}</p>')
        # **표 위에 판단이 온다.** 사업가는 29줄 표가 아니라 그 위의 한 문장에 돈을 낸다.
        if code == "PRICE" and 판단 and (판단.get("가격") or {}).get("갈래"):
            J = 판단["가격"]
            P.append('<div class="judge"><b>이 값이 어디에 서는가</b> '
                     '<span class="hint">— 모두 아래 표에 실린 사실로만 셈했다. '
                     '계산식을 붙였으니 손으로 검산할 수 있다.</span><ul>')
            for g in J["갈래"]:
                if g.get("문장"):
                    P.append(f'<li>{_강조(g["문장"])} <code>{_esc(g["계산"])}</code></li>')
                else:
                    P.append(f'<li class="mute">({_esc(g["무엇"])}) 안 쓴다 — '
                             f'{_강조(g.get("왜_못_쓰나", ""))}</li>')
            P.append("</ul>")
            if J.get("결론"):
                P.append(f'<p class="concl">⇒ {_강조(J["결론"])}</p>')
            P.append("</div>")
        if rows:
            for 경고 in _구성비_경고(rows):
                P.append(경고)
            P.append('<table><tr><th>값</th><th>무엇의 수인가</th><th>연도</th>'
                     '<th>갈래</th><th>출처</th></tr>')
            P += [_row(it, u) for it, u in rows]
            P.append("</table>")
            if any(it["게재"] == "OURS_UMBRELLA" for it, _ in rows):
                P.append('<p class="note">⚠ 「상한으로만」 표시된 값을 이 시장의 크기로 '
                         '읽으면 안 된다 — 우리 세그먼트는 그 안의 일부다.</p>')
        else:
            왜 = 사유.get(code) or Counter()
            n = sum(왜.values())
            top = " · ".join(사유말.get(w, w) for w, _ in 왜.most_common(3)) or "후보가 없었다"
            # ⚠ 예전에는 여기에 「이 절을 **묻는 검색 질문이 없었다**」가 **고정 문구로** 박혀
            # 있었다. 규제 절은 실제로 물었고 29건을 검토했으므로 **거짓말이었고**, 바로 아래
            # 8절이 정반대(「공개 자료에 있는데 못 닿았다」)를 적어 같은 화면이 자기모순이었다.
            # 다음 수는 **8절 처방에서 물려받는다** — 한 곳에서만 말한다.
            무값 = 왜.get("값이 없다", 0) + 왜.get("값 자리에 숫자가 없다", 0)
            진단 = (f'후보 {n}건 중 <b>{무값}건이 값 자리에 숫자가 없었다</b> — '
                    f'이름만 잡히고 지켜야 할 수치가 안 잡혔다'
                    if n and 무값 / n >= 0.5 else
                    f'수치를 {n}건 검토했지만 하나도 싣지 못했다<br>가장 흔한 이유: {_esc(top)}'
                    if n else "후보 자체가 안 잡혔다")
            다음 = next((f'<b>어디서 구하나</b> — {_강조(x["어디서"])}'
                        for x in ((처방 or {}).get("행") or []) if x.get("절") == code), "")
            P.append(f'<div class="empty"><b>못 구했다.</b> {진단}'
                     + (f"<br><br>{다음}" if 다음 else "") + "</div>")
        P.append("</section>")

    # ── 8. 무엇을 더 구해야 하나 (5단계 · 기계) ────────────────
    if 처방 and 처방.get("행"):
        P.append('<section><h2>8. 무엇을 더 구해야 하나</h2>'
                 '<p class="hint">못 구한 것을 못 구했다고 적고, <b>어디서 구하는지까지</b> 적는다</p>'
                 '<table><tr><th>무엇이 비었나</th><th>왜 비었나</th>'
                 '<th>갈래</th><th>어디서 구하나</th></tr>')
        for x in 처방["행"]:
            n = "" if x["실림"] is None else f' <span class="hint">({x["실림"]}건)</span>'
            P.append(f'<tr><td>{_esc(x["절말"])}{n}</td><td>{_강조(x["진단"])}</td>'
                     f'<td><b>{_esc(x["갈래말"])}</b></td><td>{_강조(x["어디서"])}</td></tr>')
        P.append("</table>")
        질문 = [x for x in 처방["행"] if x["갈래"] == "INTERVIEW"]
        if 질문:
            P.append('<p class="note"><b>이 조사가 답하지 못하는 것 — 시장 인터뷰의 질문이 된다.</b><br>'
                     + "<br>".join("· " + _강조(x["어디서"]) for x in 질문) + "</p>")
        P.append("</section>")

    # ── 9. 지지하는 것 / 흔드는 것 (4단계 · 기계가 갈래를 정하고 LLM 은 문장화만) ──
    if 합성 and 합성.get("문장"):
        산 = [x for x in 합성["문장"] if x.get("문장")]
        버림 = [x for x in 합성["문장"] if not x.get("문장")]
        P.append('<section><h2>9. 이 사업안을 지지하는 것 / 흔드는 것</h2>'
                 '<p class="hint">갈래와 근거는 <b>기계가</b> 정했다. 문장 안의 모든 수가 '
                 '그 근거 안에 있는지 <b>기계가 검사</b>하고, 걸리면 문장째로 버린다</p>')
        for 갈, 말 in (("지지", "지지하는 것"), ("흔듦", "흔드는 것")):
            줄 = [x for x in 산 if x["갈래"] == 갈]
            if not 줄:
                continue
            P.append(f'<p class="hint" style="margin:.8rem 0 .3rem"><b>{말} {len(줄)}</b></p><ul>')
            for x in 줄:
                근 = " · ".join(f'{_esc(s["number_raw"])}{_esc(s.get("unit_raw"))}' for s in x["근거"])
                P.append(f'<li>{_강조(x["문장"])}<br><span class="src">근거 {근}</span></li>')
            P.append("</ul>")
        if 버림:
            # **버린 것을 숨기지 않는다** (규칙 5 — 실패는 값이다).
            # **기계 내부 묶음명(`손님이_많다`)이 화면 문구로 새면 안 된다** — 이 제품의
            # 실측 결함이고, 원형에서부터 막는다. 사람이 읽는 이름(`무엇`)으로 말한다.
            P.append('<p class="note">검사에서 버린 문장 — '
                     + " · ".join(f'「{_esc(x.get("무엇") or x["키"])}」({_esc(x["버린_이유"])})'
                                  for x in 버림)
                     + "</p>")
        P.append("</section>")

    # ── 내부 진단 (사용자에게 안 간다) ──────────────────────────
    P.append('<div class="diag"><h2>내부 진단 — 안 실은 것</h2>'
             '<p class="hint">이 표는 판정용이다. 사용자 화면에는 가지 않는다.</p><table>'
             '<tr><th>왜 안 실었나</th><th>건수</th></tr>')
    합 = Counter()
    for c in 사유.values():
        합.update(c)
    P += [f'<tr><td>{_esc(사유말.get(w, w) or w)}</td><td class="v">{n}</td></tr>'
          for w, n in 합.most_common()]
    P.append("</table></div></body></html>")

    out = a.out or os.path.join(os.path.dirname(a.publish), "report.html")
    io.open(out, "w", encoding="utf-8").write("\n".join(P))
    print(f"기록: {out}")
    print(f"실린 사실 {n실림} / 검토 {전체}")
    for code, title, _ in SECTIONS:
        print(f"  {title:<16}{len(실림.get(code, [])):>4}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
