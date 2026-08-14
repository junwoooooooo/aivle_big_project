# -*- coding: utf-8 -*-
"""직접 주입 **병합** 배선 검증 (2026-08-07). **LLM 0회 · 네트워크 0회.**

예전에는 `--direct-urls` 가 배타 모드라 검색이 아예 안 돌았다. 그래서 「검색 원장」과
「주입 원장」이 갈렸고 **시장크기와 경쟁 실명이 한 원장에 같이 선 적이 없었다.**
여기서 재는 것 넷:
  ① 검색분 + 주입분이 **한 원장**에 들어온다
  ② 주입 URL 이 없는 슬롯의 **가짜 not_found 가 0건**이다 (§7 오염 방지)
  ③ 주입분 `channel="direct_url"` 표시가 유지된다 (회수율·검색 지표 분리)
  ④ `--direct-urls` 없는 기존 경로는 **동작 불변**이고, `--direct-only` 는 옛 배타 동작

    python tests/test_step9.py
"""
from __future__ import annotations
import io, json, os, shutil, sys, types

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import run as RUN
from schema import Document, Finding, FindingItem

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


RUN_IDS = ["t9-merge", "t9-plain", "t9-directonly", "t9-multi"]


def _cleanup():
    # ⚠ 삭제는 **명시한 id 목록으로만** 한다 (동시 세션 규칙 1). 패턴 삭제 금지.
    for rid in RUN_IDS:
        shutil.rmtree(os.path.join(ROOT, "runs", rid), ignore_errors=True)


def _doc(slot_id, tid, url, text, channel="web"):
    return Document(slot_id=slot_id, trace_id=tid, url=url, text=text,
                    http_status="200", content_status="usable", channel=channel)


def _fake_search(slot, rules, meter, trace):
    """검색 어댑터 대역 — 슬롯마다 문서 1개·사실 1개."""
    tid = f"{slot.slot_id}-web"
    d = _doc(slot.slot_id, tid, f"https://example.com/{slot.slot_id}",
             f"{slot.subject} {slot.metric} 는 100 {slot.unit} 이다.")
    f = Finding(slot_id=slot.slot_id, trace_id=tid, status="found",
                findings=[FindingItem(quote=f"100 {slot.unit}", number_raw="100",
                                      unit_raw=slot.unit, url=d.url)])
    return f, {tid: d}, [], "ok"


def _fake_direct(path, slots, rules, meter, run, seen_per_slot=None):
    """`_collect_direct` 대역 — S7 에만 주입 URL 이 있고 나머지는 **가짜 not_found**.

    진짜 함수와 같은 계약을 흉내낸다: 채널은 **사양 파일이 정하고**, `seen_per_slot` 이
    있으면 trace_id 를 이어 매긴다(사양을 여럿 줄 때 문서가 덮어써지지 않게 하는 장치).
    """
    spec = json.load(io.open(path, encoding="utf-8"))
    channel = spec.get("channel") or "direct_url"
    base = (seen_per_slot or {}).get("S7", 0)
    tid = f"S7-direct{f'-{base}' if base else ''}"
    if seen_per_slot is not None:
        seen_per_slot["S7"] = base + 1
    d = _doc("S7", tid, "https://platum.kr/archives/273579",
             "코케비즈 가맹점 2만 곳", channel=channel)
    out = [Finding(slot_id="S7", trace_id=tid, status="found",
                   findings=[FindingItem(quote="2만 곳", number_raw="2만",
                                         unit_raw="곳", url=d.url)])]
    for s_ in slots:
        if s_.slot_id != "S7":
            out.append(Finding(slot_id=s_.slot_id, trace_id=f"{s_.slot_id}-direct",
                               status="not_found",
                               note="direct_url 모드 — 이 슬롯에 지정된 URL 이 없다"))
    return out, {tid: d}, {"direct_url": "ok"}, []


