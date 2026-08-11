# -*- coding: utf-8 -*-
"""not_found 의 원인을 가른다 — "본문에 없음" vs "있는데 못 뽑음".

이 둘은 고칠 곳이 다르다:
    본문에 정말 없음  → 회수율 문제 → prompts.SEARCH
    있는데 못 뽑음    → **어느 지표에도 안 잡힌다** → prompts.EXTRACT

두 번째가 함정이다. 회수율은 "가져왔나"만 보고 정확도는 "뽑은 게 맞나"만 본다.
"가져왔는데 못 뽑았다"를 재는 지표가 없다.

    python tests/probe_notfound.py
"""
from __future__ import annotations
import io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import web
from base import load_env_key
from runlog import Meter, Run, load_rules
from schema import Slot

os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
from openai import OpenAI

rules = load_rules()
run = Run("probe-notfound", rules=rules, reference_date="2026-08-05")
meter = Meter(OpenAI(), run)

slot = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="TAM",
            subject="커피전문점", subject_code="KSIC-56221", metric="사업체 수",
            period="2023", unit="개", region="대한민국",
            must_contain=["커피"], value_range=[10000, 500000])

print("검색어:", web.plan_query(slot))
cands = web.search(slot, meter, "S1")
print(f"후보 {len(cands)}개\n")

# 슬롯이 찾는 것이 본문에 실제로 있는지 기계로 본다 (LLM 0회)
KEY = ["커피"]
NUM = re.compile(r"\d[\d,]*\s*(?:개|곳|개소)")
CNT = re.compile(r"(사업체|점포|매장|업체)\s*수|수\s*는")

rows = []
for c in cands:
    d = web.fetch(c, rules)
    text = d.text or ""
    has_kw = any(k in text for k in KEY)
    nums = NUM.findall(text)[:6]
    has_cnt = bool(CNT.search(text))
    rows.append({"url": d.url, "status": d.http_status, "content": d.content_status,
                 "len": d.text_len, "digits": d.digit_count,
                 "커피": has_kw, "개수표현": has_cnt, "숫자+단위": nums})
    print(f"[{d.content_status:8}] len={d.text_len:>6} dig={d.digit_count:>4} "
          f"커피={'O' if has_kw else 'X'} 개수표현={'O' if has_cnt else 'X'} "
          f"{d.url[:60]}")
    if nums:
        print(f"           숫자+단위: {nums}")
    # 슬롯 값범위에 드는 숫자가 실제로 있는지
    hits = [n for n in re.findall(r"\d[\d,]*", text)
            if 10000 <= int(n.replace(",", "")) <= 500000]
    if hits:
        print(f"           ★ 값범위(10,000~500,000) 안 숫자: {sorted(set(hits))[:8]}")
        for h in sorted(set(hits))[:2]:
            i = text.find(h)
            print(f"             …{text[max(0, i - 60):i + 40]}…".replace("\n", " "))

usable = [r for r in rows if r["content"] == "usable"]
candidate_docs = [r for r in usable if r["커피"] and r["숫자+단위"]]
print(f"\n── 판정 재료")
print(f"  usable 문서 {len(usable)}개 중, 키워드+숫자단위를 **동시에** 가진 문서: "
      f"{len(candidate_docs)}개")
print("  → 0개면 회수율 문제(SEARCH). 1개 이상이면 발췌 문제(EXTRACT)일 수 있다.")

io.open(os.path.join(HERE, "probe_notfound.json"), "w", encoding="utf-8").write(
    json.dumps(rows, ensure_ascii=False, indent=2))
print(f"\nLLM {run.counters.get('llm.calls', 0)}회 · 저장: tests/probe_notfound.json")
