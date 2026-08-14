# -*- coding: utf-8 -*-
"""수집 깔때기 진단 — **어디서 자료가 사라지는가를 코드가 잰다.** LLM 0회 · 원장 쓰기 0회.

    python -X utf8 tools/funnel.py --run smoke-collect-01
    python -X utf8 tools/funnel.py --run smoke-collect-01 --json --save funnel_before.json

왜 있는가: 성적표(`tools/scorecard.py`)는 **결과**를 재고, 이 도구는 **과정**을 잰다.
성적이 3·0·3 일 때 「자료가 없어서」인지 「우리가 버려서」인지를 성적표는 답하지 못한다.
그 답을 손으로 세면 판마다 잣대가 흔들리고 디스크와 어긋난다 — 성적표가 생긴 것과 같은 이유다.

⚠ **이 도구는 아무것도 고치지 않는다.** 세기만 한다. 그래서 고치기 **전에** 돌려
   기준선을 남기고, 고친 **뒤에** 같은 축으로 다시 잰다.

읽는 것은 원장뿐이다 — `run.jsonl` · `result.json`. 본문 길이는 `a3_document.text_len`
에 이미 값으로 있어 `a3_bodies.json` 을 안 열어도 된다(721KB 를 안 읽는다).

절단 상한은 **실행 당시 값**을 `result.json.rules` 에서 읽는다. 지금 규칙 파일을 읽으면
옛 원장을 새 상한으로 재는 셈이 되어 before/after 가 거짓이 된다.
"""
from __future__ import annotations

import argparse
import collections
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
import runpath                                           # noqa: E402

#: `a3_extract` 원장 노드가 없던 시절 원장을 읽기 위한 역산 패턴.
#: 노드가 생긴 뒤에도 옛 원장으로 기준선을 다시 뜰 수 있어야 한다.
_NOTE_SENT = re.compile(r"문서\s*(\d+)\s*개\s*묶어")
_NOTE_CUT = re.compile(r"(\d+)\s*개\s*제외:\s*(\[[^\]]*\])")


def _load(run: str) -> tuple[list, dict]:
    d = runpath.read_dir(run)
    p = os.path.join(d, "run.jsonl")
    if not os.path.isfile(p):
        raise SystemExit(f"원장이 없다: {p}")
    rows = [json.loads(ln) for ln in io.open(p, encoding="utf-8") if ln.strip()]
    res = json.load(io.open(os.path.join(d, "result.json"), encoding="utf-8"))
    return rows, res


def _by_node(rows: list) -> dict:
    out = collections.defaultdict(list)
    for o in rows:
        p = o.get("payload") or {}
        # a4_ledger 는 한 줄에 여러 행이 묶여 오는 판본이 있다.
        if o.get("node") == "a4_ledger":
            out["a4_ledger"] += p.get("rows") or ([p] if "label" in p else [])
        else:
            out[o.get("node")].append(p)
    return out


def _extract_view(nodes: dict, docs_by_slot: dict) -> dict:
    """슬롯별 {sent:[trace_id], cut:[trace_id]}.

    `a3_extract` 노드가 있으면 **값을 읽고**, 없으면 `a3_finding.note` 에서 역산한다.
    역산은 옛 원장 전용이다 — 문자열은 기록이 아니라 흔적이라 새 판은 값으로 남긴다(0-1).
    """
    out = {}
    for p in nodes.get("a3_extract") or []:
        out[p["slot_id"]] = {"sent": list(p.get("picked") or []),
                             "cut": list(p.get("cut") or []), "_원천": "a3_extract"}
    if out:
        return out
    for p in nodes.get("a3_finding") or []:
        note, sid = str(p.get("note") or ""), p.get("slot_id")
        m_sent, m_cut = _NOTE_SENT.search(note), _NOTE_CUT.search(note)
        if not m_sent:
            continue                       # kosis·dart 는 발췌 프롬프트를 안 탄다
        cut = []
        if m_cut:
            try:
                cut = json.loads(m_cut.group(2).replace("'", '"'))
            except Exception:
                cut = []
        usable = [d["trace_id"] for d in docs_by_slot.get(sid, [])
                  if d.get("content_status") == "usable" and (d.get("text_len") or 0) > 0]
        sent = [t for t in usable if t not in set(cut)][:int(m_sent.group(1))]
        out[sid] = {"sent": sent, "cut": cut, "_원천": "note 역산"}
    return out