def _run(run_id, extra_args, direct=True):
    """가짜 어댑터로 run.main() 을 통째로 돌린다. **LLM 0회 · 네트워크 0회.**"""
    old = (RUN.web, RUN.kosis, RUN.dart, RUN._collect_direct, sys.argv)
    calls = {"search": 0, "direct": 0}

    def _cnt_search(*a_, **k_):
        calls["search"] += 1
        return _fake_search(*a_, **k_)

    def _cnt_direct(*a_, **k_):
        calls["direct"] += 1
        return _fake_direct(*a_, **k_)

    def _fake_kosis(slot, rules):
        """통계 어댑터 대역 — **네트워크 0회**. not_found 라 web 으로 폴백한다."""
        import base as ADP
        d = _doc(slot.slot_id, f"{slot.slot_id}-kosis", "", "", channel="kosis_api")
        d.http_status, d.content_status = "error", "empty"
        return ADP.AdapterResult(
            Finding(slot_id=slot.slot_id, trace_id=d.trace_id, status="not_found",
                    findings=[], note="가짜 어댑터"), d, adapter_state="ok")

    RUN.web = types.SimpleNamespace(collect=_cnt_search, extract=None)
    RUN.kosis = types.SimpleNamespace(collect=_fake_kosis)
    RUN._collect_direct = _cnt_direct
    sys.argv = ["run.py", "--id", run_id,
                "--slots", os.path.join(ROOT, "data", "slots.json"),
                "--formulas", os.path.join(ROOT, "data", "formulas.json")] + extra_args
    try:
        RUN.main()
    finally:
        RUN.web, RUN.kosis, RUN.dart, RUN._collect_direct, sys.argv = old
    res = json.load(io.open(os.path.join(ROOT, "runs", run_id, "result.json"),
                            encoding="utf-8"))
    rows = [json.loads(l) for l in
            io.open(os.path.join(ROOT, "runs", run_id, "run.jsonl"), encoding="utf-8")
            if l.strip()]
    return res, rows, calls


_cleanup()

# ══════════════════════════════════════════════════════════════
print("[병합] 검색분 + 주입분이 한 원장에 들어온다")
res, rows, calls = _run("t9-merge", ["--direct-urls",
                                     os.path.join(ROOT, "data", "direct_urls.json")])
docs = [r["payload"] for r in rows if r["node"] == "a3_document"]
finds = [r["payload"] for r in rows if r["node"] == "a3_finding"]
channels = {d["trace_id"]: d.get("channel") for d in docs}
check("검색이 실제로 돌았다 (배타 모드가 아니다)", calls["search"] > 0, str(calls))
check("주입도 같은 실행에서 돌았다", calls["direct"] == 1, str(calls))
check("① 한 원장에 검색분과 주입분이 함께 있다",
      any(v == "direct_url" for v in channels.values())
      and any(v == "web" for v in channels.values()), str(sorted(set(channels.values()))))
check("② 가짜 not_found 가 0건이다 (§7 오염 방지)",
      not [f for f in finds if f["trace_id"].endswith("-direct")
           and f["status"] == "not_found"],
      str([f["trace_id"] for f in finds if f["status"] == "not_found"])[:80])
check("  주입 Finding 은 found 만 남는다",
      [f["status"] for f in finds if f["trace_id"].endswith("-direct")] == ["found"])
check("③ channel='direct_url' 표시가 유지된다", channels.get("S7-direct") == "direct_url")
check("  검색분은 그대로 web 이다 — 지표가 섞이지 않는다",
      channels.get("S1-web") in ("web", None) and channels.get("S7-direct") != "web")
check("  adapters 에 direct_url 상태가 남는다",
      (res.get("adapters") or {}).get("direct_url") == "ok",
      str(res.get("adapters")))
facts = [r["payload"] for r in rows if r["node"] == "a4_facts"]
check("주입분이 A4 까지 간다 (사실이 된다)",
      any((f.get("channel") == "direct_url") or "platum" in (f.get("url") or "")
          for f in facts), str(len(facts)))

