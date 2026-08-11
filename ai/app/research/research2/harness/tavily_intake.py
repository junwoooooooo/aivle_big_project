# -*- coding: utf-8 -*-
"""Tavily 적재기 — 다른 검색 API 가 물어온 URL 을 **코퍼스에 넣기만** 한다.

    슬롯(subject·metric·period·region) → Tavily search(raw content) → 화이트리스트 1차 필터
        → runs/<tag>/{run.jsonl, a3_bodies.json} → data/direct_urls_<tag>.json (channel="tavily")

**기존 검색을 대체하지 않는다. 병합이다**(`run.py:344` 가 검색 결과에 얹는다).

두 가지를 반드시 지킨다:
  · **raw content 를 받는다.** `quote_verified` 는 인용문을 **본문 문자열과 대조**한다 —
    요약본만 받으면 원문에 없는 인용이 통과하거나 멀쩡한 인용이 죽는다(절대 규칙 3).
  · **채널을 `tavily` 로 남긴다.** 「검색이 찾았는가 · 사람이 넣었는가 · 다른 API 가
    물어왔는가」는 지표의 분모를 가르는 값이다.

키가 없으면 `not_configured` 를 값으로 남기고 **가짜 결과를 만들지 않는다.**

실행:
    python harness/tavily_intake.py --slots data/slots_beauty-noshow.json --only S15 --tag tavily-01
    python harness/tavily_intake.py ... --replay <응답파일>     # 네트워크 0회 (테스트용)
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from doc_intake import document_payload                            # noqa: E402
from slot_harness import _env_key                                  # noqa: E402

ENDPOINT = "https://api.tavily.com/search"


def _pin(key: str) -> str:
    """규칙 파일명을 `rules/rule_pins.json` 에서 읽는다 — **핀의 단일 원천**(판 ㉙ S0).

    없는 키면 **조용히 기본값을 고르지 않고 멈춘다**(명시 실패). 적재기가 어느 목록으로
    걸렀는지가 분모를 가르는 값이라, 추측한 목록으로 거르면 그 거름이 거짓이 된다.
    """
    pins = json.load(io.open(os.path.join(ROOT, "rules", "rule_pins.json"), encoding="utf-8"))
    if key not in pins["pins"]:
        raise KeyError(f"rule_pins.json 에 '{key}' 핀이 없다 — 추측하지 않고 멈춘다")
    return pins["pins"][key]


def whitelisted(url: str, wl: dict) -> str | None:
    """등재 도메인이면 그 kind, 아니면 None.

    ⚠ **판 ㉙ S4 부터 이 함수는 문지기가 아니라 «등급 힌트 기록기»다.**
    예전에는 None 이면 문서를 **버렸다**(하드 드롭). 그 결과 미등재 도메인은 원장에
    **흔적조차 남지 않았고**, 성적표의 「미확보」가 자료 부재인지 우리가 안 열어서인지
    구분할 수 없었다. 기준 v2 는 도메인을 **입장 심사가 아니라 등급 산정에만** 쓴다 —
    적재는 하고, 미등재는 `추정` 등급으로 표기된다.

    함수를 **삭제하지 않는 이유**: kind 를 `note` 에 남겨야 「적재 시점에 무엇으로 봤나」와
    「A4 가 최종적으로 무엇으로 매겼나」를 나중에 대조할 수 있다.
    """
    host = url.split("//", 1)[-1].split("/", 1)[0].lower()
    for kind, domains in (wl.get("kinds") or {}).items():
        if any(host == d or host.endswith("." + d) for d in domains):
            return kind
    return None


def rejected_domain(url: str, wl: dict) -> dict | None:
    """등재 **거부** 도메인이면 그 기록. 개방과 거부는 다른 물음이다.

    미등재(아직 안 봤다)는 이제 통과하지만, **거부(보고 나서 안 쓰기로 했다)** 는
    통과하지 않는다 — 백로그 30 이 지적한 「거부했다와 안 봤다가 동작상 같다」를
    적재 층에서도 가른다. 엔진 쪽 대응은 `a_desk.grade` 의 `채택` 축에 있다.
    """
    host = url.split("//", 1)[-1].split("/", 1)[0].lower()
    for r in (wl.get("rejected") or []):
        if host == r["domain"] or host.endswith("." + r["domain"]):
            return r
    return None


def query_of(slot: dict) -> str:
    parts = [slot.get("region"), slot.get("subject"), slot.get("metric"), slot.get("period")]
    return " ".join(p for p in parts if p)


def search(query: str, key: str, max_results: int = 5) -> dict:
    import requests
    r = requests.post(ENDPOINT, timeout=30, json={
        "api_key": key, "query": query, "max_results": max_results,
        "include_raw_content": True,          # ← 요약본이 아니라 원문. 절대 규칙 3
        "search_depth": "advanced"})
    r.raise_for_status()
    return r.json()


def to_docs(slot_id: str, payload: dict, wl: dict, tag: str) -> tuple[list, list]:
    """Tavily 응답 → (적재할 문서, 거른 것).

    **거르는 사유는 이제 둘뿐이다** (판 ㉙ S4):
      · `raw_content 없음` — 요약본으로는 인용을 원문 대조할 수 없다(절대 규칙 3).
        이것은 완화 대상이 **아니다** — 대조 못 하는 문서는 채택 요건을 구조적으로 못 채운다.
      · `등재 거부 도메인` — 보고 나서 안 쓰기로 한 것. 「아직 안 봤다」와 다르다.

    **미등재는 더 이상 거름 사유가 아니다.** 적재하고 `note` 에 `default:unlisted` 를 남긴다 —
    최종 등급은 A4 가 `등급표` 로 매기고, 미등재는 `추정` 으로 표기된다.
    """
    keep, dropped = [], []
    for i, res in enumerate(payload.get("results") or []):
        url, raw = res.get("url") or "", res.get("raw_content") or ""
        rj = rejected_domain(url, wl)
        if rj:
            dropped.append({"url": url, "why": f"등재 거부 도메인 — {rj.get('why', '')}"})
            continue
        if not raw.strip():
            dropped.append({"url": url, "why": "raw_content 없음 — 요약본으로는 인용 대조를 못 한다"})
            continue
        kind = whitelisted(url, wl)
        keep.append({"url": url, "text": raw, "http_status": "ok", "http_code": 200,
                     "content_status": "usable",
                     "published_at_raw": res.get("published_date"),
                     "note": f"kind={kind}" if kind else "kind=default:unlisted"})
    return keep, dropped


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--slots", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--only", default="", help="쉼표로 슬롯 id 지정. 비우면 전부")
    ap.add_argument("--replay", default="", help="저장된 응답으로 재적재 (네트워크 0회)")
    a = ap.parse_args()

    slots = json.load(io.open(a.slots, encoding="utf-8"))["slots"]
    if a.only:
        want = {x.strip() for x in a.only.split(",")}
        slots = [s for s in slots if s["slot_id"] in want]
    # 규칙 핀의 단일 원천은 `rules/rule_pins.json` 하나뿐이다(판 ㉙ S0).
    # 여기에 파일명을 리터럴로 적으면 엔진(v8)과 적재기(v5)가 갈라진다 — 실제로 갈라져 있었다.
    # 유리벽 밖이라 `runlog` 를 import 할 수 없으므로 **같은 데이터 파일**을 읽는다.
    wl = json.load(io.open(os.path.join(ROOT, "rules", _pin("whitelist")), encoding="utf-8"))
    outdir = os.path.join(ROOT, "runs", a.tag)
    os.makedirs(outdir, exist_ok=True)

    key = _env_key("TAVILY_API_KEY")
    replay = json.load(io.open(a.replay, encoding="utf-8")) if a.replay else None
    if not (key or replay):
        # 실패는 값이다 — 사양에 not_configured 를 남기고 끝낸다. 가짜 문서를 만들지 않는다.
        io.open(os.path.join(outdir, "intake_report.json"), "w", encoding="utf-8").write(
            json.dumps({"state": "not_configured", "why": "TAVILY_API_KEY 없음",
                        "slots": [s["slot_id"] for s in slots]}, ensure_ascii=False, indent=2))
        print("TAVILY_API_KEY 없음 → not_configured. 가짜 결과를 만들지 않는다.")
        return 2

    lines, bodies, report, dropped_all = [], {}, [], []
    for s in slots:
        q = query_of(s)
        payload = (replay or {}).get(s["slot_id"]) if replay else search(q, key)
        if payload is None:
            report.append({"slot_id": s["slot_id"], "query": q, "state": "응답 없음"})
            continue
        keep, dropped = to_docs(s["slot_id"], payload, wl, a.tag)
        dropped_all += [{**d, "slot_id": s["slot_id"]} for d in dropped]
        for i, doc in enumerate(keep):
            tid = f"{a.tag}-{s['slot_id']}-{i}"
            lines.append({"node": "a3_document",
                          "payload": document_payload(s["slot_id"], tid, doc, "tavily")})
            bodies[tid] = doc["text"]
        report.append({"slot_id": s["slot_id"], "query": q,
                       "kept": len(keep), "dropped": len(dropped)})
        print(f"  {s['slot_id']} '{q[:48]}' → 적재 {len(keep)} · 거름 {len(dropped)}")

    with io.open(os.path.join(outdir, "run.jsonl"), "w", encoding="utf-8") as f:
        for x in lines:
            f.write(json.dumps(x, ensure_ascii=False) + "\n")
    io.open(os.path.join(outdir, "a3_bodies.json"), "w", encoding="utf-8").write(
        json.dumps(bodies, ensure_ascii=False))
    io.open(os.path.join(outdir, "intake_report.json"), "w", encoding="utf-8").write(
        json.dumps({"state": "ok", "channel": "tavily", "slots": report,
                    "dropped": dropped_all}, ensure_ascii=False, indent=2))

    by_slot = {}
    for x in lines:
        p = x["payload"]
        by_slot.setdefault(p["slot_id"], []).append(p["url"])
    spec = os.path.join(ROOT, "data", f"direct_urls_{a.tag}.json")
    io.open(spec, "w", encoding="utf-8").write(json.dumps(
        {"_설명": f"{a.tag} — Tavily 가 물어온 URL. 병합용이며 심사는 엔진이 한다.",
         "channel": "tavily", "by_slot": by_slot}, ensure_ascii=False, indent=2))
    print(f"\n적재 {len(lines)}건 · 거름 {len(dropped_all)}건 → {outdir}\n사양: {spec}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
