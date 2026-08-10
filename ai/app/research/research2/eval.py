# -*- coding: utf-8 -*-
"""지표 — **"모델을 어떻게 개선할지"의 답이 여기 있다.**

    python eval.py <run_id> [<run_id2>]
    python eval.py <run_id> --out runs/<run_id>/eval.json    # viewer.html 이 읽는 파일

네 지표는 **분모가 서로 다르다.** 같은 분모를 쓰면 서로 흔들려 판단이 불가능해진다.

| 지표   | 분자 / 분모                                   | 고칠 곳 |
|--------|-----------------------------------------------|---------|
| 회수율 | golden 도메인 적중 / golden 항목 수            | `prompts.SEARCH` |
| 추출률 | 값을 뽑은 문서 / **뽑을 만했던** 문서          | `prompts.EXTRACT` |
| 정확도 | quote_verified / 인용 수                       | `prompts.EXTRACT` |
| 적합률 | off_slot 아닌 사실 / 사실 총수                 | `data/slots.json` (슬롯 정의) |

**추출률이 왜 필요한가:** "가져왔는데 못 뽑았다"는 회수율에도 정확도에도 안 잡힌다.
회수율은 "가져왔나"만 보고 정확도는 "뽑은 게 맞나"만 본다. 그 사이가 비어 있었다.
'뽑을 만했던 문서' = usable 이고 슬롯 키워드와 숫자·단위를 **동시에** 가진 문서 (LLM 0회 판정).
분모가 0이면 `null` — "발췌를 탓할 근거가 없다"가 정확한 상태다. 0과 null 을 구분해야
EXTRACT 를 헛되이 고치지 않는다.

**비교 축:** `adapters` 상태와 `rules.whitelist.version` 이 다르면 같은 그래프에 올리지 않는다.
화이트리스트를 v4 로 올리는 순간 정확도·적합률이 소급해서 달라 보인다.
"""
from __future__ import annotations

import io, json, os, re, sys
from urllib.parse import urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
RUNS = os.path.join(HERE, "runs")

NUM_UNIT = re.compile(r"\d[\d,]*\s*(?:개|곳|개소|명|건|원|%|％)")


def _rows(run_id: str, node: str) -> list[dict]:
    p = os.path.join(RUNS, run_id, "run.jsonl")
    if not os.path.exists(p):
        return []
    out = []
    for line in io.open(p, encoding="utf-8"):
        if not line.strip():
            continue
        r = json.loads(line)
        if r["node"] == node:
            out.append(r["payload"])
    return out


def _result(run_id: str) -> dict:
    p = os.path.join(RUNS, run_id, "result.json")
    return json.load(io.open(p, encoding="utf-8")) if os.path.exists(p) else {}


def _bodies(run_id: str) -> dict:
    p = os.path.join(RUNS, run_id, "a3_bodies.json")
    return json.load(io.open(p, encoding="utf-8")) if os.path.exists(p) else {}


def _golden() -> dict:
    p = os.path.join(HERE, "data", "golden.json")
    return json.load(io.open(p, encoding="utf-8")).get("slots", {}) if os.path.exists(p) else {}


def _dom(url: str) -> str:
    return urlsplit(url).netloc.lower().replace("www.", "")


def rate(a: int, b: int):
    return round(a / b, 3) if b else None


