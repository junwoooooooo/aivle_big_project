# -*- coding: utf-8 -*-
"""코퍼스 적재기 — 사람이 준 URL(또는 검색 API 가 물어온 URL)의 **본문만** 넣어 준다.

    URL 목록 → HTTP fetch(순수, LLM 0회) → 본문 추출
             → runs/<tag>/{run.jsonl, a3_bodies.json}  (코퍼스 모양)
             → data/direct_urls_<tag>.json             (--direct-urls 사양)

**이것은 어댑터가 아니라 적재기다.** 심사는 하나도 하지 않는다 — 인용 대조·화이트리스트·
off_slot·값 일치는 전부 엔진이 `--direct-urls` 로 병합하면서 **원래 잣대 그대로** 한다
(`run.py:385 _collect_direct`). 여기서 해 주는 일이 늘어나는 순간 「사람이 넣은 근거」와
「검색이 찾은 근거」의 심사가 갈리고, 그 갈림은 원장에서 보이지 않는다.

유리벽: `blocks/` import 0 · 원장 쓰기 0. 새로 만드는 `runs/<tag>/` 는 **코퍼스일 뿐**
실행 기록이 아니다(`result.json` 을 쓰지 않는다).

실행:
    python harness/doc_intake.py --spec harness/intake/pain_beauty.json --tag userdocs-pain
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
import time

import requests
import trafilatura
from trafilatura.metadata import extract_metadata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

import pdf_text          # noqa: E402  — 잎 모듈. `blocks/` 가 아니므로 유리벽을 넘지 않는다

# `canonical_url` 을 import 하지 않는다 — 그건 `blocks/a_desk.py` 안이고 유리벽 밖이다.
# URL 정규화는 엔진이 병합할 때 자기 함수로 한다. 여기서는 **받은 URL 을 그대로** 넘긴다.

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0 Safari/537.36")


def fetch(url: str, timeout: int = 20) -> dict:
    """순수 HTTP. **모델이 snippet 으로 추측하는 경로를 구조적으로 막는다**(web 어댑터와 같은 이유).

    JS 껍데기면 본문이 비고, 그 사실이 `content_status="empty"` 로 남는다 — 봐주지 않는다.
    """
    try:
        r = requests.get(url, headers={"User-Agent": UA}, timeout=timeout)
        code, html = r.status_code, r.text
    except Exception as e:
        return {"url": url, "http_status": "fetch_failed", "http_code": 0,
                "content_status": "empty", "text": "", "note": f"{type(e).__name__}: {str(e)[:80]}"}

    # PDF 는 **엔진과 같은 잣대**로 읽는다(`pdf_text` 잎 모듈). 여기서만 다르게 읽으면
    # 「사람이 넣은 PDF」와 「검색이 물어온 PDF」의 본문이 갈리고 원장에서는 안 보인다.
    cfg = pdf_text.load_pdf_cfg()
    if cfg and pdf_text.is_pdf(r.content, r.headers.get("content-type", ""), cfg):
        ptext, why = pdf_text.extract(r.content, cfg)
        return {"url": url, "http_status": "ok" if code == 200 else "http_error",
                "http_code": code,
                "content_status": "usable" if ptext.strip()
                                  else cfg.get("unreadable_status", pdf_text.UNREADABLE),
                "text": ptext, "published_at_raw": None, "note": why}

    txt = trafilatura.extract(html, include_comments=False, include_tables=True) or ""
    pub = None
    try:
        md = extract_metadata(html)
        pub = getattr(md, "date", None)
    except Exception:
        pass
    return {"url": url, "http_status": "ok" if code == 200 else "http_error", "http_code": code,
            "content_status": "usable" if txt.strip() else "empty",
            "text": txt, "published_at_raw": pub, "note": ""}


def document_payload(slot_id: str, trace_id: str, doc: dict, channel: str) -> dict:
    """`adapters/base.py:88 make_document` 와 **같은 모양**. 필드가 어긋나면 A4 가 조용히 다르게 센다."""
    t = doc["text"]
    return {"slot_id": slot_id, "trace_id": trace_id, "url": doc["url"],
            "text": t[:400],                       # run.jsonl 은 400자 절단(엔진과 동일)
            "published_at_raw": doc.get("published_at_raw"),
            "http_status": doc["http_status"], "http_code": doc["http_code"],
            "content_status": doc["content_status"], "text_len": len(t),
            "digit_count": sum(c.isdigit() for c in t),
            "has_table": False, "channel": channel}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", required=True,
                    help='{"channel": "...", "_경계": "...", "by_slot": {"S16": [url, ...]}}')
    ap.add_argument("--tag", required=True, help="코퍼스 run 디렉터리 이름")
    ap.add_argument("--sleep", type=float, default=1.0)
    a = ap.parse_args()

    spec = json.load(io.open(a.spec, encoding="utf-8"))
    channel = spec.get("channel") or "user_doc"
    outdir = os.path.join(ROOT, "runs", a.tag)
    os.makedirs(outdir, exist_ok=True)

    lines, bodies, report = [], {}, []
    for slot_id, urls in spec["by_slot"].items():
        for i, url in enumerate(urls):
            doc = fetch(url)
            tid = f"{a.tag}-{slot_id}-{i}"
            lines.append({"node": "a3_document",
                          "payload": document_payload(slot_id, tid, doc, channel)})
            bodies[tid] = doc["text"]
            report.append({"slot_id": slot_id, "url": url, "trace_id": tid,
                           "http": doc["http_code"], "content_status": doc["content_status"],
                           "chars": len(doc["text"]), "note": doc["note"]})
            print(f"  {slot_id} {doc['http_code']} {doc['content_status']:6} "
                  f"{len(doc['text']):6}자  {url[:70]}")
            time.sleep(a.sleep)

    with io.open(os.path.join(outdir, "run.jsonl"), "w", encoding="utf-8") as f:
        for x in lines:
            f.write(json.dumps(x, ensure_ascii=False) + "\n")
    io.open(os.path.join(outdir, "a3_bodies.json"), "w", encoding="utf-8").write(
        json.dumps(bodies, ensure_ascii=False))
    io.open(os.path.join(outdir, "intake_report.json"), "w", encoding="utf-8").write(
        json.dumps({"spec": a.spec, "channel": channel, "_경계": spec.get("_경계"),
                    "docs": report}, ensure_ascii=False, indent=2))

    # --direct-urls 사양. 본문이 안 잡힌 URL 은 **넣지 않는다** — 넣으면 엔진이
    # "코퍼스에 본문 없음" 으로 다시 세고, 같은 실패가 두 번 계상된다.
    usable = {}
    for r in report:
        if r["content_status"] == "usable":
            usable.setdefault(r["slot_id"], []).append(r["url"])
    out_spec = os.path.join(ROOT, "data", f"direct_urls_{a.tag}.json")
    io.open(out_spec, "w", encoding="utf-8").write(json.dumps(
        {"_설명": f"{a.tag} 적재분. 입수만 사람이고 심사는 엔진이 원래 잣대로 한다.",
         "_경계": spec.get("_경계"), "channel": channel, "by_slot": usable},
        ensure_ascii=False, indent=2))

    ok = sum(1 for r in report if r["content_status"] == "usable")
    print(f"\n적재 {ok}/{len(report)} usable → {outdir}\n사양: {out_spec}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
