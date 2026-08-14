# -*- coding: utf-8 -*-
"""URL 필터 초안을 **저장된 후보 전수에 소급 적용**한다. LLM 0회 · 네트워크 0회.

    python tools/sim_url_filter.py            # 요약 + 오발 전수
    python tools/sim_url_filter.py --caught   # 걸린 URL 전부

패턴은 `rules.adapters.web.url_filter.patterns` 에 있다. 여기서 고치지 마라.

**오발(false positive) 의 정의** — 셋 중 하나라도 해당하면 오발 후보로 본다:
  ① 그 문서에서 **격리되지 않은 사실**이 나왔다 (원장에 근거로 올라갔다)
  ② `content_status == usable` 이고 화이트리스트 등급이 **base_score ≥ 4** 다
  ③ usable 이고 슬롯의 `must_contain` 낱말이 본문에 있다 (주제가 맞다)
①은 확정적이고 ②③은 "쓸모 있었을 수도" 다 — 셋을 갈라서 보고한다.
"""
from __future__ import annotations

import argparse, collections, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import a_desk as A
from runlog import load_rules
from schema import QUARANTINE_LABELS


def load_all(rules):
    """모든 실행의 문서 + 그 문서가 만든 원장 행을 모은다."""
    wl, base = rules["whitelist"], rules["scoring"]["base_score"]
    human = {s["slot_id"]: s for s in json.load(
        io.open(os.path.join(ROOT, "data", "slots.json"), encoding="utf-8"))["slots"]}
    out = []
    for run in sorted(os.listdir(os.path.join(ROOT, "runs"))):
        d = os.path.join(ROOT, "runs", run)
        jl = os.path.join(d, "run.jsonl")
        if not os.path.isfile(jl):
            continue
        rows = [json.loads(l) for l in io.open(jl, encoding="utf-8") if l.strip()]
        bodies = {}
        bp = os.path.join(d, "a3_bodies.json")
        if os.path.exists(bp):
            bodies = json.load(io.open(bp, encoding="utf-8"))
        # url → 이 실행에서 그 url 이 만든 원장 라벨들
        facts = {f["fact_id"]: f for f in
                 [x["payload"] for x in rows if x["node"] == "a4_facts"]}
        by_url = collections.defaultdict(list)
        for r in [x["payload"] for x in rows if x["node"] == "a4_ledger"]:
            f = facts.get(r["fact_id"])
            if f:
                by_url[A.canonical_url(f["url"])].append(r["label"])
        seen = set()
        for doc in [x["payload"] for x in rows if x["node"] == "a3_document"]:
            u = doc.get("url") or ""
            if not u or (run, u) in seen:
                continue
            seen.add((run, u))
            kind, _ = A.kind_of(u, wl)
            mc = (human.get(doc["slot_id"]) or {}).get("must_contain", [])
            text = bodies.get(doc["trace_id"]) or doc.get("text") or ""
            out.append({
                "run": run, "slot_id": doc["slot_id"], "url": u,
                "status": doc.get("content_status"), "kind": kind,
                "base": base.get(kind, 0),
                "labels": by_url.get(A.canonical_url(u), []),
                "topic_hit": bool(mc) and any(w in text for w in mc),
            })
    return out


def classify(rec, pats):
    return [p["id"] for p in pats if re.search(p["re"], rec["url"], re.I)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--caught", action="store_true", help="걸린 URL 전부 출력")
    a = ap.parse_args()

    rules = load_rules()
    cfg = rules["adapters"]["web"]["url_filter"]
    pats = cfg["patterns"]
    recs = load_all(rules)

    hits = collections.defaultdict(list)
    caught = []
    for r in recs:
        ids = classify(r, pats)
        if ids:
            r["by"] = ids
            caught.append(r)
            for i in ids:
                hits[i].append(r)

    print(f"저장된 문서 {len(recs)}건 (실행 전체 · 중복 URL 은 실행별로 1건씩)")
    print(f"필터에 걸리는 것 {len(caught)}건 ({len(caught) / max(len(recs), 1):.1%})"
          f"   · enabled={cfg['enabled']}")
    print()
    print(f"{'패턴':16s} {'걸림':>5s} {'usable':>7s} {'근거된적':>8s} {'base>=4':>8s} {'주제적중':>8s}")
    for p in pats:
        h = hits[p["id"]]
        used = [r for r in h if [x for x in r["labels"] if x not in QUARANTINE_LABELS]]
        print(f"  {p['id']:14s} {len(h):5d} "
              f"{sum(1 for r in h if r['status'] == 'usable'):7d} "
              f"{len(used):8d} {sum(1 for r in h if r['base'] >= 4):8d} "
              f"{sum(1 for r in h if r['topic_hit']):8d}")

    print()
    print("═" * 78)
    print("오발 전수 — ① 근거로 올라갔던 문서 (확정적 오발)")
    fp1 = [r for r in caught if [x for x in r["labels"] if x not in QUARANTINE_LABELS]]
    for r in sorted(fp1, key=lambda x: x["url"]):
        print(f"  [{','.join(r['by'])}] {r['slot_id']} {r['kind']}({r['base']}) "
              f"{r['labels']} {r['url'][:88]}")
    print(f"  → {len(fp1)}건")

    print()
    print("오발 후보 — ② usable 이고 base_score >= 4 (쓸모 있었을 수도)")
    fp2 = [r for r in caught if r["status"] == "usable" and r["base"] >= 4 and r not in fp1]
    for r in sorted(fp2, key=lambda x: (x["by"][0], x["url"]))[:60]:
        print(f"  [{','.join(r['by'])}] {r['slot_id']} {r['kind']}({r['base']}) "
              f"{'주제O' if r['topic_hit'] else '주제X'} {r['url'][:82]}")
    print(f"  → {len(fp2)}건")

    if a.caught:
        print()
        print("걸린 것 전부")
        for r in sorted(caught, key=lambda x: (x["by"][0], x["url"])):
            print(f"  [{','.join(r['by'])}] {r['status']:8s} {r['kind']:14s} {r['url'][:90]}")


if __name__ == "__main__":
    main()