def metrics(run_id: str) -> dict:
    res = _result(run_id)
    slots = {s["slot_id"]: s for s in (res.get("input", {}).get("slots") or [])}
    cands = _rows(run_id, "a3_candidate")
    docs = _rows(run_id, "a3_document")
    findings = _rows(run_id, "a3_finding")
    facts = _rows(run_id, "a4_facts")
    bodies = _bodies(run_id)
    golden = _golden()

    # ── 회수율 : golden 도메인을 검색이 물어왔나
    hit = want = 0
    missed, forbidden_hit = [], []
    found_by_slot: dict[str, set] = {}
    for c in cands:
        if c.get("url"):
            found_by_slot.setdefault(c["slot_id"], set()).add(_dom(c["url"]))
    # golden 은 사람이 적은 6개 슬롯 기준이므로 claim_type 으로 대응시킨다
    by_claim: dict[str, set] = {}
    for sid, doms in found_by_slot.items():
        by_claim.setdefault((slots.get(sid) or {}).get("claim_type", "?"), set()).update(doms)
    claim_of_golden = {"S1": "TAM", "S2": "SAM", "S3": "COMP", "S4": "COMPARABLE",
                       "S5": "PAIN", "S6": "PRICE"}
    for gid, g in golden.items():
        claim = claim_of_golden.get(gid, gid)
        seen = by_claim.get(claim, set())
        for d in g.get("must_hit_domain", []):
            want += 1
            if any(x == d or x.endswith("." + d) for x in seen):
                hit += 1
            else:
                missed.append(f"{claim}:{d}({g.get('basis')})")
        for d in g.get("must_not_hit", []):
            if any(x == d or x.endswith("." + d) for x in seen):
                forbidden_hit.append(f"{claim}:{d}")

    # ── 추출률 : 뽑을 만했던 문서 중 실제로 뽑은 문서
    extractable, extracted = [], set()
    for d in docs:
        if d.get("content_status") != "usable":
            continue
        text = bodies.get(d.get("trace_id"), "") or d.get("text", "")
        slot = slots.get(d.get("slot_id")) or {}
        kws = slot.get("must_contain") or []
        kw_ok = (not kws) or any(k in text for k in kws)
        if kw_ok and NUM_UNIT.search(text):
            extractable.append(d)
    ok_urls = {f.get("url") for f in facts if f.get("url")}
    for d in extractable:
        if d.get("url") in ok_urls:
            extracted.add(d.get("url"))

    # ── 정확도 : 인용이 본문에 실재했나
    quotes = [f for f in facts]
    verified = [f for f in quotes if f.get("quote_verified")]

    # ── 적합률 : 슬롯에 맞는 자료였나
    ledger = _rows(run_id, "a4_ledger")
    on_slot = [r for r in ledger if r.get("label") != "off_slot"]

    # 파생 실행(--from a4)은 **검색을 하지 않았다.** 후보가 0건인 것은 "하나도 못 맞췄다"가
    # 아니라 "잴 대상이 이 실행에 없다"이다. 0 으로 찍으면 ↓악화로 읽혀 검색 프롬프트를
    # 헛되이 고친다 — 0 과 null 을 구분하라는 그 함정이 여기 있었다.
    collected = bool(cands)
    return {
        "run_id": run_id,
        # 수집이 같은 실행끼리만 채점 비교가 유효하다 — 파생 실행은 source_run 이 축이다
        "축": {"source_run": res.get("source_run"), "from_stage": res.get("from_stage"),
               "adapters": res.get("adapters"),
               "whitelist_version": (res.get("rules", {}).get("whitelist", {}) or {}).get("version"),
               "scoring_version": (res.get("rules", {}).get("scoring", {}) or {}).get("version"),
               # 슬롯이 바뀌면 적합률의 분모가 바뀐다. Run.finish 가 박아 둔 값을 읽기만 한다
               "slot_set_hash": res.get("slot_set_hash"),
               # 사람 칸을 현재 파일 값으로 갈아끼웠는가(--slots-from current).
               # 같은 수집이라도 채점 규칙이 다르므로 source 끼리·current 끼리만 비교한다.
               "slots_overlay": bool(res.get("slots_overlay")),
               "as_of": res.get("reference_date")},
        "회수율": (rate(hit, want) if collected else None),
        "회수_분모": (want if collected else 0),
        "_회수_주의": ("" if collected else
                       "이 실행은 수집을 하지 않았다(파생 실행) — 회수율을 잴 수 없다"),
        "놓친_정답": (missed[:8] if collected else []),
        "금지도메인_유입": forbidden_hit,
        "추출률": rate(len(extracted), len(extractable)), "추출_분모": len(extractable),
        "_추출_주의": ("분모 0 — 뽑을 만한 문서가 없었다. 발췌를 탓할 근거가 없다"
                       if not extractable else ""),
        "정확도": rate(len(verified), len(quotes)), "정확_분모": len(quotes),
        "적합률": rate(len(on_slot), len(ledger)), "적합_분모": len(ledger),
        "확인됨": sum(1 for r in ledger if r.get("label") == "확인됨"),
        "격리": {"off_slot": sum(1 for r in ledger if r.get("label") == "off_slot"),
                 "미검증": sum(1 for r in ledger if r.get("label") == "미검증")},
        "a1": (res.get("a1_audit") or {}),
        "stat_code": stat_code_outcome(run_id),
        "not_found_사유": _count([f.get("note", "")[:40] for f in findings
                                  if f.get("status") != "found"]),
    }