# ══════════════════════════════════════════════════════════════
print("\n[회귀 ④-1] --direct-urls 없는 기존 경로는 동작 불변")
res2, rows2, calls2 = _run("t9-plain", [])
check("주입 코드가 아예 안 불린다", calls2["direct"] == 0, str(calls2))
docs2 = [r["payload"] for r in rows2 if r["node"] == "a3_document"]
check("  direct_url 문서 0건", not [d for d in docs2 if d.get("channel") == "direct_url"])
check("  검색 호출 수가 병합 실행과 같다 (검색이 줄지 않았다)",
      calls2["search"] == calls["search"], f"{calls2['search']} vs {calls['search']}")
check("  슬롯 수·원장 구조 동일",
      len(res2["input"]["slots"]) == len(res["input"]["slots"]))

print("\n[회귀 ④-2] --direct-only 는 옛 배타 동작 (audit-final 을 만든 경로)")
res3, rows3, calls3 = _run("t9-directonly",
                           ["--direct-urls", os.path.join(ROOT, "data", "direct_urls.json"),
                            "--direct-only"])
check("검색을 한 번도 부르지 않는다", calls3["search"] == 0, str(calls3))
check("  주입만 돈다", calls3["direct"] == 1)
docs3 = [r["payload"] for r in rows3 if r["node"] == "a3_document"]
check("  문서가 전부 direct_url 이다",
      docs3 and all(d.get("channel") == "direct_url" for d in docs3),
      str({d.get("channel") for d in docs3}))
check("  옛 모드에서는 가짜 not_found 가 그대로 남는다 (동작 불변 — 손대지 않았다)",
      any(f["status"] == "not_found" for f in
          [r["payload"] for r in rows3 if r["node"] == "a3_finding"]))

print("\n[병합 ④-3] 사양을 **여럿** 주면 채널 분모가 갈린다 (판 ②, 2026-08-08)")
# 왜 필요한가: 채널 태그는 사양 파일당 **하나**다. user_doc·tavily 를 한 파일에 합치면
# 「어느 채널이 물어왔는가」의 분모가 뭉뚱그려지고, 커버리지가 올라도 무엇 덕인지 못 말한다.
# 그리고 trace_id 는 슬롯별 일련번호라, 사양마다 0 부터 다시 매기면 **문서가 조용히
# 덮어써진다**(docs 는 trace_id 키). 둘 다 여기서 잰다.
_spec1 = os.path.join(ROOT, "data", "direct_urls.json")
_spec2 = os.path.join(ROOT, "runs", "_t9_spec2.json")
_s2 = json.load(io.open(_spec1, encoding="utf-8"))
_s2["channel"] = "tavily"
io.open(_spec2, "w", encoding="utf-8").write(json.dumps(_s2, ensure_ascii=False))
try:
    res4, rows4, calls4 = _run("t9-multi", ["--direct-urls", f"{_spec1},{_spec2}"])
finally:
    os.remove(_spec2)
docs4 = [r["payload"] for r in rows4 if r["node"] == "a3_document"]
ch4 = {d["trace_id"]: d.get("channel") for d in docs4
       if d["trace_id"].startswith("S7-direct")}      # 주입분만. 검색·kosis 분은 별개다
check("사양 수만큼 주입이 돈다", calls4["direct"] == 2, str(calls4))
check("① 문서가 덮어써지지 않는다 (trace_id 가 이어 매겨진다)",
      len(ch4) == 2, str(ch4))
check("② 채널 분모가 갈린다 — 사양마다 자기 channel 을 지킨다",
      set(ch4.values()) == {"direct_url", "tavily"}, str(sorted(set(ch4.values()))))
check("  첫 사양의 이름은 예전과 같다 (단일 사양 원장과 비교 가능)",
      "S7-direct" in ch4 and ch4["S7-direct"] == "direct_url", str(sorted(ch4)))
check("  검색은 그대로 돈다 (병합이지 대체가 아니다)", calls4["search"] > 0, str(calls4))

_cleanup()
print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print("   -", f)
sys.exit(1 if fail else 0)