def _err_key(e) -> str:
    """`error` 문자열을 사유 **종류**로 접는다.

    판 ㉞ 가 이 축이 없어 4판을 태웠다 — `pdfplumber 없음: ModuleNotFoundError` 와
    `텍스트층 없음(스캔본 추정)` 과 `파싱 실패: …` 가 전부 같은 `pdf_unreadable` 칸에
    앉아 있었고, 그래서 「우리 환경에 해석기가 없다」가 「스캔본이 많다」와 구별되지 않았다.
    """
    s = str(e or "").strip()
    return s.split(":")[0].strip() if ":" in s else s


def _fetch_key(d: dict) -> str:
    """HTTP 쪽 사유. **코드를 붙인다** — 429(우리가 막힌 것)와 404(없는 것)는 다른 얘기다."""
    hs = d.get("http_status") or "?"
    code = d.get("http_code")
    return f"{hs}:{code}" if hs != "ok" and code else hs


def build(run: str, claim_type: str = "") -> dict:
    rows, res = _load(run)
    nodes = _by_node(rows)
    # 슬롯 사양은 `result.json.input.slots` 에만 있다 — 원장 노드에는 claim_type 이 없다.
    specs = {s["slot_id"]: s for s in (res.get("input") or {}).get("slots") or []}
    want = None
    if claim_type:
        want = {sid for sid, s in specs.items()
                if str(s.get("claim_type") or "").upper() == claim_type.upper()}
        # slot_id 가 없는 줄(실행 전체에 걸린 것)은 남긴다 — 지우면 분모가 거짓이 된다.
        nodes = {k: [p for p in v if "slot_id" not in p or p.get("slot_id") in want]
                 for k, v in nodes.items()}
    rules_web = (((res.get("rules") or {}).get("adapters") or {}).get("web") or {})
    cap_chars = int(rules_web.get("extract_doc_chars") or 0)
    cap_docs = int(rules_web.get("extract_max_docs") or 0)

    route = {p["slot_id"]: p.get("adapter") for p in nodes.get("a2_route") or []}
    docs = nodes.get("a3_document") or []
    docs_by_slot = collections.defaultdict(list)
    for d in docs:
        docs_by_slot[d.get("slot_id")].append(d)
    ex = _extract_view(nodes, docs_by_slot)
    doc_by_tid = {d.get("trace_id"): d for d in docs}

    # ── 단계 ①②③ ────────────────────────────────────────────────
    cand = collections.Counter(p.get("status") for p in nodes.get("a3_candidate") or [])
    filt = collections.Counter(p.get("filter_reason") for p in nodes.get("a3_candidate") or []
                               if p.get("status") == "filtered")
    body = collections.Counter(d.get("content_status") for d in docs)
    usable_n = body.get("usable", 0)

    # ── PDF 단계 (판 ㉟ ③) ───────────────────────────────────────
    #   ⚠ `is_pdf` 는 판 ㉟ 부터 남는다. 그 이전 원장에는 **칸 자체가 없어** 죽은 PDF 만
    #     보인다 — 성공한 PDF 는 usable 더미에 섞여 셀 수 없다. 그것을 0 으로 적으면
    #     미측정이 0 으로 둔갑하므로 **하한이라고 표시한다.**
    pdf_측정 = any("is_pdf" in d for d in docs)
    pdf_docs = ([d for d in docs if d.get("is_pdf")] if pdf_측정
                else [d for d in docs if d.get("content_status") == "pdf_unreadable"])
    pdf_usable = sum(1 for d in pdf_docs if d.get("content_status") == "usable")
    pdf_사유 = collections.Counter(_err_key(d.get("error")) or d.get("content_status")
                                 for d in pdf_docs if d.get("content_status") != "usable")

    # ── 단계 ④ 본문 절단 ─────────────────────────────────────────
    #   발췌 프롬프트를 **실제로 탄 문서에만** 적용한다. kosis·dart 는 어댑터가 직접
    #   구조화 응답을 읽으므로 절단이 없다 — 전체 문서로 재면 손실이 부풀려진다.
    sent_ids = [t for v in ex.values() for t in v["sent"]]
    chars_total = sum((doc_by_tid.get(t, {}).get("text_len") or 0) for t in sent_ids)
    # ⚠ **적힌 값을 읽는다. 다시 계산하지 않는다.** 예전에는 `min(문서길이, 상한)` 으로
    #   되짚었는데, 그것은 「앞에서 자른다」를 **가정한** 식이다 — 판 ㉛B 의 창 고르기가
    #   들어오자 창을 켠 판과 끈 판이 **글자 하나까지 같게** 보고됐다(실측: 둘 다 395,144자,
    #   실제로는 315,915 vs 395,144). 재는 도구가 방식을 가정하면 그 방식을 바꾼 판을 못 잰다.
    #   옛 원장에는 이 값이 없으므로 그때만 되짚는다.
    logged = {t: c for p in (nodes.get("a3_extract") or [])
              for d in (p.get("per_doc") or [])
              if (t := d.get("trace_id")) and (c := (d.get("창") or {}).get("chars_sent"))}
    chars_sent = sum(
        logged.get(t, (min((doc_by_tid.get(t, {}).get("text_len") or 0), cap_chars) if cap_chars
                       else (doc_by_tid.get(t, {}).get("text_len") or 0)))
        for t in sent_ids)
    window_modes = collections.Counter()
    for p in nodes.get("a3_extract") or []:
        window_modes.update(p.get("window_modes") or {})
    over = [t for t in sent_ids
            if cap_chars and (doc_by_tid.get(t, {}).get("text_len") or 0) > cap_chars]
    all_chars = sum((d.get("text_len") or 0) for d in docs)

    # ── 단계 ⑤ 발췌 ──────────────────────────────────────────────
    fin = nodes.get("a3_finding") or []
    fin_by_slot = {p["slot_id"]: p for p in fin}
    cites = sum(len(p.get("findings") or []) for p in fin)

    # ── 단계 ⑥ A4 ────────────────────────────────────────────────
    ledger = nodes.get("a4_ledger") or []
    labels = collections.Counter(r.get("label") for r in ledger)
    적 = [r for r in ledger if r.get("채택")]
    사유 = collections.Counter(s for r in ledger for s in (r.get("채택_불가_사유") or []))
    off = collections.Counter((r.get("off_slot_reason") or "").split(":")[0]
                              for r in ledger if r.get("off_slot_reason"))

    # ── 슬롯별 발췌 결과 (판 ㉟ ③) ────────────────────────────────
    #   「모델이 없다고 했다」와 「본문이 안 왔다」가 지금 같은 칸(인용 0)에 앉아 있다.
    perdoc_by_slot = collections.defaultdict(collections.Counter)
    for p in nodes.get("a3_extract") or []:
        for d in p.get("per_doc") or []:
            perdoc_by_slot[p.get("slot_id")][d.get("status") or "?"] += 1

    # ── 슬롯별 ───────────────────────────────────────────────────
    slots = []
    for sid in sorted(docs_by_slot | ex.keys() | set(route), key=lambda x: (len(x), x)):
        ds = docs_by_slot.get(sid, [])
        e = ex.get(sid) or {}
        f = fin_by_slot.get(sid) or {}
        죽은 = [d for d in ds if d.get("content_status") != "usable"]
        slots.append({
            "slot_id": sid, "adapter": route.get(sid),
            "claim_type": (specs.get(sid) or {}).get("claim_type"),
            "문서": len(ds),
            "usable": sum(1 for d in ds if d.get("content_status") == "usable"),
            "발췌진입": len(e.get("sent") or []),
            "상한절단": len(e.get("cut") or []),
            "인용": len(f.get("findings") or []),
            "사실": sum(1 for x in nodes.get("a4_facts") or [] if x.get("slot_id") == sid),
            "채택": sum(1 for r in 적 if r.get("slot_id") == sid),
            # 사유 세 축 — 여기가 없어서 `pdf_unreadable × 48` 이 4판을 통과했다.
            "본문사유": dict(collections.Counter(d.get("content_status") for d in 죽은)),
            "fetch사유": dict(collections.Counter(_fetch_key(d) for d in 죽은)),
            "error사유": dict(collections.Counter(_err_key(d.get("error")) for d in 죽은
                                                if d.get("error"))),
            "발췌사유": dict(perdoc_by_slot.get(sid) or {}),
        })

    # ── 어댑터별 수율 ────────────────────────────────────────────
    #   ⚠ **a2_route 의 경로로 묶으면 안 된다.** 폴백이 있기 때문이다 — kosis 로 라우팅된
    #   슬롯이 `should_fallback` 으로 web 발췌를 타면(실측: S4), 그 문서와 빈손이 kosis
    #   성적으로 잡혀 「kosis 수율 100%」 같은 거짓이 나온다. **실제로 값을 낸 경로**는
    #   finding 의 trace_id 접미사가 말한다(`S1-kosis` · `S12-dart` · `S4-extract`).
    def _실제경로(sid: str) -> str:
        tid = str((fin_by_slot.get(sid) or {}).get("trace_id") or "")
        return tid.rsplit("-", 1)[-1] if "-" in tid else (route.get(sid) or "?")

    adp = {}
    for s in slots:
        a = _실제경로(s["slot_id"])
        b = adp.setdefault(a, {"슬롯": 0, "문서": 0, "발췌진입": 0, "인용": 0, "채택": 0})
        b["슬롯"] += 1
        for k in ("문서", "발췌진입", "인용", "채택"):
            b[k] += s[k]
    for a, b in adp.items():
        # 발췌를 타는 경로는 프롬프트에 들어간 문서가 분모고, 어댑터가 구조화 응답을
        # 직접 읽는 경로(kosis·dart)는 받아 온 문서가 분모다.
        밑 = b["발췌진입"] if a == "extract" else b["문서"]
        b["_분모"] = "발췌진입" if a == "extract" else "문서"
        b["수율"] = round(b["인용"] / 밑, 4) if 밑 else None

    단계 = [
        {"이름": "검색 후보", "들어감": sum(cand.values()), "나옴": cand.get("ok", 0),
         "잃음": sum(v for k, v in cand.items() if k != "ok"), "내역": dict(filt) or dict(cand)},
        {"이름": "본문 판정", "들어감": len(docs), "나옴": usable_n,
         "잃음": len(docs) - usable_n,
         "내역": {k: v for k, v in body.items() if k != "usable"}},
        {"이름": ("PDF 해석" if pdf_측정 else "PDF 해석 (하한 — is_pdf 미측정 원장)"),
         "들어감": len(pdf_docs), "나옴": pdf_usable,
         "잃음": len(pdf_docs) - pdf_usable, "내역": dict(pdf_사유)},
        {"이름": f"상한 절단 (extract_max_docs={cap_docs or '?'})",
         "들어감": len(sent_ids) + sum(len(v.get("cut") or []) for v in ex.values()),
         "나옴": len(sent_ids),
         "잃음": sum(len(v.get("cut") or []) for v in ex.values()), "내역": {}},
        {"이름": f"본문 절단 (extract_doc_chars={cap_chars or '?'})",
         "들어감": chars_total, "나옴": chars_sent, "잃음": chars_total - chars_sent,
         "단위": "자", "내역": {"상한 초과 문서": len(over)}},
        {"이름": "발췌", "들어감": len(sent_ids), "나옴": cites,
         "잃음": len(sent_ids) - cites,
         "내역": {"found 슬롯": sum(1 for p in fin if p.get("status") == "found"),
                "not_found 슬롯": sum(1 for p in fin if p.get("status") != "found")}},
        {"이름": "A4 채택", "들어감": len(ledger), "나옴": len(적),
         "잃음": len(ledger) - len(적), "내역": dict(사유)},
    ]

    return {
        "run": run,
        "_원천": (list(ex.values())[0]["_원천"] if ex else "발췌 기록 없음"),
        "_claim_type": claim_type or None,
        "_실행_능력": res.get("실행_능력"),          # 판 ㉟ ① — 없으면 미측정 원장이다
        "PDF": {"측정됨": pdf_측정, "문서": len(pdf_docs), "usable": pdf_usable,
                "사유": dict(pdf_사유)},
        "단계": 단계,
        "절단": {"발췌진입_문서": len(sent_ids), "총_글자": chars_total,
               "보낸_글자": chars_sent,
               # 입력을 **어떻게** 골랐는가. `head` 는 앞자르기, `window` 는 닻 주변 창,
               # `whole` 은 상한 아래라 통째로, `head_fallback` 은 닻이 없어 되돌아간 것.
               # ⚠ 도달률은 **양**이지 **적중**이 아니다 — 창 방식은 양을 늘리지 않는다.
               "고른_방식": dict(window_modes),
               "도달률": round(chars_sent / chars_total, 4) if chars_total else None,
               "상한": cap_chars, "상한초과_문서": len(over),
               "_전체문서_글자": all_chars},
        "어댑터": adp,
        "A4": {"label": dict(labels), "채택": len(적), "전체행": len(ledger),
               "채택_불가_사유": dict(사유), "off_slot_사유": dict(off)},
        "슬롯": slots,
    }


