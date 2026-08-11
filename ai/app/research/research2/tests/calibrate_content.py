# -*- coding: utf-8 -*-
"""content_status 기준값 실측 — 추측한 임계치는 그 자체가 버그다.

실제 페이지를 받아 text_len · digit_count 분포를 본 뒤 rules/scoring.v1.json 을 정한다.
LLM 0회.

    python tests/calibrate_content.py
"""
from __future__ import annotations
import io, json, os, sys

import requests
import trafilatura

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0 Safari/537.36")

# 유형별로 골고루 — 통계 포털(JS) · 언론 · 공식 가격 · 정부 보도자료 · 블로그
SAMPLES = [
    ("통계포털(JS 의심)", "https://kosis.kr/statHtml/statHtml.do?orgId=101&tblId=DT_1B040B3"),
    ("통계포털(JS 의심)", "https://kosis.kr/statHtml/statHtml.do?orgId=101&tblId=DT_1K52C01"),
    ("통계포털(목록)",   "https://kosis.kr/index/index.do"),
    ("정부 보도자료",     "https://www.mss.go.kr/site/smba/main.do"),
    ("언론 기사",         "https://imnews.imbc.com/news/2024/econo/article/6612670_36452.html"),
    ("언론 기사",         "https://www.etnews.com/20240526000012"),
    ("공식 가격",         "https://loyverse.com/ko/pricing"),
    ("공식 가격",         "https://www.cafepost.kr/main/price.html"),
    ("공식 소개",         "https://www.orderlist.co.kr/"),
    ("앱스토어",          "https://apps.apple.com/kr/app/id1459090715"),
]


def measure(url: str) -> dict:
    try:
        r = requests.get(url, headers={"User-Agent": UA}, timeout=20)
    except Exception as e:
        return {"url": url, "err": type(e).__name__}
    text = trafilatura.extract(r.text) or ""
    return {"url": url, "http": r.status_code,
            "text_len": len(text), "digits": sum(c.isdigit() for c in text),
            "html_len": len(r.text),
            "digit_ratio": round(sum(c.isdigit() for c in text) / max(len(text), 1), 4)}


def main():
    rows = []
    print(f"{'유형':16} {'text_len':>9} {'digits':>7} {'digit비율':>9}  url")
    for label, url in SAMPLES:
        m = measure(url)
        m["label"] = label
        rows.append(m)
        if m.get("err"):
            print(f"{label:16} {'ERR':>9} {m['err']}")
        else:
            print(f"{label:16} {m['text_len']:>9} {m['digits']:>7} {m['digit_ratio']:>9}  {url[:52]}")

    good = [r for r in rows if not r.get("err")]
    js = [r for r in good if r["text_len"] < 1000 and r["digits"] < 30]
    usable = [r for r in good if r not in js]
    print("\n── 관측")
    print(f"  본문 확보 실패로 보이는 것: {len(js)}건 "
          f"(text_len {sorted(r['text_len'] for r in js)}, digits {sorted(r['digits'] for r in js)})")
    print(f"  쓸 만한 본문: {len(usable)}건 "
          f"(text_len 최소 {min((r['text_len'] for r in usable), default=0)}, "
          f"digits 최소 {min((r['digits'] for r in usable), default=0)})")

    io.open(os.path.join(HERE, "calibrate_content.json"), "w", encoding="utf-8").write(
        json.dumps(rows, ensure_ascii=False, indent=2))
    print("\n저장: tests/calibrate_content.json")


if __name__ == "__main__":
    main()