def stat_code_outcome(run_id: str) -> dict:
    """stat_code 를 **결과로** 3단 집계한다. 형식 검사로는 '100/100' 을 못 거른다 —
    호출해 봐야 안다.

        no_code            : A1 이 안 냈다 (정직). 라우팅이 web 으로 흘러간다 ← 라우팅 문제
        hallucinated_code  : 냈는데 그런 통계표가 없다 (err 21)          ← 모델 문제
        hit                : 냈고 값이 나왔다

    앞의 둘은 처방이 다르다. 섞어 세면 어느 쪽을 고칠지 알 수 없다.
    """
    res = _result(run_id)
    slots = {s["slot_id"]: s for s in (res.get("input", {}).get("slots") or [])}
    routes = {r["slot_id"]: r for r in _rows(run_id, "a2_route")}
    findings = {f["slot_id"]: f for f in _rows(run_id, "a3_finding")}

    stat_targets = {"TAM", "SAM"}
    out = {"no_code": [], "hallucinated_code": [], "hit": [], "other": []}
    for sid, s_ in slots.items():
        if s_.get("claim_type") not in stat_targets:
            continue
        if not s_.get("stat_code"):
            out["no_code"].append({"slot_id": sid,
                                   "routed_to": (routes.get(sid) or {}).get("adapter")})
            continue
        f = findings.get(sid) or {}
        note = (f.get("note") or "")
        if f.get("status") == "found":
            out["hit"].append({"slot_id": sid, "stat_code": s_["stat_code"]})
        elif "bad_stat_code" in note or "code=21" in note or "존재하지" in note:
            out["hallucinated_code"].append({"slot_id": sid, "stat_code": s_["stat_code"],
                                             "note": note[:80]})
        else:
            out["other"].append({"slot_id": sid, "stat_code": s_["stat_code"],
                                 "status": f.get("status"), "note": note[:80]})
    total = sum(len(v) for v in out.values())
    out["_요약"] = {k: len(v) for k, v in out.items() if not k.startswith("_")}
    out["_총_통계슬롯"] = total
    return out


def _count(xs) -> dict:
    out: dict = {}
    for x in xs:
        out[x] = out.get(x, 0) + 1
    return dict(sorted(out.items(), key=lambda kv: -kv[1])[:6])


def compare(a: dict, b: dict) -> None:
    print("\n=== 비교 (뒤가 새 실행) ===")
    if a["축"] != b["축"]:
        print("⚠ 실행 조건이 다르다 — 같은 그래프에 올리면 안 된다")
        for k in ("source_run", "from_stage", "adapters", "whitelist_version",
                  "scoring_version", "slot_set_hash", "slots_overlay"):
            if a["축"].get(k) != b["축"].get(k):
                print(f"   {k}: {a['축'].get(k)} → {b['축'].get(k)}")
    # 분모 키는 문자열 치환으로 만들지 않는다 — '률' 과 '율' 이 달라 분모 자리에 값이 찍혔다
    den = {"회수율": "회수_분모", "추출률": "추출_분모",
           "정확도": "정확_분모", "적합률": "적합_분모", "확인됨": ""}
    for k in ("회수율", "추출률", "정확도", "적합률", "확인됨"):
        va, vb = a.get(k), b.get(k)
        da, db = a.get(den[k], ""), b.get(den[k], "")
        if va is None or vb is None:
            print(f"{k:5} {va}(n={da}) → {vb}(n={db})   판단 불가")
            continue
        arrow = "↑ 개선" if vb > va else ("↓ 악화" if vb < va else "= 동일")
        warn = "  ⚠ 표본 작음" if isinstance(db, int) and db < 10 else ""
        print(f"{k:5} {va}(n={da}) → {vb}(n={db})   {arrow}{warn}")
    print("\n지표가 오른 프롬프트만 채택한다. 하나 바꾸고 한 번 잰다.")


def _out_path(argv: list[str]) -> str:
    """--out <path> 또는 --out=<path>."""
    for i, a in enumerate(argv):
        if a.startswith("--out="):
            return a.split("=", 1)[1]
        if a == "--out" and i + 1 < len(argv):
            return argv[i + 1]
    return ""


def main():
    # 리다이렉트(`> eval.json`)로 나갈 때 Windows 는 CP949 로 쓴다 — 한글이 깨진다.
    # 파일로 남길 때는 --out 을 쓰고, 그래도 리다이렉트하는 경우를 위해 방어한다.
    if not sys.stdout.isatty():
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass

    out = _out_path(sys.argv[1:])
    ids = [x for x in sys.argv[1:] if not x.startswith("-")]
    if out in ids:                      # `--out path` 의 path 는 run_id 가 아니다
        ids.remove(out)
    if not ids:
        ids = sorted([d for d in os.listdir(RUNS)
                      if os.path.isdir(os.path.join(RUNS, d))])[-1:]
    rows = [metrics(i) for i in ids]

    if out:
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        io.open(out, "w", encoding="utf-8").write(
            json.dumps(rows[0] if len(rows) == 1 else rows,
                       ensure_ascii=False, indent=2))
        print(f"기록: {out}  ({', '.join(ids)})")
    else:
        for m in rows:
            print(json.dumps(m, ensure_ascii=False, indent=2))
    if len(rows) == 2:
        compare(*rows)


if __name__ == "__main__":
    main()