def _pct(a, b):
    return f"{a / b:.1%}" if b else "—"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--save", default="", help="원장 디렉터리에 저장할 파일명")
    ap.add_argument("--claim-type", dest="claim_type", default="",
                    help="그 유형의 슬롯만 남긴 깔때기 (예: PAIN)")
    a = ap.parse_args()
    r = build(a.run, a.claim_type)

    if a.save:
        p = os.path.join(runpath.read_dir(a.run), a.save)
        io.open(p, "w", encoding="utf-8").write(json.dumps(r, ensure_ascii=False, indent=2))
        print(f"저장: {p}")
    if a.json:
        print(json.dumps(r, ensure_ascii=False, indent=2))
        return 0

    범위 = f"  · claim_type={r['_claim_type']} 만" if r["_claim_type"] else ""
    print(f"\n[{r['run']}] 깔때기  (발췌 기록 원천: {r['_원천']}){범위}")
    # 실행 능력 — 「자료가 없었나」와 「우리가 못 읽었나」를 가르는 첫 줄이다(판 ㉟ ①)
    cap = r.get("_실행_능력")
    if cap is None:
        print("  ⚠ 실행_능력 미측정 원장 — 이 판이 무엇으로 돌았는지 알 수 없다")
    else:
        print("  실행 능력 " + " · ".join(f"{k} {v or '없음'}" for k, v in cap.items()))
    print()
    print(f"  {'단계':<38}{'들어감':>10}{'나옴':>10}{'잃음':>10}  내역")
    for s in r["단계"]:
        u = s.get("단위", "")
        내역 = " · ".join(f"{k} {v}" for k, v in (s["내역"] or {}).items())
        print(f"  {s['이름']:<38}{s['들어감']:>10,}{u}{s['나옴']:>10,}{u}"
              f"{s['잃음']:>10,}{u}  {내역}")
    c = r["절단"]
    print(f"\n  본문 도달률 {_pct(c['보낸_글자'], c['총_글자'])} "
          f"(발췌 진입 {c['발췌진입_문서']}건 · 상한 초과 {c['상한초과_문서']}건)")
    if c.get("고른_방식"):
        print("  입력 고른 방식 " + " · ".join(f"{k} {v}" for k, v in c["고른_방식"].items())
              + "\n  ⚠ 도달률은 **양**이다. 창 고르기는 양을 늘리지 않고 **자리**를 바꾼다 —"
                " 그 효과는 아래 발췌 수율로 본다.")

    print("\n  어댑터별 수율 (인용 ÷ 발췌진입)")
    for k, v in sorted(r["어댑터"].items()):
        수 = f"{v['수율']:.1%}" if v["수율"] is not None else "—"
        print(f"    {k:<8} 슬롯 {v['슬롯']:>2} · 문서 {v['문서']:>3} · "
              f"발췌진입 {v['발췌진입']:>3} · 인용 {v['인용']:>3} · 채택 {v['채택']:>3} → {수}")

    print(f"\n  A4  채택 {r['A4']['채택']}/{r['A4']['전체행']}행 · label {r['A4']['label']}")
    if r["A4"]["채택_불가_사유"]:
        print(f"      불가 사유 {r['A4']['채택_불가_사유']}")

    print("\n  슬롯별")
    print(f"    {'슬롯':<6}{'경로':<8}{'문서':>5}{'usable':>8}{'발췌':>6}{'절단':>6}"
          f"{'인용':>6}{'사실':>6}{'채택':>6}")
    for s in r["슬롯"]:
        print(f"    {s['slot_id']:<6}{str(s['adapter'] or '?'):<8}{s['문서']:>5}"
              f"{s['usable']:>8}{s['발췌진입']:>6}{s['상한절단']:>6}"
              f"{s['인용']:>6}{s['사실']:>6}{s['채택']:>6}")

    # ── 사유 축 (판 ㉟ ③) ────────────────────────────────────────
    #   표의 숫자는 「몇 건 잃었나」까지만 말한다. **왜** 잃었는지가 여기 있다.
    잃은 = [s for s in r["슬롯"] if s["본문사유"] or s["발췌사유"]]
    if 잃은:
        print("\n  사유 (usable 아닌 문서만 · 발췌는 문서당 호출 결과)")
        for s in 잃은:
            축 = [("본문", s["본문사유"]), ("fetch", s["fetch사유"]),
                 ("error", s["error사유"]), ("발췌", s["발췌사유"])]
            줄 = " | ".join(f"{name}: " + " · ".join(f"{k} {v}" for k, v in d.items())
                           for name, d in 축 if d)
            print(f"    {s['slot_id']:<6}{줄}")
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
